package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.Parsed;

import org.junit.jupiter.api.Test;

import java.util.*;

class ProductDefinitionCommandPlanTest {
    final UUID tenant = UUID.randomUUID(), root = UUID.randomUUID(), operation = UUID.randomUUID();

    ObjectNode create() throws Exception {
        var definition =
                (ObjectNode)
                        DefinitionCanonical.parse(
                                new String(
                                        Objects.requireNonNull(
                                                        getClass()
                                                                .getResourceAsStream(
                                                                        "/definition/v1/account.json"))
                                                .readAllBytes(),
                                        java.nio.charset.StandardCharsets.UTF_8));
        var fact = (ObjectNode) definition.at("/common/requirements/facts/0");
        fact.put("kind", "DECIMAL_RATIO");
        fact.put("operator", "EQUALS");
        fact.set("values", DefinitionCanonical.tree(List.of("0.200")));
        return (ObjectNode)
                DefinitionCanonical.tree(
                        Map.of(
                                "operationId",
                                operation,
                                "productId",
                                UUID.randomUUID(),
                                "categoryId",
                                UUID.randomUUID(),
                                "definition",
                                definition,
                                "cost",
                                Map.of(
                                        "schemaVersion",
                                        1,
                                        "businessArea",
                                        "BANKING",
                                        "profileCode",
                                        "ACCOUNT_TARIFF_V1",
                                        "mode",
                                        "NONE",
                                        "lines",
                                        List.of())));
    }

    Parsed parse(ProductDefinitionCommand command, ObjectNode body) {
        return ProductDefinitionInstructionCodec.parse(tenant, root, command, body.toString());
    }

    String encode(Parsed parsed, ProductDefinitionCommandResources ids) {
        try {
            var type =
                    Class.forName(
                            "io.fireflysoftware.core.product.core.services.definition.impl.ProductDefinitionCommandPlan");
            return (String)
                    type.getDeclaredMethod(
                                    "encode", Parsed.class, ProductDefinitionCommandResources.class)
                            .invoke(null, parsed, ids);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException error) throw error;
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException missing) {
            throw new AssertionError("Missing server-only native prepared plan", missing);
        }
    }

    @Test
    void publicNormalizationIsBoundSeparatelyFromRetainedFullOriginalIntent() throws Exception {
        var body = create();
        var parsed = parse(ProductDefinitionCommand.CREATE_DRAFT, body);
        var encoded =
                encode(
                        parsed,
                        ProductDefinitionCommandResources.fresh(
                                ProductDefinitionCommand.CREATE_DRAFT));
        var plan = DefinitionCanonical.parse(encoded);
        assertThat(plan.path("encoding").asText()).isEqualTo("product-definition-native-plan-v1");
        assertThat(plan.path("validationBoundary").asText()).isEqualTo("TRUSTED_SERVICE");
        assertThat(plan.path("originalIntentDigest").asText()).isEqualTo(parsed.intentDigest());
        assertThat(parsed.canonicalIntent()).contains("0.200");
        var snapshot = plan.get("publicSnapshot");
        String canonical = snapshot.get("canonical").asText();
        assertThat(canonical).contains("0.2").doesNotContain("0.200");
        assertThat(snapshot.get("digest").asText())
                .isEqualTo(DefinitionCanonical.hash("product-definition-public-v1", canonical));
        assertThat(
                        plan.at("/preparedBody/definition/common/requirements/facts/0/values/0")
                                .asText())
                .isEqualTo("0.2");
    }

    @Test
    void privateCostNormalizationAndDigestArePrivatePlanValuesWithoutChangingOriginalBytes()
            throws Exception {
        var body = create();
        var payee =
                Map.of(
                        "owner",
                        "core-common-customer-mgmt",
                        "kind",
                        "PARTY",
                        "id",
                        UUID.randomUUID().toString(),
                        "version",
                        1,
                        "digest",
                        "a".repeat(64));
        var allocation =
                Map.of(
                        "owner",
                        "core-common-accounting-gl",
                        "kind",
                        "GL_ALLOCATION",
                        "id",
                        UUID.randomUUID().toString(),
                        "version",
                        1,
                        "digest",
                        "b".repeat(64));
        body.set(
                "cost",
                DefinitionCanonical.tree(
                        Map.of(
                                "schemaVersion",
                                1,
                                "businessArea",
                                "BANKING",
                                "profileCode",
                                "ACCOUNT_TARIFF_V1",
                                "mode",
                                "DEFINED",
                                "lines",
                                List.of(
                                        Map.of(
                                                "code",
                                                "REVENUE_SHARE",
                                                "kind",
                                                "RATIO",
                                                "ratio",
                                                "0.200",
                                                "basis",
                                                "TARIFF_REVENUE",
                                                "occurrence",
                                                "ON_CREATION",
                                                "payee",
                                                payee,
                                                "allocation",
                                                allocation)))));
        var parsed = parse(ProductDefinitionCommand.CREATE_DRAFT, body);
        var plan =
                DefinitionCanonical.parse(
                        encode(
                                parsed,
                                ProductDefinitionCommandResources.fresh(
                                        ProductDefinitionCommand.CREATE_DRAFT)));
        var snapshot = plan.get("privateSnapshot");
        String canonical = snapshot.get("canonical").asText();
        assertThat(canonical).contains("\"ratio\":\"0.2\"").doesNotContain("0.200");
        assertThat(snapshot.get("digest").asText())
                .isEqualTo(
                        DefinitionCanonical.hash("product-definition-private-cost-v1", canonical));
        assertThat(parsed.canonicalIntent()).contains("0.200");
    }

    @Test
    void commandsWithoutNewSnapshotsDoNotPretendToHavePreparedPublicOrPrivateBytes() {
        var body =
                (ObjectNode)
                        DefinitionCanonical.tree(
                                Map.of(
                                        "operationId",
                                        operation,
                                        "expectedPublicVersion",
                                        2,
                                        "expectedPublicHead",
                                        UUID.randomUUID()));
        var parsed = parse(ProductDefinitionCommand.SUBMIT_PUBLIC, body);
        var ids = ProductDefinitionCommandResources.fresh(ProductDefinitionCommand.SUBMIT_PUBLIC);
        var plan = DefinitionCanonical.parse(encode(parsed, ids));
        assertThat(plan.get("publicSnapshot").isNull()).isTrue();
        assertThat(plan.get("privateSnapshot").isNull()).isTrue();
        assertThat(plan.at("/resources/actionId").asText()).isEqualTo(ids.actionId().toString());
        assertThat(plan.at("/scope/rootId").asText()).isEqualTo(root.toString());
    }

    @Test
    void inventedDigestOrTypedBodyDifferentFromOriginalCannotEnterNativePlan() throws Exception {
        var parsed = parse(ProductDefinitionCommand.CREATE_DRAFT, create());
        var ids = ProductDefinitionCommandResources.fresh(ProductDefinitionCommand.CREATE_DRAFT);
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                encode(
                                        new Parsed(
                                                parsed.instruction(),
                                                parsed.canonicalIntent(),
                                                "f".repeat(64)),
                                        ids));
        var other = parse(ProductDefinitionCommand.CREATE_DRAFT, create());
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                encode(
                                        new Parsed(
                                                other.instruction(),
                                                parsed.canonicalIntent(),
                                                parsed.intentDigest()),
                                        ids));
    }

    @Test
    void missingFreshResourcesCannotBeHiddenByAValidInput() throws Exception {
        var parsed = parse(ProductDefinitionCommand.CREATE_DRAFT, create());
        var good = ProductDefinitionCommandResources.fresh(ProductDefinitionCommand.SUBMIT_PUBLIC);
        assertThatIllegalArgumentException().isThrownBy(() -> encode(parsed, good));
    }

    @Test
    void callerCannotSupplyInternalValidationBoundaryOrPlanThroughPublicBody() throws Exception {
        for (String field :
                List.of(
                        "validationBoundary",
                        "publicSnapshot",
                        "privateSnapshot",
                        "nativePlan",
                        "resources")) {
            var body = create();
            body.put(field, "TRUSTED_SERVICE");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> parse(ProductDefinitionCommand.CREATE_DRAFT, body));
        }
    }

    @Test
    void freshAttemptIdsMayDifferWhileTheImmutableOriginalIntentBindingStaysExact()
            throws Exception {
        var parsed = parse(ProductDefinitionCommand.CREATE_DRAFT, create());
        var a =
                DefinitionCanonical.parse(
                        encode(
                                parsed,
                                ProductDefinitionCommandResources.fresh(
                                        ProductDefinitionCommand.CREATE_DRAFT)));
        var b =
                DefinitionCanonical.parse(
                        encode(
                                parsed,
                                ProductDefinitionCommandResources.fresh(
                                        ProductDefinitionCommand.CREATE_DRAFT)));
        assertThat(a.get("resources")).isNotEqualTo(b.get("resources"));
        assertThat(a.get("originalIntentDigest")).isEqualTo(b.get("originalIntentDigest"));
        assertThat(a.get("publicSnapshot")).isEqualTo(b.get("publicSnapshot"));
        assertThat(a).isNotEqualTo(b);
        assertThat(a.has("recordedAt")).isFalse();
    }
}
