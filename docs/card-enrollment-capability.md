# Card enrollment capability contracts

The product owner needs an explicit CARD_ENROLLMENT purpose before a card journey can use a product definition. The pure contracts in this checkpoint compare supplied intent, selection, and evidence. Their only capability results are NOT_OFFERED and UNKNOWN. A governed CARD profile and an authenticated owner producer are still required before product eligibility can support enrollment.

An ACTIVE product, a FINANCIAL product type, a managed account, or a generic ProductDTO does not establish that capability. The current governed profile decoder supports account tariffs, fixed-rate installment loans, and term P&C insurance; it does not support CARD. Existing account-tariff metadata and native boundaries cannot be reused as card admission by changing a profile label.

## Purpose and policy

The [contracts](../core-common-product-mgmt-interfaces/src/main/java/io/fireflysoftware/core/product/interfaces/dtos/cardeligibility/CardEnrollmentCapabilityContracts.java) fix the purpose to CARD_ENROLLMENT. The policy is a supplied internal input to the pure rules; this checkpoint does not add a configuration endpoint or persist a policy.

| Policy and available facts | Assessment |
| --- | --- |
| Policy absent or NOT_OFFERED | NOT_OFFERED. Supplied evidence cannot override the default. |
| EVIDENCE_REQUIRED with missing, stale, incomplete, or mismatched facts | UNKNOWN. Current product capability has not been established. |
| EVIDENCE_REQUIRED with matching claimed CARD facts | UNKNOWN. Governed CARD and the actual owner producer are unsupported. |

EVIDENCE_REQUIRED requires evidence; it does not grant a capability. There is no OFFERED result in this contract. UNKNOWN must leave enrollment unavailable, without creating an instrument or substituting an eligible response. NOT_OFFERED expresses the policy restriction; UNKNOWN expresses the inability to establish the requested capability.

## Intent, server selection, and supplied facts

The [strict codec](../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/cardeligibility/CardEnrollmentCapabilityCodec.java) accepts only an action ID, expected product revision, bounded uppercase reason code, and fixed acceptance deadline. It rejects unknown, duplicate, missing, null, coerced, or malformed fields, noncanonical identifiers, trailing JSON documents, invalid UTF-8, and oversized input. Scope, policy, status, and evidence are not request fields. No HTTP route or generated SDK operation is introduced by this codec.

The server selection identifies the tenant, institution, bank, product, definition, publication, product revision, and publication-control revision. The [comparison rules](../core-common-product-mgmt-core/src/main/java/io/fireflysoftware/core/product/core/services/cardeligibility/CardEnrollmentCapabilityRules.java) retain that selection and the original request in the assessment. They compare supplied evidence against the exact selection, purpose, owner label, observation interval, and receipt identifier. An expired request or different product revision cannot gain a renewed deadline through evaluation.

These inputs are internal supplied facts. A matching owner string, receipt UUID, revision, or timestamp is not proof that an authenticated owner produced it, that a database committed it, or that the selected bank, party, contract, or account is currently eligible. The assessment is a pure comparison result, not a durable receipt or native admission. Tests of those comparisons do not establish authentication, persistence, provider acceptance, or financial authority.

## Prerequisites for a usable CARD capability

The next governed CARD work must define and validate actual product terms before enabling a positive owner purpose. Its scope includes:

* Explicit pricing and fees, currency policy, occurrences, refund treatment, and required references. Missing price rules must not silently become free. The initial inactive debit profile and any free-only pricing restriction require an explicit governed decision.
* Separate private cost terms, payee and allocation references, cost revisions, and independent review. Public metadata and receipts must exclude private costs and review reasons. A NONE cost decision must be explicit rather than an assumed zero cost.
* Actual fact, document, mandate, program, and dependency requirements from their owning services. Reference syntax alone does not establish existence, approval, or current eligibility.
* A coherent lifecycle for authoring, independent public and cost review, publication, current publication control, and withdrawal. Accepting a new Java profile alone does not implement that lifecycle.
* Native CARD admission and read support with exact scope, roles, privileges, current revisions, completed transactions, and original finite deadlines. Existing migration and account-tariff contracts require deliberate compatibility review.
* An authenticated product-owner purpose producer, together with current institution and bank authority and the actual account, contract, party, and product linkage. Missing owner purposes must remain unavailable; they cannot be replaced by ACTIVE flags or supplied eligible facts.

Any later service/API boundary must come from the actual owner implementation and its genuine generated SDK. The current pure types do not supply that integration.

## Inactive enrollment and downstream obligations

The proposed first card scope is inactive, restricted provisioning. Even a future successful eligibility observation and inactive enrollment would not activate the card, authorize spending, reserve funds, post fees, or complete issuer-network acceptance. Credit, overdraft, cash advance, FX, and token-wallet capabilities require their own explicit supported contracts.

Account admission must durably reserve the pending enrollment relationship before provider effects and share the account's closure fence. Uncertain provider outcomes require durable recovery and observation of the original operation before retry. Expiry, withdrawal, or a missing response must not erase that relationship or release a restriction. Cancellation, provider confirmation, and card status do not prove complete liability coverage or final account closure.

The [governed definition guide](governed-product-definitions.md) and [metadata read guide](governed-product-definition-reads.md) describe the existing foundation and its separate integration limits.
