package org.pipelineframework.awaitable;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.pipelineframework.orchestrator.SerializedTransitionPayload;

class AwaitValueOperationRequestTest {
    private static final SerializedTransitionPayload VALUE = new SerializedTransitionPayload("canonical", "json", "{}");

    @Test void responseIsBoundToScopeReleaseVersionAndExactBytes() {
        var request = request("tenant", "release-1", 3, VALUE);
        var response = new AwaitValueOperationResult(request.operation(), request.operationId(), request.requestDigest(), VALUE);
        assertTrue(response.matches(request));
        assertEquals(request.requestDigest(), request("tenant", "release-1", 3, VALUE).requestDigest());
        assertFalse(response.matches(request("other", "release-1", 3, VALUE)));
        assertFalse(response.matches(request("tenant", "release-2", 3, VALUE)));
        assertFalse(response.matches(request("tenant", "release-1", 4, VALUE)));
        assertFalse(response.matches(request("tenant", "release-1", 3,
            new SerializedTransitionPayload("canonical", "json", "{ }"))));
    }

    @Test void incompleteProjectionAndAcknowledgementRequestsAreRejected() {
        for (var operation : AwaitValueOperation.values()) {
            assertThrows(IllegalArgumentException.class, () -> new AwaitValueOperationRequest(operation,
                "operation", "tenant", "execution", "interaction", "unit", "step", 0,
                "pipeline", "contract", "release", Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), "actor", 1));
        }
    }

    private static AwaitValueOperationRequest request(String tenant, String release, long version,
        SerializedTransitionPayload value) {
        return new AwaitValueOperationRequest(AwaitValueOperation.NORMALIZE_AND_PROJECT_COMPLETION,
            "operation", tenant, "execution", "interaction", "unit", "step", version,
            "pipeline", "contract", release, Optional.of("projector-v1"), Optional.empty(),
            Optional.of(VALUE), Optional.empty(), Optional.of(value), "actor", 1);
    }
}
