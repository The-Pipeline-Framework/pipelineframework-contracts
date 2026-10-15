package org.pipelineframework.awaitable.spi;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.smallrye.mutiny.Uni;
import org.pipelineframework.awaitable.AwaitCompletionCommand;
import org.pipelineframework.awaitable.AwaitCompletionResult;
import org.pipelineframework.awaitable.AwaitCreateCommand;
import org.pipelineframework.awaitable.AwaitCreateResult;
import org.pipelineframework.awaitable.AwaitInteractionRecord;
import org.pipelineframework.awaitable.AwaitInteractionCursor;
import org.pipelineframework.awaitable.AwaitInteractionPage;
import org.pipelineframework.awaitable.AwaitCompletionAdmission;
import org.pipelineframework.awaitable.AwaitCompletionCursor;
import org.pipelineframework.awaitable.AwaitCompletionPage;

/**
 * Control-plane persistence SPI for await interactions.
 */
public interface AwaitInteractionStore {

    /**
     * Provider name used for configuration-based selection.
     *
     * @return provider name
     */
    default String providerName() {
        return "memory";
    }

    /**
     * Provider priority used when multiple stores are available.
     *
     * @return provider priority
     */
    default int priority() {
        return 0;
    }

    /**
     * Creates a new interaction or returns an active duplicate for the same idempotency key.
     *
     * @param command create command
     * @return create result
     */
    Uni<AwaitCreateResult> createOrGet(AwaitCreateCommand command);

    /**
     * Fetches one interaction by tenant and interaction id.
     *
     * @param tenantId tenant id
     * @param interactionId interaction id
     * @return matching interaction
     */
    Uni<Optional<AwaitInteractionRecord>> get(String tenantId, String interactionId);

    /**
     * Authoritative point read of lifecycle/identity metadata. Payloads are not required by
     * timeout recovery; providers with separately decoded payloads should avoid resolving them.
     * The compatibility default delegates to the ordinary authoritative read.
     */
    default Uni<Optional<AwaitInteractionRecord>> getMetadata(String tenantId, String interactionId) {
        return get(tenantId, interactionId);
    }


    /**
     * Imports an interaction created by a transition worker. Existing records win so the operation is idempotent.
     */
    Uni<AwaitInteractionRecord> importRecord(AwaitInteractionRecord record);

    /**
     * Fetches one interaction by tenant and correlation id.
     *
     * @param tenantId tenant id
     * @param correlationId correlation id
     * @return matching interaction
     */
    Uni<Optional<AwaitInteractionRecord>> findByCorrelation(String tenantId, String correlationId);

    /**
     * Fetches interactions that belong to one await unit.
     *
     * @param tenantId tenant id
     * @param unitId await unit id
     * @return matching interactions
     */
    Uni<List<AwaitInteractionRecord>> findByUnit(
        String tenantId,
        String unitId);

    /**
     * Reads at most one unit page, evaluating no more than {@code limit} index entries.
     * The limit must be between 1 and 100. Providers must validate cursor scope before I/O.
     * Pages follow a provider-defined stable index order; callers must not assume global
     * item-index or identifier order across pages. Following continuations must enumerate
     * every unfiltered member of an unchanged unit, including distinct identities whose
     * ordering fields contain delimiters. Positions must remain stable when an interaction
     * changes lifecycle status. Providers need not supply a cross-page snapshot.
     * A continuation must be preserved even when all evaluated entries were filtered out.
     * Index results are observations; callers must point-read a selected interaction before
     * relying on current status. Exhaustion does not establish semantic unit completion.
     */
    Uni<AwaitInteractionPage> findByUnitPage(
        String tenantId, String unitId, int limit, Optional<AwaitInteractionCursor> cursor);

    /**
     * Claims an interaction for dispatch. Only WAITING interactions may be claimed.
     */
    Uni<Optional<AwaitInteractionRecord>> markDispatching(
        String tenantId,
        String interactionId,
        long expectedVersion,
        long nowEpochMs);

    /** Persists a durable dispatch intent together with metadata required to recover that intent. */
    Uni<Optional<AwaitInteractionRecord>> markDispatching(
        String tenantId,
        String interactionId,
        long expectedVersion,
        Map<String, Object> transportMetadata,
        long nowEpochMs
    );

    /**
     * Marks a claimed interaction as dispatched and stores adapter metadata.
     * Only DISPATCHING interactions may be completed as dispatched.
     *
     * @param tenantId tenant id
     * @param interactionId interaction id
     * @param expectedVersion expected version
     * @param transportMetadata transport metadata captured after dispatch
     * @param nowEpochMs current time
     * @return updated record when the transition succeeds
     */
    Uni<Optional<AwaitInteractionRecord>> markDispatched(
        String tenantId,
        String interactionId,
        long expectedVersion,
        java.util.Map<String, Object> transportMetadata,
        long nowEpochMs);

    /**
     * Accepts a correlated completion. The winning completion CAS must atomically retain
     * an immutable {@link AwaitCompletionAdmission} and its open projection, one per interaction
     * (including each itemised interaction). The admission timestamp is the first accepted time,
     * not a callback delivery or retry time. Duplicate completion must not rewrite that fact or
     * recreate an already retired open projection. Unsupported providers must fail closed;
     * separately sequenced writes do not satisfy this contract.
     *
     * @param command completion command
     * @return completion result
     */
    Uni<AwaitCompletionResult> complete(AwaitCompletionCommand command);

    /**
     * Strongly reads the immutable first admission for the scoped interaction, including after
     * its open projection was retired. Empty means no retained admission was observed, not that
     * lifecycle consequences have converged. Facts remain available for their semantic retention;
     * TTL zero preserves the existing no-expiry contract.
     */
    Uni<Optional<AwaitCompletionAdmission>> getCompletionAdmission(String tenantId, String interactionId);

    /**
     * Strongly reads one native page of open completion admissions for one tenant. The page
     * limit must be between 1 and 100; at most that many entries may be evaluated, including
     * entries omitted because their semantic retention expired. Validate cursor tenant and
     * provider position before I/O, preserve continuation through empty filtered pages, and
     * enumerate all retained open admissions of an unchanged tenant when cursors are followed.
     * Secondary-index lag cannot establish absence or exhaustion. This operation does not
     * promise a cross-page snapshot or confer lifecycle authority; empty results never release
     * a parent. Unsupported providers must fail closed, without a scan or unbounded fallback.
     */
    Uni<AwaitCompletionPage> findOpenCompletions(
        String tenantId, int pageLimit, Optional<AwaitCompletionCursor> cursor);

    /**
     * Idempotently retires only the matching admission's open projection after the runtime
     * verified unit/admission convergence and the authoritative Await unit acknowledgement,
     * either CONTINUATION_QUEUED or TERMINAL_FAILED. A missing acknowledgement
     * does not permit retirement. Validate the retained immutable fact and its scoped identity;
     * a foreign or conflicting fact must fail closed. Retirement does not delete the fact or
     * change interaction status/version, first admission time, or acknowledgement. Subsequent
     * duplicate completion must not recreate the retired projection. Retention expiry permits
     * bounded cleanup only after the stored semantic horizon; TTL zero must not be expired.
     */
    Uni<Void> retireCompletionAdmission(AwaitCompletionAdmission admission);

    /** Unsupported stores fail before Command dispatch. */
    default boolean supportsCommandCompletion() {
        return false;
    }

    default Uni<Optional<AwaitInteractionRecord>> settleCommandDispatch(AwaitInteractionRecord expected,
        org.pipelineframework.awaitable.CommandDispatchSettlement settlement, long nowEpochMs) {
        return Uni.createFrom().failure(new UnsupportedOperationException("Store does not support Command completion"));
    }

    /** Atomically replaces acknowledgement-aware Command state when the expected version still owns it. */
    default Uni<Optional<AwaitInteractionRecord>> compareAndSetCommandCompletion(
        AwaitInteractionRecord expected, AwaitInteractionRecord replacement) {
        return Uni.createFrom().failure(new UnsupportedOperationException(
            "Store does not support acknowledgement-aware Command completion"));
    }

    default Uni<Optional<AwaitInteractionRecord>> markDispatchRetryable(AwaitInteractionRecord expected, long nowEpochMs) {
        if (expected.status() != org.pipelineframework.awaitable.AwaitInteractionStatus.DISPATCHING) {
            return Uni.createFrom().item(Optional.empty());
        }
        return settleCommandDispatch(expected, org.pipelineframework.awaitable.CommandDispatchSettlement.RETRYABLE, nowEpochMs);
    }

    /**
     * Marks an interaction as failed.
     */
    Uni<Optional<AwaitInteractionRecord>> fail(
        String tenantId,
        String interactionId,
        long expectedVersion,
        String reason,
        long nowEpochMs);

    /**
     * Marks an interaction as cancelled.
     */
    Uni<Optional<AwaitInteractionRecord>> cancel(
        String tenantId,
        String interactionId,
        long expectedVersion,
        String reason,
        long nowEpochMs);

    /**
     * Atomically marks an active interaction as timed out and retains pending timeout
     * consequences. Completion and timeout must remain mutually exclusive. Pending work
     * survives process loss within the configured semantic retention horizon.
     */
    Uni<Optional<AwaitInteractionRecord>> markTimedOut(
        String tenantId,
        String interactionId,
        long expectedVersion,
        long nowEpochMs);

    /**
     * Returns active interactions whose deadline has passed.
     */
    Uni<List<AwaitInteractionRecord>> findTimedOut(long nowEpochMs, int limit);

    /**
     * Reads one bounded index page of admitted timeout consequences that are not acknowledged.
     * Evaluates at most {@code limit} entries; providers may return fewer observations because
     * of index lag or expired records. Results are metadata observations, not timeout authority.
     * Timeout admission, unit/parent transitions and required journal facts must be reconciled
     * idempotently before acknowledging an observation. The limit must be between 1 and 100.
     */
    Uni<List<AwaitInteractionRecord>> findDueTimeoutConsequences(long nowEpochMs, int limit);

    /**
     * Idempotently retires the work projection for the matching timed-out interaction version.
     * Does not change its semantic status/version. A foreign or non-timeout identity must fail
     * closed; lifecycle consequences remain the caller's responsibility before acknowledgement.
     */
    Uni<Void> completeTimeoutConsequences(AwaitInteractionRecord interaction);

    /**
     * Returns active interactions matching query filters.
     */
    Uni<List<AwaitInteractionRecord>> queryPending(
        String tenantId,
        String assignee,
        String group,
        String stepId,
        int limit);
}
