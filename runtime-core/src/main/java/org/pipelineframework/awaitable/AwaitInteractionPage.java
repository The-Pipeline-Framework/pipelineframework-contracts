package org.pipelineframework.awaitable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One bounded store page. An empty page with a cursor is not an exhausted read. */
public record AwaitInteractionPage(
    List<AwaitInteractionRecord> interactions,
    Optional<AwaitInteractionCursor> nextCursor
) {
    public AwaitInteractionPage {
        interactions = List.copyOf(Objects.requireNonNull(interactions, "interactions"));
        nextCursor = Objects.requireNonNull(nextCursor, "nextCursor");
        if (interactions.size() > 100) {
            throw new IllegalArgumentException("Await page exceeds 100 interactions");
        }
    }
}
