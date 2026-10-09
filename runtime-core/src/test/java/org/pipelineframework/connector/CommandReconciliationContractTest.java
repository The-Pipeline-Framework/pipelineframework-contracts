package org.pipelineframework.connector;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommandReconciliationContractTest {
    @Test
    void operationExposesSeparateOptInInquiryAndTargetBinding() {
        assertTrue(Arrays.stream(CommandOperation.class.getMethods()).anyMatch(method -> method.getName().equals("reconcile")));
        assertTrue(Arrays.stream(CommandOperation.class.getMethods()).anyMatch(method -> method.getName().equals("recoveryTarget")));
    }

    @Test
    void oldProviderEvenWithCapabilityDeclarationDoesNotAcquireRecovery() {
        CommandOperation<String, String, String> operation = new CommandOperation<>() {
            public String id() { return "write"; }
            public CommandCapabilities capabilities() {
                return new CommandCapabilities(true, true, true, CommandMachineConfirmation.NONE, false, Set.of());
            }
            public CompletionStage<CommandOutcome<String>> dispatch(CommandInvocation<String, String> invocation) {
                return CompletableFuture.failedFuture(new AssertionError("inquiry must not dispatch"));
            }
        };
        CommandRecoveryBinding binding = binding("tenant", "command", "occurrence", "attempt");
        CommandInvocation<String, String> invocation = invocation(binding);
        assertTrue(operation.recoveryTarget(invocation).isEmpty());
        assertEquals(new CommandReconciliationResult.Unresolved<>("reconciliation-unsupported"),
            operation.reconcile(new CommandReconciliationInvocation<>(invocation, binding)).toCompletableFuture().join());
    }

    @Test
    void invocationRequiresExactOriginalTenantCommandOccurrenceAttemptAndManagedTarget() {
        CommandRecoveryBinding original = binding("tenant", "command", "occurrence", "attempt");
        assertDoesNotThrow(() -> new CommandReconciliationInvocation<>(invocation(original), original));
        for (CommandRecoveryBinding wrong : List.of(
            binding("other", "command", "occurrence", "attempt"),
            binding("tenant", "other", "occurrence", "attempt"),
            binding("tenant", "command", "other", "attempt"),
            binding("tenant", "command", "occurrence", "other"))) {
            assertThrows(IllegalArgumentException.class,
                () -> new CommandReconciliationInvocation<>(invocation(original), wrong));
        }
        assertThrows(IllegalArgumentException.class, () -> new CommandReconciliationInvocation<>(
            new CommandInvocation<>("input", "config", String.class, ConnectorExecutionContext.empty(),
                Optional.of(new CommandDispatchIdentity("command", "occurrence", "attempt"))), original));
    }

    @Test
    void receiptCannotBeMissingOrMerelyCorrelationAndUnresolvedIsNotDispatchPermission() {
        CommandRecoveryBinding binding = binding("tenant", "command", "occurrence", "attempt");
        CommandOutcome.Succeeded<String> success = new CommandOutcome.Succeeded<>(
            "output", CommandConfirmation.none(), List.of());
        assertThrows(NullPointerException.class,
            () -> new CommandReconciliationResult.ConfirmedSucceeded<>(binding, success, null));
        assertThrows(IllegalArgumentException.class, () -> new CommandReconciliationResult.ConfirmedSucceeded<>(
            binding, success, new CommandReference("receipt", "r-1", CommandReferencePurpose.CORRELATION)));
        assertInstanceOf(CommandReconciliationResult.Unresolved.class,
            new CommandReconciliationResult.Unresolved<>("provider-not-found"));
        assertThrows(IllegalArgumentException.class, () -> new CommandReconciliationResult.Unresolved<>("unsafe text"));
    }

    @Test
    void targetIsOpaqueAndBindingDigestsAreTypedStableMetadata() {
        ConnectorConfigurationSnapshot configuration = new ConnectorConfigurationSnapshot("provider", 1, "b".repeat(64), List.of());
        assertThrows(IllegalArgumentException.class, () -> new CommandRecoveryTarget("https://provider/account", configuration));
        assertThrows(IllegalArgumentException.class, () -> new CommandRecoveryTarget("arbitrary secret payload", configuration));
        assertEquals("account-1", new CommandRecoveryTarget("account-1", configuration).identity());
    }

    @Test
    void recoveryBindingAcceptsEqualRestoredCommandKindButRejectsOtherKinds() {
        ConnectorOperationKind restored = new ConnectorOperationKind("tpf:command");
        assertNotSame(ConnectorOperationKind.COMMAND, restored);
        assertEquals(ConnectorOperationKind.COMMAND, restored);
        assertEquals(binding("tenant", "command", "occurrence", "attempt"),
            binding("tenant", "command", "occurrence", "attempt", restored));
        for (ConnectorOperationKind other : List.of(ConnectorOperationKind.QUERY,
            new ConnectorOperationKind("provider:command"))) {
            assertThrows(IllegalArgumentException.class,
                () -> binding("tenant", "command", "occurrence", "attempt", other));
        }
    }

    private static CommandInvocation<String, String> invocation(CommandRecoveryBinding binding) {
        return new CommandInvocation<>("input", "config", String.class,
            ConnectorExecutionContext.managed(binding.tenantId(), "execution", "pipeline", "1", "release-1", "step",
                new ConnectorInvocationTarget(binding.binding(), binding.operationIdentity()),
                Optional.empty(), Optional.empty(), Optional.empty()),
            Optional.of(new CommandDispatchIdentity(binding.commandId(), binding.occurrenceId(), binding.attemptId())));
    }

    private static CommandRecoveryBinding binding(String tenant, String command, String occurrence, String attempt) {
        return binding(tenant, command, occurrence, attempt, ConnectorOperationKind.COMMAND);
    }

    private static CommandRecoveryBinding binding(String tenant, String command, String occurrence, String attempt,
        ConnectorOperationKind kind) {
        ConnectorConfigurationSnapshot configuration = new ConnectorConfigurationSnapshot("configuration", 1, "b".repeat(64), List.of());
        return new CommandRecoveryBinding(tenant, command, occurrence, attempt, "execution", "pipeline", "1", "release-1", "step",
            new ConnectorOperationIdentity(new ConnectorProviderId("provider"), "write", kind, 1),
            1, ConnectorBindingName.of("configured"), String.class.getName(), String.class.getName(), "a".repeat(64),
            configuration, new CommandRecoveryTarget("account-1", configuration));
    }
}
