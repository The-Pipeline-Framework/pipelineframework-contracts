package org.pipelineframework.paging;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Demand-aware page items and their provider-resource completion.
 *
 * <p>The provider completes {@code completion} only after normal publisher completion and
 * release of its owned resources. Failure or cancellation completes it exceptionally and never
 * yields an advancing checkpoint. Opening the stream must not drain it. Subscribing does not
 * authorize a request beyond downstream demand.</p>
 */
public record PagedSourceStream<O>(
    Flow.Publisher<O> items,
    CompletionStage<PagedSourceCompletion> completion
) {
    public PagedSourceStream {
        Objects.requireNonNull(items, "paged source items must not be null");
        Objects.requireNonNull(completion, "paged source completion must not be null");
    }
}
