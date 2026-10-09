package org.pipelineframework.connector;

import java.util.Objects;

/**
 * Provider-authoritative read-only reconciliation. An unresolved result never permits dispatch.
 * A success means the provider verified an immutable receipt against the full original binding;
 * echoing the supplied identity, absence, elapsed time or an eventually consistent read is not proof.
 */
public sealed interface CommandReconciliationResult<O>
    permits CommandReconciliationResult.ConfirmedSucceeded, CommandReconciliationResult.Unresolved {

    record ConfirmedSucceeded<O>(
        CommandRecoveryBinding binding,
        CommandOutcome.Succeeded<O> outcome,
        CommandReference receipt
    ) implements CommandReconciliationResult<O> {
        public ConfirmedSucceeded {
            binding = Objects.requireNonNull(binding, "verified binding must not be null");
            outcome = Objects.requireNonNull(outcome, "verified success must not be null");
            receipt = Objects.requireNonNull(receipt, "authoritative receipt must not be null");
            if (receipt.purpose() != CommandReferencePurpose.RECONCILIATION) {
                throw new IllegalArgumentException("authoritative receipt must be a reconciliation reference");
            }
        }
    }

    record Unresolved<O>(String code) implements CommandReconciliationResult<O> {
        public Unresolved {
            if (code == null || !code.matches("[a-z][a-z0-9-]{0,127}")) {
                throw new IllegalArgumentException("unresolved code must be lowercase letters, digits or hyphens");
            }
        }
    }
}
