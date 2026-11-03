# Current owner references for property insurance quotes

The insurance quote configuration read requires live observations from the owners of its currency policy, premium taxes, coverage wording, exclusion wording, contract issuer, template, and layout. Here, “current” means that the owning service checks the selected publication and its current sources and permissions now. A historical GET, a digest, a configured route, or a successful previous response does not establish current usability.

Enable `product.definitions.insurance-quote-read.references.enabled` explicitly. The default is disabled. Enabling the insurance quote read while its required resolver is absent fails startup; missing authorities never produce a positive fallback. The resolver uses the controller-produced, original-generator Java SDKs of Configuration and Masters. It does not use copied client models, handwritten OpenAPI, direct owner database queries, or an owner’s implementation as a production client.

## Deployment inputs

Under `product.definitions.insurance-quote-read.references`, configure:

| Property | Required meaning |
| --- | --- |
| `masters-endpoint` | Exact trusted HTTPS Masters origin with no credentials, path, query, or fragment. |
| `masters-registration-id` | An explicitly authorized Spring client-credentials registration for the required current Masters observations. |
| `configuration-endpoint` | Exact trusted HTTPS Configuration origin. |
| `configuration-registration-id` | An explicitly authorized client-credentials registration for current document issuer observations. |
| `currencies[].reference` | All six exact Product currency policy fields: policy ID, currency ID, ISO code, numeric version, digest, and encoding version. |
| `currencies[].posted-scale` | Exact posted representation scale to request from Masters. It is checked against the owner’s policy and active currency source; it is not guessed from the ISO code. |
| `insurance[].reference` | Exact Product reference owner, kind, record ID, numeric version, and digest. |
| `insurance[].publication-id` | The selected actual immutable Masters publication UUID. |
| `insurance[].generation` | The exact expected current Masters control generation. |

Configuration properties bind these values through the existing Spring configuration mechanism. Supply registration credentials through the deployment secret mechanism. Each owner independently authenticates the configured machine identity and evaluates its own current policy. No incoming caller bearer is forwarded, and callers cannot select a registration, endpoint, route, publication generation, or credential.

Read the actual owner publication and control receipts when preparing the selectors. Product’s exact insurance reference intentionally does not contain an owner publication ID or current control generation. Those inputs select a candidate for a real owner check; the resolver never infers them from an ID, digest, version text, or cached record. A newly published generation requires an explicit correct selection and a successful new owner observation. Multiple currency and insurance selections are supported; duplicate or invalid selectors are refused.

Numeric insurance/issuer versions remain numeric. Document template/layout versions remain exact opaque strings, including case. The current Configuration issuer API uses a numeric immutable publication revision; the Masters template/layout API uses opaque versions. An incompatible Product reference is refused, never coerced or normalized into the other contract.

Opaque platform tenant mapping is separately described in [Current tenant mapping](insurance-quote-current-tenant-binding.md). It preserves the original principal and does not authorize any catalog or document reference. Owner machine registrations must be authorized for the appropriate real resource namespace; a UUID-shaped opaque platform tenant is not a substitute for a binding.

## Verification and business consistency

The resolver validates the canonical Product configuration before contacting owners. It rejects an ambient SQL transaction, retains the original native caller and typed expiry, and uses one five-second paired wall/monotonic budget. It checks that the caller is unchanged around owner I/O. No policy decision or token acquisition renews that budget.

Every required reference is observed through its genuine typed current API, then observed again. A missing response, owner error, changed publication/control/source authority, changed principal, denied owner permission, expired window, redirect, or unexpected owner origin withholds the configuration. The resulting lifetime is the intersection of the original budget, caller expiry, and all owner observations. It is a technical observation lifetime, not the validity period of a business quote or insurance policy.

Currency checks bind the complete expected policy identity, version, encoding, digest, canonical definition, source digest, active currency representation, scale, publication lifecycle, effectivity, and current timing. Insurance checks bind kind, record, immutable publication, revision, generation, active control, complete canonical definition and provenance. Applicability must include the Product jurisdiction, profile, and new business event. Each configured tax amount must equal the actual current premium tax rate applied to the configured base premium using the owner’s rounding mode, posted scale, and exact currency policy.

Every configured contract requirement uses the independent `INSURANCE_TERM_PC_QUOTE` owner purpose. Issuer checks bind the tenant, publication, revision, complete issuer definition digest, source generations, active tenant/branding, requested document type and locale, and finite native/effective windows. Every source/presentation field participates in the owner’s existing token digest protocol. Template/layout checks bind the complete Product publication scope and public definition digest, exact opaque versions and IDs, template source digest and locale, complete layout digest, profile metadata, legal notice metadata, and current owner timing.

The reference read returns configuration facts only. It does not issue a policy, admit an applicant, authorize money movement, render or fetch document assets, sign a contract, or claim legal/financial acceptance. Those steps remain separate domain journeys with their own owners and authorities. The current owner APIs retain responsibility for their native stored publication/source graphs and before-response permissions.

## Source validation boundaries

The maintained resolver tests execute the actual generated SDK methods and serialize their actual requests against controlled HTTP and OAuth transports. Fixtures use the actual pure owner codecs to catch canonical mismatches; owner implementation dependencies are test-scoped only. These checks are not running owner Mains, JWT/JWKS verification, native Product persistence, provider acceptance, or a completed Experience → Domain → Core journey. Fresh hosted source generation and consumer compilation must use the actual public owner source and unchanged official generator, without an installed or cached owner SDK substitution. Native and full journey proofs remain separate.
