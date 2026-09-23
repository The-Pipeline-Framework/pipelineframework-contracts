package org.pipelineframework.objectpublish;

import java.util.Map;

/**
 * Opt-in renderer for one logical grouped object assembled from durable page fragments.
 *
 * <p>{@link #openGroup(String, Object)} renders the body of one page. Its {@code onOpen} and
 * {@code onClose} chunks must be page-local. The prefix and suffix below are emitted exactly
 * once, when the final object is composed. Metadata folding must be deterministic and bounded
 * independently of the number of pages; it must not retain item or page lists.</p>
 */
public interface PagedStreamingObjectPublishMapper<T> extends StreamingObjectPublishMapper<T> {
    ObjectPayloadChunk groupPrefix(String groupKey);

    ObjectPayloadChunk groupSuffix(String groupKey, Map<String, String> combinedMetadata);

    Map<String, String> combinePageMetadata(
        String groupKey,
        Map<String, String> accumulated,
        Map<String, String> pageMetadata);
}
