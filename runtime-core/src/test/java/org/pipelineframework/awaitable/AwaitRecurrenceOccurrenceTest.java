package org.pipelineframework.awaitable;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.pipelineframework.invocation.PipelineInvocationPath;
import static org.junit.jupiter.api.Assertions.*;

class AwaitRecurrenceOccurrenceTest {
    @Test
    void repeatedLiveVisitsHaveDistinctBoundedIdentitiesWithoutChangingTheOwnersCause() {
        var initial = new AwaitUnitOccurrence("tenant", "execution", "execution", "Review", 4, "initial-v1");
        var firstPath = new PipelineInvocationPath(List.of(new PipelineInvocationPath.Frame("$root", "$recur", 1)));
        var secondPath = new PipelineInvocationPath(List.of(new PipelineInvocationPath.Frame("$root", "$recur", 1),
            new PipelineInvocationPath.Frame("$root", "$recur", 2)));
        var first = initial.withInvocationPath(firstPath);
        var second = initial.withInvocationPath(secondPath);
        assertNotEquals(initial.unitId(), first.unitId());
        assertNotEquals(first.unitId(), second.unitId());
        assertEquals(first.unitId(), initial.withInvocationPath(firstPath).unitId());
        assertEquals(initial.occurrenceCause(), second.occurrenceCause());
        assertEquals(second, AwaitUnitOccurrence.fromUnitId(second.unitId()).orElseThrow());
        assertEquals(initial.unitId(), initial.withInvocationPath(new PipelineInvocationPath(List.of())).unitId());
        assertTrue(second.unitId().length() < 256);
        assertThrows(IllegalArgumentException.class, () -> AwaitUnitOccurrence.fromUnitId(initial.unitId() + ".invalid"));
    }
}
