package org.pipelineframework.connector;

import java.util.concurrent.CompletionStage;
import java.util.Optional;

/**
 * Command-family operation contract. Command outcome semantics are defined by the command runtime work.
 */
public interface CommandOperation<I, C, O> extends ConnectorOperation {
    default java.util.List<ConnectorOperationCallbackDescriptor> callbacks() {
        return java.util.List.of();
    }

    default CommandCapabilities capabilities() {
        return CommandCapabilities.conservative();
    }

    default Optional<ConnectorConfigSchema<C>> configurationSchema() {
        return Optional.empty();
    }

    CompletionStage<CommandOutcome<O>> dispatch(CommandInvocation<I, C> invocation);

    /**
     * Opt-in stable destination binding, evaluated before reservation and again before recovery.
     * Providers must derive this from authoritative non-secret destination/configuration identity,
     * not merely a logical connection name. Empty means this invocation is not recoverable.
     */
    default Optional<CommandRecoveryTarget> recoveryTarget(CommandInvocation<I, C> invocation) {
        return Optional.empty();
    }

    /**
     * Read-only authoritative inquiry, never a dispatch or retry. Capability declarations alone
     * do not implement this hook. Old providers remain unresolved; missing/stale/conflicting
     * evidence must not be promoted to success or permission to repeat an effect.
     */
    default CompletionStage<CommandReconciliationResult<O>> reconcile(CommandReconciliationInvocation<I, C> invocation) {
        return java.util.concurrent.CompletableFuture.completedFuture(
            new CommandReconciliationResult.Unresolved<>("reconciliation-unsupported"));
    }

    default CompletionStage<CommandOutcome<O>> dispatch(
        I input,
        ConnectorConfigurationDocument configuration,
        ConnectorExecutionContext executionContext
    ) {
        ConnectorConfigSchema<C> schema = configurationSchema().orElseThrow(() -> new ConnectorConfigurationException(
            "command operation " + id() + " does not declare a configuration schema"));
        C boundConfiguration = ConnectorConfigurationBinder.bind(schema, configuration, "command operation " + id());
        return dispatch(new CommandInvocation<>(input, boundConfiguration, executionContext));
    }
}
