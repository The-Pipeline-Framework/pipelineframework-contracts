package org.pipelineframework.command;

import java.util.Optional;

import io.smallrye.mutiny.Uni;
import org.pipelineframework.connector.CommandRecoveryBinding;

/**
 * Stores managed command effect state.
 *
 * Implementations own the durable command-id index and should encode persistence,
 * optimistic-lock, and connectivity failures as failed {@link Uni} items. Transition
 * methods are expected to fail on missing command ids or illegal state transitions;
 * callers do not perform multi-writer conflict resolution.
 *
 * Implementations must preserve the output type recorded by {@link #markSucceeded}
 * so replayed command results can be returned without lossy casts or schema drift.
 */
public interface CommandEffectStore {
    /**
     * Returns the current effect record for the tenant and command id, or empty when no
     * effect has been recorded yet.
     */
    Uni<Optional<CommandEffectRecord>> find(String tenantId, String commandId);

    /**
     * Creates the initial pending record for a command request. Implementations should
     * fail the returned {@link Uni} when the command id already exists.
     */
    Uni<CommandEffectRecord> createPending(CommandRequest<?> request, long nowEpochMs);

    /**
     * Opt-in authority for original bound reservations, strict single-winner dispatch claims,
     * and guarded reconciled success. All recovery overloads must be implemented atomically.
     * Both original and recovery callers must use claimPendingDispatch before external dispatch.
     * A repeated claim is a conflict, even for the same attempt; it never grants dispatch twice.
     */
    default boolean supportsRecovery() {
        return false;
    }

    /** Persists original binding together with the initial reservation, in one write. */
    default Uni<CommandEffectRecord> createPending(
        CommandRequest<?> request, CommandRecoveryBinding binding, long nowEpochMs
    ) {
        return recoveryUnsupported();
    }

    /** Persists a deliberate attempt and its original binding together, in one write. */
    default Uni<CommandEffectRecord> createAttempt(
        CommandRequest<?> request, CommandAttemptAdmission admission,
        CommandRecoveryBinding binding, long nowEpochMs
    ) {
        return recoveryUnsupported();
    }

    /**
     * Atomically compares the exact current bound PENDING attempt and appends DISPATCHING.
     * Only the successful caller may dispatch. Losers must not retry dispatch admission or
     * treat a same-attempt DISPATCHING record as a successful claim.
     */
    default Uni<CommandEffectRecord> claimPendingDispatch(CommandRecoveryBinding expected, long nowEpochMs) {
        return recoveryUnsupported();
    }

    /**
     * Atomically verifies the exact binding and expected uncertain state before appending typed
     * success plus safe settlement provenance. The runtime must first validate authoritative
     * provider evidence, confirmation policy, output type/digest and declared receipt kind.
     * Conflicts never authorize overwriting newer state or creating another attempt.
     */
    default Uni<CommandEffectRecord> reconcileSucceeded(
        CommandRecoveryBinding expected, CommandEffectStatus expectedStatus, Object output,
        CommandOutcomeSnapshot outcome, CommandReconciliationReceipt receipt, long nowEpochMs
    ) {
        return recoveryUnsupported();
    }

    private static Uni<CommandEffectRecord> recoveryUnsupported() {
        return Uni.createFrom().failure(new UnsupportedOperationException(
            "Command recovery requires an explicitly recovery-capable effect store"));
    }

    /**
     * Whether this store can atomically append and persist deliberate Command attempts.
     * Implementations returning {@code true} must override {@code createRetryAttempt}
     * and every attempt-id-aware transition overload; the compatibility defaults discard
     * the attempt id and are only suitable for stores that retain the {@code false} default.
     */
    default boolean supportsRetryAttempts() {
        return false;
    }

    /**
     * Atomically appends the next attempt when, and only when, the logical effect is
     * currently {@link CommandEffectStatus#FAILED_RETRYABLE}.
     */
    default Uni<CommandEffectRecord> createRetryAttempt(CommandRequest<?> request, long nowEpochMs) {
        return Uni.createFrom().failure(new UnsupportedOperationException(
            "deliberate Command retry requires a CommandEffectStore that persists attempt history"));
    }

    /** Whether this store can atomically append the requested deliberate attempt kind. */
    default boolean supportsAttempt(CommandAttemptPurpose purpose) {
        return purpose == CommandAttemptPurpose.RETRY && supportsRetryAttempts();
    }

    /**
     * Atomically appends one deliberate retry or reissue attempt. Compatibility stores retain
     * retry support through {@link #createRetryAttempt}; reissue is opt-in.
     */
    default Uni<CommandEffectRecord> createAttempt(
        CommandRequest<?> request,
        CommandAttemptAdmission admission,
        long nowEpochMs
    ) {
        if (admission.purpose() == CommandAttemptPurpose.RETRY) {
            return createRetryAttempt(request, nowEpochMs);
        }
        return Uni.createFrom().failure(new UnsupportedOperationException(
            "Command reissue requires a CommandEffectStore that persists occurrence history"));
    }

    /**
     * Marks an existing pending command as dispatching. Retrying this transition may be
     * accepted only when the stored state already reflects the same dispatch.
     */
    Uni<CommandEffectRecord> markDispatching(String tenantId, String commandId, long nowEpochMs);

    default Uni<CommandEffectRecord> markDispatching(
        String tenantId,
        String commandId,
        String attemptId,
        long nowEpochMs
    ) {
        return markDispatching(tenantId, commandId, nowEpochMs);
    }

    /**
     * Records the successful command output. The stored output type must remain compatible
     * with the command step output type for duplicate replay.
     */
    Uni<CommandEffectRecord> markSucceeded(String tenantId, String commandId, Object output, long nowEpochMs);

    default Uni<CommandEffectRecord> markSucceeded(
        String tenantId,
        String commandId,
        String attemptId,
        Object output,
        long nowEpochMs
    ) {
        return markSucceeded(tenantId, commandId, output, nowEpochMs);
    }

    /**
     * Whether this store can durably preserve native command outcome snapshots. Native command
     * dispatch checks this before it creates any effect state, retaining source compatibility for
     * legacy store implementations that only support legacy commands.
     */
    default boolean supportsNativeOutcomeSnapshots() {
        return false;
    }

    /**
     * Records a successful native command output and its sanitized outcome snapshot.
     * Implementations that preserve native outcome snapshots must override this method.
     */
    default Uni<CommandEffectRecord> markSucceeded(
        String tenantId,
        String commandId,
        Object output,
        CommandOutcomeSnapshot outcome,
        long nowEpochMs
    ) {
        return Uni.createFrom().failure(new UnsupportedOperationException(
            "native command outcomes require a CommandEffectStore that persists outcome snapshots"));
    }

    default Uni<CommandEffectRecord> markSucceeded(
        String tenantId,
        String commandId,
        String attemptId,
        Object output,
        CommandOutcomeSnapshot outcome,
        long nowEpochMs
    ) {
        return markSucceeded(tenantId, commandId, output, outcome, nowEpochMs);
    }

    /**
     * Records a retryable command failure. Implementations should retain enough error
     * detail for operators to classify and retry the effect.
     */
    Uni<CommandEffectRecord> markFailed(String tenantId, String commandId, Throwable failure, long nowEpochMs);

    default Uni<CommandEffectRecord> markFailed(
        String tenantId,
        String commandId,
        String attemptId,
        Throwable failure,
        long nowEpochMs
    ) {
        return markFailed(tenantId, commandId, failure, nowEpochMs);
    }

    /**
     * Records a terminal command failure that should be routed to dead-letter handling.
     */
    Uni<CommandEffectRecord> markDlq(String tenantId, String commandId, Throwable failure, long nowEpochMs);

    default Uni<CommandEffectRecord> markDlq(
        String tenantId,
        String commandId,
        String attemptId,
        Throwable failure,
        long nowEpochMs
    ) {
        return markDlq(tenantId, commandId, failure, nowEpochMs);
    }

    /**
     * Records a non-success native command outcome with its durable terminal or retryable state.
     * Implementations that preserve native outcome snapshots must override this method.
     */
    default Uni<CommandEffectRecord> markOutcome(
        String tenantId,
        String commandId,
        CommandEffectStatus status,
        Throwable failure,
        CommandOutcomeSnapshot outcome,
        long nowEpochMs
    ) {
        return Uni.createFrom().failure(new UnsupportedOperationException(
            "native command outcomes require a CommandEffectStore that persists outcome snapshots"));
    }

    default Uni<CommandEffectRecord> markOutcome(
        String tenantId,
        String commandId,
        String attemptId,
        CommandEffectStatus status,
        Throwable failure,
        CommandOutcomeSnapshot outcome,
        long nowEpochMs
    ) {
        return markOutcome(tenantId, commandId, status, failure, outcome, nowEpochMs);
    }
}
