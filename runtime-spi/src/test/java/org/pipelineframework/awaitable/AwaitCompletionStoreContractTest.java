package org.pipelineframework.awaitable;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.pipelineframework.awaitable.spi.AwaitInteractionStore;
import org.pipelineframework.orchestrator.ExecutionStateStore;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AwaitCompletionStoreContractTest {
    @Test
    void completionAndReleaseObservationsRequireProviderImplementationsWithoutSequencedFallbacks() throws Exception {
        assertFalse(AwaitInteractionStore.class.getMethod("getCompletionAdmission", String.class, String.class).isDefault());
        assertFalse(AwaitInteractionStore.class.getMethod("findOpenCompletions", String.class, int.class, Optional.class).isDefault());
        assertFalse(AwaitInteractionStore.class.getMethod("retireCompletionAdmission", AwaitCompletionAdmission.class).isDefault());
        assertFalse(ExecutionStateStore.class.getMethod("getAwaitUnitReleaseAcknowledgement",
            String.class, String.class, String.class).isDefault());
    }
}
