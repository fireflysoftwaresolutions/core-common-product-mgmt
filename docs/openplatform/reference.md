# Product Management: source reference

[Repository guide](../../README.md) · [Machine-readable inventory](source-inventory.json)

This reference is derived from checked-in source. It separates declared HTTP mappings, typed contracts, property placeholders, and validation locations. Java reflection, inherited framework routes, generated targets, and runtime infrastructure are outside this static inventory.

## HTTP operations

| Mapping | Method | Controller | Kind |
| --- | --- | --- | --- |
| `GET /api/v1/categories/{categoryId}` | `public Mono<ResponseEntity<ProductCategoryDTO>> getCategoryById( @Parameter(description = "Unique identifier of the product category", required = true) @PathVariable UUID categoryId)` | [ProductCategoryController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductCategoryController.java) | Local declaration |
| `POST /api/v1/categories/filter` | `public Mono<ResponseEntity<PaginationResponse<ProductCategoryDTO>>> filterCategories( @Parameter(description = "Filter criteria for categories", required = true) @Valid @RequestBody FilterRequest<ProductCategoryDTO> filterRequest)` | [ProductCategoryController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductCategoryController.java) | Local declaration |
| `POST /api/v1/categories` | `public Mono<ResponseEntity<ProductCategoryDTO>> createCategory( @Parameter(description = "Category data to create", required = true) @Valid @RequestBody ProductCategoryDTO categoryDTO)` | [ProductCategoryController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductCategoryController.java) | Local declaration |
| `PUT /api/v1/categories/{categoryId}` | `public Mono<ResponseEntity<ProductCategoryDTO>> updateCategory( @Parameter(description = "Unique identifier of the product category", required = true) @PathVariable UUID categoryId, @Parameter(description = "Updated category data", required = true) @Valid @RequestBody ProductCategoryDTO categoryDTO)` | [ProductCategoryController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductCategoryController.java) | Local declaration |
| `DELETE /api/v1/categories/{categoryId}` | `public Mono<ResponseEntity<Void>> deleteCategory( @Parameter(description = "Unique identifier of the product category", required = true) @PathVariable UUID categoryId)` | [ProductCategoryController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductCategoryController.java) | Local declaration |
| `GET /api/v1/products/{productId}/configurations/{configId}` | `public Mono<ResponseEntity<ProductConfigurationDTO>> getConfigurationById( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Unique identifier of the product configuration", required = true) @PathVariable UUID configId)` | [ProductConfigurationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductConfigurationController.java) | Local declaration |
| `GET /api/v1/products/{productId}/configurations/by-key/{configKey}` | `public Mono<ResponseEntity<ProductConfigurationDTO>> getConfigurationByKey( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Configuration key", required = true) @PathVariable String configKey)` | [ProductConfigurationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductConfigurationController.java) | Local declaration |
| `GET /api/v1/products/{productId}/configurations/by-type/{configType}` | `public Flux<ProductConfigurationDTO> getConfigurationsByType( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Configuration type", required = true) @PathVariable ProductConfigTypeEnum configType)` | [ProductConfigurationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductConfigurationController.java) | Local declaration |
| `POST /api/v1/products/{productId}/configurations/filter` | `public Mono<ResponseEntity<PaginationResponse<ProductConfigurationDTO>>> filterConfigurations( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Filter criteria for configurations", required = true) @Valid @RequestBody FilterRequest<ProductConfigurationDTO> filterRequest)` | [ProductConfigurationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductConfigurationController.java) | Local declaration |
| `POST /api/v1/products/{productId}/configurations` | `public Mono<ResponseEntity<ProductConfigurationDTO>> createConfiguration( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Configuration data to create", required = true) @Valid @RequestBody ProductConfigurationDTO configDTO)` | [ProductConfigurationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductConfigurationController.java) | Local declaration |
| `PUT /api/v1/products/{productId}/configurations/{configId}` | `public Mono<ResponseEntity<ProductConfigurationDTO>> updateConfiguration( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Unique identifier of the product configuration", required = true) @PathVariable UUID configId, @Parameter(description = "Updated configuration data", required = true) @Valid @RequestBody ProductConfigurationDTO configDTO)` | [ProductConfigurationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductConfigurationController.java) | Local declaration |
| `DELETE /api/v1/products/{productId}/configurations/{configId}` | `public Mono<ResponseEntity<Void>> deleteConfiguration( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Unique identifier of the product configuration", required = true) @PathVariable UUID configId)` | [ProductConfigurationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductConfigurationController.java) | Local declaration |
| `GET /api/v1/products/{productId}` | `public Mono<ResponseEntity<ProductDTO>> getProductById( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId)` | [ProductController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductController.java) | Local declaration |
| `POST /api/v1/products/filter` | `public Mono<ResponseEntity<PaginationResponse<ProductDTO>>> filterProducts( @Parameter(description = "Filter criteria for products", required = true) @Valid @RequestBody FilterRequest<ProductDTO> filterRequest)` | [ProductController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductController.java) | Local declaration |
| `POST /api/v1/products` | `public Mono<ResponseEntity<ProductDTO>> createProduct( @Parameter(description = "Product data to create", required = true) @Valid @RequestBody ProductDTO productDTO)` | [ProductController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductController.java) | Local declaration |
| `PUT /api/v1/products/{productId}` | `public Mono<ResponseEntity<ProductDTO>> updateProduct( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Updated product data", required = true) @Valid @RequestBody ProductDTO productDTO)` | [ProductController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductController.java) | Local declaration |
| `DELETE /api/v1/products/{productId}` | `public Mono<ResponseEntity<Void>> deleteProduct( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId)` | [ProductController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductController.java) | Local declaration |
| `GET /api/v1/products/{productId}/documentation/{docId}` | `public Mono<ResponseEntity<ProductDocumentationDTO>> getDocumentationById( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Unique identifier of the documentation item", required = true) @PathVariable UUID docId)` | [ProductDocumentationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDocumentationController.java) | Local declaration |
| `POST /api/v1/products/{productId}/documentation/filter` | `public Mono<ResponseEntity<PaginationResponse<ProductDocumentationDTO>>> filterDocumentation( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Filter criteria for documentation", required = true) @Valid @RequestBody FilterRequest<ProductDocumentationDTO> filterRequest)` | [ProductDocumentationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDocumentationController.java) | Local declaration |
| `POST /api/v1/products/{productId}/documentation` | `public Mono<ResponseEntity<ProductDocumentationDTO>> createDocumentation( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Documentation data to create", required = true) @Valid @RequestBody ProductDocumentationDTO documentationDTO)` | [ProductDocumentationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDocumentationController.java) | Local declaration |
| `PUT /api/v1/products/{productId}/documentation/{docId}` | `public Mono<ResponseEntity<ProductDocumentationDTO>> updateDocumentation( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Unique identifier of the documentation item", required = true) @PathVariable UUID docId, @Parameter(description = "Updated documentation data", required = true) @Valid @RequestBody ProductDocumentationDTO documentationDTO)` | [ProductDocumentationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDocumentationController.java) | Local declaration |
| `DELETE /api/v1/products/{productId}/documentation/{docId}` | `public Mono<ResponseEntity<Void>> deleteDocumentation( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Unique identifier of the documentation item", required = true) @PathVariable UUID docId)` | [ProductDocumentationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDocumentationController.java) | Local declaration |
| `GET /api/v1/products/{productId}/documentation-requirements/{requirementId}` | `public Mono<ResponseEntity<ProductDocumentationRequirementDTO>> getDocumentationRequirementById( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Unique identifier of the documentation requirement", required = true) @PathVariable UUID requirementId)` | [ProductDocumentationRequirementController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDocumentationRequirementController.java) | Local declaration |
| `GET /api/v1/products/{productId}/documentation-requirements/by-type/{docType}` | `public Mono<ResponseEntity<ProductDocumentationRequirementDTO>> getDocumentationRequirementByType( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Type of document", required = true) @PathVariable ContractingDocTypeEnum docType)` | [ProductDocumentationRequirementController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDocumentationRequirementController.java) | Local declaration |
| `GET /api/v1/products/{productId}/documentation-requirements/mandatory` | `public Mono<ResponseEntity<Flux<ProductDocumentationRequirementDTO>>> getMandatoryDocumentationRequirements( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId)` | [ProductDocumentationRequirementController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDocumentationRequirementController.java) | Local declaration |
| `POST /api/v1/products/{productId}/documentation-requirements/filter` | `public Mono<ResponseEntity<PaginationResponse<ProductDocumentationRequirementDTO>>> filterDocumentationRequirements( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Filter criteria for documentation requirements", required = true) @Valid @RequestBody FilterRequest<ProductDocumentationRequirementDTO> filterRequest)` | [ProductDocumentationRequirementController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDocumentationRequirementController.java) | Local declaration |
| `POST /api/v1/products/{productId}/documentation-requirements` | `public Mono<ResponseEntity<ProductDocumentationRequirementDTO>> createDocumentationRequirement( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Documentation requirement data to create", required = true) @Valid @RequestBody ProductDocumentationRequirementDTO requirementDTO)` | [ProductDocumentationRequirementController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDocumentationRequirementController.java) | Local declaration |
| `PUT /api/v1/products/{productId}/documentation-requirements/{requirementId}` | `public Mono<ResponseEntity<ProductDocumentationRequirementDTO>> updateDocumentationRequirement( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Unique identifier of the documentation requirement", required = true) @PathVariable UUID requirementId, @Parameter(description = "Updated documentation requirement data", required = true) @Valid @RequestBody ProductDocumentationRequirementDTO requirementDTO)` | [ProductDocumentationRequirementController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDocumentationRequirementController.java) | Local declaration |
| `DELETE /api/v1/products/{productId}/documentation-requirements/{requirementId}` | `public Mono<ResponseEntity<Void>> deleteDocumentationRequirement( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Unique identifier of the documentation requirement", required = true) @PathVariable UUID requirementId)` | [ProductDocumentationRequirementController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDocumentationRequirementController.java) | Local declaration |
| `GET /api/v1/products/{productId}/localizations/{localizationId}` | `public Mono<ResponseEntity<ProductLocalizationDTO>> getLocalizationById( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Unique identifier of the localization record", required = true) @PathVariable UUID localizationId)` | [ProductLocalizationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductLocalizationController.java) | Local declaration |
| `POST /api/v1/products/{productId}/localizations/filter` | `public Mono<ResponseEntity<PaginationResponse<ProductLocalizationDTO>>> filterLocalizations( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Filter criteria for localizations", required = true) @Valid @RequestBody FilterRequest<ProductLocalizationDTO> filterRequest)` | [ProductLocalizationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductLocalizationController.java) | Local declaration |
| `POST /api/v1/products/{productId}/localizations` | `public Mono<ResponseEntity<ProductLocalizationDTO>> createLocalization( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Localization data to create", required = true) @Valid @RequestBody ProductLocalizationDTO localizationDTO)` | [ProductLocalizationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductLocalizationController.java) | Local declaration |
| `PUT /api/v1/products/{productId}/localizations/{localizationId}` | `public Mono<ResponseEntity<ProductLocalizationDTO>> updateLocalization( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Unique identifier of the localization record", required = true) @PathVariable UUID localizationId, @Parameter(description = "Updated localization data", required = true) @Valid @RequestBody ProductLocalizationDTO localizationDTO)` | [ProductLocalizationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductLocalizationController.java) | Local declaration |
| `DELETE /api/v1/products/{productId}/localizations/{localizationId}` | `public Mono<ResponseEntity<Void>> deleteLocalization( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Unique identifier of the localization record", required = true) @PathVariable UUID localizationId)` | [ProductLocalizationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductLocalizationController.java) | Local declaration |
| `GET /api/v1/products/{productId}/pricing` | `public Mono<ResponseEntity<ProductPricingDTO>> getProductPricing( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId)` | [ProductPricingAggregatorController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductPricingAggregatorController.java) | Local declaration |
| `GET /api/v1/products/with-pricing` | `public Flux<ProductPricingDTO> listProductsWithPricing( @Parameter(description = "Optional derived product-type label (e.g. PERSONAL_LOAN, LEASING)", required = false) @RequestParam(value = "productType", required = false) String productType)` | [ProductPricingAggregatorController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductPricingAggregatorController.java) | Local declaration |
| `GET /api/v1/products/{productId}/relationships/{relationshipId}` | `public Mono<ResponseEntity<ProductRelationshipDTO>> getRelationshipById( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Unique identifier of the relationship record", required = true) @PathVariable UUID relationshipId)` | [ProductRelationshipController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductRelationshipController.java) | Local declaration |
| `POST /api/v1/products/{productId}/relationships/filter` | `public Mono<ResponseEntity<PaginationResponse<ProductRelationshipDTO>>> filterRelationships( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Filter criteria for relationships", required = true) @Valid @RequestBody FilterRequest<ProductRelationshipDTO> filterRequest)` | [ProductRelationshipController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductRelationshipController.java) | Local declaration |
| `POST /api/v1/products/{productId}/relationships` | `public Mono<ResponseEntity<ProductRelationshipDTO>> createRelationship( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Relationship data to create", required = true) @Valid @RequestBody ProductRelationshipDTO productRelationshipDTO)` | [ProductRelationshipController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductRelationshipController.java) | Local declaration |
| `PUT /api/v1/products/{productId}/relationships/{relationshipId}` | `public Mono<ResponseEntity<ProductRelationshipDTO>> updateRelationship( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Unique identifier of the relationship record", required = true) @PathVariable UUID relationshipId, @Parameter(description = "Updated relationship data", required = true) @Valid @RequestBody ProductRelationshipDTO productRelationshipDTO)` | [ProductRelationshipController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductRelationshipController.java) | Local declaration |
| `DELETE /api/v1/products/{productId}/relationships/{relationshipId}` | `public Mono<ResponseEntity<Void>> deleteRelationship( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Unique identifier of the relationship record", required = true) @PathVariable UUID relationshipId)` | [ProductRelationshipController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductRelationshipController.java) | Local declaration |
| `GET /api/v1/products/{productId}/versions/{versionId}` | `public Mono<ResponseEntity<ProductVersionDTO>> getProductVersionById( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Unique identifier of the product version", required = true) @PathVariable UUID versionId)` | [ProductVersionController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductVersionController.java) | Local declaration |
| `POST /api/v1/products/{productId}/versions/filter` | `public Mono<ResponseEntity<PaginationResponse<ProductVersionDTO>>> filterProductVersions( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Filter criteria for versions", required = true) @Valid @RequestBody FilterRequest<ProductVersionDTO> filterRequest)` | [ProductVersionController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductVersionController.java) | Local declaration |
| `POST /api/v1/products/{productId}/versions` | `public Mono<ResponseEntity<ProductVersionDTO>> createProductVersion( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Version data to create", required = true) @Valid @RequestBody ProductVersionDTO productVersionDTO)` | [ProductVersionController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductVersionController.java) | Local declaration |
| `PUT /api/v1/products/{productId}/versions/{versionId}` | `public Mono<ResponseEntity<ProductVersionDTO>> updateProductVersion( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Unique identifier of the product version", required = true) @PathVariable UUID versionId, @Parameter(description = "Updated version data", required = true) @Valid @RequestBody ProductVersionDTO productVersionDTO)` | [ProductVersionController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductVersionController.java) | Local declaration |
| `DELETE /api/v1/products/{productId}/versions/{versionId}` | `public Mono<ResponseEntity<Void>> deleteProductVersion( @Parameter(description = "Unique identifier of the product", required = true) @PathVariable UUID productId, @Parameter(description = "Unique identifier of the product version", required = true) @PathVariable UUID versionId)` | [ProductVersionController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductVersionController.java) | Local declaration |

## Data contracts

### BaseDTO

[BaseDTO](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/BaseDTO.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `dateCreated` | `LocalDateTime` | See declaration / service rules |
| `dateUpdated` | `LocalDateTime` | See declaration / service rules |

### FeeDefinitionDTO

[FeeDefinitionDTO](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/FeeDefinitionDTO.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `type` | `String` | See declaration / service rules |
| `percentage` | `BigDecimal` | See declaration / service rules |
| `fixed` | `BigDecimal` | See declaration / service rules |

### InterestRateBracketDTO

[InterestRateBracketDTO](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/InterestRateBracketDTO.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `minAmount` | `BigDecimal` | See declaration / service rules |
| `maxAmount` | `BigDecimal` | See declaration / service rules |
| `tin` | `BigDecimal` | See declaration / service rules |

### ProductCategoryDTO

[ProductCategoryDTO](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/ProductCategoryDTO.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `productCategoryId` | `UUID` | See declaration / service rules |
| `categoryName` | `String` | See declaration / service rules |
| `categoryDescription` | `String` | See declaration / service rules |
| `parentCategoryId` | `UUID` | See declaration / service rules |
| `level` | `Integer` | See declaration / service rules |

### ProductConfigurationDTO

[ProductConfigurationDTO](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/ProductConfigurationDTO.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `productConfigurationId` | `UUID` | See declaration / service rules |
| `productId` | `UUID` | See declaration / service rules |
| `configType` | `ProductConfigTypeEnum` | See declaration / service rules |
| `configKey` | `String` | See declaration / service rules |
| `configValue` | `String` | See declaration / service rules |

### ProductDTO

[ProductDTO](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/ProductDTO.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `productId` | `UUID` | See declaration / service rules |
| `tenantId` | `UUID` | See declaration / service rules |
| `productCategoryId` | `UUID` | See declaration / service rules |
| `productType` | `ProductTypeEnum` | See declaration / service rules |
| `productName` | `String` | See declaration / service rules |
| `productCode` | `String` | See declaration / service rules |
| `productDescription` | `String` | See declaration / service rules |
| `productStatus` | `ProductStatusEnum` | See declaration / service rules |
| `launchDate` | `LocalDate` | See declaration / service rules |
| `endDate` | `LocalDate` | See declaration / service rules |
| `marketingFeatures` | `List<String>` | See declaration / service rules |

### ProductDocumentationDTO

[ProductDocumentationDTO](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/ProductDocumentationDTO.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `productDocumentationId` | `UUID` | See declaration / service rules |
| `productId` | `UUID` | See declaration / service rules |
| `docType` | `DocTypeEnum` | See declaration / service rules |
| `documentManagerRef` | `Long` | See declaration / service rules |
| `dateAdded` | `LocalDateTime` | See declaration / service rules |

### ProductDocumentationRequirementDTO

[ProductDocumentationRequirementDTO](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/ProductDocumentationRequirementDTO.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `productDocRequirementId` | `UUID` | See declaration / service rules |
| `productId` | `UUID` | See declaration / service rules |
| `docType` | `ContractingDocTypeEnum` | See declaration / service rules |
| `isMandatory` | `Boolean` | See declaration / service rules |
| `description` | `String` | See declaration / service rules |

### ProductLocalizationDTO

[ProductLocalizationDTO](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/ProductLocalizationDTO.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `productLocalizationId` | `UUID` | See declaration / service rules |
| `productId` | `UUID` | See declaration / service rules |
| `languageCode` | `String` | See declaration / service rules |
| `localizedName` | `String` | See declaration / service rules |
| `localizedDescription` | `String` | See declaration / service rules |

### ProductPricingDTO

[ProductPricingDTO](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/ProductPricingDTO.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `productId` | `UUID` | See declaration / service rules |
| `productCode` | `String` | See declaration / service rules |
| `productType` | `String` | See declaration / service rules |
| `name` | `String` | See declaration / service rules |
| `description` | `String` | See declaration / service rules |
| `available` | `Boolean` | See declaration / service rules |
| `features` | `List<String>` | See declaration / service rules |
| `currency` | `String` | See declaration / service rules |
| `minAmount` | `BigDecimal` | See declaration / service rules |
| `maxAmount` | `BigDecimal` | See declaration / service rules |
| `minTerm` | `Integer` | See declaration / service rules |
| `maxTerm` | `Integer` | See declaration / service rules |
| `interestRates` | `List<InterestRateBracketDTO>` | See declaration / service rules |
| `fees` | `List<FeeDefinitionDTO>` | See declaration / service rules |

### ProductRelationshipDTO

[ProductRelationshipDTO](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/ProductRelationshipDTO.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `productRelationshipId` | `UUID` | See declaration / service rules |
| `productId` | `UUID` | See declaration / service rules |
| `relatedProductId` | `UUID` | See declaration / service rules |
| `relationshipType` | `RelationshipTypeEnum` | See declaration / service rules |
| `description` | `String` | See declaration / service rules |

### ProductVersionDTO

[ProductVersionDTO](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/ProductVersionDTO.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `productVersionId` | `UUID` | See declaration / service rules |
| `productId` | `UUID` | See declaration / service rules |
| `versionNumber` | `Long` | See declaration / service rules |
| `versionDescription` | `String` | See declaration / service rules |
| `effectiveDate` | `LocalDateTime` | See declaration / service rules |

### AccountTariffTerms

[AccountTariffTerms](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/v1/AccountTariffTerms.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `accountKind` | `String` | See declaration / service rules |
| `ownershipKinds` | `List<String>` | See declaration / service rules |
| `openingMinimum` | `Money` | See declaration / service rules |
| `retainedMinimum` | `Money` | See declaration / service rules |
| `perTransferLimit` | `Money` | See declaration / service rules |
| `dailyTransferLimit` | `Money` | See declaration / service rules |
| `interest` | `String` | See declaration / service rules |
| `overdraft` | `String` | See declaration / service rules |
| `tariffs` | `List<FeeRule>` | See declaration / service rules |

### CostTerms

[CostTerms](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/v1/CostTerms.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `schemaVersion` | `Integer` | See declaration / service rules |
| `businessArea` | `String` | See declaration / service rules |
| `profileCode` | `String` | See declaration / service rules |
| `mode` | `String` | See declaration / service rules |
| `lines` | `List<CostLine>` | See declaration / service rules |

### FixedRateInstallmentLoanTerms

[FixedRateInstallmentLoanTerms](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/v1/FixedRateInstallmentLoanTerms.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `principalMinimum` | `Money` | See declaration / service rules |
| `principalMaximum` | `Money` | See declaration / service rules |
| `minimumMonths` | `Integer` | See declaration / service rules |
| `maximumMonths` | `Integer` | See declaration / service rules |
| `annualNominalRate` | `String` | See declaration / service rules |
| `interestConvention` | `String` | See declaration / service rules |
| `repaymentConvention` | `String` | See declaration / service rules |
| `installmentRounding` | `String` | See declaration / service rules |
| `finalInstallmentAdjustment` | `String` | See declaration / service rules |
| `originationFee` | `FeeRule` | See declaration / service rules |
| `earlyRepaymentPolicy` | `String` | See declaration / service rules |
| `repaymentAllocation` | `ExactReference` | See declaration / service rules |
| `calendar` | `ExactReference` | See declaration / service rules |

### TermPcInsuranceTerms

[TermPcInsuranceTerms](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/v1/TermPcInsuranceTerms.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `riskKind` | `String` | See declaration / service rules |
| `minimumCoverageDays` | `Integer` | See declaration / service rules |
| `maximumCoverageDays` | `Integer` | See declaration / service rules |
| `premiumBasis` | `String` | See declaration / service rules |
| `paymentSchedule` | `String` | See declaration / service rules |
| `basePremium` | `Money` | See declaration / service rules |
| `taxComponents` | `List<TaxComponent>` | See declaration / service rules |
| `totalPremium` | `Money` | See declaration / service rules |
| `coverages` | `List<Coverage>` | See declaration / service rules |
| `deductibleGroups` | `List<DeductibleGroup>` | See declaration / service rules |
| `exclusions` | `List<ExactReference>` | See declaration / service rules |
| `territory` | `String` | See declaration / service rules |
| `cancellationRefundPolicy` | `String` | See declaration / service rules |

### BaseEntity

[BaseEntity](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/entities/BaseEntity.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `dateCreated` | `LocalDateTime` | See declaration / service rules |
| `dateUpdated` | `LocalDateTime` | See declaration / service rules |

### Product

[Product](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/entities/Product.java) · Persisted entity

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `productId` | `UUID` | See declaration / service rules |
| `tenantId` | `UUID` | See declaration / service rules |
| `productCategoryId` | `UUID` | See declaration / service rules |
| `productType` | `ProductTypeEnum` | See declaration / service rules |
| `productName` | `String` | See declaration / service rules |
| `productCode` | `String` | See declaration / service rules |
| `productDescription` | `String` | See declaration / service rules |
| `productStatus` | `ProductStatusEnum` | See declaration / service rules |
| `launchDate` | `LocalDate` | See declaration / service rules |
| `endDate` | `LocalDate` | See declaration / service rules |
| `marketingFeatures` | `Json` | See declaration / service rules |

### ProductCategory

[ProductCategory](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/entities/ProductCategory.java) · Persisted entity

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `productCategoryId` | `UUID` | See declaration / service rules |
| `categoryName` | `String` | See declaration / service rules |
| `categoryDescription` | `String` | See declaration / service rules |
| `parentCategoryId` | `UUID` | See declaration / service rules |
| `level` | `Integer` | See declaration / service rules |

### ProductConfiguration

[ProductConfiguration](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/entities/ProductConfiguration.java) · Persisted entity

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `productConfigurationId` | `UUID` | See declaration / service rules |
| `productId` | `UUID` | See declaration / service rules |
| `configType` | `ProductConfigTypeEnum` | See declaration / service rules |
| `configKey` | `String` | See declaration / service rules |
| `configValue` | `String` | See declaration / service rules |

### ProductDocumentation

[ProductDocumentation](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/entities/ProductDocumentation.java) · Persisted entity

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `productDocumentationId` | `UUID` | See declaration / service rules |
| `productId` | `UUID` | See declaration / service rules |
| `docType` | `DocTypeEnum` | See declaration / service rules |
| `documentManagerRef` | `Long` | See declaration / service rules |
| `dateAdded` | `LocalDateTime` | See declaration / service rules |

### ProductDocumentationRequirement

[ProductDocumentationRequirement](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/entities/ProductDocumentationRequirement.java) · Persisted entity

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `productDocRequirementId` | `UUID` | See declaration / service rules |
| `productId` | `UUID` | See declaration / service rules |
| `docType` | `ContractingDocTypeEnum` | See declaration / service rules |
| `isMandatory` | `Boolean` | See declaration / service rules |
| `description` | `String` | See declaration / service rules |

### ProductLocalization

[ProductLocalization](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/entities/ProductLocalization.java) · Persisted entity

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `productLocalizationId` | `UUID` | See declaration / service rules |
| `productId` | `UUID` | See declaration / service rules |
| `languageCode` | `String` | See declaration / service rules |
| `localizedName` | `String` | See declaration / service rules |
| `localizedDescription` | `String` | See declaration / service rules |

### ProductRelationship

[ProductRelationship](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/entities/ProductRelationship.java) · Persisted entity

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `productRelationshipId` | `UUID` | See declaration / service rules |
| `productId` | `UUID` | See declaration / service rules |
| `relatedProductId` | `UUID` | See declaration / service rules |
| `relationshipType` | `RelationshipTypeEnum` | See declaration / service rules |
| `description` | `String` | See declaration / service rules |

### ProductVersion

[ProductVersion](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/entities/ProductVersion.java) · Persisted entity

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `productVersionId` | `UUID` | See declaration / service rules |
| `productId` | `UUID` | See declaration / service rules |
| `versionNumber` | `Long` | See declaration / service rules |
| `versionDescription` | `String` | See declaration / service rules |
| `effectiveDate` | `LocalDateTime` | See declaration / service rules |

## Ports and service contracts

### ProductCategoryService

[ProductCategoryService](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/ProductCategoryService.java)

```java
Mono<PaginationResponse<ProductCategoryDTO>> filterRootCategories(FilterRequest<ProductCategoryDTO> filterRequest);
Mono<PaginationResponse<ProductCategoryDTO>> filterChildCategories(UUID parentCategoryId, FilterRequest<ProductCategoryDTO> filterRequest);
Mono<PaginationResponse<ProductCategoryDTO>> filterCategoriesByName(String namePattern, FilterRequest<ProductCategoryDTO> filterRequest);
Mono<ProductCategoryDTO> createCategory(ProductCategoryDTO categoryDTO);
Mono<ProductCategoryDTO> getCategoryById(UUID categoryId);
Mono<ProductCategoryDTO> updateCategory(UUID categoryId, ProductCategoryDTO categoryDTO);
Mono<Void> deleteCategory(UUID categoryId);
```

### ProductConfigurationService

[ProductConfigurationService](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/ProductConfigurationService.java)

```java
Mono<PaginationResponse<ProductConfigurationDTO>> filterConfigurations( UUID productId, FilterRequest<ProductConfigurationDTO> filterRequest);
Mono<ProductConfigurationDTO> createConfiguration(UUID productId, ProductConfigurationDTO configDTO);
Mono<ProductConfigurationDTO> getConfigurationById(UUID productId, UUID configId);
Mono<ProductConfigurationDTO> getConfigurationByKey(UUID productId, String configKey);
Flux<ProductConfigurationDTO> getConfigurationsByType(UUID productId, ProductConfigTypeEnum configType);
Mono<ProductConfigurationDTO> updateConfiguration( UUID productId, UUID configId, ProductConfigurationDTO configDTO);
Mono<Void> deleteConfiguration(UUID productId, UUID configId);
```

### ProductDocumentationRequirementService

[ProductDocumentationRequirementService](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/ProductDocumentationRequirementService.java)

```java
Mono<PaginationResponse<ProductDocumentationRequirementDTO>> filterDocumentationRequirements( UUID productId, FilterRequest<ProductDocumentationRequirementDTO> filterRequest);
Mono<ProductDocumentationRequirementDTO> createDocumentationRequirement( UUID productId, ProductDocumentationRequirementDTO requirementDTO);
Mono<ProductDocumentationRequirementDTO> getDocumentationRequirementById( UUID productId, UUID requirementId);
Mono<ProductDocumentationRequirementDTO> getDocumentationRequirementByType( UUID productId, ContractingDocTypeEnum docType);
Mono<ProductDocumentationRequirementDTO> updateDocumentationRequirement( UUID productId, UUID requirementId, ProductDocumentationRequirementDTO requirementDTO);
Mono<Void> deleteDocumentationRequirement(UUID productId, UUID requirementId);
Flux<ProductDocumentationRequirementDTO> filterMandatoryDocumentationRequirements(UUID productId);
```

### ProductDocumentationService

[ProductDocumentationService](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/ProductDocumentationService.java)

```java
Mono<PaginationResponse<ProductDocumentationDTO>> filterDocumentations(UUID productId, FilterRequest<ProductDocumentationDTO> filterRequest);
Mono<ProductDocumentationDTO> createDocumentation(UUID productId, ProductDocumentationDTO documentationDTO);
Mono<ProductDocumentationDTO> getDocumentationById(UUID productId, UUID documentationId);
Mono<ProductDocumentationDTO> updateDocumentation(UUID productId, UUID documentationId, ProductDocumentationDTO documentationDTO);
Mono<Void> deleteDocumentation(UUID productId, UUID documentationId);
```

### ProductLocalizationService

[ProductLocalizationService](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/ProductLocalizationService.java)

```java
Mono<PaginationResponse<ProductLocalizationDTO>> filterLocalizations(UUID productId, FilterRequest<ProductLocalizationDTO> filterRequest);
Mono<ProductLocalizationDTO> createLocalization(UUID productId, ProductLocalizationDTO localizationDTO);
Mono<ProductLocalizationDTO> getLocalizationById(UUID productId, UUID localizationId);
Mono<ProductLocalizationDTO> updateLocalization(UUID productId, UUID localizationId, ProductLocalizationDTO localizationDTO);
Mono<Void> deleteLocalization(UUID productId, UUID localizationId);
```

### ProductPricingAggregatorService

[ProductPricingAggregatorService](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/ProductPricingAggregatorService.java)

```java
Mono<ProductPricingDTO> getProductPricing(UUID productId);
Flux<ProductPricingDTO> listProductsWithPricing(String productType);
```

### ProductRelationshipService

[ProductRelationshipService](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/ProductRelationshipService.java)

```java
Mono<PaginationResponse<ProductRelationshipDTO>> filterRelationships(UUID productId, FilterRequest<ProductRelationshipDTO> filterRequest);
Mono<ProductRelationshipDTO> createRelationship(UUID productId, ProductRelationshipDTO relationshipDTO);
Mono<ProductRelationshipDTO> getRelationshipById(UUID productId, UUID relationshipId);
Mono<ProductRelationshipDTO> updateRelationship(UUID productId, UUID relationshipId, ProductRelationshipDTO relationshipDTO);
Mono<Void> deleteRelationship(UUID productId, UUID relationshipId);
```

### ProductService

[ProductService](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/ProductService.java)

```java
Mono<PaginationResponse<ProductDTO>> filterProducts(FilterRequest<ProductDTO> filterRequest);
Mono<ProductDTO> createProduct(ProductDTO productDTO);
Mono<ProductDTO> getProductById(UUID productId);
Flux<ProductDTO> getProductsByTenantId(UUID tenantId);
Mono<ProductDTO> updateProduct(UUID productId, ProductDTO productDTO);
Mono<Void> deleteProduct(UUID productId);
```

### ProductVersionService

[ProductVersionService](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/ProductVersionService.java)

```java
Mono<PaginationResponse<ProductVersionDTO>> filterProductVersions(UUID productId, FilterRequest<ProductVersionDTO> filterRequest);
Mono<ProductVersionDTO> createProductVersion(UUID productId, ProductVersionDTO productVersionDTO);
Mono<ProductVersionDTO> getProductVersionById(UUID productId, UUID versionId);
Mono<ProductVersionDTO> updateProductVersion(UUID productId, UUID versionId, ProductVersionDTO productVersionDTO);
Mono<Void> deleteProductVersion(UUID productId, UUID versionId);
```

## Configuration

Values below come from source placeholders. Plain YAML values and configuration-property classes must also be reviewed at the linked configuration sources. Secret fallback values are deliberately redacted.

| Key | Fallback | Source |
| --- | --- | --- |
| `DB_HOST` | `localhost` | [R2dbcConfiguration](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/config/R2dbcConfiguration.java) |
| `DB_PORT` | `5432` | [R2dbcConfiguration](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/config/R2dbcConfiguration.java) |
| `DB_NAME` | `postgres` | [R2dbcConfiguration](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/config/R2dbcConfiguration.java) |
| `DB_USERNAME` | `postgres` | [R2dbcConfiguration](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/config/R2dbcConfiguration.java) |
| `DB_PASSWORD` | `[redacted]` | [R2dbcConfiguration](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/config/R2dbcConfiguration.java) |
| `DB_SSL_MODE` | `disable` | [R2dbcConfiguration](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/config/R2dbcConfiguration.java) |
| `spring.application.name` | No fallback | [ProductManagementApplication](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/ProductManagementApplication.java) |
| `spring.application.version` | No fallback | [ProductManagementApplication](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/ProductManagementApplication.java) |
| `spring.application.description` | No fallback | [ProductManagementApplication](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/ProductManagementApplication.java) |
| `spring.application.team.name` | No fallback | [ProductManagementApplication](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/ProductManagementApplication.java) |
| `spring.application.team.email` | No fallback | [ProductManagementApplication](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/ProductManagementApplication.java) |
| `DB_HOST` | No fallback | [application](../../core-common-product-mgmt-web/src/main/resources/application.yaml) |
| `DB_PORT` | No fallback | [application](../../core-common-product-mgmt-web/src/main/resources/application.yaml) |
| `DB_NAME` | No fallback | [application](../../core-common-product-mgmt-web/src/main/resources/application.yaml) |
| `DB_SSL_MODE` | No fallback | [application](../../core-common-product-mgmt-web/src/main/resources/application.yaml) |
| `DB_USERNAME` | No fallback | [application](../../core-common-product-mgmt-web/src/main/resources/application.yaml) |
| `DB_PASSWORD` | No fallback | [application](../../core-common-product-mgmt-web/src/main/resources/application.yaml) |
| `SERVER_ADDRESS` | `localhost` | [application](../../core-common-product-mgmt-web/src/main/resources/application.yaml) |
| `SERVER_PORT` | `8080` | [application](../../core-common-product-mgmt-web/src/main/resources/application.yaml) |

## Security and failure evidence

| Declaration or failure site | Source |
| --- | --- |
| `new RuntimeException("Error parsing product marketing_features JSON", e);` | [ProductMapper](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/mappers/ProductMapper.java) |
| `new RuntimeException("Error serialising product marketing_features", e);` | [ProductMapper](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/mappers/ProductMapper.java) |
| `new IllegalArgumentException("COST_DEFINITION_INVALID");` | [CostRules](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/v1/CostRules.java) |
| `new IllegalStateException("SHA-256 unavailable", e);` | [DefinitionCanonical](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/v1/DefinitionCanonical.java) |
| `new IllegalArgumentException("DEFINITION_SHAPE");` | [DefinitionCanonical](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/v1/DefinitionCanonical.java) |
| `new IllegalArgumentException("CHECKER_IDENTITY_REQUIRED");` | [DefinitionReviewRules](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/v1/DefinitionReviewRules.java) |
| `new IllegalArgumentException("HUMAN_CHECKER_REQUIRED");` | [DefinitionReviewRules](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/v1/DefinitionReviewRules.java) |
| `new IllegalArgumentException("REVIEW_CONTRIBUTORS_REQUIRED");` | [DefinitionReviewRules](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/v1/DefinitionReviewRules.java) |
| `new IllegalArgumentException("INDEPENDENT_CHECKER_REQUIRED");` | [DefinitionReviewRules](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/v1/DefinitionReviewRules.java) |
| `new IllegalArgumentException("DEFINITION_RULE at " + path);` | [DefinitionRules](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/v1/DefinitionRules.java) |
| `new RuntimeException("Parent category not found with ID: " + parentCategoryId)))` | [ProductCategoryServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductCategoryServiceImpl.java) |
| `new RuntimeException("Category not found with ID: " + categoryId)))` | [ProductCategoryServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductCategoryServiceImpl.java) |
| `new RuntimeException("Category not found with ID: " + categoryId)))` | [ProductCategoryServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductCategoryServiceImpl.java) |
| `new RuntimeException("A category cannot be its own parent"));` | [ProductCategoryServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductCategoryServiceImpl.java) |
| `new RuntimeException("Category not found with ID: " + categoryId)))` | [ProductCategoryServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductCategoryServiceImpl.java) |
| `new RuntimeException("Cannot delete category with ID " + categoryId + " because it has child categories"));` | [ProductCategoryServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductCategoryServiceImpl.java) |
| `new RuntimeException(` | [ProductCategoryServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductCategoryServiceImpl.java) |
| `new RuntimeException("Parent category not found with ID: " + parentCategoryId)))` | [ProductCategoryServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductCategoryServiceImpl.java) |
| `new RuntimeException("Circular reference detected: category cannot be an ancestor of itself"));` | [ProductCategoryServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductCategoryServiceImpl.java) |
| `new RuntimeException("Configuration not found with ID: " + configId)))` | [ProductConfigurationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductConfigurationServiceImpl.java) |
| `new RuntimeException("Configuration with ID " + configId + " does not belong to product " + productId));` | [ProductConfigurationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductConfigurationServiceImpl.java) |
| `new RuntimeException("Configuration not found for product " + productId + " with key " + configKey)))` | [ProductConfigurationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductConfigurationServiceImpl.java) |
| `new RuntimeException("Configuration not found with ID: " + configId)))` | [ProductConfigurationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductConfigurationServiceImpl.java) |
| `new RuntimeException("Configuration with ID " + configId + " does not belong to product " + productId));` | [ProductConfigurationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductConfigurationServiceImpl.java) |
| `new RuntimeException("Configuration not found with ID: " + configId)))` | [ProductConfigurationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductConfigurationServiceImpl.java) |
| `new RuntimeException("Configuration with ID " + configId + " does not belong to product " + productId));` | [ProductConfigurationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductConfigurationServiceImpl.java) |
| `new RuntimeException("Documentation requirement not found with ID: " + requirementId)))` | [ProductDocumentationRequirementServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductDocumentationRequirementServiceImpl.java) |
| `new RuntimeException("Documentation requirement with ID " + requirementId + " does not belong to product " + productId));` | [ProductDocumentationRequirementServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductDocumentationRequirementServiceImpl.java) |
| `new RuntimeException("Documentation requirement not found for product " + productId + " with type " + docType)))` | [ProductDocumentationRequirementServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductDocumentationRequirementServiceImpl.java) |
| `new RuntimeException("Documentation requirement not found with ID: " + requirementId)))` | [ProductDocumentationRequirementServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductDocumentationRequirementServiceImpl.java) |
| `new RuntimeException("Documentation requirement with ID " + requirementId + " does not belong to product " + productId));` | [ProductDocumentationRequirementServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductDocumentationRequirementServiceImpl.java) |
| `new RuntimeException("Documentation requirement not found with ID: " + requirementId)))` | [ProductDocumentationRequirementServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductDocumentationRequirementServiceImpl.java) |
| `new RuntimeException("Documentation requirement with ID " + requirementId + " does not belong to product " + productId));` | [ProductDocumentationRequirementServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductDocumentationRequirementServiceImpl.java) |
| `new RuntimeException("Documentation not found with ID: " + documentationId)))` | [ProductDocumentationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductDocumentationServiceImpl.java) |
| `new RuntimeException("Documentation with ID " + documentationId + " does not belong to product " + productId));` | [ProductDocumentationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductDocumentationServiceImpl.java) |
| `new RuntimeException("Documentation not found with ID: " + documentationId)))` | [ProductDocumentationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductDocumentationServiceImpl.java) |
| `new RuntimeException("Documentation with ID " + documentationId + " does not belong to product " + productId));` | [ProductDocumentationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductDocumentationServiceImpl.java) |
| `new RuntimeException("Documentation not found with ID: " + documentationId)))` | [ProductDocumentationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductDocumentationServiceImpl.java) |
| `new RuntimeException("Documentation with ID " + documentationId + " does not belong to product " + productId));` | [ProductDocumentationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductDocumentationServiceImpl.java) |
| `new RuntimeException("Localization not found with ID: " + localizationId)))` | [ProductLocalizationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductLocalizationServiceImpl.java) |
| `new RuntimeException("Localization with ID " + localizationId + " does not belong to product " + productId));` | [ProductLocalizationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductLocalizationServiceImpl.java) |
| `new RuntimeException("Localization not found with ID: " + localizationId)))` | [ProductLocalizationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductLocalizationServiceImpl.java) |
| `new RuntimeException("Localization with ID " + localizationId + " does not belong to product " + productId));` | [ProductLocalizationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductLocalizationServiceImpl.java) |
| `new RuntimeException("Localization not found with ID: " + localizationId)))` | [ProductLocalizationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductLocalizationServiceImpl.java) |
| `new RuntimeException("Localization with ID " + localizationId + " does not belong to product " + productId));` | [ProductLocalizationServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductLocalizationServiceImpl.java) |
| `new BusinessException(` | [ProductPricingAggregatorServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductPricingAggregatorServiceImpl.java) |
| `HttpStatus.NOT_FOUND` | [ProductPricingAggregatorServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductPricingAggregatorServiceImpl.java) |
| `new BusinessException(` | [ProductPricingAggregatorServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductPricingAggregatorServiceImpl.java) |
| `HttpStatus.BAD_GATEWAY` | [ProductPricingAggregatorServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductPricingAggregatorServiceImpl.java) |
| `new RuntimeException("A product cannot be related to itself"));` | [ProductRelationshipServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductRelationshipServiceImpl.java) |
| `new RuntimeException("Relationship not found with ID: " + relationshipId)))` | [ProductRelationshipServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductRelationshipServiceImpl.java) |
| `new RuntimeException("Relationship with ID " + relationshipId + " does not belong to product " + productId));` | [ProductRelationshipServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductRelationshipServiceImpl.java) |
| `new RuntimeException("Relationship not found with ID: " + relationshipId)))` | [ProductRelationshipServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductRelationshipServiceImpl.java) |
| `new RuntimeException("Relationship with ID " + relationshipId + " does not belong to product " + productId));` | [ProductRelationshipServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductRelationshipServiceImpl.java) |
| `new RuntimeException("A product cannot be related to itself"));` | [ProductRelationshipServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductRelationshipServiceImpl.java) |
| `new RuntimeException("Relationship not found with ID: " + relationshipId)))` | [ProductRelationshipServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductRelationshipServiceImpl.java) |
| `new RuntimeException("Relationship with ID " + relationshipId + " does not belong to product " + productId));` | [ProductRelationshipServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductRelationshipServiceImpl.java) |
| `new RuntimeException("Product not found with ID: " + productId)))` | [ProductServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductServiceImpl.java) |
| `new RuntimeException("Product not found with ID: " + productId)))` | [ProductServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductServiceImpl.java) |
| `new RuntimeException("Product not found with ID: " + productId)))` | [ProductServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductServiceImpl.java) |
| `new RuntimeException(` | [ProductServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductServiceImpl.java) |
| `new RuntimeException("Product version not found with ID: " + versionId)))` | [ProductVersionServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductVersionServiceImpl.java) |
| `new RuntimeException("Product version with ID " + versionId + " does not belong to product " + productId));` | [ProductVersionServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductVersionServiceImpl.java) |
| `new RuntimeException("Product version not found with ID: " + versionId)))` | [ProductVersionServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductVersionServiceImpl.java) |
| `new RuntimeException("Product version with ID " + versionId + " does not belong to product " + productId));` | [ProductVersionServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductVersionServiceImpl.java) |
| `new RuntimeException("Product version not found with ID: " + versionId)))` | [ProductVersionServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductVersionServiceImpl.java) |
| `new RuntimeException("Product version with ID " + versionId + " does not belong to product " + productId));` | [ProductVersionServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/impl/ProductVersionServiceImpl.java) |
| `HttpStatus.CREATED` | [ProductCategoryController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductCategoryController.java) |
| `HttpStatus.CREATED` | [ProductConfigurationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductConfigurationController.java) |
| `HttpStatus.CREATED` | [ProductController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductController.java) |
| `HttpStatus.CREATED` | [ProductDocumentationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDocumentationController.java) |
| `HttpStatus.CREATED` | [ProductDocumentationRequirementController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDocumentationRequirementController.java) |
| `HttpStatus.CREATED` | [ProductLocalizationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductLocalizationController.java) |
| `HttpStatus.CREATED` | [ProductRelationshipController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductRelationshipController.java) |
| `HttpStatus.CREATED` | [ProductVersionController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductVersionController.java) |

## Build and dependencies

### core-common-product-mgmt-core

[core-common-product-mgmt-core/pom.xml](../../core-common-product-mgmt-core/pom.xml)

Coordinates: `io.fireflysoftware:core-common-product-mgmt-core:26.10.6`. Parent: `io.fireflysoftware:core-common-product-mgmt:26.10.6`.

- `io.fireflysoftware:core-common-product-mgmt-interfaces:`
- `io.fireflysoftware:core-common-product-mgmt-models:`
- `io.projectreactor:reactor-test:`
- `org.fireflyframework:fireflyframework-starter-core:`
- `org.fireflyframework:fireflyframework-web:`
- `org.flywaydb:flyway-database-postgresql:`
- `org.mapstruct:mapstruct:`
- `org.projectlombok:lombok:`
- `org.springframework.boot:spring-boot-starter-test:`
- `org.springframework.boot:spring-boot-starter-webflux:`
- `org.testcontainers:testcontainers-junit-jupiter:`
- `org.testcontainers:testcontainers-postgresql:`

### core-common-product-mgmt-interfaces

[core-common-product-mgmt-interfaces/pom.xml](../../core-common-product-mgmt-interfaces/pom.xml)

Coordinates: `io.fireflysoftware:core-common-product-mgmt-interfaces:26.10.6`. Parent: `io.fireflysoftware:core-common-product-mgmt:26.10.6`.

- `com.fasterxml.jackson.core:jackson-databind:`
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:`
- `io.swagger.core.v3:swagger-annotations:`
- `org.projectlombok:lombok:`
- `org.springframework.boot:spring-boot-starter-validation:`

### core-common-product-mgmt-models

[core-common-product-mgmt-models/pom.xml](../../core-common-product-mgmt-models/pom.xml)

Coordinates: `io.fireflysoftware:core-common-product-mgmt-models:26.10.6`. Parent: `io.fireflysoftware:core-common-product-mgmt:26.10.6`.

- `io.fireflysoftware:core-common-product-mgmt-interfaces:`
- `org.fireflyframework:fireflyframework-r2dbc:`
- `org.flywaydb:flyway-core:`
- `org.postgresql:postgresql:`
- `org.postgresql:r2dbc-postgresql:`
- `org.projectlombok:lombok:`
- `org.springframework.boot:spring-boot-starter-data-r2dbc:`

### core-common-product-mgmt-sdk

[core-common-product-mgmt-sdk/pom.xml](../../core-common-product-mgmt-sdk/pom.xml)

Coordinates: `io.fireflysoftware:core-common-product-mgmt-sdk:26.10.6`. Parent: `io.fireflysoftware:core-common-product-mgmt:26.10.6`.

- `com.fasterxml.jackson.core:jackson-databind:`
- `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:`
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:`
- `com.squareup.okhttp3:logging-interceptor:`
- `com.squareup.okhttp3:okhttp:`
- `io.fireflysoftware:core-common-product-mgmt-web:`
- `io.swagger.core.v3:swagger-annotations:`
- `jakarta.annotation:jakarta.annotation-api:`
- `javax.annotation:javax.annotation-api:1.3.2`
- `org.openapitools:jackson-databind-nullable:0.2.8`
- `org.projectlombok:lombok:`
- `org.springframework:spring-webflux:`

### core-common-product-mgmt-web

[core-common-product-mgmt-web/pom.xml](../../core-common-product-mgmt-web/pom.xml)

Coordinates: `io.fireflysoftware:core-common-product-mgmt-web:26.10.6`. Parent: `io.fireflysoftware:core-common-product-mgmt:26.10.6`.

- `io.fireflysoftware:core-common-product-mgmt-core:`
- `io.fireflysoftware:core-common-product-mgmt-interfaces:`
- `io.micrometer:micrometer-registry-prometheus:`
- `io.projectreactor:reactor-test:`
- `org.fireflyframework:fireflyframework-web:`
- `org.projectlombok:lombok:`
- `org.springdoc:springdoc-openapi-starter-webflux-ui:`
- `org.springframework.boot:spring-boot-starter-actuator:`
- `org.springframework.boot:spring-boot-starter-test:`
- `org.springframework.boot:spring-boot-starter-validation:`
- `org.springframework.boot:spring-boot-starter-webflux:`

### core-common-product-mgmt

[pom.xml](../../pom.xml)

Coordinates: `io.fireflysoftware:core-common-product-mgmt:26.10.6`. Parent: `io.fireflysoftware:firefly-parent:26.10.6`.

- `io.fireflysoftware:core-common-product-mgmt-core:${project.version}`
- `io.fireflysoftware:core-common-product-mgmt-interfaces:${project.version}`
- `io.fireflysoftware:core-common-product-mgmt-models:${project.version}`
- `io.fireflysoftware:core-common-product-mgmt-web:${project.version}`

## Test sources

- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/README.md](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/README.md)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/category/v1/ProductCategoryServiceImplTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/category/v1/ProductCategoryServiceImplTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/configuration/v1/ProductConfigurationServiceImplTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/configuration/v1/ProductConfigurationServiceImplTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/AccountTariffRulesTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/AccountTariffRulesTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/CostRulesTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/CostRulesTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/DefinitionCanonicalTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/DefinitionCanonicalTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/DefinitionEnvelopeRulesTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/DefinitionEnvelopeRulesTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/DefinitionReviewRulesTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/DefinitionReviewRulesTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/DefinitionRulesTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/DefinitionRulesTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/FixedRateInstallmentLoanRulesTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/FixedRateInstallmentLoanRulesTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/ProductDefinitionDatabaseTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/ProductDefinitionDatabaseTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/ProfileFixtures.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/ProfileFixtures.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/TermPcInsuranceRulesTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/v1/TermPcInsuranceRulesTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/impl/ProductRelationshipServiceImplTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/impl/ProductRelationshipServiceImplTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/impl/ProductServiceImplTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/impl/ProductServiceImplTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/pricing/v1/ProductPricingAggregatorServiceImplTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/pricing/v1/ProductPricingAggregatorServiceImplTest.java)
- [core-common-product-mgmt-core/src/test/resources/definition/v1/account.json](../../core-common-product-mgmt-core/src/test/resources/definition/v1/account.json)
- [core-common-product-mgmt-core/src/test/resources/definition/v1/insurance.json](../../core-common-product-mgmt-core/src/test/resources/definition/v1/insurance.json)
- [core-common-product-mgmt-core/src/test/resources/definition/v1/loan.json](../../core-common-product-mgmt-core/src/test/resources/definition/v1/loan.json)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/controllers/ProductPricingAggregatorControllerTest.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/controllers/ProductPricingAggregatorControllerTest.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/openapi/OpenApiGenApplication.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/openapi/OpenApiGenApplication.java)

## CI definitions

- [.github/workflows/maven-central.yml](../../.github/workflows/maven-central.yml)
- [.github/workflows/release.yml](../../.github/workflows/release.yml)
