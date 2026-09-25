package org.pipelineframework.paging;

/**
 * Opt-in framework-neutral source capability for one bounded resumable slice.
 *
 * <p>The provider interprets the checkpoint and validates {@code sourceIdentity} against an
 * immutable or versioned snapshot on every open. The same request must emit the same logical
 * record order on replay. A record limit counts source records consumed, including records
 * rejected or skipped by provider mapping; a format header does not count. Ordinary sources
 * do not implement this capability and remain unpaged.</p>
 */
public interface PagedSourceOperation<I, O> {
    PagedSourceStream<O> openPage(PagedSourceRequest<I> request);
}
