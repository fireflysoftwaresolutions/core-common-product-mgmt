# Current tenant mapping for insurance quote product reads

An authenticated platform tenant is an opaque namespace. It is not a Configuration resource tenant UUID. Product insurance quote reads can explicitly opt in to the Configuration owner’s current binding. The legacy constructor and default configuration retain the existing UUID tenant contract.

Set these properties only after qualifying the Configuration owner and its generated SDK:

| Property under `product.definitions.insurance-quote-read` | Meaning |
| --- | --- |
| `current-tenant-binding-enabled` | Defaults to false. True requires the real resolver and an OAuth authorized client manager. |
| `current-tenant-binding-endpoint` | Exact trusted HTTPS origin of Configuration, without a path, credentials, query, or fragment. |
| `current-tenant-binding-registration-id` | Operator-configured Spring OAuth client registration using client credentials. |
| `current-tenant-binding-issuer` | Exact issuer of the incoming platform namespace. |
| `current-tenant-binding-platform-tenant` | Exact opaque namespace served by the configured machine registration. |

Configure the registration, token provider, credentials, and authorized client manager through Spring’s normal OAuth client configuration. Supply secrets through the deployment secret mechanism. The adapter selects only this configured registration; callers cannot select a registration, authority, namespace, origin, or token. The Configuration owner independently validates the machine token and current policy. Its current response must name the exact expected issuer, opaque platform tenant, and requested resource UUID.

The adapter calls the genuine generated `TenantResourceBindingCurrentApi.observeCurrentTenantResourceBinding()` operation. It uses its own machine credential, not the incoming caller’s bearer token. The current lookup does not attest a human or grant a Product permission. Product keeps the original native principal, including issuer, subject, client, audience, scopes, tenant claim, and typed NBF/expiry. The Product policy still receives that original principal and the complete Product publication resource. There is no claim rewriting and no UUID-shaped namespace shortcut.

Product carries the exact binding ID, version, digest, control version, tenant generation, eligibility status ID/generation, resource UUID, and business expiry. It observes the same current authority again after the owner work and before returning the quote configuration. A replaced or revoked binding, changed caller or namespace, expired observation, missing Product scope, changed client, denied policy, redirect, unavailable owner, or exhausted original wall/monotonic budget withholds the response. Neither an owner response nor a recheck renews the original five-second operation budget.

This mapping does not resolve currency policies, premium taxes, wording, exclusions, issuer publications, template/layout references, policy issuance, signatures, or financial permissions. Those owners and purpose-specific checks remain independently required. Enabling mapping does not enable the insurance quote read feature or satisfy its reference resolver requirement.

The maintained core tests use controlled owner observations. The adapter tests exercise the actual generated Java API against controlled HTTP and OAuth transports. These checks must not be reported as token verification, a native Configuration owner, a running Product Main, a provider acceptance, or a completed insurance journey. The current original source producer and unchanged generator must run in a fresh hosted consumer build before adoption; no cached owner SDK JAR is an authority or a replacement producer.
