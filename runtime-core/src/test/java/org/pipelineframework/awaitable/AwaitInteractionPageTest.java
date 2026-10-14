package org.pipelineframework.awaitable;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AwaitInteractionPageTest {
    @Test
    void emptyFilteredPagePreservesContinuation() {
        var cursor = new AwaitInteractionCursor("tenant", "unit", "opaque-position");
        var page = new AwaitInteractionPage(List.of(), Optional.of(cursor));
        assertTrue(page.interactions().isEmpty());
        assertEquals(Optional.of(cursor), page.nextCursor());
    }

    @Test
    void cursorCannotCrossUnitOrTenant() {
        var cursor = new AwaitInteractionCursor("tenant", "unit", "opaque-position");
        assertDoesNotThrow(() -> cursor.requireScope("tenant", "unit"));
        assertThrows(IllegalArgumentException.class, () -> cursor.requireScope("other", "unit"));
        assertThrows(IllegalArgumentException.class, () -> cursor.requireScope("tenant", "other"));
        assertThrows(IllegalArgumentException.class,
            () -> new AwaitInteractionCursor("tenant", "unit", "x".repeat(8193)));
    }
}
