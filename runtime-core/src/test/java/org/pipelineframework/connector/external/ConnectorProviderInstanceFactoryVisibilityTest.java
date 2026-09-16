package org.pipelineframework.connector.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pipelineframework.connector.ConnectorBindingRegistry;
import org.pipelineframework.connector.ConnectorConfigSchema;
import org.pipelineframework.connector.ConnectorConfigurationBinder;
import org.pipelineframework.connector.ConnectorConfigurationDocument;
import org.pipelineframework.connector.ConnectorProviderInstanceFactory;
import org.pipelineframework.connector.ConnectorProviderLease;

class ConnectorProviderInstanceFactoryVisibilityTest {
    @Test
    void hostFactoryCanBeImplementedOutsideTheConnectorPackage() {
        ConnectorProviderInstanceFactory factory = provider -> ConnectorProviderLease.of(provider, () -> { });

        assertNotNull(factory);
        assertNotNull(ConnectorBindingRegistry.fromProvidersAllowingUnavailable(List.of(), List.of(), factory));
    }

    @Test
    void configurationRecordsCanRemainNonPublicOutsideTheBinderPackage() {
        ConnectorConfigSchema<HostConfig> schema = ConnectorConfigSchema.record(HostConfig.class, "test.host", 1);

        HostConfig config = ConnectorConfigurationBinder.bind(
            schema, new ConnectorConfigurationDocument(Map.of("name", "host")), "host");

        assertEquals(new HostConfig("host"), config);
    }

    record HostConfig(String name) {
    }
}
