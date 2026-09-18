package org.pipelineframework.connector;

import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectorProviderManifestLoaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsBootstrapLoadedMetadataAnchor() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> ConnectorProviderManifestLoader.metadataClassLoader(Object.class));

        assertEquals("metadata class loader anchor must not be bootstrap-loaded", failure.getMessage());
    }

    @Test
    void collapsesByteIdenticalMetadataExposedThroughSeparateClassLoaderRoots() throws Exception {
        String manifest = manifest("layered.provider", 1);
        Path contextualRoot = writeManifest("context", manifest);
        Path anchoredRoot = writeManifest("anchor", manifest);

        ConnectorProviderManifestCatalog catalog = loadFromSeparateRoots(contextualRoot, anchoredRoot);

        assertEquals(1, catalog.providers().size());
        assertEquals("layered.provider", catalog.providers().getFirst().provider().id().value());
    }

    @Test
    void retainsConflictingMetadataExposedThroughSeparateClassLoaderRoots() throws Exception {
        Path contextualRoot = writeManifest("context", manifest("layered.provider", 1));
        Path anchoredRoot = writeManifest("anchor", manifest("layered.provider", 2));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> loadFromSeparateRoots(contextualRoot, anchoredRoot));

        assertEquals("duplicate connector provider ID in static metadata: layered.provider", failure.getMessage());
    }

    @Test
    void loadsApplicationTypesFromTheContextClassLoader() throws Exception {
        ClassLoader parent = getClass().getClassLoader();
        try (URLClassLoader contextual = new URLClassLoader(new java.net.URL[0], parent);
             URLClassLoader anchored = new URLClassLoader(new java.net.URL[0], parent)) {
            Class<?> contextualType = proxyClass(contextual);
            Class<?> anchor = proxyClass(anchored);
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            try {
                thread.setContextClassLoader(contextual);

                assertSame(contextualType,
                    ConnectorProviderManifestLoader.metadataClassLoader(anchor).loadClass(contextualType.getName()));
            } finally {
                thread.setContextClassLoader(previous);
            }
        }
    }

    private ConnectorProviderManifestCatalog loadFromSeparateRoots(Path contextualRoot, Path anchoredRoot)
            throws Exception {
        ClassLoader parent = getClass().getClassLoader();
        try (URLClassLoader contextual = new URLClassLoader(new java.net.URL[]{contextualRoot.toUri().toURL()}, parent);
             URLClassLoader anchored = new URLClassLoader(new java.net.URL[]{anchoredRoot.toUri().toURL()}, parent)) {
            Class<?> anchor = proxyClass(anchored);
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            try {
                thread.setContextClassLoader(contextual);
                return ConnectorProviderManifestLoader.load(
                    ConnectorProviderManifestLoader.metadataClassLoader(anchor));
            } finally {
                thread.setContextClassLoader(previous);
            }
        }
    }

    private static Class<?> proxyClass(ClassLoader classLoader) {
        return Proxy.newProxyInstance(classLoader, new Class<?>[]{Runnable.class},
            (proxy, method, arguments) -> null).getClass();
    }

    private Path writeManifest(String directory, String manifest) throws Exception {
        Path root = temporaryDirectory.resolve(directory);
        Path resource = root.resolve(ConnectorProviderManifestLoader.RESOURCE_PATH);
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, manifest, StandardCharsets.UTF_8);
        return root;
    }

    private static String manifest(String providerId, int majorVersion) {
        return """
            {"schemaVersion":1,"providers":[{"id":"%s","version":{"major":%d,"minor":0},"operations":[]}]}
            """.formatted(providerId, majorVersion);
    }
}
