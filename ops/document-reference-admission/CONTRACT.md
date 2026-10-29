# Deposit document reference admission

The deposit catalog remains disabled by default. A DEPOSIT_ACCOUNT_V1 PUBLISH request now refuses after current command authorization and before database acquisition. Draft, edit, cost, submission, review, withdrawal, and the existing insurance paths retain their prior behavior.

## Exact owner binding

`DepositAccountDocumentReferenceResolver` accepts a target containing UUID tenant, root, product, approved revision, and intended publication identities, the exact product definition digest, purpose `DEPOSIT_ACCOUNT_ENROLLMENT`, and the original document requirement. A successful binder response is a bounded observation of the document owners. It does not establish that the product is published or operationally admitted.

| Reference | Owner | Identity and version | Digest |
| --- | --- | --- | --- |
| DOCUMENT_ISSUER | core-common-config-mgmt | Issuer publication UUID and positive numeric revision | Issuer definition digest |
| DOCUMENT_TEMPLATE | core-common-reference-master-data | Immutable source revision UUID and exact OPAQUE versionText | Complete source snapshot sourceDigest |
| DOCUMENT_LAYOUT | core-common-reference-master-data | Immutable layout revision UUID and exact OPAQUE versionText | Layout definitionDigest |

Template and layout versions must match the actual owner version exactly. A numeric historical template/layout version is not converted into an opaque current reference. The exact tenant, product tuple, purpose, document type, locale, and three references remain bound throughout the call. Masters validates its own sources and layout, and echoes the product intent; it does not decide product authority.

Both providers receive the identical original `R2dbcOperationBudget`. The second provider additionally receives the earliest inherited absolute boundary. The binder checks the original budget before and after both calls, retains each owner's monotonic span from before its call, intersects finite native and effective boundaries, and rejects changed target echoes, expired windows, unavailable owners, and ambient SQL transactions. Both current owner contracts have a maximum five-second native/effective window. UUIDs must be canonical and nonnil; locales must match their canonical language tag exactly and cannot be undefined. Millisecond owner windows retain a conservative one-millisecond conversion margin. Locally narrowed adapter and composed response boundaries are floored to milliseconds so fractional original clocks cannot extend a cap or create an incompatible observation.

## Generated issuer SDK

The web adapter consumes `DocumentIssuerApi.observeCurrentDocumentIssuer` from the genuine original configuration producer's generated SDK. The core contracts contain no generated configuration types. The application must explicitly supply the authenticated generated client; this change supplies no token, trust context, default provider, or success fallback.

The owning web POM declares `core-common-config-mgmt-sdk` through `document-issuer-sdk.version`. Its retained value is 26.10.6, which does not establish that the released artifact contains the new issuer API. Source qualification uses the independently retained original producer JAR with SHA-256 `e2f77cff75f63213b1162fccde4e417a375d6c1e01490e190066a6f7fbe1b5d3`. A separately qualified new release and consumer version selection remain required. Do not install or replace the released 26.10.6 artifact.

The SDK adapter validates the echoed publication, revision, digest and exact use, issuer/definition identity, active tenant and branding source, source generation, and bounded observation/definition windows. It does not use preparation, publication, or recovery receipts as current authority.

`DepositAccountTemplateReferenceProvider` is mandatory. Its new current observation transport remains unwired until the actual Masters producer emits and qualifies the endpoint and SDK. No historical resolver, metadata DTO, preview asset, or fabricated successful provider satisfies this requirement.

## Missing lifecycle integration

`ProductDefinitionLifecycleServiceImpl.executeBounded` is the refusal boundary. The existing `PublishBody` contains review identity and expected public/cost versions. `publish(Frame)` loads the exact approved public and cost snapshots inside the native command transaction. Remote owner calls cannot run inside that transaction.

Positive deposit publication still requires an authorized exact snapshot pre-read, outside-SQL current owner observations, and a second native transaction that revalidates the same snapshot, approval, versions, references, current permission, and earliest original/owner deadline before committing. The generated publication identity must remain unchanged across that sequence. Late expiry and changed state must roll back the entire business/evidence/private command graph. A binder DTO alone cannot bypass the current refusal.

Actual native integration, signed owner HTTP, the new Masters SDK, layouts/assets and render/signature admission, full deposit origination, ledger/GL effects, releases, and financial acceptance remain open.

The unpublished V22/V23 purpose SQL is staged outside runtime resources under `ops/database/purpose-activation`. The original default `classpath:db/migration` V1-V21 names and bytes remain unchanged. Staging and Spring resource-discovery proof do not qualify original V20/V21 startup, optional installation, role catalogues, or native purpose activation.
