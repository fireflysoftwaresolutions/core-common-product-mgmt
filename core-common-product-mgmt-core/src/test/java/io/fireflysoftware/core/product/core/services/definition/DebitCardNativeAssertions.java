package io.fireflysoftware.core.product.core.services.definition;

import static org.assertj.core.api.Assertions.*;
import static io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCommand.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import org.springframework.web.server.ResponseStatusException;

/** Shared assertions for original Testcontainers and separately owned local PostgreSQL. */
final class DebitCardNativeAssertions {
    private static final Duration WAIT = Duration.ofSeconds(10);
    private DebitCardNativeAssertions() {}

    static void lifecycle(ProductDefinitionCommandDatabaseFixture.Settings settings,
            UUID category, Connection owner) throws Exception {
        UUID tenant = UUID.randomUUID(), root = UUID.randomUUID();
        var create = DebitCardLifecycleProfileTest.body(CREATE_DRAFT);
        create.put("categoryId", category.toString());
        ((com.fasterxml.jackson.databind.node.ObjectNode) create.at("/definition/common"))
                .put("code", "DEBIT_CARD_" + root.toString().replace("-", "").toUpperCase(Locale.ROOT));
        try (var insurance = new ProductDefinitionCommandDatabaseFixture(settings, GovernedDefinitionProfile.DEBIT_CARD)) {
            assertThatThrownBy(() -> insurance.executeAs(tenant, root, CREATE_DRAFT, create.toString(),
                    "maker", Set.of("product:definitions:write")).block(WAIT))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
            assertThat(count(owner, root, "product_definition_action")).isZero();
            var created = (PublicReceipt) insurance.executeAs(tenant, root, CREATE_DRAFT, create.toString(),
                    "maker", CREATE_DRAFT.requiredScopes()).block(WAIT);
            assertThat(created).isNotNull();
            assertThat(insurance.currentCost(tenant, root).block(WAIT)).isNotNull();
            for (var wrongProfile : List.of(GovernedDefinitionProfile.ACCOUNT_TARIFF, GovernedDefinitionProfile.TERM_PC_INSURANCE)) {
              try (var banking = new ProductDefinitionCommandDatabaseFixture(settings, wrongProfile)) {
                assertThatThrownBy(() -> banking.currentCost(tenant, root).block(WAIT))
                        .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
            }
            }
            assertWrongNativeReplayProfile(settings, owner, tenant, root);
            var submitted = (PublicReceipt) execute(insurance, tenant, root, SUBMIT_PUBLIC,
                    Map.of("operationId", UUID.randomUUID(), "expectedPublicVersion", created.publicVersion(),
                            "expectedPublicHead", created.revisionId()), "maker");
            var view = insurance.selection(tenant, root, "checker").block(WAIT);
            assertThat(view).isNotNull();
            assertThat(view.selection().publicVersion()).isEqualTo(submitted.publicVersion());
            var selection = view.selection();
            var approval = Map.of("operationId", UUID.randomUUID(), "submissionId", selection.submissionId(),
                    "revisionId", selection.revisionId(), "publicDigest", selection.publicDigest(),
                    "publicVersion", selection.publicVersion(), "costRevisionId", selection.costRevisionId(),
                    "costDigest", selection.costDigest(), "costVersion", selection.costVersion(),
                    "selectionFingerprint", view.selectionFingerprint());
            assertThatThrownBy(() -> execute(insurance, tenant, root, APPROVE_SELECTION, approval, "maker"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
            assertThat(count(owner, root, "product_definition_action")).isEqualTo(2);
            var approved = (PrivateReceipt) execute(insurance, tenant, root, APPROVE_SELECTION, approval, "checker");
            assertThat(approved.decision()).isEqualTo("APPROVED");
            var published = (PublicReceipt) execute(insurance, tenant, root, PUBLISH,
                    Map.of("operationId", UUID.randomUUID(), "reviewId", approved.reviewId(),
                            "expectedPublicVersion", selection.publicVersion(), "expectedCostVersion", selection.costVersion(),
                            "versionNumber", 1), "checker");
            assertThat(published.operationalAdmission()).isEqualTo("NOT_ADMITTED");
            assertThat(published.publicationId()).isNotNull();
            assertThat(count(owner, root, "product_definition_action")).isEqualTo(4);
            assertThat(count(owner, root, "product_definition_audit")).isEqualTo(4);
            assertThat(count(owner, root, "product_definition_publication")).isEqualTo(1);
            try (var query = owner.prepareStatement("SELECT a.command,a.actor_issuer,a.actor_id,u.actor_issuer,u.actor_id FROM public.product_definition_action a JOIN public.product_definition_audit u USING(tenant_id,root_id,action_id) WHERE a.tenant_id=? AND a.root_id=?")) {
                query.setObject(1, tenant); query.setObject(2, root);
                int rows = 0;
                try (var row = query.executeQuery()) {
                    while (row.next()) {
                        String expected = Set.of("CREATE_DRAFT", "SUBMIT_PUBLIC").contains(row.getString(1)) ? "maker" : "checker";
                        assertThat(row.getString(2)).isEqualTo("https://owned-native-staff.example.test");
                        assertThat(row.getString(3)).isEqualTo(expected);
                        assertThat(row.getString(4)).isEqualTo(row.getString(2));
                        assertThat(row.getString(5)).isEqualTo(expected); rows++;
                    }
                }
                assertThat(rows).isEqualTo(4);
            }
        }
    }

    private static Receipt execute(ProductDefinitionCommandDatabaseFixture fixture, UUID tenant, UUID root,
            ProductDefinitionCommand command, Map<String, ?> body, String subject) {
        return fixture.executeAs(tenant, root, command, DefinitionCanonical.encode(DefinitionCanonical.tree(body)),
                subject, command.requiredScopes()).block(WAIT);
    }

    private static long count(Connection owner, UUID root, String table) throws SQLException {
        if (!Set.of("product_definition_action", "product_definition_audit", "product_definition_publication").contains(table))
            throw new IllegalArgumentException();
        try (var query = owner.prepareStatement("SELECT count(*) FROM public." + table + " WHERE root_id=?")) {
            query.setObject(1, root); try (var row = query.executeQuery()) { row.next(); return row.getLong(1); }
        }
    }

    private static void assertWrongNativeReplayProfile(ProductDefinitionCommandDatabaseFixture.Settings settings,
            Connection owner, UUID tenant, UUID root) throws Exception {
        try (var original = owner.prepareStatement("SELECT operation_id,original_intent,original_intent_digest,prepared_plan FROM firefly_product_definition_private.operation_claim WHERE tenant_id=? AND root_id=?")) {
            original.setObject(1, tenant); original.setObject(2, root);
            try (var row = original.executeQuery()) {
                assertThat(row.next()).isTrue();
                try (var command = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:" + settings.port() + "/" + settings.database(), settings.commandUser(), settings.commandPassword());
                        var query = command.prepareStatement("SELECT * FROM public.begin_product_definition_command(?,?,?,'CREATE_DRAFT','https://owned-native-staff.example.test','maker',?,?,'product-definition-human-staff-v1',?,?,?)")) {
                    command.setAutoCommit(false);
                    query.setObject(1, tenant); query.setObject(2, root); query.setObject(3, row.getObject(1));
                    long until = System.currentTimeMillis() + 30000;
                    query.setLong(4, until); query.setLong(5, until);
                    query.setString(6, row.getString(2)); query.setString(7, row.getString(3)); query.setString(8, row.getString(4));
                    assertThatThrownBy(query::execute).isInstanceOfSatisfying(SQLException.class,
                            error -> assertThat(error.getSQLState()).isEqualTo("PD403"));
                    command.rollback();
                }
            }
        }
        assertThat(count(owner, root, "product_definition_action")).isEqualTo(1);
    }
}
