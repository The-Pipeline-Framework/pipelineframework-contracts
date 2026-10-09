package org.pipelineframework.connector;

import java.util.Objects;

/**
 * Read-only inquiry for the original effect, with the original durable request binding.
 * Runtime must validate typed input/configuration digests before constructing this invocation.
 */
public record CommandReconciliationInvocation<I, C>(
    CommandInvocation<I, C> invocation,
    CommandRecoveryBinding binding
) {
    public CommandReconciliationInvocation {
        invocation = Objects.requireNonNull(invocation, "command invocation must not be null");
        binding = Objects.requireNonNull(binding, "recovery binding must not be null");
        CommandDispatchIdentity identity = invocation.dispatchIdentity().orElseThrow(() ->
            new IllegalArgumentException("reconciliation requires original dispatch identity"));
        ConnectorExecutionContext context = invocation.executionContext();
        if (!identity.commandId().equals(binding.commandId())
            || !identity.occurrenceId().equals(binding.occurrenceId())
            || !identity.attemptId().equals(binding.attemptId())
            || !context.tenantId().filter(binding.tenantId()::equals).isPresent()
            || !context.executionId().filter(binding.executionId()::equals).isPresent()
            || !context.pipelineId().filter(binding.pipelineId()::equals).isPresent()
            || !context.contractVersion().filter(binding.contractVersion()::equals).isPresent()
            || !context.releaseVersion().filter(binding.releaseVersion()::equals).isPresent()
            || !context.stepId().filter(binding.stepId()::equals).isPresent()
            || !context.invocationTarget().filter(
                new ConnectorInvocationTarget(binding.binding(), binding.operationIdentity())::equals).isPresent()
            || !invocation.outputType().getName().equals(binding.outputType())) {
            throw new IllegalArgumentException("reconciliation invocation does not match original recovery binding");
        }
    }
}
