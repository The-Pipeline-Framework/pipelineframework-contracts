package org.pipelineframework.connector;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One host-owned provider instance and its container cleanup action.
 *
 * <p>The type is public so a host adapter in another artifact and classloader can implement
 * {@link ConnectorProviderInstanceFactory}. Provider authors should not create leases directly.</p>
 */
public final class ConnectorProviderLease {
    private final ConnectorProvider<?> provider;
    private final Runnable release;
    private final AtomicBoolean released = new AtomicBoolean();

    private ConnectorProviderLease(ConnectorProvider<?> provider, Runnable release) {
        this.provider = Objects.requireNonNull(provider, "connector provider must not be null");
        this.release = Objects.requireNonNull(release, "connector provider release action must not be null");
    }

    static ConnectorProviderLease of(ConnectorProvider<?> provider) {
        return new ConnectorProviderLease(provider, () -> { });
    }

    public static ConnectorProviderLease of(ConnectorProvider<?> provider, Runnable release) {
        return new ConnectorProviderLease(provider, release);
    }

    ConnectorProvider<?> provider() {
        return provider;
    }

    void release() {
        if (released.compareAndSet(false, true)) {
            release.run();
        }
    }
}
