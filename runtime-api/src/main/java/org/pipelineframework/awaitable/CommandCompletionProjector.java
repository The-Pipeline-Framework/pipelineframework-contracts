package org.pipelineframework.awaitable;

/**
 * Pure projection of an authenticated Command callback using the durable dispatch acknowledgement.
 * An acknowledgement alone is never a completion. A projector must reject mismatched provider identity.
 *
 * @param <I> initiating canonical request
 * @param <A> acknowledged Command output
 * @param <C> admitted callback payload
 * @param <O> final pipeline output
 */
@FunctionalInterface
public interface CommandCompletionProjector<I, A, C, O> {

    O project(I request, A acknowledgement, C completion, AwaitCompletionMetadata metadata);
}
