package org.pipelineframework.command;

import java.util.Objects;
import java.util.Optional;
import org.pipelineframework.connector.CommandRecoveryBinding;

/**
 * Immutable state snapshot for one actual dispatch attempt of a logical Command effect.
 */
public record CommandEffectAttemptRecord(
    String attemptId,
    String occurrenceId,
    int attemptNumber,
    String executionId,
    CommandAttemptPurpose purpose,
    CommandEffectStatus status,
    Optional<Object> output,
    String errorClass,
    String errorMessage,
    Optional<CommandOutcomeSnapshot> outcome,
    Optional<String> reason,
    long createdAtEpochMs,
    long updatedAtEpochMs,
    Optional<CommandRecoveryBinding> recoveryBinding,
    Optional<CommandReconciliationReceipt> reconciliationReceipt
) {
    /** Compatibility constructor: older/unbound attempts are not implicitly recoverable. */
    public CommandEffectAttemptRecord(
        String attemptId, String occurrenceId, int attemptNumber, String executionId,
        CommandAttemptPurpose purpose, CommandEffectStatus status, Optional<Object> output,
        String errorClass, String errorMessage, Optional<CommandOutcomeSnapshot> outcome,
        Optional<String> reason, long createdAtEpochMs, long updatedAtEpochMs
    ) {
        this(attemptId, occurrenceId, attemptNumber, executionId, purpose, status, output,
            errorClass, errorMessage, outcome, reason, createdAtEpochMs, updatedAtEpochMs,
            Optional.empty(), Optional.empty());
    }

    public CommandEffectAttemptRecord {
        if (attemptId == null || attemptId.isBlank()) {
            throw new IllegalArgumentException("attemptId must not be blank");
        }
        if (occurrenceId == null || occurrenceId.isBlank()) {
            throw new IllegalArgumentException("occurrenceId must not be blank");
        }
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be positive");
        }
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        output = output == null ? Optional.empty() : output;
        outcome = outcome == null ? Optional.empty() : outcome;
        reason = reason == null ? Optional.empty() : reason.map(String::trim).filter(value -> !value.isEmpty());
        recoveryBinding = recoveryBinding == null ? Optional.empty() : recoveryBinding;
        reconciliationReceipt = reconciliationReceipt == null ? Optional.empty() : reconciliationReceipt;
        if (recoveryBinding.isPresent()) {
            CommandRecoveryBinding binding = recoveryBinding.orElseThrow();
            if (!attemptId.equals(binding.attemptId()) || !occurrenceId.equals(binding.occurrenceId())
                || !executionId.equals(binding.executionId())) {
                throw new IllegalArgumentException("recovery binding must identify this attempt and occurrence");
            }
        }
        if (reconciliationReceipt.isPresent()
            && (status != CommandEffectStatus.SUCCEEDED || recoveryBinding.isEmpty()
                || output.isEmpty() || outcome.isEmpty()
                || outcome.orElseThrow().outcomeStatus() != CommandEffectStatus.SUCCEEDED)) {
            throw new IllegalArgumentException("reconciliation receipt requires a bound successful attempt");
        }
        if (purpose == CommandAttemptPurpose.REISSUE && reason.isEmpty()) {
            throw new IllegalArgumentException("reissue attempt reason must not be blank");
        }
        if (createdAtEpochMs < 0 || updatedAtEpochMs < createdAtEpochMs) {
            throw new IllegalArgumentException("invalid attempt timestamps");
        }
        if (reconciliationReceipt.isPresent()) {
            long settled = reconciliationReceipt.orElseThrow().settledAtEpochMs();
            if (settled < createdAtEpochMs || settled > updatedAtEpochMs) {
                throw new IllegalArgumentException("settlement timestamp is outside the attempt history");
            }
        }
    }

    public CommandEffectAttemptRecord withStatus(CommandEffectStatus newStatus, long nowEpochMs) {
        return new CommandEffectAttemptRecord(
            attemptId, occurrenceId, attemptNumber, executionId, purpose, newStatus, output,
            errorClass, errorMessage, outcome, reason, createdAtEpochMs, nowEpochMs,
            recoveryBinding, reconciliationReceipt);
    }

    /** Attaches original immutable metadata only before this reservation can dispatch. */
    public CommandEffectAttemptRecord bindRecovery(CommandRecoveryBinding binding) {
        Objects.requireNonNull(binding, "recovery binding must not be null");
        if (status != CommandEffectStatus.PENDING || recoveryBinding.isPresent()) {
            throw new IllegalStateException("recovery binding must be established once before dispatch");
        }
        return new CommandEffectAttemptRecord(
            attemptId, occurrenceId, attemptNumber, executionId, purpose, status, output,
            errorClass, errorMessage, outcome, reason, createdAtEpochMs, updatedAtEpochMs,
            Optional.of(binding), Optional.empty());
    }

    public CommandEffectAttemptRecord reconciledSucceeded(
        Object commandOutput, CommandOutcomeSnapshot snapshot, CommandReconciliationReceipt receipt, long nowEpochMs
    ) {
        if (recoveryBinding.isEmpty()
            || (status != CommandEffectStatus.DISPATCHING && status != CommandEffectStatus.AMBIGUOUS)) {
            throw new IllegalStateException("reconciliation requires a bound uncertain dispatched attempt");
        }
        return new CommandEffectAttemptRecord(
            attemptId, occurrenceId, attemptNumber, executionId, purpose, CommandEffectStatus.SUCCEEDED,
            Optional.of(Objects.requireNonNull(commandOutput, "reconciled output must not be null")),
            null, null, Optional.of(Objects.requireNonNull(snapshot, "outcome snapshot must not be null")),
            reason, createdAtEpochMs, nowEpochMs, recoveryBinding,
            Optional.of(Objects.requireNonNull(receipt, "reconciliation receipt must not be null")));
    }

    public CommandEffectAttemptRecord succeeded(
        Object commandOutput,
        CommandOutcomeSnapshot snapshot,
        long nowEpochMs
    ) {
        return terminal(
            CommandEffectStatus.SUCCEEDED,
            Optional.ofNullable(commandOutput),
            null,
            snapshot,
            nowEpochMs);
    }

    public CommandEffectAttemptRecord failed(
        CommandEffectStatus failureStatus,
        Throwable failure,
        CommandOutcomeSnapshot snapshot,
        long nowEpochMs
    ) {
        return terminal(failureStatus, Optional.empty(), failure, snapshot, nowEpochMs);
    }

    private CommandEffectAttemptRecord terminal(
        CommandEffectStatus terminalStatus,
        Optional<Object> terminalOutput,
        Throwable failure,
        CommandOutcomeSnapshot snapshot,
        long nowEpochMs
    ) {
        return new CommandEffectAttemptRecord(
            attemptId,
            occurrenceId,
            attemptNumber,
            executionId,
            purpose,
            terminalStatus,
            terminalOutput,
            failure == null ? null : failure.getClass().getName(),
            failure == null ? null : failure.getMessage(),
            Optional.ofNullable(snapshot),
            reason,
            createdAtEpochMs,
            nowEpochMs,
            recoveryBinding,
            reconciliationReceipt);
    }
}
