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


package io.fireflysoftware.core.product.core.services.category;

import io.fireflysoftware.core.product.core.mappers.ProductCategoryMapper;
import io.fireflysoftware.core.product.core.services.impl.ProductCategoryServiceImpl;
import io.fireflysoftware.core.product.interfaces.dtos.ProductCategoryDTO;
import io.fireflysoftware.core.product.models.entities.ProductCategory;
import io.fireflysoftware.core.product.models.repositories.ProductCategoryRepository;
import io.fireflysoftware.core.product.models.repositories.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceImplTest {

    @Mock
    private ProductCategoryRepository repository;

    @Mock
    private ProductCategoryMapper mapper;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductCategoryServiceImpl service;

    private ProductCategory productCategory;
    private ProductCategoryDTO productCategoryDTO;
    private final UUID CATEGORY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private final UUID PARENT_CATEGORY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

    @BeforeEach
    void setUp() {
        // Setup test data
        LocalDateTime now = LocalDateTime.now();

        productCategory = new ProductCategory();
        productCategory.setProductCategoryId(CATEGORY_ID);
        productCategory.setCategoryName("Test Category");
        productCategory.setCategoryDescription("Test Description");
        productCategory.setParentCategoryId(PARENT_CATEGORY_ID);
        productCategory.setLevel(1);
        productCategory.setDateCreated(now);
        productCategory.setDateUpdated(now);

        productCategoryDTO = ProductCategoryDTO.builder()
                .productCategoryId(CATEGORY_ID)
                .categoryName("Test Category")
                .categoryDescription("Test Description")
                .parentCategoryId(PARENT_CATEGORY_ID)
                .level(1)
                .dateCreated(now)
                .dateUpdated(now)
                .build();
        lenient().when(productRepository.countByProductCategoryId(any())).thenReturn(Mono.just(0L));
    }

    // Note: filterRootCategories, filterChildCategories, and filterCategoriesByName tests are not included
    // because they use FilterUtils which is a static utility that works directly with the database
    // and cannot be easily mocked in unit tests.

    @Test
    void getCategoryById_Success() {
        // Arrange
        when(repository.findById(CATEGORY_ID)).thenReturn(Mono.just(productCategory));
        when(mapper.toDto(productCategory)).thenReturn(productCategoryDTO);

        // Act & Assert
        StepVerifier.create(service.getCategoryById(CATEGORY_ID))
                .expectNext(productCategoryDTO)
                .verifyComplete();

        verify(repository).findById(CATEGORY_ID);
        verify(mapper).toDto(productCategory);
    }

    @Test
    void getCategoryById_NotFound() {
        // Arrange
        when(repository.findById(CATEGORY_ID)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.getCategoryById(CATEGORY_ID))
                .expectErrorMatches(throwable ->
                    throwable instanceof RuntimeException &&
                    throwable.getMessage().contains("Category not found with ID"))
                .verify();

        verify(repository).findById(CATEGORY_ID);
        verify(mapper, never()).toDto(any());
    }

    @Test
    void create_Success() {
        // Arrange
        ProductCategory parentCategory = new ProductCategory();
        parentCategory.setProductCategoryId(PARENT_CATEGORY_ID);
        parentCategory.setLevel(0);

        when(repository.findById(PARENT_CATEGORY_ID)).thenReturn(Mono.just(parentCategory));
        when(mapper.toEntity(any(ProductCategoryDTO.class))).thenReturn(productCategory);
        when(repository.save(productCategory)).thenReturn(Mono.just(productCategory));
        when(mapper.toDto(productCategory)).thenReturn(productCategoryDTO);

        // Act & Assert
        StepVerifier.create(service.createCategory(productCategoryDTO))
                .expectNext(productCategoryDTO)
                .verifyComplete();

        verify(repository).findById(PARENT_CATEGORY_ID);
        verify(mapper).toEntity(any(ProductCategoryDTO.class));
        verify(repository).save(productCategory);
        verify(mapper).toDto(productCategory);
    }

    @Test
    void createCategory_RootCategory_Success() {
        // Arrange - root category has no parent
        ProductCategoryDTO rootCategoryDTO = ProductCategoryDTO.builder()
                .categoryName("Root Category")
                .categoryDescription("Root Description")
                .parentCategoryId(null)
                .build();

        ProductCategory rootCategory = new ProductCategory();
        rootCategory.setCategoryName("Root Category");
        rootCategory.setCategoryDescription("Root Description");
        rootCategory.setLevel(0);

        when(mapper.toEntity(any(ProductCategoryDTO.class))).thenReturn(rootCategory);
        when(repository.save(rootCategory)).thenReturn(Mono.just(rootCategory));
        when(mapper.toDto(rootCategory)).thenReturn(rootCategoryDTO);

        // Act & Assert
        StepVerifier.create(service.createCategory(rootCategoryDTO))
                .expectNext(rootCategoryDTO)
                .verifyComplete();

        verify(repository, never()).findById(any(UUID.class));
        verify(mapper).toEntity(any(ProductCategoryDTO.class));
        verify(repository).save(rootCategory);
        verify(mapper).toDto(rootCategory);
    }

    @Test
    void createCategory_Error() {
        // Arrange
        ProductCategory parentCategory = new ProductCategory();
        parentCategory.setProductCategoryId(PARENT_CATEGORY_ID);
        parentCategory.setLevel(0);

        when(repository.findById(PARENT_CATEGORY_ID)).thenReturn(Mono.just(parentCategory));
        when(mapper.toEntity(any(ProductCategoryDTO.class))).thenReturn(productCategory);
        when(repository.save(productCategory)).thenReturn(Mono.error(new RuntimeException("Database error")));

        // Act & Assert
        StepVerifier.create(service.createCategory(productCategoryDTO))
                .expectErrorMatches(throwable ->
                    throwable instanceof RuntimeException)
                .verify();

        verify(mapper).toEntity(any(ProductCategoryDTO.class));
        verify(repository).save(productCategory);
        verify(mapper, never()).toDto(any());
    }

    @Test
    void update_Success() {
        // Arrange
        ProductCategory existingCategory = new ProductCategory();
        existingCategory.setProductCategoryId(CATEGORY_ID);
        existingCategory.setCategoryName("Old Name");
        existingCategory.setCategoryDescription("Old Description");
        existingCategory.setParentCategoryId(UUID.fromString("550e8400-e29b-41d4-a716-446655440999")); // Different parent ID
        existingCategory.setLevel(1);

        ProductCategory parentCategory = new ProductCategory();
        parentCategory.setProductCategoryId(PARENT_CATEGORY_ID);
        parentCategory.setLevel(0);

        when(repository.findById(CATEGORY_ID)).thenReturn(Mono.just(existingCategory));
        when(repository.findById(PARENT_CATEGORY_ID)).thenReturn(Mono.just(parentCategory));
        doNothing().when(mapper).updateEntityFromDto(productCategoryDTO, existingCategory);
        when(repository.save(existingCategory)).thenReturn(Mono.just(existingCategory));
        when(mapper.toDto(existingCategory)).thenReturn(productCategoryDTO);

        // Act & Assert
        StepVerifier.create(service.updateCategory(CATEGORY_ID, productCategoryDTO))
                .expectNext(productCategoryDTO)
                .verifyComplete();

        verify(repository, times(3)).findById(any(UUID.class)); // Once for existing, once for circular check, once for level calculation
        verify(mapper).updateEntityFromDto(productCategoryDTO, existingCategory);
        verify(repository).save(existingCategory);
        verify(mapper).toDto(existingCategory);
    }

    @Test
    void updateCategory_CircularReference_SelfParent() {
        // Arrange - trying to set a category as its own parent
        ProductCategoryDTO selfParentDTO = ProductCategoryDTO.builder()
                .categoryName("Test Category")
                .categoryDescription("Test Description")
                .parentCategoryId(CATEGORY_ID) // Same as the category being updated
                .build();

        when(repository.findById(CATEGORY_ID)).thenReturn(Mono.just(productCategory));

        // Act & Assert
        StepVerifier.create(service.updateCategory(CATEGORY_ID, selfParentDTO))
                .expectErrorMatches(throwable ->
                    throwable instanceof RuntimeException &&
                    throwable.getMessage().equals("A category cannot be its own parent"))
                .verify();
    }

    @Test
    void updateCategory_NotFound() {
        // Arrange
        when(repository.findById(CATEGORY_ID)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.updateCategory(CATEGORY_ID, productCategoryDTO))
                .expectErrorMatches(throwable ->
                    throwable instanceof RuntimeException &&
                    throwable.getMessage().contains("Category not found with ID"))
                .verify();

        verify(repository).findById(CATEGORY_ID);
        verify(repository, never()).save(any());
        verify(mapper, never()).toDto(any());
    }

    @Test
    void updateCategory_Error() {
        // Arrange
        ProductCategory parentCategory = new ProductCategory();
        parentCategory.setProductCategoryId(PARENT_CATEGORY_ID);
        parentCategory.setLevel(0);

        when(repository.findById(CATEGORY_ID)).thenReturn(Mono.just(productCategory));
        when(repository.findById(PARENT_CATEGORY_ID)).thenReturn(Mono.just(parentCategory));
        doNothing().when(mapper).updateEntityFromDto(productCategoryDTO, productCategory);
        when(repository.save(productCategory)).thenReturn(Mono.error(new RuntimeException("Database error")));

        // Act & Assert
        StepVerifier.create(service.updateCategory(CATEGORY_ID, productCategoryDTO))
                .expectErrorMatches(throwable ->
                    throwable instanceof RuntimeException)
                .verify();

        verify(repository).save(productCategory);
        verify(mapper, never()).toDto(any());
    }

    @Test
    void deleteCategory_Success() {
        // Arrange
        when(repository.findById(CATEGORY_ID)).thenReturn(Mono.just(productCategory));
        when(repository.countByParentCategoryId(CATEGORY_ID)).thenReturn(Mono.just(0L));
        when(repository.deleteById(CATEGORY_ID)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.deleteCategory(CATEGORY_ID))
                .verifyComplete();

        verify(repository).findById(CATEGORY_ID);
        verify(repository).countByParentCategoryId(CATEGORY_ID);
        verify(repository).deleteById(CATEGORY_ID);
    }

    @Test
    void deleteCategory_HasChildren() {
        // Arrange - category has child categories
        when(repository.findById(CATEGORY_ID)).thenReturn(Mono.just(productCategory));
        when(repository.countByParentCategoryId(CATEGORY_ID)).thenReturn(Mono.just(2L));

        // Act & Assert
        StepVerifier.create(service.deleteCategory(CATEGORY_ID))
                .expectErrorMatches(throwable ->
                    throwable instanceof RuntimeException &&
                    throwable.getMessage().contains("because it has child categories"))
                .verify();

        verify(repository).findById(CATEGORY_ID);
        verify(repository).countByParentCategoryId(CATEGORY_ID);
        verify(repository, never()).deleteById((UUID) any());
    }

    @Test
    void deleteCategory_NotFound() {
        // Arrange
        when(repository.findById(CATEGORY_ID)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.deleteCategory(CATEGORY_ID))
                .expectErrorMatches(throwable ->
                    throwable instanceof RuntimeException &&
                    throwable.getMessage().contains("Category not found with ID"))
                .verify();

        verify(repository).findById(CATEGORY_ID);
        verify(repository, never()).deleteById((UUID) any());
    }

    @Test
    void deleteCategory_Error() {
        // Arrange
        when(repository.findById(CATEGORY_ID)).thenReturn(Mono.just(productCategory));
        when(repository.countByParentCategoryId(CATEGORY_ID)).thenReturn(Mono.just(0L));
        when(repository.deleteById(CATEGORY_ID)).thenReturn(Mono.error(new RuntimeException("Database error")));

        // Act & Assert
        StepVerifier.create(service.deleteCategory(CATEGORY_ID))
                .expectErrorMatches(throwable ->
                    throwable instanceof RuntimeException)
                .verify();

        verify(repository).findById(CATEGORY_ID);
        verify(repository).countByParentCategoryId(CATEGORY_ID);
        verify(repository).deleteById(CATEGORY_ID);
    }

    @Test
    void deleteCategory_HasProducts() {
        when(repository.findById(CATEGORY_ID)).thenReturn(Mono.just(productCategory));
        when(repository.countByParentCategoryId(CATEGORY_ID)).thenReturn(Mono.just(0L));
        when(productRepository.countByProductCategoryId(CATEGORY_ID)).thenReturn(Mono.just(1L));

        StepVerifier.create(service.deleteCategory(CATEGORY_ID))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException
                                && throwable.getMessage().contains("products still use it"))
                .verify();

        verify(repository, never()).deleteById((UUID) any());
    }
}
