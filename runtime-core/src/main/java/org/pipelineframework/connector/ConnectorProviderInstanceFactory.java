package org.pipelineframework.connector;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

/**
 * Host-internal creation seam for binding-owned provider instances.
 *
 * <p>The type is public because host implementations can live in a separate artifact and classloader. It is not a
 * provider-author extension point.</p>
 */
@FunctionalInterface
public interface ConnectorProviderInstanceFactory {
    ConnectorProviderLease create(ConnectorProvider<?> prototype);

    static ConnectorProviderInstanceFactory plainJava() {
        return prototype -> {
            Class<?> type = Objects.requireNonNull(prototype, "connector provider prototype must not be null").getClass();
            try {
                Object instance = type.getConstructor().newInstance();
                if (!(instance instanceof ConnectorProvider<?> provider)) {
                    throw new IllegalStateException("constructed type is not a connector provider: " + type.getName());
                }
                return ConnectorProviderLease.of(provider);
            } catch (NoSuchMethodException failure) {
                throw new IllegalArgumentException(
                    "connector provider " + type.getName()
                        + " needs a public no-argument constructor for named bindings",
                    failure);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException failure) {
                throw new IllegalArgumentException(
                    "failed to create binding-owned connector provider " + type.getName(), failure);
            }
        };
    }
}
