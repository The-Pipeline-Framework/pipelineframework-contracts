package org.pipelineframework.orchestrator;

import java.util.Objects;
import java.util.Optional;

/**
 * Original admission receipt with optional still-available native execution state.
 * An absent creation is historical replay, not unknown admission or permission to recreate/enqueue a root.
 */
public record ExecutionAdmissionResult(Optional<CreateExecutionResult> creation, ExecutionAdmissionReceipt receipt) {
    public ExecutionAdmissionResult {
        creation = Objects.requireNonNull(creation, "creation");
        receipt = Objects.requireNonNull(receipt, "receipt");
        if (creation.isPresent()) {
            ExecutionRecord<Object, Object> execution = Objects.requireNonNull(creation.get().record(), "creation.record");
            if (!execution.executionId().equals(receipt.executionId())
                || !execution.tenantId().equals(receipt.tenantId()) || !execution.pipelineId().equals(receipt.pipelineId())
                || !execution.contractVersion().equals(receipt.contractVersion())
                || !execution.releaseVersion().equals(receipt.releaseVersion())) {
                throw new IllegalArgumentException("Admission receipt must identify the created execution and immutable pin");
            }
        }
    }

    /** True only for a newly committed execution; live and historical replay never grant enqueue authority. */
    public boolean newlyCreated() {
        return creation.filter(value -> !value.duplicate()).isPresent();
    }
}
