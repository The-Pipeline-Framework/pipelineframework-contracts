package org.pipelineframework.objectpublish;

import java.util.Map;

/** Durable manifest for one attempt-safe page fragment. */
public record PagedObjectPart(
    String objectKey,
    String groupKey,
    String finalObjectKey,
    String contentType,
    int pageIndex,
    Map<String, String> metadata
) {
    public PagedObjectPart {
        if (objectKey == null || objectKey.isBlank() || groupKey == null || groupKey.isBlank()
            || finalObjectKey == null || finalObjectKey.isBlank() || contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("paged object part identities must not be blank");
        }
        if (pageIndex < 0) {
            throw new IllegalArgumentException("pageIndex must not be negative");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
