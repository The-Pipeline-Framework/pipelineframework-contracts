package org.pipelineframework.invocation;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PipelineInvocationPathTest {
    @Test
    void retainsAnImmutableOrderedOccurrencePath() {
        var frame = new PipelineInvocationPath.Frame("$root", "$recur", 1);
        var frames = new ArrayList<>(List.of(frame));
        var path = new PipelineInvocationPath(frames);
        frames.clear();
        assertEquals(List.of(frame), path.frames());
        assertEquals("$root:$recur#1", frame.display());
        assertThrows(UnsupportedOperationException.class, () -> path.frames().clear());
        assertThrows(IllegalArgumentException.class, () -> new PipelineInvocationPath.Frame(" ", "$recur", 1));
        assertThrows(IllegalArgumentException.class, () -> new PipelineInvocationPath.Frame("$root", "", 1));
        assertThrows(IllegalArgumentException.class, () -> new PipelineInvocationPath.Frame("$root", "$recur", 0));
    }
}
