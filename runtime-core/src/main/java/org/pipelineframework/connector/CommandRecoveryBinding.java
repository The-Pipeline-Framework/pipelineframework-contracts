package org.pipelineframework.connector;

import java.util.Objects;

/**
 * Immutable original request identity retained before a recoverable Command reservation.
 * Input digest covers the deterministic typed durable representation, never toString/object identity.
 * Configuration snapshots and target identity must exclude resolved secrets and credential hashes.
 */
public record CommandRecoveryBinding(
    String tenantId,
    String commandId,
    String occurrenceId,
    String attemptId,
    String executionId,
    String pipelineId,
    String contractVersion,
    String releaseVersion,
    String stepId,
    ConnectorOperationIdentity operationIdentity,
    int providerMajorVersion,
    ConnectorBindingName binding,
    String inputType,
    String outputType,
    String inputDigest,
    ConnectorConfigurationSnapshot operationConfiguration,
    CommandRecoveryTarget target
) {
    public CommandRecoveryBinding {
        requireText(tenantId, "tenantId");
        requireText(commandId, "commandId");
        requireText(occurrenceId, "occurrenceId");
        requireText(attemptId, "attemptId");
        requireText(executionId, "executionId");
        requireText(pipelineId, "pipelineId");
        requireText(contractVersion, "contractVersion");
        requireText(releaseVersion, "releaseVersion");
        requireText(stepId, "stepId");
        operationIdentity = Objects.requireNonNull(operationIdentity, "operation identity must not be null");
        if (operationIdentity.kind() != ConnectorOperationKind.COMMAND) {
            throw new IllegalArgumentException("recovery binding requires a Command operation");
        }
        if (providerMajorVersion < 1) {
            throw new IllegalArgumentException("provider major version must be positive");
        }
        binding = Objects.requireNonNull(binding, "binding must not be null");
        requireText(inputType, "inputType");
        requireText(outputType, "outputType");
        if (inputDigest == null || !inputDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("input digest must be lowercase SHA-256");
        }
        operationConfiguration = Objects.requireNonNull(operationConfiguration, "operation configuration must not be null");
        target = Objects.requireNonNull(target, "recovery target must not be null");
        if (!operationConfiguration.digest().matches("[0-9a-f]{64}")
            || !target.configuration().digest().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("configuration digests must be lowercase SHA-256");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(name + " must be non-blank without surrounding whitespace");
        }
    }
}
