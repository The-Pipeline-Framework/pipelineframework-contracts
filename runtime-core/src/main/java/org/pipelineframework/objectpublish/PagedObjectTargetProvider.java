package org.pipelineframework.objectpublish;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Object target capability for durable page manifests and bounded-memory final composition. */
public interface PagedObjectTargetProvider extends ObjectTargetProvider {
    CompletionStage<List<PagedObjectPart>> listParts(PagedObjectPartQuery query);

    CompletionStage<ObjectWriteResult> compose(PagedObjectCompositionRequest request);
}
