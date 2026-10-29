package io.fireflysoftware.core.product.web.config.definition;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionReadAuthorization;
import io.fireflysoftware.core.product.models.repositories.definition.*;
import io.r2dbc.spi.ConnectionFactory;

import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.r2dbc.core.DatabaseClient;

class ProductDefinitionServingConfigurationTest {
    @Test
    void aDifferentEntityOperationsFactoryIsRejectedBeforeAnyReadCanStart() {
        var operations = mock(R2dbcEntityOperations.class);
        var client = mock(DatabaseClient.class);
        when(operations.getDatabaseClient()).thenReturn(client);
        when(client.getConnectionFactory()).thenReturn(mock(ConnectionFactory.class));
        assertThatThrownBy(
                        () ->
                                new ProductDefinitionServingConfiguration()
                                        .productDefinitionServingService(
                                                mock(ProductDefinitionReadAuthorization.class),
                                                mock(ProductDefinitionRootRepository.class),
                                                mock(ProductDefinitionRevisionRepository.class),
                                                mock(ProductDefinitionPublicationRepository.class),
                                                mock(
                                                        ProductDefinitionPublicationControlRepository
                                                                .class),
                                                mock(ProductDefinitionReadContextRepository.class),
                                                mock(
                                                        ProductDefinitionVersionIdentityRepository
                                                                .class),
                                                operations,
                                                mock(ConnectionFactory.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same ConnectionFactory");
    }
}
