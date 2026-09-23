package org.pipelineframework.objectpublish;

import java.util.Objects;
import org.pipelineframework.config.boundary.PipelineObjectPublishConfig;

/** Query for durable part manifests below one execution-owned stage prefix. */
public record PagedObjectPartQuery(
    String targetName,
    PipelineObjectPublishConfig target,
    String stagePrefix
) {
    public PagedObjectPartQuery {
        if (targetName == null || targetName.isBlank() || stagePrefix == null || stagePrefix.isBlank()) {
            throw new IllegalArgumentException("paged object query identities must not be blank");
        }
        Objects.requireNonNull(target, "target must not be null");
    }
}
