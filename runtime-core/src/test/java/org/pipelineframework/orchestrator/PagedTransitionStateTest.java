package org.pipelineframework.orchestrator;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PagedTransitionStateTest {

    @Test
    void directConstructionRejectsCompletionBeyondPageLimit() {
        PagedTransitionCompletion completion = new PagedTransitionCompletion(
            11, Optional.of("next"), false);

        assertThrows(IllegalArgumentException.class, () -> new PagedExecutionState(
            2, "source", Optional.of("start"), 10, Optional.of(completion)));
        assertThrows(IllegalArgumentException.class, () -> new PagedTransitionContext(
            2, "source", Optional.of("start"), 10, Optional.of(completion)));
    }

    @Test
    void directConstructionRejectsNonExhaustedRepeatedCheckpoint() {
        PagedTransitionCompletion completion = new PagedTransitionCompletion(
            1, Optional.of("same"), false);

        assertThrows(IllegalArgumentException.class, () -> new PagedExecutionState(
            2, "source", Optional.of("same"), 10, Optional.of(completion)));
        assertThrows(IllegalArgumentException.class, () -> new PagedTransitionContext(
            2, "source", Optional.of("same"), 10, Optional.of(completion)));
    }
}
