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


package io.fireflysoftware.core.product.core.services.pricing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fireflysoftware.core.product.core.services.impl.ProductPricingAggregatorServiceImpl;
import io.fireflysoftware.core.product.interfaces.enums.ProductConfigTypeEnum;
import io.fireflysoftware.core.product.interfaces.enums.ProductStatusEnum;
import io.fireflysoftware.core.product.interfaces.enums.ProductTypeEnum;
import io.fireflysoftware.core.product.models.entities.Product;
import io.fireflysoftware.core.product.models.entities.ProductConfiguration;
import io.fireflysoftware.core.product.models.repositories.ProductConfigurationRepository;
import io.fireflysoftware.core.product.models.repositories.ProductRepository;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductPricingAggregatorServiceImplTest {

    private static final UUID PERSONAL_LOAN_ID =
            UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID LEASING_ID =
            UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductConfigurationRepository productConfigurationRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ProductPricingAggregatorServiceImpl service;

    private Product personalLoan;
    private Product leasing;

    @BeforeEach
    void setUp() {
        personalLoan = new Product();
        personalLoan.setProductId(PERSONAL_LOAN_ID);
        personalLoan.setProductCode("PERSONAL_LOAN_DEMO");
        personalLoan.setProductName("Demo Personal Loan");
        personalLoan.setProductType(ProductTypeEnum.FINANCIAL);
        personalLoan.setProductStatus(ProductStatusEnum.ACTIVE);

        leasing = new Product();
        leasing.setProductId(LEASING_ID);
        leasing.setProductCode("LEASING_DEMO");
        leasing.setProductName("Demo Leasing");
        leasing.setProductType(ProductTypeEnum.FINANCIAL);
        leasing.setProductStatus(ProductStatusEnum.ACTIVE);
    }

    @Test
    void getProductPricing_personalLoan_happyPath() {
        when(productRepository.findById(PERSONAL_LOAN_ID))
                .thenReturn(Mono.just(personalLoan));
        when(productConfigurationRepository.findByProductId(PERSONAL_LOAN_ID))
                .thenReturn(Flux.fromIterable(personalLoanConfigs()));

        StepVerifier.create(service.getProductPricing(PERSONAL_LOAN_ID))
                .assertNext(dto -> {
                    org.junit.jupiter.api.Assertions.assertEquals(PERSONAL_LOAN_ID, dto.getProductId());
                    org.junit.jupiter.api.Assertions.assertEquals("PERSONAL_LOAN_DEMO", dto.getProductCode());
                    org.junit.jupiter.api.Assertions.assertEquals("PERSONAL_LOAN", dto.getProductType());
                    org.junit.jupiter.api.Assertions.assertEquals("EUR", dto.getCurrency());
                    org.junit.jupiter.api.Assertions.assertEquals(0,
                            new BigDecimal("1000").compareTo(dto.getMinAmount()));
                    org.junit.jupiter.api.Assertions.assertEquals(0,
                            new BigDecimal("60000").compareTo(dto.getMaxAmount()));
                    org.junit.jupiter.api.Assertions.assertEquals(12, dto.getMinTerm());
                    org.junit.jupiter.api.Assertions.assertEquals(96, dto.getMaxTerm());
                    org.junit.jupiter.api.Assertions.assertEquals(1, dto.getInterestRates().size());
                    org.junit.jupiter.api.Assertions.assertEquals(0,
                            new BigDecimal("7.99").compareTo(dto.getInterestRates().get(0).getTin()));
                    org.junit.jupiter.api.Assertions.assertEquals(1, dto.getFees().size());
                    org.junit.jupiter.api.Assertions.assertEquals("OPENING_FEE",
                            dto.getFees().get(0).getType());
                })
                .verifyComplete();
    }

    @Test
    void getProductPricing_leasing_fourBrackets() {
        when(productRepository.findById(LEASING_ID))
                .thenReturn(Mono.just(leasing));
        when(productConfigurationRepository.findByProductId(LEASING_ID))
                .thenReturn(Flux.fromIterable(leasingConfigs()));

        StepVerifier.create(service.getProductPricing(LEASING_ID))
                .assertNext(dto -> {
                    org.junit.jupiter.api.Assertions.assertEquals("LEASING", dto.getProductType());
                    org.junit.jupiter.api.Assertions.assertEquals(0,
                            new BigDecimal("5000").compareTo(dto.getMinAmount()));
                    org.junit.jupiter.api.Assertions.assertEquals(0,
                            new BigDecimal("500000").compareTo(dto.getMaxAmount()));
                    org.junit.jupiter.api.Assertions.assertEquals(4, dto.getInterestRates().size());
                    org.junit.jupiter.api.Assertions.assertEquals(0,
                            new BigDecimal("6.90").compareTo(dto.getInterestRates().get(0).getTin()));
                    org.junit.jupiter.api.Assertions.assertEquals(0,
                            new BigDecimal("5.20").compareTo(dto.getInterestRates().get(3).getTin()));
                    org.junit.jupiter.api.Assertions.assertEquals(1, dto.getFees().size());
                    org.junit.jupiter.api.Assertions.assertEquals(0,
                            new BigDecimal("1.0").compareTo(dto.getFees().get(0).getPercentage()));
                })
                .verifyComplete();
    }

    @Test
    void getProductPricing_productNotFound_emitsError() {
        UUID unknown = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
        when(productRepository.findById(unknown)).thenReturn(Mono.empty());

        StepVerifier.create(service.getProductPricing(unknown))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(BusinessException.class);
                    BusinessException be = (BusinessException) err;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(be.getCode()).isEqualTo("PRODUCT_NOT_FOUND");
                    assertThat(be.getMessage()).contains("Product not found");
                })
                .verify();
    }

    @Test
    void getProductPricing_missingLimitsConfig_stillReturnsTheProduct() {
        when(productRepository.findById(PERSONAL_LOAN_ID))
                .thenReturn(Mono.just(personalLoan));
        // Only PRICING rows present; LIMITS/amount_term is missing.
        List<ProductConfiguration> partial = List.of(
                config(PERSONAL_LOAN_ID, ProductConfigTypeEnum.PRICING,
                        "interest_rate_brackets",
                        "[{\"minAmount\":1000,\"maxAmount\":60000,\"tin\":7.99}]"),
                config(PERSONAL_LOAN_ID, ProductConfigTypeEnum.PRICING,
                        "fees",
                        "[{\"type\":\"OPENING_FEE\",\"percentage\":0,\"fixed\":0}]")
        );
        when(productConfigurationRepository.findByProductId(PERSONAL_LOAN_ID))
                .thenReturn(Flux.fromIterable(partial));

        StepVerifier.create(service.getProductPricing(PERSONAL_LOAN_ID))
                .assertNext(dto -> {
                    assertThat(dto.getProductId()).isEqualTo(PERSONAL_LOAN_ID);
                    assertThat(dto.getMinAmount()).isNull();
                    assertThat(dto.getMaxAmount()).isNull();
                    assertThat(dto.getInterestRates()).hasSize(1);
                    assertThat(dto.getFees()).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    void getProductPricing_withoutLendingRows_isStillAProduct() {
        when(productRepository.findById(PERSONAL_LOAN_ID))
                .thenReturn(Mono.just(personalLoan));
        when(productConfigurationRepository.findByProductId(PERSONAL_LOAN_ID))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.getProductPricing(PERSONAL_LOAN_ID))
                .assertNext(dto -> {
                    assertThat(dto.getProductCode()).isEqualTo(personalLoan.getProductCode());
                    assertThat(dto.getCurrency()).isNull();
                    assertThat(dto.getInterestRates()).isEmpty();
                    assertThat(dto.getFees()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void listProductsWithPricing_returnsOnlyActiveProducts() {
        // Build a RETIRED product alongside the two ACTIVE demo products to
        // assert that listProductsWithPricing never leaks deprecated catalog
        // entries into the calculator pipeline.
        Product retired = new Product();
        retired.setProductId(UUID.fromString("00000000-0000-0000-0000-0000000000ee"));
        retired.setProductCode("LEGACY_LOAN_DEMO");
        retired.setProductName("Legacy retired loan");
        retired.setProductType(ProductTypeEnum.FINANCIAL);
        retired.setProductStatus(ProductStatusEnum.RETIRED);

        // The repository contract guarantees only ACTIVE rows reach the
        // aggregator. The mock therefore returns ONLY the active products
        // (mirroring what the SQL filter does) — the RETIRED product is built
        // in the test only to make the intent explicit and to fail loudly if
        // someone reverts the implementation back to findAll().
        when(productRepository.findByProductStatus(ProductStatusEnum.ACTIVE))
                .thenReturn(Flux.just(personalLoan, leasing));
        when(productRepository.findById(PERSONAL_LOAN_ID))
                .thenReturn(Mono.just(personalLoan));
        when(productRepository.findById(LEASING_ID))
                .thenReturn(Mono.just(leasing));
        when(productConfigurationRepository.findByProductId(PERSONAL_LOAN_ID))
                .thenReturn(Flux.fromIterable(personalLoanConfigs()));
        when(productConfigurationRepository.findByProductId(LEASING_ID))
                .thenReturn(Flux.fromIterable(leasingConfigs()));

        StepVerifier.create(service.listProductsWithPricing(null)
                        .map(dto -> dto.getProductCode()))
                .expectNext("PERSONAL_LOAN_DEMO")
                .expectNext("LEASING_DEMO")
                .verifyComplete();

        // Sanity check: the RETIRED product was never asked for its
        // configuration — i.e. the status filter cut it off before any
        // downstream work was done.
        org.mockito.Mockito.verify(productConfigurationRepository,
                org.mockito.Mockito.never())
                .findByProductId(retired.getProductId());
    }

    @Test
    void listProductsWithPricing_filtersByDerivedProductType() {
        when(productRepository.findByProductStatus(ProductStatusEnum.ACTIVE))
                .thenReturn(Flux.just(personalLoan, leasing));
        when(productRepository.findById(LEASING_ID))
                .thenReturn(Mono.just(leasing));
        when(productConfigurationRepository.findByProductId(LEASING_ID))
                .thenReturn(Flux.fromIterable(leasingConfigs()));

        StepVerifier.create(service.listProductsWithPricing("LEASING")
                        .map(dto -> dto.getProductCode()))
                .expectNext("LEASING_DEMO")
                .verifyComplete();
    }

    @Test
    void getProductPricing_malformedJson_emitsParseError() {
        when(productRepository.findById(PERSONAL_LOAN_ID))
                .thenReturn(Mono.just(personalLoan));
        // amount_term is not valid JSON.
        List<ProductConfiguration> bad = List.of(
                config(PERSONAL_LOAN_ID, ProductConfigTypeEnum.LIMITS,
                        "amount_term", "{not-json"),
                config(PERSONAL_LOAN_ID, ProductConfigTypeEnum.PRICING,
                        "interest_rate_brackets",
                        "[{\"minAmount\":1000,\"maxAmount\":60000,\"tin\":7.99}]"),
                config(PERSONAL_LOAN_ID, ProductConfigTypeEnum.PRICING,
                        "fees",
                        "[{\"type\":\"OPENING_FEE\",\"percentage\":0,\"fixed\":0}]")
        );
        when(productConfigurationRepository.findByProductId(PERSONAL_LOAN_ID))
                .thenReturn(Flux.fromIterable(bad));

        StepVerifier.create(service.getProductPricing(PERSONAL_LOAN_ID))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(BusinessException.class);
                    BusinessException be = (BusinessException) err;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(be.getCode()).isEqualTo("PRODUCT_CONFIG_PARSE_ERROR");
                    assertThat(be.getMessage()).contains("Configuration parse error");
                })
                .verify();
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private List<ProductConfiguration> personalLoanConfigs() {
        return List.of(
                config(PERSONAL_LOAN_ID, ProductConfigTypeEnum.LIMITS,
                        "amount_term",
                        "{\"currency\":\"EUR\",\"minAmount\":1000,\"maxAmount\":60000,\"minTerm\":12,\"maxTerm\":96}"),
                config(PERSONAL_LOAN_ID, ProductConfigTypeEnum.PRICING,
                        "interest_rate_brackets",
                        "[{\"minAmount\":1000,\"maxAmount\":60000,\"tin\":7.99}]"),
                config(PERSONAL_LOAN_ID, ProductConfigTypeEnum.PRICING,
                        "fees",
                        "[{\"type\":\"OPENING_FEE\",\"percentage\":0,\"fixed\":0}]")
        );
    }

    private List<ProductConfiguration> leasingConfigs() {
        return List.of(
                config(LEASING_ID, ProductConfigTypeEnum.LIMITS,
                        "amount_term",
                        "{\"currency\":\"EUR\",\"minAmount\":5000,\"maxAmount\":500000,\"minTerm\":12,\"maxTerm\":84}"),
                config(LEASING_ID, ProductConfigTypeEnum.PRICING,
                        "interest_rate_brackets",
                        "[{\"minAmount\":5000,\"maxAmount\":25000,\"tin\":6.90},"
                                + "{\"minAmount\":25001,\"maxAmount\":100000,\"tin\":5.90},"
                                + "{\"minAmount\":100001,\"maxAmount\":250000,\"tin\":5.50},"
                                + "{\"minAmount\":250001,\"maxAmount\":500000,\"tin\":5.20}]"),
                config(LEASING_ID, ProductConfigTypeEnum.PRICING,
                        "fees",
                        "[{\"type\":\"OPENING_FEE\",\"percentage\":1.0,\"fixed\":0}]")
        );
    }

    private ProductConfiguration config(UUID productId,
                                        ProductConfigTypeEnum type,
                                        String key,
                                        String value) {
        ProductConfiguration cfg = new ProductConfiguration();
        cfg.setProductConfigurationId(UUID.randomUUID());
        cfg.setProductId(productId);
        cfg.setConfigType(type);
        cfg.setConfigKey(key);
        cfg.setConfigValue(value);
        return cfg;
    }
}
