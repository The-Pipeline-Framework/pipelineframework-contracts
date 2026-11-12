package org.pipelineframework.orchestrator;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionDispatchPinTest {
    private static final ExecutionDispatchPin PIN = new ExecutionDispatchPin("entry-original", "deployment-original");

    @Test
    void recordCopiesRetainOriginalSelection() {
        var record = new ExecutionRecord<>("tenant", "execution", "key", "pipeline", "contract", "release",
            ExecutionResultShape.SINGLE, ExecutionStatus.QUEUED, 0, 0, 0, "", 0, 100, "", "input", "", "result",
            "", "", 100, 100, 1000, 0, 0, "", ExecutionRedriveIntent.REPLAY, -1,
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("initial-v1"), Optional.of(PIN));
        assertEquals(Optional.of(PIN), record.withStatus(ExecutionStatus.RUNNING, 101).dispatchPin());
        assertEquals(Optional.of(PIN), record.withAwaitOccurrenceCause(Optional.of("redrive-v2")).dispatchPin());

    }

    @Test
    void admissionCommandRetainsPinAndOriginalConstructorRemainsAvailable() {
        var old = new ExecutionCreateCommand("tenant", "key", "pipeline", "contract", "release", "input",
            ExecutionResultShape.SINGLE, Optional.empty(), 0, Optional.empty(), 100, 1000);
        assertTrue(old.dispatchPin().isEmpty());
        var pinned = new ExecutionCreateCommand("tenant", "key", "pipeline", "contract", "release", "input",
            ExecutionResultShape.SINGLE, Optional.empty(), 0, Optional.empty(), 100, 1000, Optional.of(PIN));
        assertEquals(Optional.of(PIN), pinned.dispatchPin());
    }

    @Test
    void pinCannotNameAnEmptyOrNoncanonicalObservation() {
        for (String invalid : new String[] {"", " ", " entry", "entry "}) {
            assertThrows(IllegalArgumentException.class, () -> new ExecutionDispatchPin(invalid, "operation"));
            assertThrows(IllegalArgumentException.class, () -> new ExecutionDispatchPin("entry", invalid));
        }
    }
}
