/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.core.product.core.services.configuration;

import io.fireflysoftware.core.product.core.mappers.ProductConfigurationMapper;
import io.fireflysoftware.core.product.core.services.impl.ProductConfigurationServiceImpl;
import io.fireflysoftware.core.product.interfaces.dtos.ProductConfigurationDTO;
import io.fireflysoftware.core.product.interfaces.enums.ProductConfigTypeEnum;
import io.fireflysoftware.core.product.models.entities.ProductConfiguration;
import io.fireflysoftware.core.product.models.repositories.ProductConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductConfigurationServiceImplTest {

    @Mock
    private ProductConfigurationRepository repository;

    @Mock
    private ProductConfigurationMapper mapper;

    @InjectMocks
    private ProductConfigurationServiceImpl service;

    private ProductConfiguration config;
    private ProductConfigurationDTO configDTO;
    private final UUID PRODUCT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private final UUID CONFIG_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
    private final String CONFIG_KEY = "max_limit";
    private final String CONFIG_VALUE = "10000";

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        config = new ProductConfiguration();
        config.setProductConfigurationId(CONFIG_ID);
        config.setProductId(PRODUCT_ID);
        config.setConfigType(ProductConfigTypeEnum.LIMITS);
        config.setConfigKey(CONFIG_KEY);
        config.setConfigValue(CONFIG_VALUE);
        config.setDateCreated(now);
        config.setDateUpdated(now);

        configDTO = ProductConfigurationDTO.builder()
                .productConfigurationId(CONFIG_ID)
                .productId(PRODUCT_ID)
                .configType(ProductConfigTypeEnum.LIMITS)
                .configKey(CONFIG_KEY)
                .configValue(CONFIG_VALUE)
                .dateCreated(now)
                .dateUpdated(now)
                .build();
    }

    // Note: filterConfigurations test is not included because it uses FilterUtils which is a static utility
    // that works directly with the database and cannot be easily mocked in unit tests.

    @Test
    void createConfiguration_Success() {
        ProductConfigurationDTO requestDTO = ProductConfigurationDTO.builder()
                .configType(ProductConfigTypeEnum.LIMITS)
                .configKey(CONFIG_KEY)
                .configValue(CONFIG_VALUE)
                .build();

        when(mapper.toEntity(requestDTO)).thenReturn(config);
        when(repository.save(config)).thenReturn(Mono.just(config));
        when(mapper.toDto(config)).thenReturn(configDTO);

        StepVerifier.create(service.createConfiguration(PRODUCT_ID, requestDTO))
                .expectNext(configDTO)
                .verifyComplete();

        verify(mapper).toEntity(requestDTO);
        verify(repository).save(config);
        verify(mapper).toDto(config);
    }

    @Test
    void createConfiguration_Error() {
        ProductConfigurationDTO requestDTO = ProductConfigurationDTO.builder()
                .configType(ProductConfigTypeEnum.LIMITS)
                .configKey(CONFIG_KEY)
                .configValue(CONFIG_VALUE)
                .build();

        when(mapper.toEntity(requestDTO)).thenReturn(config);
        when(repository.save(config)).thenReturn(Mono.error(new RuntimeException("Database error")));

        StepVerifier.create(service.createConfiguration(PRODUCT_ID, requestDTO))
                .expectErrorMatches(throwable ->
                    throwable instanceof RuntimeException &&
                    throwable.getMessage().equals("Database error"))
                .verify();

        verify(mapper).toEntity(requestDTO);
        verify(repository).save(config);
        verify(mapper, never()).toDto(any());
    }

    @Test
    void getConfigurationById_Success() {
        when(repository.findById(CONFIG_ID)).thenReturn(Mono.just(config));
        when(mapper.toDto(config)).thenReturn(configDTO);

        StepVerifier.create(service.getConfigurationById(PRODUCT_ID, CONFIG_ID))
                .expectNext(configDTO)
                .verifyComplete();

        verify(repository).findById(CONFIG_ID);
        verify(mapper).toDto(config);
    }

    @Test
    void getConfigurationById_NotFound() {
        when(repository.findById(CONFIG_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.getConfigurationById(PRODUCT_ID, CONFIG_ID))
                .expectErrorMatches(throwable ->
                    throwable instanceof RuntimeException &&
                    throwable.getMessage().contains("Configuration not found with ID"))
                .verify();

        verify(repository).findById(CONFIG_ID);
        verify(mapper, never()).toDto(any());
    }

    @Test
    void getConfigurationById_WrongProduct() {
        ProductConfiguration configFromDifferentProduct = new ProductConfiguration();
        configFromDifferentProduct.setProductConfigurationId(CONFIG_ID);
        configFromDifferentProduct.setProductId(UUID.fromString("550e8400-e29b-41d4-a716-446655440999"));

        when(repository.findById(CONFIG_ID)).thenReturn(Mono.just(configFromDifferentProduct));

        StepVerifier.create(service.getConfigurationById(PRODUCT_ID, CONFIG_ID))
                .expectErrorMatches(throwable ->
                    throwable instanceof RuntimeException &&
                    throwable.getMessage().contains("does not belong to product"))
                .verify();

        verify(repository).findById(CONFIG_ID);
        verify(mapper, never()).toDto(any());
    }

    @Test
    void getConfigurationByKey_Success() {
        when(repository.findByProductIdAndConfigKey(PRODUCT_ID, CONFIG_KEY)).thenReturn(Mono.just(config));
        when(mapper.toDto(config)).thenReturn(configDTO);

        StepVerifier.create(service.getConfigurationByKey(PRODUCT_ID, CONFIG_KEY))
                .expectNext(configDTO)
                .verifyComplete();

        verify(repository).findByProductIdAndConfigKey(PRODUCT_ID, CONFIG_KEY);
        verify(mapper).toDto(config);
    }

    @Test
    void getConfigurationByKey_NotFound() {
        when(repository.findByProductIdAndConfigKey(PRODUCT_ID, CONFIG_KEY)).thenReturn(Mono.empty());

        StepVerifier.create(service.getConfigurationByKey(PRODUCT_ID, CONFIG_KEY))
                .expectErrorMatches(throwable ->
                    throwable instanceof RuntimeException &&
                    throwable.getMessage().contains("Configuration not found for product"))
                .verify();

        verify(repository).findByProductIdAndConfigKey(PRODUCT_ID, CONFIG_KEY);
        verify(mapper, never()).toDto(any());
    }

    @Test
    void getConfigurationsByType_Success() {
        when(repository.findByProductIdAndConfigType(PRODUCT_ID, ProductConfigTypeEnum.LIMITS))
                .thenReturn(Flux.just(config));
        when(mapper.toDto(config)).thenReturn(configDTO);

        StepVerifier.create(service.getConfigurationsByType(PRODUCT_ID, ProductConfigTypeEnum.LIMITS))
                .expectNext(configDTO)
                .verifyComplete();

        verify(repository).findByProductIdAndConfigType(PRODUCT_ID, ProductConfigTypeEnum.LIMITS);
        verify(mapper).toDto(config);
    }

    @Test
    void getConfigurationsByType_Empty() {
        when(repository.findByProductIdAndConfigType(PRODUCT_ID, ProductConfigTypeEnum.PRICING))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.getConfigurationsByType(PRODUCT_ID, ProductConfigTypeEnum.PRICING))
                .verifyComplete();

        verify(repository).findByProductIdAndConfigType(PRODUCT_ID, ProductConfigTypeEnum.PRICING);
        verify(mapper, never()).toDto(any());
    }

    @Test
    void updateConfiguration_Success() {
        ProductConfigurationDTO updateRequest = ProductConfigurationDTO.builder()
                .configType(ProductConfigTypeEnum.LIMITS)
                .configKey("updated_key")
                .configValue("20000")
                .build();

        when(repository.findById(CONFIG_ID)).thenReturn(Mono.just(config));
        doNothing().when(mapper).updateEntityFromDto(updateRequest, config);
        when(repository.save(config)).thenReturn(Mono.just(config));
        when(mapper.toDto(config)).thenReturn(updateRequest);

        StepVerifier.create(service.updateConfiguration(PRODUCT_ID, CONFIG_ID, updateRequest))
                .expectNext(updateRequest)
                .verifyComplete();

        verify(repository).findById(CONFIG_ID);
        verify(mapper).updateEntityFromDto(updateRequest, config);
        verify(repository).save(config);
        verify(mapper).toDto(config);
    }

    @Test
    void updateConfiguration_NotFound() {
        ProductConfigurationDTO updateRequest = ProductConfigurationDTO.builder()
                .configType(ProductConfigTypeEnum.LIMITS)
                .configKey("updated_key")
                .configValue("20000")
                .build();

        when(repository.findById(CONFIG_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.updateConfiguration(PRODUCT_ID, CONFIG_ID, updateRequest))
                .expectErrorMatches(throwable ->
                    throwable instanceof RuntimeException &&
                    throwable.getMessage().contains("Configuration not found with ID"))
                .verify();

        verify(repository).findById(CONFIG_ID);
        verify(mapper, never()).updateEntityFromDto(any(), any());
        verify(repository, never()).save(any());
        verify(mapper, never()).toDto(any());
    }

    @Test
    void updateConfiguration_WrongProduct() {
        ProductConfigurationDTO updateRequest = ProductConfigurationDTO.builder()
                .configType(ProductConfigTypeEnum.LIMITS)
                .configKey("updated_key")
                .configValue("20000")
                .build();

        ProductConfiguration configFromDifferentProduct = new ProductConfiguration();
        configFromDifferentProduct.setProductConfigurationId(CONFIG_ID);
        configFromDifferentProduct.setProductId(UUID.fromString("550e8400-e29b-41d4-a716-446655440999"));

        when(repository.findById(CONFIG_ID)).thenReturn(Mono.just(configFromDifferentProduct));

        StepVerifier.create(service.updateConfiguration(PRODUCT_ID, CONFIG_ID, updateRequest))
                .expectErrorMatches(throwable ->
                    throwable instanceof RuntimeException &&
                    throwable.getMessage().contains("does not belong to product"))
                .verify();

        verify(repository).findById(CONFIG_ID);
        verify(mapper, never()).updateEntityFromDto(any(), any());
        verify(repository, never()).save(any());
        verify(mapper, never()).toDto(any());
    }

    @Test
    void deleteConfiguration_Success() {
        when(repository.findById(CONFIG_ID)).thenReturn(Mono.just(config));
        when(repository.deleteById(CONFIG_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.deleteConfiguration(PRODUCT_ID, CONFIG_ID))
                .verifyComplete();

        verify(repository).findById(CONFIG_ID);
        verify(repository).deleteById(CONFIG_ID);
    }

    @Test
    void deleteConfiguration_NotFound() {
        when(repository.findById(CONFIG_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.deleteConfiguration(PRODUCT_ID, CONFIG_ID))
                .expectErrorMatches(throwable ->
                    throwable instanceof RuntimeException &&
                    throwable.getMessage().contains("Configuration not found with ID"))
                .verify();

        verify(repository).findById(CONFIG_ID);
        verify(repository, never()).deleteById(any(UUID.class));
    }

    @Test
    void deleteConfiguration_WrongProduct() {
        ProductConfiguration configFromDifferentProduct = new ProductConfiguration();
        configFromDifferentProduct.setProductConfigurationId(CONFIG_ID);
        configFromDifferentProduct.setProductId(UUID.fromString("550e8400-e29b-41d4-a716-446655440999"));

        when(repository.findById(CONFIG_ID)).thenReturn(Mono.just(configFromDifferentProduct));

        StepVerifier.create(service.deleteConfiguration(PRODUCT_ID, CONFIG_ID))
                .expectErrorMatches(throwable ->
                    throwable instanceof RuntimeException &&
                    throwable.getMessage().contains("does not belong to product"))
                .verify();

        verify(repository).findById(CONFIG_ID);
        verify(repository, never()).deleteById(any(UUID.class));
    }
}

