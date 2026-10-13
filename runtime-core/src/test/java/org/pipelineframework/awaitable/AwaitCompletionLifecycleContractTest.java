package org.pipelineframework.awaitable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AwaitCompletionLifecycleContractTest {
    @Test
    void itemAdmissionsRemainDistinctAndCarryFirstAdmissionTime() {
        var scalar = admission("tenant", "scalar", 1_500L, 0L);
        var firstItem = new AwaitCompletionAdmission(1, "tenant", "execution", "item-0", Optional.of("unit"),
            "step", 2, OptionalInt.of(0), Optional.of("cause-0"), 1_500L, 2L);
        var secondItem = new AwaitCompletionAdmission(1, "tenant", "execution", "item-1", Optional.of("unit"),
            "step", 2, OptionalInt.of(1), Optional.of("cause-1"), 1_501L, 2L);
        assertTrue(scalar.itemIndex().isEmpty());
        assertEquals(0, firstItem.itemIndex().orElseThrow());
        assertEquals(1, secondItem.itemIndex().orElseThrow());
        assertNotEquals(firstItem.interactionId(), secondItem.interactionId());
        assertEquals(1_500L, firstItem.firstAdmittedAtEpochMs());
        assertEquals(Optional.of("cause-1"), secondItem.causationId());
    }

    @Test
    void optionalBlankFieldsNormalizeWithoutAliasingPresentIdentities() {
        var admission = new AwaitCompletionAdmission(1, "tenant", "execution", "interaction#a:b",
            Optional.of("  "), "step", 0, OptionalInt.empty(), Optional.of("	"), 0L, 0L);
        assertTrue(admission.unitId().isEmpty());
        assertTrue(admission.causationId().isEmpty());
        var preserved = new AwaitCompletionAdmission(1, "tenant", "execution", "interaction#a:b ",
            Optional.of(" unit#a:b "), "step", 0, OptionalInt.empty(), Optional.of(" cause "), 0L, 0L);
        assertEquals("interaction#a:b ", preserved.interactionId());
        assertEquals(Optional.of(" unit#a:b "), preserved.unitId());
        assertEquals(Optional.of(" cause "), preserved.causationId());
    }

    @Test
    void admissionRejectsUnsupportedSchemaBlankIdentityAndNegativeIndices() {
        assertThrows(IllegalArgumentException.class, () -> new AwaitCompletionAdmission(2, "tenant", "execution",
            "interaction", Optional.empty(), "step", 0, OptionalInt.empty(), Optional.empty(), 0L, 0L));
        assertThrows(IllegalArgumentException.class, () -> admission(" ", "interaction", 0L, 0L));
        assertThrows(IllegalArgumentException.class, () -> admission("tenant", " ", 0L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new AwaitCompletionAdmission(1, "tenant", "execution",
            "interaction", Optional.empty(), "step", -1, OptionalInt.empty(), Optional.empty(), 0L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new AwaitCompletionAdmission(1, "tenant", "execution",
            "interaction", Optional.empty(), "step", 0, OptionalInt.of(-1), Optional.empty(), 0L, 0L));
    }

    @Test
    void admissionPreservesNoExpiryAndRejectsInvalidFiniteRetentionWithoutOverflow() {
        assertEquals(0L, admission("tenant", "interaction", Long.MAX_VALUE, 0L).ttlEpochS());
        assertEquals(Long.MAX_VALUE, admission("tenant", "interaction", Long.MAX_VALUE, Long.MAX_VALUE).ttlEpochS());
        assertThrows(IllegalArgumentException.class, () -> admission("tenant", "interaction", -1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> admission("tenant", "interaction", 1_500L, -1L));
        assertThrows(IllegalArgumentException.class, () -> admission("tenant", "interaction", 1_500L, 1L));
        assertEquals(2L, admission("tenant", "interaction", 1_500L, 2L).ttlEpochS());
    }

    @Test
    void acknowledgementsDistinguishContinuationFromTerminalDispositionAndKeepActualClock() {
        var queued = acknowledgement(1, 2, 3L, AwaitReleaseDisposition.CONTINUATION_QUEUED, 900L, 0L);
        var failed = acknowledgement(1, 2, 3L, AwaitReleaseDisposition.TERMINAL_FAILED, 901L, 0L);
        // A clock regression relative to first admission is retained, not manufactured into a latency.
        assertTrue(queued.acknowledgedAtEpochMs() < admission("tenant", "interaction", 1_500L, 0L).firstAdmittedAtEpochMs());
        assertEquals(AwaitReleaseDisposition.CONTINUATION_QUEUED, queued.disposition());
        assertEquals(AwaitReleaseDisposition.TERMINAL_FAILED, failed.disposition());
        assertEquals("pipeline", queued.pipelineId());
        assertEquals("contract", queued.contractVersion());
        assertEquals("release", queued.releaseVersion());
        assertEquals(3L, queued.parentVersion());
    }

    @Test
    void acknowledgementValidatesSchemaVersionIndicesAndRetention() {
        assertThrows(IllegalArgumentException.class, () -> acknowledgement(0, 2, 3L, AwaitReleaseDisposition.CONTINUATION_QUEUED, 0L, 0L));
        assertThrows(IllegalArgumentException.class, () -> acknowledgement(1, -1, 3L, AwaitReleaseDisposition.CONTINUATION_QUEUED, 0L, 0L));
        assertThrows(IllegalArgumentException.class, () -> acknowledgement(1, 2, -1L, AwaitReleaseDisposition.CONTINUATION_QUEUED, 0L, 0L));
        assertThrows(IllegalArgumentException.class, () -> acknowledgement(1, 2, 3L, AwaitReleaseDisposition.TERMINAL_FAILED, -1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> acknowledgement(1, 2, 3L, AwaitReleaseDisposition.TERMINAL_FAILED, 1_500L, 1L));
        assertThrows(IllegalArgumentException.class, () -> acknowledgement(1, 2, 3L, AwaitReleaseDisposition.TERMINAL_FAILED, 1_500L, -1L));
        assertEquals(0L, acknowledgement(1, 0, 0L, AwaitReleaseDisposition.TERMINAL_FAILED, 0L, 0L).ttlEpochS());
    }

    @Test
    void cursorPreservesOpaquePositionAndRejectsCrossTenantUseOrUnboundedToken() {
        var cursor = new AwaitCompletionCursor("tenant", "native#a:b#position");
        assertEquals("native#a:b#position", cursor.position());
        assertDoesNotThrow(() -> cursor.requireScope("tenant"));
        assertThrows(IllegalArgumentException.class, () -> cursor.requireScope("foreign-tenant"));
        assertThrows(IllegalArgumentException.class, () -> new AwaitCompletionCursor("tenant", " "));
        assertThrows(IllegalArgumentException.class, () -> new AwaitCompletionCursor(" ", "opaque"));
        assertDoesNotThrow(() -> new AwaitCompletionCursor("tenant", "x".repeat(8192)));
        assertThrows(IllegalArgumentException.class, () -> new AwaitCompletionCursor("tenant", "x".repeat(8193)));
    }

    @Test
    void emptyFilteredPageRetainsProgressAndEvaluatedBudget() {
        var cursor = new AwaitCompletionCursor("tenant", "next-native-page");
        var page = new AwaitCompletionPage(List.of(), Optional.of(cursor), 100);
        assertTrue(page.admissions().isEmpty());
        assertEquals(Optional.of(cursor), page.nextCursor());
        assertEquals(100, page.evaluatedEntryCount());
        assertEquals(0, new AwaitCompletionPage(List.of(), Optional.empty(), 0).evaluatedEntryCount());
    }

    @Test
    void pageCopiesRowsAndRejectsUndercountOversizeDuplicatesAndForeignScope() {
        var row = admission("tenant", "interaction", 0L, 0L);
        var rows = new ArrayList<>(List.of(row));
        var page = new AwaitCompletionPage(rows, Optional.empty(), 1);
        rows.clear();
        assertEquals(List.of(row), page.admissions());
        assertThrows(UnsupportedOperationException.class, () -> page.admissions().clear());
        assertThrows(IllegalArgumentException.class, () -> new AwaitCompletionPage(List.of(row), Optional.empty(), 0));
        assertThrows(IllegalArgumentException.class, () -> new AwaitCompletionPage(List.of(), Optional.empty(), -1));
        assertThrows(IllegalArgumentException.class, () -> new AwaitCompletionPage(List.of(), Optional.empty(), 101));
        assertThrows(IllegalArgumentException.class, () -> new AwaitCompletionPage(List.of(row, row), Optional.empty(), 2));
        assertThrows(IllegalArgumentException.class, () -> new AwaitCompletionPage(List.of(row,
            admission("foreign-tenant", "another", 0L, 0L)), Optional.empty(), 2));
        assertThrows(IllegalArgumentException.class, () -> new AwaitCompletionPage(List.of(row),
            Optional.of(new AwaitCompletionCursor("foreign-tenant", "position")), 1));
    }

    private AwaitCompletionAdmission admission(String tenant, String interaction, long firstAdmitted, long ttl) {
        return new AwaitCompletionAdmission(1, tenant, "execution", interaction, Optional.of("unit"),
            "step", 2, OptionalInt.empty(), Optional.empty(), firstAdmitted, ttl);
    }

    private AwaitUnitReleaseAcknowledgement acknowledgement(int schema, int stepIndex, long parentVersion,
        AwaitReleaseDisposition disposition, long acknowledgedAt, long ttl) {
        return new AwaitUnitReleaseAcknowledgement(schema, "tenant", "execution", "unit", "pipeline", "contract",
            "release", stepIndex, parentVersion, disposition, acknowledgedAt, ttl);
    }
}
