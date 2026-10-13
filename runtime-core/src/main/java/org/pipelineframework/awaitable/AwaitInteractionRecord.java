package org.pipelineframework.awaitable;

import java.util.Map;
import java.util.Optional;
import java.util.Objects;

/**
 * Durable projection for a single await interaction.
 *
 * @param tenantId tenant id
 * @param executionId owning execution id
 * @param stepId owning await step id
 * @param stepIndex owning await step index
 * @param outputType expected await output Java type
 * @param interactionId framework-owned interaction id
 * @param correlationId adapter-visible correlation id
 * @param causationId id that caused this interaction
 * @param idempotencyKey duplicate suppression key
 * @param version optimistic-concurrency version
 * @param status interaction status
 * @param requestPayload request snapshot
 * @param responsePayload response snapshot
 * @param unitId owning await unit id
 * @param itemIndex zero-based item index when the await unit owns multiple ordered items
 * @param actor completing actor, if any
 * @param assignee assigned user, if any
 * @param group assigned group, if any
 * @param transportType adapter type
 * @param transportMetadata transport metadata
 * @param deadlineEpochMs absolute deadline
 * @param createdAtEpochMs creation timestamp
 * @param updatedAtEpochMs update timestamp
 * @param ttlEpochS expiry timestamp
 */
public record AwaitInteractionRecord(
    String tenantId,
    String executionId,
    String stepId,
    int stepIndex,
    String outputType,
    String interactionId,
    String correlationId,
    String causationId,
    String idempotencyKey,
    long version,
    AwaitInteractionStatus status,
    Object requestPayload,
    Object responsePayload,
    String unitId,
    Integer itemIndex,
    String actor,
    String assignee,
    String group,
    String transportType,
    Map<String, Object> transportMetadata,
    long deadlineEpochMs,
    long createdAtEpochMs,
    long updatedAtEpochMs,
    long ttlEpochS,
    String transportOutputType,
    Optional<Object> commandAcknowledgement,
    Optional<Object> stagedCompletionPayload
) {
    /** Compatibility constructor for interactions created before acknowledgement-aware Command completion. */
    public AwaitInteractionRecord(
        String tenantId, String executionId, String stepId, int stepIndex, String outputType,
        String interactionId, String correlationId, String causationId, String idempotencyKey,
        long version, AwaitInteractionStatus status, Object requestPayload, Object responsePayload,
        String unitId, Integer itemIndex, String actor, String assignee, String group,
        String transportType, Map<String, Object> transportMetadata, long deadlineEpochMs,
        long createdAtEpochMs, long updatedAtEpochMs, long ttlEpochS, String transportOutputType
    ) {
        this(tenantId, executionId, stepId, stepIndex, outputType, interactionId, correlationId,
            causationId, idempotencyKey, version, status, requestPayload, responsePayload,
            unitId, itemIndex, actor, assignee, group, transportType, transportMetadata,
            deadlineEpochMs, createdAtEpochMs, updatedAtEpochMs, ttlEpochS, transportOutputType,
            Optional.empty(), Optional.empty());
    }
    public AwaitInteractionRecord(
        String tenantId,
        String executionId,
        String stepId,
        int stepIndex,
        String outputType,
        String interactionId,
        String correlationId,
        String causationId,
        String idempotencyKey,
        long version,
        AwaitInteractionStatus status,
        Object requestPayload,
        Object responsePayload,
        String unitId,
        Integer itemIndex,
        String actor,
        String assignee,
        String group,
        String transportType,
        Map<String, Object> transportMetadata,
        long deadlineEpochMs,
        long createdAtEpochMs,
        long updatedAtEpochMs,
        long ttlEpochS
    ) {
        this(tenantId, executionId, stepId, stepIndex, outputType, interactionId, correlationId, causationId,
            idempotencyKey, version, status, requestPayload, responsePayload, unitId, itemIndex, actor, assignee,
            group, transportType, transportMetadata, deadlineEpochMs, createdAtEpochMs, updatedAtEpochMs,
            ttlEpochS, outputType, Optional.empty(), Optional.empty());
    }
    public AwaitInteractionRecord(
        String tenantId,
        String executionId,
        String stepId,
        int stepIndex,
        String outputType,
        String interactionId,
        String correlationId,
        String causationId,
        String idempotencyKey,
        long version,
        AwaitInteractionStatus status,
        Object requestPayload,
        Object responsePayload,
        String actor,
        String assignee,
        String group,
        String transportType,
        Map<String, Object> transportMetadata,
        long deadlineEpochMs,
        long createdAtEpochMs,
        long updatedAtEpochMs,
        long ttlEpochS
    ) {
        this(
            tenantId,
            executionId,
            stepId,
            stepIndex,
            outputType,
            interactionId,
            correlationId,
            causationId,
            idempotencyKey,
            version,
            status,
            requestPayload,
            responsePayload,
            interactionId,
            null,
            actor,
            assignee,
            group,
            transportType,
            transportMetadata,
            deadlineEpochMs,
            createdAtEpochMs,
            updatedAtEpochMs,
            ttlEpochS,
            outputType, Optional.empty(), Optional.empty());
    }

    public AwaitInteractionRecord {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }
        if (stepId == null || stepId.isBlank()) {
            throw new IllegalArgumentException("stepId must not be blank");
        }
        if (stepIndex < 0) {
            throw new IllegalArgumentException("stepIndex must not be negative");
        }
        if (outputType == null || outputType.isBlank()) {
            throw new IllegalArgumentException("outputType must not be blank");
        }
        if (interactionId == null || interactionId.isBlank()) {
            throw new IllegalArgumentException("interactionId must not be blank");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (unitId == null || unitId.isBlank()) {
            throw new IllegalArgumentException("unitId must not be blank");
        }
        if (itemIndex != null && itemIndex < 0) {
            throw new IllegalArgumentException("itemIndex must be non-negative when set");
        }
        transportMetadata = transportMetadata == null ? Map.of() : Map.copyOf(transportMetadata);
        // Older durable JSON records lack these fields.
        commandAcknowledgement = commandAcknowledgement == null ? Optional.empty() : commandAcknowledgement;
        stagedCompletionPayload = stagedCompletionPayload == null ? Optional.empty() : stagedCompletionPayload;
        transportOutputType = transportOutputType == null || transportOutputType.isBlank()
            ? outputType
            : transportOutputType;
    }

    public boolean itemInteraction() {
        return itemIndex != null;
    }

    /** The completion gate is persisted in the existing interaction. */
    public boolean commandCallback() {
        return "CONNECTOR_CALLBACK".equals(transportMetadata.get("completionMode"));
    }

    /** Explicit opt-in; absent mode retains the legacy callback contract. */
    public boolean acknowledgementAwareCommandCallback() {
        return commandCallback() && "ACKNOWLEDGEMENT_REQUIRED".equals(transportMetadata.get("commandCompletionMode"));
    }

    /** Guards the store CAS against changing ownership, pinned contract, or terminal authority. */
    public boolean permitsCommandCompletionReplacement(AwaitInteractionRecord next) {
        if (!acknowledgementAwareCommandCallback() || next == null
            || !next.acknowledgementAwareCommandCallback() || status.terminal()
            || next.version != version + 1 || next.updatedAtEpochMs < updatedAtEpochMs
            || !Objects.equals(tenantId, next.tenantId)
            || !Objects.equals(executionId, next.executionId)
            || !Objects.equals(stepId, next.stepId)
            || stepIndex != next.stepIndex
            || !Objects.equals(outputType, next.outputType)
            || !Objects.equals(interactionId, next.interactionId)
            || !Objects.equals(correlationId, next.correlationId)
            || !Objects.equals(causationId, next.causationId)
            || !Objects.equals(idempotencyKey, next.idempotencyKey)
            || !Objects.equals(requestPayload, next.requestPayload)
            || !Objects.equals(unitId, next.unitId)
            || !Objects.equals(itemIndex, next.itemIndex)
            || !Objects.equals(assignee, next.assignee)
            || !Objects.equals(group, next.group)
            || !Objects.equals(transportType, next.transportType)
            || !Objects.equals(transportOutputType, next.transportOutputType)
            || deadlineEpochMs != next.deadlineEpochMs
            || createdAtEpochMs != next.createdAtEpochMs
            || ttlEpochS != next.ttlEpochS
            || !Objects.equals(transportMetadata.get("completionMode"), next.transportMetadata.get("completionMode"))
            || !Objects.equals(transportMetadata.get("callbackSelection"), next.transportMetadata.get("callbackSelection"))
            || !Objects.equals(transportMetadata.get("commandCompletionMode"), next.transportMetadata.get("commandCompletionMode"))
            || !Objects.equals(transportMetadata.get("commandAcknowledgementType"), next.transportMetadata.get("commandAcknowledgementType"))
            || !Objects.equals(transportMetadata.get("tpf.await.completion.projector"),
                next.transportMetadata.get("tpf.await.completion.projector"))) {
            return false;
        }
        if (commandAcknowledgement.isPresent()
            && !commandAcknowledgement.equals(next.commandAcknowledgement)) {
            return false;
        }
        if (stagedCompletionPayload.isPresent() && next.stagedCompletionPayload.isPresent()
            && !stagedCompletionPayload.equals(next.stagedCompletionPayload)) {
            return false;
        }
        if (stagedCompletionPayload.isPresent() && next.stagedCompletionPayload.isEmpty()
            && (next.status != AwaitInteractionStatus.DISPATCHED
                || !Boolean.TRUE.equals(next.transportMetadata.get("rejectedEarlyCallback")))) {
            return false;
        }
        if (next.commandAcknowledgement.isPresent()
            && !"SUCCEEDED".equals(next.transportMetadata.get("commandSettlement"))) {
            return false;
        }
        if (next.status == AwaitInteractionStatus.COMPLETION_OBSERVED
            && next.stagedCompletionPayload.isEmpty()) {
            return false;
        }
        if (next.status == AwaitInteractionStatus.COMPLETED
            && (next.commandAcknowledgement.isEmpty() || next.stagedCompletionPayload.isEmpty()
                || next.responsePayload == null)) {
            return false;
        }
        return switch (status) {
            case DISPATCHING -> next.status == AwaitInteractionStatus.DISPATCHED
                || next.status == AwaitInteractionStatus.COMPLETION_OBSERVED
                || next.status == AwaitInteractionStatus.WAITING
                || next.status == AwaitInteractionStatus.FAILED
                || next.status == AwaitInteractionStatus.TIMED_OUT;
            case DISPATCHED -> next.status == AwaitInteractionStatus.COMPLETION_OBSERVED
                || next.status == AwaitInteractionStatus.COMPLETED
                || next.status == AwaitInteractionStatus.TIMED_OUT;
            case COMPLETION_OBSERVED -> next.status == AwaitInteractionStatus.COMPLETED
                || next.status == AwaitInteractionStatus.DISPATCHED
                || next.status == AwaitInteractionStatus.FAILED
                || next.status == AwaitInteractionStatus.COMPLETION_OBSERVED
                || next.status == AwaitInteractionStatus.TIMED_OUT;
            default -> false;
        };
    }

    public AwaitInteractionStatus observedCompletionStatus() {
        if (!commandCallback()) {
            return AwaitInteractionStatus.COMPLETED;
        }
        return switch (status) {
            case DISPATCHING, COMPLETION_OBSERVED -> AwaitInteractionStatus.COMPLETION_OBSERVED;
            case DISPATCHED, COMPLETED -> AwaitInteractionStatus.COMPLETED;
            default -> throw new IllegalStateException("Callback cannot be admitted while Command interaction is " + status);
        };
    }

    public AwaitInteractionRecord settleCommandDispatch(CommandDispatchSettlement settlement, long nowEpochMs) {
        java.util.Objects.requireNonNull(settlement, "settlement");
        if (!commandCallback() || (status != AwaitInteractionStatus.DISPATCHING
            && status != AwaitInteractionStatus.COMPLETION_OBSERVED)) {
            throw new IllegalStateException("Command settlement requires an unsettled callback interaction");
        }
        boolean observed = status == AwaitInteractionStatus.COMPLETION_OBSERVED;
        boolean accepted = settlement == CommandDispatchSettlement.SUCCEEDED || settlement == CommandDispatchSettlement.AMBIGUOUS;
        AwaitInteractionStatus next = deadlineEpochMs <= nowEpochMs ? AwaitInteractionStatus.TIMED_OUT
            : observed ? (accepted ? AwaitInteractionStatus.COMPLETED : AwaitInteractionStatus.FAILED)
            : accepted ? AwaitInteractionStatus.DISPATCHED
            : settlement == CommandDispatchSettlement.RETRYABLE ? AwaitInteractionStatus.WAITING : AwaitInteractionStatus.FAILED;
        Map<String, Object> metadata = new java.util.LinkedHashMap<>(transportMetadata);
        metadata.put("commandSettlement", settlement.name());
        if (observed && accepted && next == AwaitInteractionStatus.COMPLETED) {
            metadata.put("completionDelivery", "dispatch");
        }
        if (observed && !accepted) {
            metadata.put("completionFailure", "contradictory-provider-evidence");
        }
        return new AwaitInteractionRecord(tenantId, executionId, stepId, stepIndex, outputType, interactionId,
            correlationId, causationId, idempotencyKey, version + 1, next, requestPayload, responsePayload,
            unitId, itemIndex, actor, assignee, group, transportType, metadata, deadlineEpochMs, createdAtEpochMs,
            nowEpochMs, ttlEpochS, transportOutputType, commandAcknowledgement, stagedCompletionPayload);
    }

    /**
     * Returns a transport-safe snapshot for a portable transition envelope.
     */
    public AwaitInteractionRecord withPayloadSnapshots(Object requestSnapshot, Object responseSnapshot) {
        return new AwaitInteractionRecord(
            tenantId, executionId, stepId, stepIndex, outputType, interactionId, correlationId, causationId,
            idempotencyKey, version, status, requestSnapshot, responseSnapshot, unitId, itemIndex, actor, assignee,
            group, transportType, transportMetadata, deadlineEpochMs, createdAtEpochMs, updatedAtEpochMs,
            ttlEpochS, transportOutputType, commandAcknowledgement, stagedCompletionPayload);
    }
}
