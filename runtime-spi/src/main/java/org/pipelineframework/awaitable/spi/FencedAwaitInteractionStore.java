package org.pipelineframework.awaitable.spi;

import java.util.Optional;
import io.smallrye.mutiny.Uni;
import org.pipelineframework.awaitable.AwaitCompletionCommand;
import org.pipelineframework.awaitable.AwaitCompletionResult;
import org.pipelineframework.awaitable.AwaitInteractionRecord;

/** Store capability required by the Coordinator when typed projection runs remotely. */
public interface FencedAwaitInteractionStore extends AwaitInteractionStore {
    /**
     * Completes only the exact interaction snapshot used to calculate a canonical completion.
     * The command must carry the expected tenant and resolved interaction identifier.
     * An empty result signals a version, status or immutable-owner conflict; callers must
     * reread and revalidate before recalculating projection. Implementations must not retry
     * against a newer snapshot using the old projected payload. Matching terminal snapshots
     * retain the ordinary completion API's duplicate and rejection semantics.
     */
    Uni<Optional<AwaitCompletionResult>> complete(
        AwaitInteractionRecord expected, AwaitCompletionCommand command);

}
