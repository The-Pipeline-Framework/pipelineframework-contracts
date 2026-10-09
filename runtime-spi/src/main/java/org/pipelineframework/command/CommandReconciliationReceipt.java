package org.pipelineframework.command;

import java.util.Objects;
import org.pipelineframework.connector.CommandReference;
import org.pipelineframework.connector.CommandReferencePurpose;

/** Sanitized settlement provenance; never arbitrary provider evidence or resolved credentials. */
public record CommandReconciliationReceipt(CommandReference reference, String outputDigest, long settledAtEpochMs) {
    public CommandReconciliationReceipt {
        reference = Objects.requireNonNull(reference, "reconciliation reference must not be null");
        if (reference.purpose() != CommandReferencePurpose.RECONCILIATION) {
            throw new IllegalArgumentException("receipt must be a reconciliation reference");
        }
        if (outputDigest == null || !outputDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("output digest must be lowercase SHA-256");
        }
        if (settledAtEpochMs < 0) {
            throw new IllegalArgumentException("settlement timestamp must not be negative");
        }
    }
}
