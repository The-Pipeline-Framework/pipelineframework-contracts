package org.pipelineframework.awaitable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.pipelineframework.orchestrator.SerializedTransitionPayload;

/** Scoped observations of an existing dispatch grant, without completion or policy authority. */
public record AwaitDispatchReportRequest(String workerId, String entryId, AwaitTransitionScope scope,
    String interactionId, long grantedInteractionVersion, AwaitDispatchReportKind kind,
    Map<String, Object> transportMetadata, Optional<String> failureMessage,
    Optional<CommandDispatchSettlement> settlement, Optional<SerializedTransitionPayload> acknowledgement) {
    public AwaitDispatchReportRequest {
        for (String value : new String[] {workerId, entryId, interactionId}) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Dispatch report identities are required");
        }
        Objects.requireNonNull(scope, "scope"); Objects.requireNonNull(kind, "kind");
        if (grantedInteractionVersion < 0) throw new IllegalArgumentException("Grant version must be nonnegative");
        Objects.requireNonNull(failureMessage, "failureMessage"); Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(acknowledgement, "acknowledgement");
        transportMetadata = Map.copyOf(Objects.requireNonNull(transportMetadata, "transportMetadata"));
        Set<String> reserved = Set.of("tenantId", "executionId", "pipelineId", "contractVersion", "releaseVersion", "unitId",
            "stepId", "version", "identityScope", "completionMode", "callbackSelection", "commandCompletionMode",
            "commandAcknowledgementType", "commandSettlement", "completionDelivery", "rejectedEarlyCallback",
            "completionProjectionPending", "completionFailure", "resumeToken", "callbackToken", "credentialRef",
            "sharedSecret", "timeout", "deadline", "ttl");
        for (String key : transportMetadata.keySet()) {
            if (key.startsWith("tpf.") || reserved.contains(key)) throw new IllegalArgumentException("Dispatch metadata must not override Coordinator authority");
        }
        kind.validateEvidence(transportMetadata, failureMessage, settlement, acknowledgement.isPresent());
    }
}
