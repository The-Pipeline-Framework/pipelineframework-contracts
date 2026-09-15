package org.pipelineframework.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable snapshot of authored YAML-like configuration values. */
public final class ImmutableConfigValues {
    private ImmutableConfigValues() {
    }

    public static Map<String, Object> copy(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copied = new LinkedHashMap<>();
        values.forEach((key, value) -> copied.put(
            Objects.requireNonNull(key, "configuration key must not be null"),
            copyValue(Objects.requireNonNull(value, "configuration value must not be null"))));
        return Collections.unmodifiableMap(copied);
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copied = new LinkedHashMap<>();
            map.forEach((key, nested) -> copied.put(
                copyScalar(Objects.requireNonNull(key, "nested configuration key must not be null")),
                nested == null ? null : copyValue(nested)));
            return Collections.unmodifiableMap(copied);
        }
        if (value instanceof List<?> list) {
            List<Object> copied = new ArrayList<>(list.size());
            list.forEach(nested -> copied.add(nested == null ? null : copyValue(nested)));
            return Collections.unmodifiableList(copied);
        }
        if (value instanceof Set<?> set) {
            Set<Object> copied = new LinkedHashSet<>();
            set.forEach(nested -> copied.add(nested == null ? null : copyValue(nested)));
            return Collections.unmodifiableSet(copied);
        }
        return copyScalar(value);
    }

    private static Object copyScalar(Object value) {
        if (value instanceof String || value instanceof Boolean || value instanceof Character
            || value instanceof Byte || value instanceof Short || value instanceof Integer
            || value instanceof Long || value instanceof Float || value instanceof Double
            || value instanceof BigInteger || value instanceof BigDecimal || value instanceof Enum<?>) {
            return value;
        }
        throw new IllegalArgumentException("Unsupported mutable configuration value: " + value.getClass().getName());
    }
}
