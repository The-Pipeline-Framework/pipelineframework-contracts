package org.pipelineframework.objectpublish;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.pipelineframework.config.boundary.PipelineObjectPublishConfig;

/** Bounded-memory final composition of already committed page fragments. */
public record PagedObjectCompositionRequest(
    String targetName,
    PipelineObjectPublishConfig target,
    String objectKey,
    String contentType,
    Map<String, String> metadata,
    String idempotencyKey,
    byte[] prefix,
    List<String> orderedPartKeys,
    byte[] suffix
) {
    public PagedObjectCompositionRequest {
        if (targetName == null || targetName.isBlank() || objectKey == null || objectKey.isBlank()
            || contentType == null || contentType.isBlank() || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("paged object composition identities must not be blank");
        }
        Objects.requireNonNull(target, "target must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        prefix = prefix == null ? new byte[0] : prefix.clone();
        orderedPartKeys = List.copyOf(Objects.requireNonNull(orderedPartKeys, "orderedPartKeys must not be null"));
        if (orderedPartKeys.isEmpty() || orderedPartKeys.stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalArgumentException("orderedPartKeys must contain nonblank part keys");
        }
        suffix = suffix == null ? new byte[0] : suffix.clone();
    }

    @Override public byte[] prefix() { return prefix.clone(); }
    @Override public byte[] suffix() { return suffix.clone(); }
}
