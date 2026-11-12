package org.pipelineframework.awaitable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.pipelineframework.orchestrator.SerializedTransitionPayload;
import static org.junit.jupiter.api.Assertions.*;

class AwaitRegistrationRequestTest {
    private static final AwaitTransitionScope SCOPE = new AwaitTransitionScope("tenant", "execution", "pipeline", "contract", "release", 3, "transition");
    @Test void requiresExplicitOptionalFieldsAndStableIdentities() {
        assertThrows(IllegalArgumentException.class, () -> new AwaitTransitionScope("", "execution", "pipeline", "contract", "release", 3, "transition"));
        assertThrows(IllegalArgumentException.class, () -> new AwaitTransitionScope("tenant", "execution", "pipeline", "contract", "release", -1, "transition"));
        assertThrows(IllegalArgumentException.class, () -> request(Optional.of(-1), Map.of()));
        assertThrows(NullPointerException.class, () -> request(null, Map.of()));
        assertTrue(request(Optional.empty(), Map.of()).itemIndex().isEmpty());
    }
    @Test void defensivelyRetainsTraceMetadataWithoutAuthorityFields() {
        var metadata = new HashMap<String, Object>(); metadata.put("tpf.trace.id", "trace");
        var request = request(Optional.empty(), metadata); metadata.clear();
        assertEquals(Map.of("tpf.trace.id", "trace"), request.traceMetadata());
        assertThrows(UnsupportedOperationException.class, () -> request.traceMetadata().put("other", "value"));
    }
    private static AwaitRegistrationRequest request(Optional<Integer> item, Map<String,Object> trace) {
        return new AwaitRegistrationRequest(SCOPE, "step", 8, "execution", "unit", "execution:8", item,
            new SerializedTransitionPayload("type", "encoding", "{}"), Optional.empty(), Optional.empty(), Optional.empty(), trace);
    }
}
