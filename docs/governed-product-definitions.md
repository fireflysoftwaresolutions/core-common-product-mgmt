# Governed product definitions and native storage

Product Management owns the product record and its governed definition. Domain services compose product behavior; finance and insurance terminals provide the staff experience. A product record or a published definition does not establish permission to originate, issue coverage, charge a fee, or move money. The governed definition foundation remains **NOT_ADMITTED** for those operations.

## Supported definition profiles

The typed rules cover account tariffs, fixed-rate installment loans, and term property-and-casualty insurance. They validate exact monetary representations and explicit profile conventions. They do not turn an arbitrary legacy configuration string into an approved customer price. See the [definition rules](../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/definition/) and [typed contracts](../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/definition/).

Keep customer-facing terms separate from internal costs. Public and cost revisions have distinct heads, counters, digests, and history. Public responses must not disclose cost data or reveal its completeness through public counters or errors. Submitting public terms and approving the combined public/private selection are different operations; the pure review rules express those boundaries. A functioning authorized lifecycle API and terminal wizard are still required to expose them.

## Storage and deployment prerequisites

[Migration V18](../core-common-product-mgmt-models/src/main/resources/db/migration/V18__Create_governed_product_definitions.sql) adds the aggregate root, immutable public/cost revisions, review and publication records, action/history/audit records, and separate outbox records. It enables and forces row-level security on the native relations and deliberately installs no runtime allow policies. Database migration is not permission to access the aggregate.

A deployment administrator must provision the dedicated `firefly_product_definition_guard` role before applying V18. The migration requires that role to have NOLOGIN, no superuser privilege, and no BYPASSRLS privilege; otherwise it fails with `PRODUCT_DEFINITION_GUARD_ROLE_REQUIRED`. It does not silently create an elevated runtime principal. The private schema and its minimal identity links belong to that role. Trigger functions use qualified relations, a fixed search path, and no PUBLIC execute grant. Review actual grants in the migration rather than granting the service private-schema ownership.

Use separate migration and application credentials supplied through deployment configuration. Test migrations only on a disposable database first. Do not connect a test initializer to a shared database or add broad allow policies to get past unavailable native application functionality.

## Product identity and legacy compatibility

A native root binds a genuinely new Product inserted in the same transaction with the exact tenant/root/category scope. Existing committed Products cannot be silently adopted. A skipped INSERT or a later UPDATE is not evidence of a new Product birth. Root identity and immutable revision content cannot be reassigned after enrollment.

Ordinary unmanaged Product and child record operations retain their existing boundaries. Once a Product is enrolled, legacy writes cannot append or reparent feature, configuration, documentation, localization, relationship, or version records around native governance. Relationship checks cover both endpoints and the old and new parent selections. Category locks follow the ancestor-to-leaf order used by native creation and category cascades.

The legacy child guard runs before each write and again as a deferred row constraint. The final check occurs after immediate foreign-key synchronization: a child that began while enrollment was uncommitted must not append after enrollment commits. If a deferred check rejects, the whole caller transaction fails; an earlier successful statement is not a successful commit. Applications must propagate the failed commit and reread authoritative state before deciding how to retry. Do not catch the database rejection and report successful product configuration.

## Local verification and diagnosis

The maintained database suite uses private PostgreSQL infrastructure and restricted application roles. Run it from the repository root with the declared JDK, Maven dependencies, and Docker available:

```sh
mvn -B -pl core-common-product-mgmt-core -am test \
  -DskipOpenApiGen -Dtest=ProductDefinitionDatabaseTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

That selected command checks native storage; it does not exercise a new HTTP API or live provider. Run the normal repository verification separately. SDK implementations remain generated from the actual OpenAPI producer; no handwritten SDK implementation is added by this storage foundation.

On migration failure, inspect the applied Flyway version and required guard-role attributes. On `23514`, retain the operation identifiers and inspect the exact Product/root/category selection and whether a legacy path touched a governed Product. On transaction serialization or foreign-key failure, retry only after checking the current authoritative source and original operation identity. Do not delete binding/history rows, relabel an old Product as newly created, or weaken constraints to recover.

## Integration work still required

The native application store, same-connection authorization context, current tenant/issuer/resource policy, lifecycle/CAS and immutable action receipts, SQL profile-shape parity, public/private read projection, product-owner observation API, pricing execution, generated-SDK workflows, and staff wizard remain separate deliveries. Current customer LegalEntity records do not authorize a bank or insurer to issue a product. Exact issuer authority and downstream operational admission must come from their actual owners.

Customer simulation, accepted offers, contracts, account/card origination, lending, policy issuance, and financial accounting each need their own current authority and retained owner outcomes. Missing integration must be reported explicitly; it cannot become a synthetic approval.

Read-only serving for retained account-tariff metadata is described in [governed definition reads](governed-product-definition-reads.md). It does not create the authorized lifecycle or operational admission described above.
