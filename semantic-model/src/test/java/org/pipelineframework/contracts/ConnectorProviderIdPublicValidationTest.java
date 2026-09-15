package org.pipelineframework.contracts;

import org.junit.jupiter.api.Test;
import org.pipelineframework.connector.ConnectorProviderId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectorProviderIdPublicValidationTest {
    @Test
    void validatesContractIdsFromOutsideTheConnectorPackage() {
        assertEquals("orders.query", ConnectorProviderId.require("orders.query", "operation ID"));

        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> ConnectorProviderId.require("Orders.Query", "operation ID"));
        assertEquals("operation ID must be a lowercase dotted name: Orders.Query", invalid.getMessage());
    }
}
