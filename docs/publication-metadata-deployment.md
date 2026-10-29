# Publication metadata deployment

This guide covers the staged database setup for governed product publication metadata. It does not grant operational product admission, approve a customer quote, or provide an available CARD eligibility producer. CARD enrollment capability remains `NOT_OFFERED` or `UNKNOWN`.

This reconciled source candidate requires joint qualification before deployment. Existing build, API, and database evidence covers separate configurations.

## Identities and ownership

Use distinct credentials for administration, migration, application commands, ordinary reads, and scoped publication reads. Never use the original administrator as an application identity.

| Identity | Responsibility |
| --- | --- |
| Original migration owner | Owns the original schema and performs guarded preparation before V21. |
| `firefly_product_definition_metadata_owner` | Protected non-login owner of nine existing metadata tables. |
| `firefly_product_definition_guard` | Protected owner of submission, review-decision, and version-identity tables and the native boundary. |
| Publication installer | Restricted migration login created explicitly by its bootstrap template. |
| Publication reader | Dedicated login belonging to the scoped publication-read group. |

Preparation transfers the existing `product_definition_action`, `product_definition_control`, `product_definition_history`, `product_definition_publication`, `product_definition_publication_control`, `product_definition_review`, `product_definition_review_private`, `product_definition_revision`, and `product_definition_root` tables to the metadata owner. The three V20 tables stay with the guard. Preparation checks ownership, history, dependencies, and privileges; it does not rewrite business rows or repair migration history.

## Stage the database

First use a fresh, explicitly owned PostgreSQL 17 qualification database. Verify the selected parent, framework, artifacts, and original migration checksums. The scripts are direct psql inputs with `ON_ERROR_STOP`, not SQL text to interpolate.

1. Run the exact original V1–V20 migrations with the original migration identity. Preserve every original byte and checksum. Do not apply V21 yet.
2. Run [`bootstrap-publication-reader.sql.template`](../ops/database/publication-metadata/bootstrap-publication-reader.sql.template) with a privileged setup connection and fresh reader credentials.
3. Run [`prepare-publication-metadata-owners.sql.template`](../ops/database/publication-metadata/prepare-publication-metadata-owners.sql.template) as the exact original owner. Review its reported ownership and privilege invariants.
4. Run [`bootstrap-publication-installer.sql.template`](../ops/database/publication-metadata/bootstrap-publication-installer.sql.template) with a privileged setup connection and a fresh restricted installer login.
5. Use the normal Flyway migration path with that restricted installer to apply V21. Run Flyway validation and restart validation with the same genuine application artifacts.
6. Verify scoped publication reads with the dedicated reader. Qualify read and command identities independently before enabling traffic.

Preparation requires the expected database and original owner, `current_user = session_user`, UTF-8, and the administrator contract checked by the script. It bounds lock and statement timeouts. Its `ALREADY_PREPARED` outcome permits repeated preparation before V21; it is not a post-V21 repair procedure.

Bootstrap scripts intentionally reject existing login names. Database and schema defaults must already satisfy their bounded privilege checks. They do not automatically revoke unrestricted public schema creation or database CREATE/TEMP privileges. Investigate failed preconditions rather than weakening checks or granting application superuser access.

## Administrator inputs

Supply these environment variables through the deployment secret mechanism. Use normal psql connection configuration for the administrator connection. Never place passwords in command arguments, the repository, or a SQL rendering step.

| Script | Environment inputs |
| --- | --- |
| Reader bootstrap | `PRODUCT_PUBLICATION_READ_USERNAME`, `PRODUCT_PUBLICATION_READ_PASSWORD`, `PRODUCT_PUBLICATION_READ_DATABASE` |
| Owner preparation | `PRODUCT_PUBLICATION_PREPARATION_DATABASE`, `PRODUCT_PUBLICATION_PREPARATION_OLD_OWNER` |
| Installer bootstrap | `PRODUCT_PUBLICATION_MIGRATOR_USERNAME`, `PRODUCT_PUBLICATION_MIGRATOR_PASSWORD`, `PRODUCT_PUBLICATION_MIGRATOR_DATABASE` |

Run each file with psql's file option after verifying the intended connection and inputs. Keep administrator output and Flyway validation in restricted deployment records. The [`original20-flyway-checksums.json`](../ops/database/publication-metadata/original20-flyway-checksums.json) inventory describes the guarded history contract; editing it does not authorize a different original history.

## Application configuration

Scoped publication reads require both `product.definitions.commands.enabled` and `product.definitions.publication-read.enabled`. Both remain disabled by default. Reader credentials must differ from the primary read and command usernames.

Example external configuration, with features still disabled:

```yaml
product:
  definitions:
    commands:
      enabled: false
    publication-read:
      enabled: false
      username: ${PRODUCT_PUBLICATION_READ_USERNAME:}
      password: ${PRODUCT_PUBLICATION_READ_PASSWORD:}
```

The reader reuses the primary PostgreSQL endpoint and connection options with its own credentials and pool. There is no separate publication-reader URL property. The `product-definition-publication-read` pool starts with no connections, allows at most two, and bounds acquisition and creation to ten and five seconds respectively.

Use `FLYWAY_USERNAME` and `FLYWAY_PASSWORD` for the restricted installer. The existing fallback to database credentials is not a reason to reuse runtime credentials. Preserve Flyway validation, disabled baseline-on-migrate, and disabled clean. JDBC uses `sslmode`; R2DBC uses `sslMode`. Keep the deployment's actual TLS and certificate-verification requirements.

The installer receives narrowly defined schema/history permissions and protected-role memberships. It is not a runtime identity. Protected owners remain non-login roles. V21 manages temporary schema privileges for native ownership changes and checks the final state.

## Build and API prerequisites

The repository retains the published parent coordinate and normal external-parent resolution. Qualification with adjacent parent/framework source must be an explicit build binding, not a hidden repository dependency or replacement of an existing same-version artifact. Parent packaging corrections and framework adoption require their own model, dependency, and integration proof.

Regenerate API resources and SDKs through the existing original OpenAPI producer and configured generator. Previous output is comparison evidence, not permission to hand-edit the canonical specification, SDK classes, packaged resources, or dependency artifacts.

Before deployment, qualify the combined source through the normal build, fresh API generation, SDK compilation and contract tests, followed by the staged native sequence above. Keep those outcomes separate from real JWT/PDP/HTTP authorization and customer-journey acceptance.

See [governed product definitions](governed-product-definitions.md), [governed reads](governed-product-definition-reads.md), and [CARD enrollment capability](card-enrollment-capability.md) for their contracts and limits.
