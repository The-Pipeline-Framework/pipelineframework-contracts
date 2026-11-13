package org.pipelineframework.awaitable;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.pipelineframework.orchestrator.SerializedTransitionPayload;
import static org.junit.jupiter.api.Assertions.*;

class AwaitRegistrationRequestTest {
    private static final AwaitTransitionScope SCOPE = new AwaitTransitionScope("tenant", "execution", "pipeline", "contract", "release", 3, "transition");
    @Test void requiresExplicitOptionalFieldsAndStableIdentities() {
        assertThrows(IllegalArgumentException.class, () -> new AwaitTransitionScope("", "execution", "pipeline", "contract", "release", 3, "transition"));
        assertThrows(IllegalArgumentException.class, () -> new AwaitTransitionScope("tenant", "execution", "pipeline", "contract", "release", -1, "transition"));
        assertThrows(IllegalArgumentException.class, () -> request(Optional.of(-1)));
        assertThrows(NullPointerException.class, () -> request(null));
        assertTrue(request(Optional.empty()).itemIndex().isEmpty());
    }
    private static AwaitRegistrationRequest request(Optional<Integer> item) {
        return new AwaitRegistrationRequest(SCOPE, "step", 8, "execution", "unit", "execution:8", item,
            new SerializedTransitionPayload("type", "encoding", "{}"), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
