package org.pipelineframework.connector;

import java.util.Objects;

/**
 * Provider-attested stable destination identity and sanitized provider configuration.
 * The identity must distinguish accounts/destinations even when a logical connection is retargeted.
 * It is a non-sensitive opaque identifier, not a credential, credential hash, URL or payload.
 */
public record CommandRecoveryTarget(String identity, ConnectorConfigurationSnapshot configuration) {
    public CommandRecoveryTarget {
        new CommandReference("recovery.target", identity, CommandReferencePurpose.RECONCILIATION);
        configuration = Objects.requireNonNull(configuration, "provider configuration snapshot must not be null");
    }
}
