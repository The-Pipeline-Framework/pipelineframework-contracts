package org.pipelineframework.awaitable;

import java.util.Map;
import java.util.Optional;

/** Evidence reported after an already authorized send. */
public enum AwaitDispatchReportKind {
    DISPATCHED((metadata, failure, settlement, acknowledgement) ->
        failure.isEmpty() && settlement.isEmpty() && !acknowledgement),
    FAILED((metadata, failure, settlement, acknowledgement) ->
        metadata.isEmpty() && failure.filter(message -> !message.isBlank()).isPresent()
            && settlement.isEmpty() && !acknowledgement),
    COMMAND_SETTLEMENT((metadata, failure, settlement, acknowledgement) ->
        metadata.isEmpty() && failure.isEmpty() && settlement.isPresent()
            && (!acknowledgement || settlement.orElseThrow() == CommandDispatchSettlement.SUCCEEDED));

    private final EvidenceRule rule;

    AwaitDispatchReportKind(EvidenceRule rule) {
        this.rule = rule;
    }

    void validateEvidence(Map<String, Object> metadata, Optional<String> failure,
        Optional<CommandDispatchSettlement> settlement, boolean acknowledgement) {
        if (!rule.accepts(metadata, failure, settlement, acknowledgement)) {
            throw new IllegalArgumentException("Invalid dispatch evidence for " + name());
        }
    }

    private interface EvidenceRule {
        boolean accepts(Map<String, Object> metadata, Optional<String> failure,
            Optional<CommandDispatchSettlement> settlement, boolean acknowledgement);
    }
}
