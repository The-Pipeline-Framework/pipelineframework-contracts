package org.pipelineframework.awaitable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One bounded authoritative observation; an empty filtered page can still require continuation. */
public record AwaitCompletionPage(
    List<AwaitCompletionAdmission> admissions,
    Optional<AwaitCompletionCursor> nextCursor,
    int evaluatedEntryCount
) {
    public AwaitCompletionPage {
        admissions = List.copyOf(Objects.requireNonNull(admissions, "admissions"));
        nextCursor = Objects.requireNonNull(nextCursor, "nextCursor");
        if (evaluatedEntryCount < 0 || evaluatedEntryCount > 100 || admissions.size() > evaluatedEntryCount) {
            throw new IllegalArgumentException("Await completion page must evaluate at most 100 entries and account for every admission");
        }
        var tenant = admissions.stream().findFirst().map(AwaitCompletionAdmission::tenantId);
        var interactions = new HashSet<String>();
        for (var admission : admissions) {
            if (tenant.filter(value -> !value.equals(admission.tenantId())).isPresent()) {
                throw new IllegalArgumentException("Await completion page contains another tenant");
            }
            if (!interactions.add(admission.interactionId())) {
                throw new IllegalArgumentException("Await completion page repeats an interaction admission");
            }
        }
        if (tenant.isPresent() && nextCursor.isPresent()) {
            nextCursor.orElseThrow().requireScope(tenant.orElseThrow());
        }
    }
}
