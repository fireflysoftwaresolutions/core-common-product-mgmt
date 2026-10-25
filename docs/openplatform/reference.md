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
| `GET /api/v1/tenants/{tenantId}/product-definitions/{rootId}/staff-cost/current` | `public Mono<ResponseEntity<ProductDefinitionCostView>> current( @PathVariable UUID tenantId, @PathVariable UUID rootId)` | [ProductDefinitionCostController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionCostController.java) | Local declaration |
| `POST /api/v1/tenants/{tenantId}/product-definitions/{rootId}/commands/draft` | `public Mono<ResponseEntity<PublicReceipt>> create( @PathVariable UUID tenantId, @PathVariable UUID rootId, @io.swagger.v3.oas.annotations.parameters.RequestBody( required = true, content = @Content( schema = @Schema( implementation = CreateDraftBody.class))) @RequestBody String body)` | [ProductDefinitionLifecycleController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionLifecycleController.java) | Local declaration |
| `POST /api/v1/tenants/{tenantId}/product-definitions/{rootId}/commands/public-revision` | `public Mono<ResponseEntity<PublicReceipt>> editPublic( @PathVariable UUID tenantId, @PathVariable UUID rootId, @io.swagger.v3.oas.annotations.parameters.RequestBody( required = true, content = @Content( schema = @Schema(implementation = EditPublicBody.class))) @RequestBody String body)` | [ProductDefinitionLifecycleController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionLifecycleController.java) | Local declaration |
| `POST /api/v1/tenants/{tenantId}/product-definitions/{rootId}/commands/private-cost` | `public Mono<ResponseEntity<PrivateReceipt>> editCost( @PathVariable UUID tenantId, @PathVariable UUID rootId, @io.swagger.v3.oas.annotations.parameters.RequestBody( required = true, content = @Content(schema = @Schema(implementation = EditCostBody.class))) @RequestBody String body)` | [ProductDefinitionLifecycleController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionLifecycleController.java) | Local declaration |
| `POST /api/v1/tenants/{tenantId}/product-definitions/{rootId}/commands/submission` | `public Mono<ResponseEntity<PublicReceipt>> submit( @PathVariable UUID tenantId, @PathVariable UUID rootId, @io.swagger.v3.oas.annotations.parameters.RequestBody( required = true, content = @Content( schema = @Schema( implementation = SubmitPublicBody.class))) @RequestBody String body)` | [ProductDefinitionLifecycleController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionLifecycleController.java) | Local declaration |
| `POST /api/v1/tenants/{tenantId}/product-definitions/{rootId}/commands/selection/approve` | `public Mono<ResponseEntity<PrivateReceipt>> approve( @PathVariable UUID tenantId, @PathVariable UUID rootId, @io.swagger.v3.oas.annotations.parameters.RequestBody( required = true, content = @Content( schema = @Schema( implementation = ReviewSelectionBody.class))) @RequestBody String body)` | [ProductDefinitionLifecycleController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionLifecycleController.java) | Local declaration |
| `POST /api/v1/tenants/{tenantId}/product-definitions/{rootId}/commands/selection/reject` | `public Mono<ResponseEntity<PrivateReceipt>> reject( @PathVariable UUID tenantId, @PathVariable UUID rootId, @io.swagger.v3.oas.annotations.parameters.RequestBody( required = true, content = @Content( schema = @Schema( implementation = ReviewSelectionBody.class))) @RequestBody String body)` | [ProductDefinitionLifecycleController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionLifecycleController.java) | Local declaration |
| `POST /api/v1/tenants/{tenantId}/product-definitions/{rootId}/commands/publication` | `public Mono<ResponseEntity<PublicReceipt>> publish( @PathVariable UUID tenantId, @PathVariable UUID rootId, @io.swagger.v3.oas.annotations.parameters.RequestBody( required = true, content = @Content(schema = @Schema(implementation = PublishBody.class))) @RequestBody String body)` | [ProductDefinitionLifecycleController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionLifecycleController.java) | Local declaration |
| `POST /api/v1/tenants/{tenantId}/product-definitions/{rootId}/commands/publication/withdraw` | `public Mono<ResponseEntity<PublicReceipt>> withdraw( @PathVariable UUID tenantId, @PathVariable UUID rootId, @io.swagger.v3.oas.annotations.parameters.RequestBody( required = true, content = @Content(schema = @Schema(implementation = WithdrawBody.class))) @RequestBody String body)` | [ProductDefinitionLifecycleController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionLifecycleController.java) | Local declaration |
| `GET /api/v1/tenants/{tenantId}/product-definitions/{rootId}/staff-publication/readiness` | `public Mono<ResponseEntity<ProductDefinitionPublicationReadinessView>> readiness( @PathVariable UUID tenantId, @PathVariable UUID rootId)` | [ProductDefinitionPublicationReadController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionPublicationReadController.java) | Local declaration |
| `GET /api/v1/tenants/{tenantId}/product-definitions/{rootId}/staff-publication/current` | `public Mono<ResponseEntity<ProductDefinitionPublicationDiscoveryView>> current( @PathVariable UUID tenantId, @PathVariable UUID rootId)` | [ProductDefinitionPublicationReadController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionPublicationReadController.java) | Local declaration |
| `GET /api/v1/tenants/{tenantId}/product-definitions/{rootId}/staff-review/selection/current` | `public Mono<ResponseEntity<ProductDefinitionReviewSelectionView>> current( @PathVariable UUID tenantId, @PathVariable UUID rootId)` | [ProductDefinitionReviewController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionReviewController.java) | Local declaration |
| `GET /api/v1/tenants/{tenantId}/product-definitions/{rootId}/staff-review/selections/{submissionId}` | `public Mono<ResponseEntity<ProductDefinitionReviewSelectionView>> selection( @PathVariable UUID tenantId, @PathVariable UUID rootId, @PathVariable UUID submissionId)` | [ProductDefinitionReviewController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionReviewController.java) | Local declaration |
| `GET /api/v1/tenants/{tenantId}/product-definitions/{rootId}` | `public Mono<ResponseEntity<RootView>> root( @PathVariable UUID tenantId, @PathVariable UUID rootId)` | [ProductDefinitionServingController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionServingController.java) | Local declaration |
| `GET /api/v1/tenants/{tenantId}/product-definitions/{rootId}/revisions/{revisionId}` | `public Mono<ResponseEntity<RevisionView>> revision( @PathVariable UUID tenantId, @PathVariable UUID rootId, @PathVariable UUID revisionId)` | [ProductDefinitionServingController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionServingController.java) | Local declaration |
| `GET /api/v1/tenants/{tenantId}/product-definitions/{rootId}/publications/{publicationId}` | `public Mono<ResponseEntity<PublicationView>> publication( @PathVariable UUID tenantId, @PathVariable UUID rootId, @PathVariable UUID publicationId)` | [ProductDefinitionServingController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionServingController.java) | Local declaration |
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

### ProductDefinitionCommandResources

[ProductDefinitionCommandResources](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandResources.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `actionId` | `UUID` | See declaration / service rules |
| `revisionId` | `UUID` | See declaration / service rules |
| `costRevisionId` | `UUID` | See declaration / service rules |
| `submissionId` | `UUID` | See declaration / service rules |
| `reviewId` | `UUID` | See declaration / service rules |
| `publicationId` | `UUID` | See declaration / service rules |
| `productVersionId` | `UUID` | See declaration / service rules |
| `historyId` | `UUID` | See declaration / service rules |
| `auditId` | `UUID` | See declaration / service rules |
| `publicEventId` | `UUID` | See declaration / service rules |
| `costEventId` | `UUID` | See declaration / service rules |

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

[AccountTariffTerms](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/AccountTariffTerms.java) · DTO / record / model

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

[CostTerms](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/CostTerms.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `schemaVersion` | `Integer` | See declaration / service rules |
| `businessArea` | `String` | See declaration / service rules |
| `profileCode` | `String` | See declaration / service rules |
| `mode` | `String` | See declaration / service rules |
| `lines` | `List<CostLine>` | See declaration / service rules |

### FixedRateInstallmentLoanTerms

[FixedRateInstallmentLoanTerms](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/FixedRateInstallmentLoanTerms.java) · DTO / record / model

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

### ProductDefinitionCommand

[ProductDefinitionCommand](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/ProductDefinitionCommand.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `action` | `String` | See declaration / service rules |
| `target` | `String` | See declaration / service rules |
| `scopes` | `Set<String>` | See declaration / service rules |

### ProductDefinitionCostView

[ProductDefinitionCostView](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/ProductDefinitionCostView.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `tenantId` | `UUID` | See declaration / service rules |
| `rootId` | `UUID` | See declaration / service rules |
| `publicRevisionId` | `UUID` | See declaration / service rules |
| `publicDigest` | `String` | See declaration / service rules |
| `publicVersion` | `Integer` | See declaration / service rules |
| `phase` | `String` | See declaration / service rules |
| `publicDefinition` | `DefinitionContracts.ProductDefinition` | See declaration / service rules |
| `costRevisionId` | `UUID` | See declaration / service rules |
| `costDigest` | `String` | See declaration / service rules |
| `costVersion` | `Integer` | See declaration / service rules |
| `cost` | `CostTerms` | See declaration / service rules |
| `costCompatibility` | `String` | See declaration / service rules |
| `validationBoundary` | `String` | See declaration / service rules |
| `observedAt` | `Instant` | See declaration / service rules |

### ProductDefinitionPublicationDiscoveryView

[ProductDefinitionPublicationDiscoveryView](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/ProductDefinitionPublicationDiscoveryView.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `tenantId` | `UUID` | See declaration / service rules |
| `rootId` | `UUID` | See declaration / service rules |
| `state` | `PublicationDiscoveryState` | See declaration / service rules |
| `publication` | `DiscoveredPublication` | See declaration / service rules |
| `observation` | `ReadObservation` | See declaration / service rules |
| `operationalAdmission` | `OperationalAdmission` | See declaration / service rules |

### ProductDefinitionPublicationReadinessView

[ProductDefinitionPublicationReadinessView](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/ProductDefinitionPublicationReadinessView.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `tenantId` | `UUID` | See declaration / service rules |
| `rootId` | `UUID` | See declaration / service rules |
| `status` | `PublicationReadinessStatus` | See declaration / service rules |
| `selection` | `PublicationReadySelection` | See declaration / service rules |
| `observation` | `ReadObservation` | See declaration / service rules |
| `operationalAdmission` | `OperationalAdmission` | See declaration / service rules |

### ProductDefinitionReviewSelectionView

[ProductDefinitionReviewSelectionView](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/ProductDefinitionReviewSelectionView.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `selection` | `Selection` | See declaration / service rules |
| `selectionFingerprint` | `String` | See declaration / service rules |
| `definition` | `ProductDefinition` | See declaration / service rules |
| `cost` | `CostTerms` | See declaration / service rules |
| `costCompatibility` | `String` | See declaration / service rules |
| `validationBoundary` | `String` | See declaration / service rules |
| `observedAt` | `Instant` | See declaration / service rules |
| `activeReviewId` | `java.util.UUID` | See declaration / service rules |

### TermPcInsuranceTerms

[TermPcInsuranceTerms](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/TermPcInsuranceTerms.java) · DTO / record / model

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

### ProductDefinitionPublication

[ProductDefinitionPublication](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/entities/definition/ProductDefinitionPublication.java) · Persisted entity

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `publicationId` | `UUID` | See declaration / service rules |
| `tenantId` | `UUID` | See declaration / service rules |
| `rootId` | `UUID` | See declaration / service rules |
| `reviewId` | `UUID` | See declaration / service rules |
| `revisionId` | `UUID` | See declaration / service rules |
| `productVersionId` | `UUID` | See declaration / service rules |
| `canonicalDefinition` | `String` | See declaration / service rules |
| `publicDigest` | `String` | See declaration / service rules |
| `encodingVersion` | `String` | See declaration / service rules |
| `operationalAdmission` | `String` | See declaration / service rules |
| `createdAt` | `Instant` | See declaration / service rules |

### ProductDefinitionPublicationControl

[ProductDefinitionPublicationControl](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/entities/definition/ProductDefinitionPublicationControl.java) · Persisted entity

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `publicationId` | `UUID` | See declaration / service rules |
| `tenantId` | `UUID` | See declaration / service rules |
| `rootId` | `UUID` | See declaration / service rules |
| `state` | `String` | See declaration / service rules |
| `stateVersion` | `Integer` | See declaration / service rules |

### ProductDefinitionRevision

[ProductDefinitionRevision](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/entities/definition/ProductDefinitionRevision.java) · Persisted entity

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `revisionId` | `UUID` | See declaration / service rules |
| `tenantId` | `UUID` | See declaration / service rules |
| `rootId` | `UUID` | See declaration / service rules |
| `canonicalDefinition` | `String` | See declaration / service rules |
| `publicDigest` | `String` | See declaration / service rules |
| `encodingVersion` | `String` | See declaration / service rules |
| `sourcePublicationId` | `UUID` | See declaration / service rules |
| `sourcePublicationDigest` | `String` | See declaration / service rules |
| `createdAt` | `Instant` | See declaration / service rules |

### ProductDefinitionRoot

[ProductDefinitionRoot](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/entities/definition/ProductDefinitionRoot.java) · Persisted entity

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `rootId` | `UUID` | See declaration / service rules |
| `tenantId` | `UUID` | See declaration / service rules |
| `productId` | `UUID` | See declaration / service rules |
| `productCode` | `String` | See declaration / service rules |
| `businessArea` | `String` | See declaration / service rules |
| `profileCode` | `String` | See declaration / service rules |
| `profileVersion` | `Integer` | See declaration / service rules |
| `phase` | `String` | See declaration / service rules |
| `publicStateVersion` | `Integer` | See declaration / service rules |
| `publicHead` | `UUID` | See declaration / service rules |
| `createdAt` | `Instant` | See declaration / service rules |

### ProductDefinitionVersionIdentity

[ProductDefinitionVersionIdentity](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/entities/definition/ProductDefinitionVersionIdentity.java) · Persisted entity

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `productVersionId` | `UUID` | See declaration / service rules |
| `tenantId` | `UUID` | See declaration / service rules |
| `rootId` | `UUID` | See declaration / service rules |
| `productId` | `UUID` | See declaration / service rules |
| `publicationId` | `UUID` | See declaration / service rules |

### ProductDefinitionCostControl

[ProductDefinitionCostControl](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/entities/definition/lifecycle/ProductDefinitionCostControl.java) · Persisted entity

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `rootId` | `UUID` | See declaration / service rules |
| `tenantId` | `UUID` | See declaration / service rules |
| `costHead` | `UUID` | See declaration / service rules |
| `costStateVersion` | `Integer` | See declaration / service rules |

### ProductDefinitionCostRevision

[ProductDefinitionCostRevision](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/entities/definition/lifecycle/ProductDefinitionCostRevision.java) · Persisted entity

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `costRevisionId` | `UUID` | See declaration / service rules |
| `tenantId` | `UUID` | See declaration / service rules |
| `rootId` | `UUID` | See declaration / service rules |
| `canonicalCost` | `String` | See declaration / service rules |
| `costDigest` | `String` | See declaration / service rules |
| `encodingVersion` | `String` | See declaration / service rules |
| `actorIssuer` | `String` | See declaration / service rules |
| `actorId` | `String` | See declaration / service rules |
| `createdAt` | `Instant` | See declaration / service rules |

### ProductDefinitionDraftRevision

[ProductDefinitionDraftRevision](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/entities/definition/lifecycle/ProductDefinitionDraftRevision.java) · Persisted entity

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `revisionId` | `UUID` | See declaration / service rules |
| `tenantId` | `UUID` | See declaration / service rules |
| `rootId` | `UUID` | See declaration / service rules |
| `canonicalDefinition` | `String` | See declaration / service rules |
| `publicDigest` | `String` | See declaration / service rules |
| `encodingVersion` | `String` | See declaration / service rules |
| `sourcePublicationId` | `UUID` | See declaration / service rules |
| `sourcePublicationDigest` | `String` | See declaration / service rules |
| `actorIssuer` | `String` | See declaration / service rules |
| `actorId` | `String` | See declaration / service rules |
| `createdAt` | `Instant` | See declaration / service rules |

### ProductDefinitionDraftRoot

[ProductDefinitionDraftRoot](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/entities/definition/lifecycle/ProductDefinitionDraftRoot.java) · Persisted entity

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `rootId` | `UUID` | See declaration / service rules |
| `tenantId` | `UUID` | See declaration / service rules |
| `productId` | `UUID` | See declaration / service rules |
| `productCode` | `String` | See declaration / service rules |
| `businessArea` | `String` | See declaration / service rules |
| `profileCode` | `String` | See declaration / service rules |
| `profileVersion` | `Integer` | See declaration / service rules |
| `phase` | `String` | See declaration / service rules |
| `publicStateVersion` | `Integer` | See declaration / service rules |
| `publicHead` | `UUID` | See declaration / service rules |
| `activeReviewId` | `UUID` | See declaration / service rules |
| `createdAt` | `Instant` | See declaration / service rules |

### ProductDefinitionServingProperties

[ProductDefinitionServingProperties](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/config/definition/ProductDefinitionServingProperties.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `policyBaseUrl` | `String` | See declaration / service rules |
| `policyPath` | `String` | See declaration / service rules |

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

### ProductDefinitionCommandAuthorization

[ProductDefinitionCommandAuthorization](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionCommandAuthorization.java)

```java
record Scope( UUID tenantId, UUID rootId, UUID operationId, ProductDefinitionCommand command, UUID targetId);
record Admission( SecurityPrincipal principal, Scope scope, long actorExpiresAtEpochMillis, long validUntilEpochMillis);
Mono<Admission> authorize(Scope scope);
Mono<Void> reauthorize(Admission admission);
```

### ProductDefinitionCostReadAuthorization

[ProductDefinitionCostReadAuthorization](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionCostReadAuthorization.java)

```java
record Scope(UUID tenantId, UUID rootId);
record Admission( SecurityPrincipal principal, Scope scope, long actorExpiresAtEpochMillis, long validUntilEpochMillis);
Mono<Admission> authorize(Scope scope);
Mono<Void> reauthorize(Admission admission);
```

### ProductDefinitionCostReadService

[ProductDefinitionCostReadService](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionCostReadService.java)

```java
Mono<ProductDefinitionCostView> readCurrent(UUID tenantId, UUID rootId);
```

### ProductDefinitionLifecycleService

[ProductDefinitionLifecycleService](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionLifecycleService.java)

```java
Mono<Receipt> execute( UUID tenantId, UUID rootId, ProductDefinitionCommand command, String body);
```

### ProductDefinitionPublicationReadAuthorization

[ProductDefinitionPublicationReadAuthorization](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionPublicationReadAuthorization.java)

```java
READINESS( "product:definitions:publish", "product.definition.publication.readiness", "readiness"), DISCOVERY( "product:definitions:withdraw", "product.definition.publication.discovery", "current");
Target(String scope, String action, String resourceSuffix);
String requiredScope();
String action();
String resourceSuffix();
record Scope(UUID tenantId, UUID rootId, Target target);
record Admission( SecurityPrincipal principal, Scope scope, long actorExpiresAtEpochMillis, long validUntilEpochMillis);
Mono<Admission> authorize(Scope scope);
Mono<Void> reauthorize(Admission admission);
```

### ProductDefinitionPublicationReadService

[ProductDefinitionPublicationReadService](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionPublicationReadService.java)

```java
Mono<ProductDefinitionPublicationReadinessView> readiness(UUID tenantId, UUID rootId);
Mono<ProductDefinitionPublicationDiscoveryView> discover(UUID tenantId, UUID rootId);
```

### ProductDefinitionReadAuthorization

[ProductDefinitionReadAuthorization](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionReadAuthorization.java)

```java
record Scope(UUID tenantId, UUID rootId, Operation operation, UUID resourceId);
record Admission( SecurityPrincipal principal, Scope scope, long actorExpiresAtEpochMillis, long validUntilEpochMillis);
Mono<Admission> authorize(Scope scope);
Mono<Void> reauthorize(Admission admission);
```

### ProductDefinitionReviewAuthorization

[ProductDefinitionReviewAuthorization](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionReviewAuthorization.java)

```java
record Scope(UUID tenantId, UUID rootId, UUID submissionId, Target target);
public Scope(UUID tenantId, UUID rootId, UUID submissionId);
this(tenantId, rootId, submissionId, Target.EXACT);
static Scope current(UUID tenantId, UUID rootId);
record Admission( SecurityPrincipal principal, Scope scope, long actorExpiresAtEpochMillis, long validUntilEpochMillis);
Mono<Admission> authorize(Scope scope);
Mono<Void> reauthorize(Admission admission);
```

### ProductDefinitionReviewReadService

[ProductDefinitionReviewReadService](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionReviewReadService.java)

```java
Mono<ProductDefinitionReviewSelectionView> readCurrentSelection(UUID tenantId, UUID rootId);
Mono<ProductDefinitionReviewSelectionView> readSelection( UUID tenantId, UUID rootId, UUID submissionId);
```

### ProductDefinitionServingService

[ProductDefinitionServingService](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionServingService.java)

```java
Mono<RootView> readRoot(UUID tenantId, UUID rootId);
Mono<RevisionView> readRevision(UUID tenantId, UUID rootId, UUID revisionId);
Mono<PublicationView> readPublication(UUID tenantId, UUID rootId, UUID publicationId);
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
| `PRODUCT_DEFINITION_ISSUER` | Empty | [application](../../core-common-product-mgmt-web/src/main/resources/application.yaml) |
| `PRODUCT_DEFINITION_AUDIENCE` | Empty | [application](../../core-common-product-mgmt-web/src/main/resources/application.yaml) |
| `PRODUCT_DEFINITION_JWKS_URI` | Empty | [application](../../core-common-product-mgmt-web/src/main/resources/application.yaml) |
| `PRODUCT_DEFINITION_POLICY_URL` | Empty | [application](../../core-common-product-mgmt-web/src/main/resources/application.yaml) |
| `PRODUCT_DEFINITION_POLICY_PATH` | Empty | [application](../../core-common-product-mgmt-web/src/main/resources/application.yaml) |

## Security and failure evidence

| Declaration or failure site | Source |
| --- | --- |
| `new RuntimeException("Error parsing product marketing_features JSON", e);` | [ProductMapper](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/mappers/ProductMapper.java) |
| `new RuntimeException("Error serialising product marketing_features", e);` | [ProductMapper](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/mappers/ProductMapper.java) |
| `new IllegalArgumentException("EXACT_CARD_ENROLLMENT_CAPABILITY_REQUEST_REQUIRED");` | [CardEnrollmentCapabilityCodec](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/cardeligibility/CardEnrollmentCapabilityCodec.java) |
| `new IllegalArgumentException("EXACT_CARD_ENROLLMENT_CAPABILITY_REQUIRED"); }` | [CardEnrollmentCapabilityRules](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/cardeligibility/CardEnrollmentCapabilityRules.java) |
| `new IllegalArgumentException("COST_DEFINITION_INVALID");` | [CostRules](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/CostRules.java) |
| `new IllegalStateException("SHA-256 unavailable", e);` | [DefinitionCanonical](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/DefinitionCanonical.java) |
| `new IllegalArgumentException("DEFINITION_SHAPE");` | [DefinitionCanonical](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/DefinitionCanonical.java) |
| `new IllegalArgumentException("CHECKER_IDENTITY_REQUIRED");` | [DefinitionReviewRules](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/DefinitionReviewRules.java) |
| `new IllegalArgumentException("HUMAN_CHECKER_REQUIRED");` | [DefinitionReviewRules](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/DefinitionReviewRules.java) |
| `new IllegalArgumentException("REVIEW_CONTRIBUTORS_REQUIRED");` | [DefinitionReviewRules](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/DefinitionReviewRules.java) |
| `new IllegalArgumentException("INDEPENDENT_CHECKER_REQUIRED");` | [DefinitionReviewRules](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/DefinitionReviewRules.java) |
| `new IllegalArgumentException("DEFINITION_RULE at " + path);` | [DefinitionRules](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/DefinitionRules.java) |
| `new IllegalArgumentException("PRODUCT_DEFINITION_COMMAND_INVALID");` | [ProductDefinitionInstructionCodec](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionInstructionCodec.java) |
| `new IllegalArgumentException(message);` | [ProductDefinitionLifecycleRules](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionLifecycleRules.java) |
| `new ResponseStatusException(HttpStatus.NOT_FOUND, "Product definition not found");` | [AccountTariffServingMapper](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/AccountTariffServingMapper.java) |
| `new ResponseStatusException(` | [AccountTariffServingMapper](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/AccountTariffServingMapper.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [AccountTariffServingMapper](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/AccountTariffServingMapper.java) |
| `new ResponseStatusException(` | [ProductDefinitionCommandAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandAuthorizationImpl.java) |
| `HttpStatus.BAD_REQUEST` | [ProductDefinitionCommandAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandAuthorizationImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionCommandAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandAuthorizationImpl.java) |
| `HttpStatus.BAD_REQUEST` | [ProductDefinitionCommandAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandAuthorizationImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionCommandAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandAuthorizationImpl.java) |
| `HttpStatus.BAD_REQUEST` | [ProductDefinitionCommandAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandAuthorizationImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionCommandAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandAuthorizationImpl.java) |
| `HttpStatus.BAD_REQUEST` | [ProductDefinitionCommandAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandAuthorizationImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionCommandAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandAuthorizationImpl.java) |
| `HttpStatus.UNAUTHORIZED` | [ProductDefinitionCommandAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandAuthorizationImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionCommandAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandAuthorizationImpl.java) |
| `HttpStatus.FORBIDDEN` | [ProductDefinitionCommandAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandAuthorizationImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionCommandAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandAuthorizationImpl.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [ProductDefinitionCommandAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandAuthorizationImpl.java) |
| `HttpStatus.CONFLICT` | [ProductDefinitionCommandErrors](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandErrors.java) |
| `HttpStatus.CONFLICT` | [ProductDefinitionCommandErrors](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandErrors.java) |
| `HttpStatus.FORBIDDEN` | [ProductDefinitionCommandErrors](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandErrors.java) |
| `HttpStatus.NOT_FOUND` | [ProductDefinitionCommandErrors](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandErrors.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [ProductDefinitionCommandErrors](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandErrors.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [ProductDefinitionCommandErrors](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandErrors.java) |
| `new ResponseStatusException(status, message);` | [ProductDefinitionCommandErrors](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandErrors.java) |
| `new IllegalArgumentException("PRODUCT_DEFINITION_NATIVE_PLAN_INVALID");` | [ProductDefinitionCommandPlan](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandPlan.java) |
| `new IllegalArgumentException("PRODUCT_DEFINITION_RECEIPT_INVALID");` | [ProductDefinitionCommandReceiptCodec](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandReceiptCodec.java) |
| `new ResponseStatusException(` | [ProductDefinitionCommandTransactionBoundary](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandTransactionBoundary.java) |
| `new ResponseStatusException(` | [ProductDefinitionCostReadServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCostReadServiceImpl.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [ProductDefinitionCostReadServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCostReadServiceImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionLifecycleServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionLifecycleServiceImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionLifecycleServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionLifecycleServiceImpl.java) |
| `HttpStatus.BAD_REQUEST` | [ProductDefinitionLifecycleServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionLifecycleServiceImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionLifecycleServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionLifecycleServiceImpl.java) |
| `HttpStatus.NOT_FOUND` | [ProductDefinitionLifecycleServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionLifecycleServiceImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionLifecycleServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionLifecycleServiceImpl.java) |
| `HttpStatus.NOT_FOUND` | [ProductDefinitionLifecycleServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionLifecycleServiceImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionLifecycleServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionLifecycleServiceImpl.java) |
| `HttpStatus.CONFLICT` | [ProductDefinitionLifecycleServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionLifecycleServiceImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionLifecycleServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionLifecycleServiceImpl.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [ProductDefinitionLifecycleServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionLifecycleServiceImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionPrivateReadSnapshots](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPrivateReadSnapshots.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [ProductDefinitionPrivateReadSnapshots](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPrivateReadSnapshots.java) |
| `new ResponseStatusException(HttpStatus.NOT_FOUND, "Product definition not found");` | [ProductDefinitionPrivateReadSnapshots](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPrivateReadSnapshots.java) |
| `new ResponseStatusException(HttpStatus.FORBIDDEN, "Current permission required");` | [ProductDefinitionPublicationReadServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPublicationReadServiceImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionPublicationReadServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPublicationReadServiceImpl.java) |
| `HttpStatus.NOT_FOUND` | [ProductDefinitionPublicationReadServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPublicationReadServiceImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionPublicationSnapshots](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPublicationSnapshots.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [ProductDefinitionPublicationSnapshots](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPublicationSnapshots.java) |
| `new ResponseStatusException(` | [ProductDefinitionReadAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReadAuthorizationImpl.java) |
| `HttpStatus.BAD_REQUEST` | [ProductDefinitionReadAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReadAuthorizationImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionReadAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReadAuthorizationImpl.java) |
| `HttpStatus.UNAUTHORIZED` | [ProductDefinitionReadAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReadAuthorizationImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionReadAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReadAuthorizationImpl.java) |
| `HttpStatus.FORBIDDEN` | [ProductDefinitionReadAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReadAuthorizationImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionReadAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReadAuthorizationImpl.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [ProductDefinitionReadAuthorizationImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReadAuthorizationImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionReviewReadServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReviewReadServiceImpl.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [ProductDefinitionReviewReadServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReviewReadServiceImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionReviewReadServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReviewReadServiceImpl.java) |
| `HttpStatus.NOT_FOUND` | [ProductDefinitionReviewReadServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReviewReadServiceImpl.java) |
| `new ResponseStatusException(HttpStatus.CONFLICT, "Product review selection changed");` | [ProductDefinitionReviewReadServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReviewReadServiceImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionServingServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionServingServiceImpl.java) |
| `HttpStatus.FORBIDDEN` | [ProductDefinitionServingServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionServingServiceImpl.java) |
| `new ResponseStatusException(` | [ProductDefinitionServingServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionServingServiceImpl.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [ProductDefinitionServingServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionServingServiceImpl.java) |
| `new ResponseStatusException(HttpStatus.NOT_FOUND, "Product definition not found");` | [ProductDefinitionServingServiceImpl](../../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionServingServiceImpl.java) |
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
| `new IllegalArgumentException("PRODUCT_DEFINITION_COMMAND_SCOPE_REQUIRED");` | [ProductDefinitionLifecycleMessages](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/ProductDefinitionLifecycleMessages.java) |
| `new IllegalArgumentException("PRODUCT_DEFINITION_COMMAND_BODY_REQUIRED");` | [ProductDefinitionLifecycleMessages](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/ProductDefinitionLifecycleMessages.java) |
| `new IllegalArgumentException("PRODUCT_PUBLICATION_CONTROL_REQUIRED");` | [ProductDefinitionPublicationDiscoveryView](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/ProductDefinitionPublicationDiscoveryView.java) |
| `new IllegalArgumentException("PRODUCT_PUBLICATION_DISCOVERY_REQUIRED");` | [ProductDefinitionPublicationDiscoveryView](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/ProductDefinitionPublicationDiscoveryView.java) |
| `new IllegalArgumentException("PRODUCT_PUBLICATION_SELECTION_REQUIRED");` | [ProductDefinitionPublicationReadinessView](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/ProductDefinitionPublicationReadinessView.java) |
| `new IllegalArgumentException("PRODUCT_PUBLICATION_READINESS_REQUIRED");` | [ProductDefinitionPublicationReadinessView](../../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/ProductDefinitionPublicationReadinessView.java) |
| `new IllegalStateException("PRODUCT_DEFINITION_READ_FACTORY_BINDING_REQUIRED");` | [ProductDefinitionReadConnectionSource](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/config/ProductDefinitionReadConnectionSource.java) |
| `new IllegalArgumentException("PRODUCT_DEFINITION_ASSIGNED_ID_REQUIRED"));` | [ProductDefinitionDraftWritesImpl](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/repositories/definition/lifecycle/impl/ProductDefinitionDraftWritesImpl.java) |
| `new IllegalArgumentException("PRODUCT_DEFINITION_VERSION_EXHAUSTED");` | [ProductDefinitionDraftWritesImpl](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/repositories/definition/lifecycle/impl/ProductDefinitionDraftWritesImpl.java) |
| `new DataIntegrityViolationException(` | [ProductDefinitionDraftWritesImpl](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/repositories/definition/lifecycle/impl/ProductDefinitionDraftWritesImpl.java) |
| `new OptimisticLockingFailureException(` | [ProductDefinitionDraftWritesImpl](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/repositories/definition/lifecycle/impl/ProductDefinitionDraftWritesImpl.java) |
| `new DataIntegrityViolationException(` | [ProductDefinitionDraftWritesImpl](../../core-common-product-mgmt-models/src/main/java/io/fireflysoftware/core/product/models/repositories/definition/lifecycle/impl/ProductDefinitionDraftWritesImpl.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [ProductDefinitionCommandSecurityConfiguration](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/config/definition/ProductDefinitionCommandSecurityConfiguration.java) |
| `new IllegalStateException(` | [ProductDefinitionServingConfiguration](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/config/definition/ProductDefinitionServingConfiguration.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [ProductDefinitionServingSecurityConfiguration](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/config/definition/ProductDefinitionServingSecurityConfiguration.java) |
| `new JwtException("Current product signing configuration unavailable"));` | [ProductDefinitionServingSecurityConfiguration](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/config/definition/ProductDefinitionServingSecurityConfiguration.java) |
| `new IllegalStateException("PRODUCT_DEFINITION_COMMAND_CREDENTIALS_REQUIRED");` | [ProductDefinitionCommandConfiguration](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionCommandConfiguration.java) |
| `new IllegalStateException("PRODUCT_DEFINITION_READ_OPTIONS_UNAVAILABLE");` | [ProductDefinitionCommandConfiguration](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionCommandConfiguration.java) |
| `new IllegalStateException(` | [ProductDefinitionCommandConfiguration](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionCommandConfiguration.java) |
| `new IllegalStateException("PRODUCT_DEFINITION_POSTGRESQL_REQUIRED");` | [ProductDefinitionCommandConfiguration](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionCommandConfiguration.java) |
| `new IllegalStateException("PRODUCT_DEFINITION_COMMAND_FACTORY_UNAVAILABLE");` | [ProductDefinitionCommandConfiguration](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionCommandConfiguration.java) |
| `new IllegalStateException("PRODUCT_DEFINITION_COMMAND_PERSISTENCE_UNAVAILABLE");` | [ProductDefinitionCommandConfiguration](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionCommandConfiguration.java) |
| `new IllegalStateException(` | [ProductDefinitionPublicationReadConfiguration](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionPublicationReadConfiguration.java) |
| `new IllegalStateException("PRODUCT_DEFINITION_READ_OPTIONS_UNAVAILABLE");` | [ProductDefinitionPublicationReadConfiguration](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionPublicationReadConfiguration.java) |
| `new IllegalStateException(` | [ProductDefinitionPublicationReadConfiguration](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionPublicationReadConfiguration.java) |
| `new IllegalStateException("PRODUCT_DEFINITION_POSTGRESQL_REQUIRED");` | [ProductDefinitionPublicationReadConfiguration](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionPublicationReadConfiguration.java) |
| `new IllegalStateException(` | [ProductDefinitionPublicationReadConfiguration](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionPublicationReadConfiguration.java) |
| `new IllegalStateException(` | [ProductDefinitionPublicationReadConfiguration](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionPublicationReadConfiguration.java) |
| `HttpStatus.CREATED` | [ProductCategoryController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductCategoryController.java) |
| `HttpStatus.CREATED` | [ProductConfigurationController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductConfigurationController.java) |
| `HttpStatus.CREATED` | [ProductController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductController.java) |
| `@SecurityRequirement(name = "productDefinitionBearer")` | [ProductDefinitionCostController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionCostController.java) |
| `new ResponseStatusException(` | [ProductDefinitionCostController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionCostController.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [ProductDefinitionCostController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionCostController.java) |
| `@SecurityRequirement(name = "productDefinitionBearer")` | [ProductDefinitionLifecycleController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionLifecycleController.java) |
| `new ResponseStatusException(` | [ProductDefinitionLifecycleController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionLifecycleController.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [ProductDefinitionLifecycleController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionLifecycleController.java) |
| `@SecurityRequirement(name = "productDefinitionBearer")` | [ProductDefinitionPublicationReadController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionPublicationReadController.java) |
| `new ResponseStatusException(` | [ProductDefinitionPublicationReadController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionPublicationReadController.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [ProductDefinitionPublicationReadController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionPublicationReadController.java) |
| `@SecurityRequirement(name = "productDefinitionBearer")` | [ProductDefinitionReviewController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionReviewController.java) |
| `new ResponseStatusException(` | [ProductDefinitionReviewController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionReviewController.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [ProductDefinitionReviewController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionReviewController.java) |
| `new ResponseStatusException(` | [ProductDefinitionReviewController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionReviewController.java) |
| `HttpStatus.SERVICE_UNAVAILABLE` | [ProductDefinitionReviewController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionReviewController.java) |
| `@SecurityRequirement(name = "productDefinitionBearer")` | [ProductDefinitionServingController](../../core-common-product-mgmt-web/src/main/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionServingController.java) |
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
- `org.fireflyframework:fireflyframework-security-spi:`
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
- `org.junit.jupiter:junit-jupiter:`
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
- `org.fireflyframework:fireflyframework-security-adapter-opa:`
- `org.fireflyframework:fireflyframework-security-resource-server:`
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

- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/architecture/ProductDefinitionServingArchitectureTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/architecture/ProductDefinitionServingArchitectureTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/README.md](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/README.md)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/cardeligibility/CardEnrollmentCapabilityCodecTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/cardeligibility/CardEnrollmentCapabilityCodecTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/cardeligibility/CardEnrollmentCapabilityRulesTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/cardeligibility/CardEnrollmentCapabilityRulesTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/category/ProductCategoryServiceImplTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/category/ProductCategoryServiceImplTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/configuration/ProductConfigurationServiceImplTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/configuration/ProductConfigurationServiceImplTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/AccountTariffRulesTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/AccountTariffRulesTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/CostRulesTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/CostRulesTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/DefinitionCanonicalTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/DefinitionCanonicalTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/DefinitionEnvelopeRulesTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/DefinitionEnvelopeRulesTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/DefinitionReviewRulesTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/DefinitionReviewRulesTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/DefinitionRulesTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/DefinitionRulesTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/FixedRateInstallmentLoanRulesTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/FixedRateInstallmentLoanRulesTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionCommandDatabaseFixture.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionCommandDatabaseFixture.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionCommandDatabaseFixtureTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionCommandDatabaseFixtureTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionDatabaseTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionDatabaseTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionFixtureGate.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionFixtureGate.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionFixtureGateTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionFixtureGateTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionInstructionCodecTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionInstructionCodecTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionLifecycleRulesTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionLifecycleRulesTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionServingNativeIT.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/ProductDefinitionServingNativeIT.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/ProfileFixtures.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/ProfileFixtures.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/TermPcInsuranceRulesTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/TermPcInsuranceRulesTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/AccountTariffServingMapperTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/AccountTariffServingMapperTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandAuthorizationTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandAuthorizationTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandErrorsTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandErrorsTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandPlanTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandPlanTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandReceiptCodecTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCommandReceiptCodecTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCostReadAuthorizationTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCostReadAuthorizationTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCostReadServiceTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCostReadServiceTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCurrentSelectionAuthorizationTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCurrentSelectionAuthorizationTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCurrentSelectionTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionCurrentSelectionTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionLifecycleServiceTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionLifecycleServiceTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPublicationDiscoverySnapshotsTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPublicationDiscoverySnapshotsTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPublicationReadAuthorizationTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPublicationReadAuthorizationTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPublicationReadDeadlineTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPublicationReadDeadlineTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPublicationReadServiceTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPublicationReadServiceTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPublicationSnapshotsTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPublicationSnapshotsTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPublicationViewsTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionPublicationViewsTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReadAuthorizationTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReadAuthorizationTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReadMappingTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReadMappingTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReviewAuthorizationTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReviewAuthorizationTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReviewReadServiceTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionReviewReadServiceTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionServingServiceTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/definition/impl/ProductDefinitionServingServiceTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/impl/ProductRelationshipServiceImplTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/impl/ProductRelationshipServiceImplTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/impl/ProductServiceImplTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/impl/ProductServiceImplTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/pricing/ProductPricingAggregatorServiceImplTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/core/services/pricing/ProductPricingAggregatorServiceImplTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/models/repositories/definition/lifecycle/impl/ProductDefinitionDraftCountTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/models/repositories/definition/lifecycle/impl/ProductDefinitionDraftCountTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/models/repositories/definition/lifecycle/impl/ProductDefinitionDraftMappingTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/models/repositories/definition/lifecycle/impl/ProductDefinitionDraftMappingTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/models/repositories/definition/lifecycle/impl/ProductDefinitionLifecyclePersistenceContractTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/models/repositories/definition/lifecycle/impl/ProductDefinitionLifecyclePersistenceContractTest.java)
- [core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/models/repositories/definition/lifecycle/impl/ProductDefinitionPublicationReadRepositoryTest.java](../../core-common-product-mgmt-core/src/test/java/io/fireflysoftware/core/product/models/repositories/definition/lifecycle/impl/ProductDefinitionPublicationReadRepositoryTest.java)
- [core-common-product-mgmt-core/src/test/resources/definition/v1/account.json](../../core-common-product-mgmt-core/src/test/resources/definition/v1/account.json)
- [core-common-product-mgmt-core/src/test/resources/definition/v1/insurance.json](../../core-common-product-mgmt-core/src/test/resources/definition/v1/insurance.json)
- [core-common-product-mgmt-core/src/test/resources/definition/v1/loan.json](../../core-common-product-mgmt-core/src/test/resources/definition/v1/loan.json)
- [core-common-product-mgmt-sdk/src/test/java/io/fireflysoftware/core/product/sdk/InstantScalarGeneratedContractTest.java](../../core-common-product-mgmt-sdk/src/test/java/io/fireflysoftware/core/product/sdk/InstantScalarGeneratedContractTest.java)
- [core-common-product-mgmt-sdk/src/test/java/io/fireflysoftware/core/product/sdk/PackagedOpenApiCompatibilityTest.java](../../core-common-product-mgmt-sdk/src/test/java/io/fireflysoftware/core/product/sdk/PackagedOpenApiCompatibilityTest.java)
- [core-common-product-mgmt-sdk/src/test/java/io/fireflysoftware/core/product/sdk/ProfileTermsGeneratedContractTest.java](../../core-common-product-mgmt-sdk/src/test/java/io/fireflysoftware/core/product/sdk/ProfileTermsGeneratedContractTest.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/ProductDefinitionCommandSecurityTest.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/ProductDefinitionCommandSecurityTest.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/ProductDefinitionPublicationSecurityTest.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/ProductDefinitionPublicationSecurityTest.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/ProductDefinitionServingConfigurationTest.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/ProductDefinitionServingConfigurationTest.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/ProductDefinitionServingSecurityTest.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/ProductDefinitionServingSecurityTest.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionCommandConfigurationTest.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionCommandConfigurationTest.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionFlywayDefaultsTest.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionFlywayDefaultsTest.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionJdbcSslModeTest.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionJdbcSslModeTest.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionLifecycleConfigurationTest.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionLifecycleConfigurationTest.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionLifecycleNativeIT.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionLifecycleNativeIT.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionOwnerCommandConfigurationTest.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionOwnerCommandConfigurationTest.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionPublicationPersistenceConfigurationTest.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/config/definition/lifecycle/ProductDefinitionPublicationPersistenceConfigurationTest.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionCostControllerTest.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionCostControllerTest.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionLifecycleControllerTest.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionLifecycleControllerTest.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionPublicationReadControllerTest.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionPublicationReadControllerTest.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionReviewControllerTest.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionReviewControllerTest.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionServingControllerTest.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/controllers/ProductDefinitionServingControllerTest.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/controllers/ProductPricingAggregatorControllerTest.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/controllers/ProductPricingAggregatorControllerTest.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/openapi/OpenApiGenApplication.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/openapi/OpenApiGenApplication.java)
- [core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/openapi/ProductDefinitionInstantSchemaTest.java](../../core-common-product-mgmt-web/src/test/java/io/fireflysoftware/core/product/web/openapi/ProductDefinitionInstantSchemaTest.java)

## CI definitions

- [.github/workflows/maven-central.yml](../../.github/workflows/maven-central.yml)
- [.github/workflows/release.yml](../../.github/workflows/release.yml)
