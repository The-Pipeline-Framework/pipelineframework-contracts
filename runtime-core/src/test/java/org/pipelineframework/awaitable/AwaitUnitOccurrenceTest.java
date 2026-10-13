package org.pipelineframework.awaitable;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Causal identity oracles: exact round trip, separation, and fail-closed legacy coverage. */
class AwaitUnitOccurrenceTest {
    @Test
    void preservesEveryIdentityComponentIncludingDelimiterAndUnicodeValues() {
        var occurrence = new AwaitUnitOccurrence("tenant.#雪", "execution.page:1", "execution.page:1:page:2",
            "Await.批准/#", 12, "reissue.parent:27.#雪");
        assertEquals(occurrence, AwaitUnitOccurrence.fromUnitId(occurrence.unitId()).orElseThrow());
        assertTrue(occurrence.matches("tenant.#雪", "execution.page:1", "Await.批准/#", 12));
    }

    @Test
    void separatesNewCausePageAuthoredStepAndTenantWithoutDelimiterCollisions() {
        var original = new AwaitUnitOccurrence("tenant", "execution", "execution", "Await", 2, "initial");
        var distinct = List.of(
            original,
            new AwaitUnitOccurrence("tenant", "execution", "execution", "Await", 2, "reissue:5"),
            new AwaitUnitOccurrence("tenant", "execution", "execution:page:0", "Await", 2, "initial"),
            new AwaitUnitOccurrence("other", "execution", "execution", "Await", 2, "initial"),
            new AwaitUnitOccurrence("tenant", "other", "execution", "Await", 2, "initial"),
            new AwaitUnitOccurrence("tenant", "execution", "execution", "OtherAwait", 2, "initial"),
            new AwaitUnitOccurrence("tenant", "execution", "execution", "Await", 3, "initial"),
            new AwaitUnitOccurrence("tenant#execution", "page", "scope", "Await", 2, "initial"),
            new AwaitUnitOccurrence("tenant", "execution#page", "scope", "Await", 2, "initial"));
        assertEquals(distinct.size(), distinct.stream().map(AwaitUnitOccurrence::unitId).distinct().count());
        assertEquals(original.unitId(), new AwaitUnitOccurrence("tenant", "execution", "execution",
            "Await", 2, "initial").unitId(), "same admitted cause must retain the retry identity");
    }

    @Test
    void legacyUnitIdsDoNotAcquireCausalCoverage() {
        for (String legacy : List.of("await:tenant:execution:Await:2", "unit", "await2", "await1.any")) {
            assertTrue(AwaitUnitOccurrence.fromUnitId(legacy).isEmpty(), legacy);
        }
    }

    @Test
    void malformedOrNoncanonicalQualifiedIdsCannotBecomeLegacyOrAnotherScope() {
        String valid = new AwaitUnitOccurrence("tenant", "execution", "execution", "Await", 2, "initial").unitId();
        for (String malformed : List.of("await2.", valid + ".extra", valid.replace(".2.", ".02."),
                valid.replace(".2.", ".-1."), valid.replace("aW5pdGlhbA", "aW5pdGlhbA="),
                valid.substring(0, valid.lastIndexOf('.') + 1), "await2._w.ZQ.ZQ.cw.2.Yw")) {
            assertThrows(IllegalArgumentException.class, () -> AwaitUnitOccurrence.fromUnitId(malformed), malformed);
        }
    }

    @Test
    void unitRecordRejectsQualifiedIdentityWithForeignTenantExecutionStepOrIndex() {
        String id = new AwaitUnitOccurrence("tenant", "execution", "execution", "Await", 2, "initial").unitId();
        record("tenant", id, "execution", "Await", 2);
        assertThrows(IllegalArgumentException.class, () -> record("other", id, "execution", "Await", 2));
        assertThrows(IllegalArgumentException.class, () -> record("tenant", id, "other", "Await", 2));
        assertThrows(IllegalArgumentException.class, () -> record("tenant", id, "execution", "Other", 2));
        assertThrows(IllegalArgumentException.class, () -> record("tenant", id, "execution", "Await", 3));
    }

    private static AwaitUnitRecord record(String tenant, String unit, String execution, String step, int index) {
        return new AwaitUnitRecord(tenant, unit, execution, step, index, "ONE_TO_ONE", 1,
            AwaitUnitStatus.WAITING_EXTERNAL, "interaction", 1, 0, Set.of(), false, 10, 10, 0);
    }
}
