package org.pipelineframework.command;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.pipelineframework.connector.*;

class CommandRecoveryStateContractTest {
    private static final CommandRecoveryBinding BINDING = binding("tenant", "command", "occurrence", "attempt", "a".repeat(64), "account-1");
    private static final CommandReconciliationReceipt RECEIPT = new CommandReconciliationReceipt(
        new CommandReference("receipt", "receipt-1", CommandReferencePurpose.RECONCILIATION), "c".repeat(64), 4L);

    @Test
    void pendingClaimIsStrictAndRetainsOneAttemptAndOriginalBinding() {
        CommandEffectRecord pending = pending().bindRecovery(BINDING);
        CommandEffectRecord claimed = pending.claimPendingDispatch(BINDING, 2L);
        assertEquals(CommandEffectStatus.PENDING, pending.status());
        assertEquals(CommandEffectStatus.DISPATCHING, claimed.status());
        assertEquals(1, claimed.attempts().size());
        assertEquals("attempt", claimed.currentAttempt().attemptId());
        assertEquals("occurrence", claimed.currentAttempt().occurrenceId());
        assertEquals(Optional.of(BINDING), claimed.currentAttempt().recoveryBinding());
        assertThrows(IllegalStateException.class, () -> claimed.claimPendingDispatch(BINDING, 3L));
        assertThrows(IllegalStateException.class, () -> claimed.dispatching("attempt", 3L));
    }

    @Test
    void unboundOldRecordsAndLateBindingRemainClosed() {
        CommandEffectRecord legacy = pending();
        assertTrue(legacy.currentAttempt().recoveryBinding().isEmpty());
        assertTrue(legacy.currentAttempt().reconciliationReceipt().isEmpty());
        assertThrows(IllegalStateException.class, () -> legacy.claimPendingDispatch(BINDING, 2L));
        assertThrows(IllegalStateException.class, () -> legacy.dispatching("attempt", 2L).bindRecovery(BINDING));
        assertThrows(IllegalStateException.class, () -> legacy.bindRecovery(BINDING).bindRecovery(BINDING));
    }

    @Test
    void authoritativeSuccessSettlesSameAttemptAndBlocksLateAckOrFailure() {
        CommandEffectRecord dispatching = pending().bindRecovery(BINDING).claimPendingDispatch(BINDING, 2L);
        CommandEffectRecord succeeded = dispatching.reconcileSucceeded(
            BINDING, CommandEffectStatus.DISPATCHING, new Result("confirmed"), snapshot(), RECEIPT, 4L);
        assertEquals(CommandEffectStatus.SUCCEEDED, succeeded.status());
        assertEquals(new Result("confirmed"), succeeded.output());
        assertEquals(Optional.of(new Result("confirmed")), succeeded.currentAttempt().output());
        assertEquals(1, succeeded.attempts().size());
        assertEquals(Optional.of(RECEIPT), succeeded.currentAttempt().reconciliationReceipt());
        assertEquals(Optional.of(BINDING), succeeded.currentAttempt().recoveryBinding());
        assertEquals(CommandEffectStatus.DISPATCHING, dispatching.status());
        assertThrows(IllegalStateException.class, () -> succeeded.succeeded("attempt", new Result("late"), 5L));
        assertThrows(IllegalStateException.class, () -> succeeded.failed("attempt", new RuntimeException("late"), 5L));
        assertThrows(IllegalStateException.class, () -> succeeded.reconcileSucceeded(
            BINDING, CommandEffectStatus.DISPATCHING, new Result("conflict"), snapshot(), RECEIPT, 5L));
    }

    @Test
    void staleAndMismatchedBindingCannotClaimOrSettleAndOriginalRecordIsUnchanged() {
        CommandEffectRecord pending = pending().bindRecovery(BINDING);
        CommandEffectRecord dispatching = pending.claimPendingDispatch(BINDING, 2L);
        for (CommandRecoveryBinding wrong : List.of(
            binding("other", "command", "occurrence", "attempt", "a".repeat(64), "account-1"),
            binding("tenant", "other", "occurrence", "attempt", "a".repeat(64), "account-1"),
            binding("tenant", "command", "other", "attempt", "a".repeat(64), "account-1"),
            binding("tenant", "command", "occurrence", "old-attempt", "a".repeat(64), "account-1"),
            binding("tenant", "command", "occurrence", "attempt", "d".repeat(64), "account-1"),
            binding("tenant", "command", "occurrence", "attempt", "a".repeat(64), "account-2"))) {
            assertThrows(IllegalStateException.class, () -> pending.claimPendingDispatch(wrong, 3L));
            assertThrows(IllegalStateException.class, () -> dispatching.reconcileSucceeded(
                wrong, CommandEffectStatus.DISPATCHING, new Result("wrong"), snapshot(), RECEIPT, 4L));
        }
        assertEquals(pending().bindRecovery(BINDING), pending);
        assertEquals(pending.claimPendingDispatch(BINDING, 2L), dispatching);
    }

    @Test
    void settlementCannotClearPendingFailureDlqOrUserConfirmationBarriers() {
        CommandEffectRecord dispatching = pending().bindRecovery(BINDING).claimPendingDispatch(BINDING, 2L);
        assertThrows(IllegalStateException.class, () -> pending().bindRecovery(BINDING).reconcileSucceeded(
            BINDING, CommandEffectStatus.PENDING, new Result("wrong"), snapshot(), RECEIPT, 4L));
        for (CommandEffectStatus status : List.of(CommandEffectStatus.FAILED_RETRYABLE, CommandEffectStatus.DLQ,
            CommandEffectStatus.USER_ACTION_REQUIRED)) {
            CommandEffectRecord barrier = dispatching.failedWithStatus("attempt", status, null, null, 3L);
            assertThrows(IllegalStateException.class, () -> barrier.reconcileSucceeded(
                BINDING, status, new Result("wrong"), snapshot(), RECEIPT, 4L));
        }
        CommandEffectRecord ambiguous = dispatching.failedWithStatus("attempt", CommandEffectStatus.AMBIGUOUS, null, null, 3L);
        assertEquals(CommandEffectStatus.SUCCEEDED, ambiguous.reconcileSucceeded(
            BINDING, CommandEffectStatus.AMBIGUOUS, new Result("confirmed"), snapshot(), RECEIPT, 4L).status());
        assertThrows(IllegalStateException.class, () -> ambiguous.reconcileSucceeded(
            BINDING, CommandEffectStatus.DISPATCHING, new Result("stale"), snapshot(), RECEIPT, 4L));
    }

    @Test
    void ordinaryTerminalTransitionsRetainBindingWithoutInventingReceipt() {
        CommandEffectRecord dispatching = pending().bindRecovery(BINDING).claimPendingDispatch(BINDING, 2L);
        for (CommandEffectRecord terminal : List.of(
            dispatching.succeeded("attempt", new Result("ordinary"), snapshot(), 3L),
            dispatching.failed("attempt", new IllegalStateException("failure"), 3L),
            dispatching.dlq("attempt", new IllegalStateException("terminal"), 3L))) {
            assertEquals(Optional.of(BINDING), terminal.currentAttempt().recoveryBinding());
            assertTrue(terminal.currentAttempt().reconciliationReceipt().isEmpty());
        }
    }

    @Test
    void outcomeMustMatchPersistedOperationAndConfiguration() {
        CommandEffectRecord dispatching = pending().bindRecovery(BINDING).claimPendingDispatch(BINDING, 2L);
        CommandOutcomeSnapshot valid = snapshot();
        CommandOutcomeSnapshot wrong = new CommandOutcomeSnapshot(valid.operationIdentity(), valid.providerMajorVersion(),
            new ConnectorConfigurationSnapshot("configuration", 1, "d".repeat(64), List.of()),
            valid.outcomeStatus(), valid.outcomeCode(), valid.flags(), valid.machineConfirmation(), valid.userConfirmed(), valid.references());
        assertThrows(IllegalArgumentException.class, () -> dispatching.reconcileSucceeded(
            BINDING, CommandEffectStatus.DISPATCHING, new Result("wrong"), wrong, RECEIPT, 4L));
    }

    @Test
    void legacyStoreDefaultsDoNotFallThroughToUnfencedMutations() {
        CommandEffectStore oldStore = new CommandEffectStore() {
            public io.smallrye.mutiny.Uni<Optional<CommandEffectRecord>> find(String tenant, String command) {
                throw new AssertionError("legacy find must not grant recovery");
            }
            public io.smallrye.mutiny.Uni<CommandEffectRecord> createPending(CommandRequest<?> request, long now) {
                throw new AssertionError("legacy reservation must not grant recovery");
            }
            public io.smallrye.mutiny.Uni<CommandEffectRecord> markDispatching(String tenant, String command, long now) {
                throw new AssertionError("legacy dispatch must not grant recovery");
            }
            public io.smallrye.mutiny.Uni<CommandEffectRecord> markSucceeded(String tenant, String command, Object output, long now) {
                throw new AssertionError("legacy success must not grant recovery");
            }
            public io.smallrye.mutiny.Uni<CommandEffectRecord> markFailed(String tenant, String command, Throwable failure, long now) {
                throw new AssertionError("legacy failure must not grant recovery");
            }
            public io.smallrye.mutiny.Uni<CommandEffectRecord> markDlq(String tenant, String command, Throwable failure, long now) {
                throw new AssertionError("legacy terminal must not grant recovery");
            }
        };
        assertFalse(oldStore.supportsRecovery());
        assertThrows(UnsupportedOperationException.class, () -> oldStore.createPending(null, BINDING, 1L).await().indefinitely());
        assertThrows(UnsupportedOperationException.class,
            () -> oldStore.createAttempt(null, CommandAttemptAdmission.retry(), BINDING, 1L).await().indefinitely());
        assertThrows(UnsupportedOperationException.class, () -> oldStore.claimPendingDispatch(BINDING, 2L).await().indefinitely());
        assertThrows(UnsupportedOperationException.class, () -> oldStore.reconcileSucceeded(
            BINDING, CommandEffectStatus.DISPATCHING, new Result("output"), snapshot(), RECEIPT, 4L).await().indefinitely());
    }

    private static CommandEffectRecord pending() {
        return new CommandEffectRecord("tenant", "execution", "step", "configured.write", "command", CommandEffectStatus.PENDING,
            "input", null, null, null, Optional.empty(), List.of(new CommandEffectAttemptRecord(
                "attempt", "occurrence", 1, "execution", CommandAttemptPurpose.INITIAL, CommandEffectStatus.PENDING,
                Optional.empty(), null, null, Optional.empty(), Optional.empty(), 1L, 1L)), 1L, 1L);
    }

    private static CommandOutcomeSnapshot snapshot() {
        return new CommandOutcomeSnapshot(BINDING.operationIdentity(), 1, BINDING.operationConfiguration(),
            CommandEffectStatus.SUCCEEDED, "succeeded", Set.of(), CommandMachineConfirmation.PROVIDER_ACKNOWLEDGED, false, List.of(RECEIPT.reference()));
    }

    private static CommandRecoveryBinding binding(String tenant, String command, String occurrence, String attempt, String inputDigest, String target) {
        ConnectorConfigurationSnapshot configuration = new ConnectorConfigurationSnapshot("configuration", 1, "b".repeat(64), List.of());
        return new CommandRecoveryBinding(tenant, command, occurrence, attempt, "execution", "pipeline", "1", "release-1", "step",
            new ConnectorOperationIdentity(new ConnectorProviderId("provider"), "write", ConnectorOperationKind.COMMAND, 1),
            1, ConnectorBindingName.of("configured"), String.class.getName(), Result.class.getName(), inputDigest,
            configuration, new CommandRecoveryTarget(target, configuration));
    }

    record Result(String value) {}
}
