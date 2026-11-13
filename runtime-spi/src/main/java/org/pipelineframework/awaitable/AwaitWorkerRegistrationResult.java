package org.pipelineframework.awaitable;

import java.util.Objects;
import java.util.Optional;

/** Retained registration and an optional opaque callback token issued only by its Coordinator. */
public record AwaitWorkerRegistrationResult(AwaitCreateResult creation, Optional<String> callbackToken) {
    public AwaitWorkerRegistrationResult {
        Objects.requireNonNull(creation, "creation");
        Objects.requireNonNull(callbackToken, "callbackToken").ifPresent(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("Callback token must not be blank");
        });
    }
}
