package org.pipelineframework.connector;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Pull-based, bounded object read. The caller requests each chunk and closes on cancellation. */
public interface ObjectReadSession extends AutoCloseable {
    /** Returns an empty value at end of stream. A subsequent read requires the previous stage to complete. */
    CompletionStage<Optional<ByteBuffer>> read(int maxBytes);

    /** Releases the provider stream, including after client cancellation. */
    @Override
    void close();
}
