package org.pipelineframework.orchestrator.release;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Runtime-build-owned package facts; contains no topology, credentials or compiler semantics. */
public record PipelineReleaseRuntimeCapabilities(int schemaVersion, Map<String, Selection> capabilities) {
    public static final String RESOURCE_PATH = "META-INF/tpf/runtime-capabilities.properties";
    public static final String COMMAND_EFFECT_DYNAMODB = "aws:dynamodb:command-effects";
    public static final String QUERY_CAPTURE_DYNAMODB = "aws:dynamodb:query-capture";
    public static final int MAX_BYTES = 16 * 1024;
    private static final List<Map.Entry<String, String>> STORE_FIELDS = List.of(
        Map.entry(COMMAND_EFFECT_DYNAMODB, "commandEffectStore"),
        Map.entry(QUERY_CAPTURE_DYNAMODB, "queryCaptureStore"));
    public PipelineReleaseRuntimeCapabilities {
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported runtime capability facts schema");
        capabilities = Map.copyOf(Objects.requireNonNull(capabilities, "Runtime capability facts are required"));
        if (capabilities.size() > 64 || capabilities.keySet().stream().anyMatch(k -> k.isBlank() || k.length() > 128))
            throw new IllegalArgumentException("Invalid runtime capability facts");
    }
    public record Selection(String provider, List<String> selectedBeans, boolean recoverySupported) {
        public Selection {
            if (provider == null || !provider.matches("[a-z][a-z0-9.-]{0,127}"))
                throw new IllegalArgumentException("Runtime capability provider is required");
            selectedBeans = List.copyOf(Objects.requireNonNull(selectedBeans, "Selected runtime beans are required"));
            if (selectedBeans.size() > 16 || selectedBeans.stream().anyMatch(s -> s.length() > 512 || !s.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")))
                throw new IllegalArgumentException("Invalid selected runtime beans");
        }
    }
    public Selection requireRecoverable(String capability) {
        var selection = capabilities.get(capability);
        if (selection == null || !selection.recoverySupported() || selection.selectedBeans().size() != 1
            || (isStoreCapability(capability) && !"dynamo".equals(selection.provider())))
            throw new IllegalArgumentException("Package does not provide the required recoverable runtime capability " + capability);
        return selection;
    }
    public static PipelineReleaseRuntimeCapabilities parse(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Bounded runtime capability facts are required");
        var values = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate runtime capability fact");
                return super.put(key, value);
            }
        };
        values.load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
        var remaining = new HashSet<>(values.stringPropertyNames());
        if (!"1".equals(values.getProperty("schemaVersion")))
            throw new IllegalArgumentException("Unsupported runtime capability facts schema");
        remaining.remove("schemaVersion");
        var selected = new HashMap<String, Selection>();
        for (var store : STORE_FIELDS) {
            String prefix = store.getValue();
            String provider = values.getProperty(prefix + ".provider");
            if (provider == null) continue;
            String recovery = values.getProperty(prefix + ".recoverySupported");
            String beans = values.getProperty(prefix + ".selectedBeans");
            if (beans == null || (!"true".equals(recovery) && !"false".equals(recovery)))
                throw new IllegalArgumentException("Incomplete or invalid runtime capability facts for " + prefix);
            selected.put(store.getKey(), new Selection(provider, beans.isEmpty() ? List.of() : List.of(beans.split(",", -1)),
                Boolean.parseBoolean(recovery)));
            remaining.removeAll(List.of(prefix + ".provider", prefix + ".selectedBeans", prefix + ".recoverySupported"));
        }
        if (selected.isEmpty() || !remaining.isEmpty())
            throw new IllegalArgumentException("Unsupported runtime capability facts fields");
        return new PipelineReleaseRuntimeCapabilities(1, selected);
    }
    /** Deterministic owner output; no timestamps, escaping or environment values. */
    public byte[] encode() {
        if (capabilities.isEmpty() || capabilities.keySet().stream().anyMatch(capability -> !isStoreCapability(capability)))
            throw new IllegalArgumentException("Unsupported runtime capability output fields");
        var output = new StringBuilder("schemaVersion=1\n");
        for (var store : STORE_FIELDS) {
            var selected = capabilities.get(store.getKey());
            if (selected == null) continue;
            String prefix = store.getValue();
            output.append(prefix).append(".provider=").append(selected.provider()).append('\n');
            output.append(prefix).append(".selectedBeans=")
                .append(String.join(",", selected.selectedBeans().stream().sorted().toList())).append('\n');
            output.append(prefix).append(".recoverySupported=").append(selected.recoverySupported()).append('\n');
        }
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    static boolean isStoreCapability(String capability) {
        return STORE_FIELDS.stream().anyMatch(store -> store.getKey().equals(capability));
    }
}
