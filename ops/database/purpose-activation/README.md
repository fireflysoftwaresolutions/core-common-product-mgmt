# Staged product purpose migrations

V22 and V23 are preserved source candidates outside the runtime classpath. Default Flyway discovery remains `classpath:db/migration`, with the exact published V1-V21 file names and bytes. Neither the insurance nor deposit feature flag changes migration discovery. These staged files are not installed or automatically activated by this change.

The files retain their original SQL bytes, including historical source-candidate comments. `source-manifest.json` records their predecessor paths, hashes, function declarations, and source-only scope. Moving the files does not qualify their grammar, catalogues, privileges, installation, or native behavior.

| Input | Required state before a future reviewed installation |
| --- | --- |
| V1-V19 | Exact original history and aggregate schema. No repair, baseline, or substituted checksum history. |
| V20 | Exact lifecycle/native command schema, protected guard and command capabilities, original finite admission, function identities, and original profile constraint. The published migration requires its own reviewed privileged migration step. |
| V21 | Separate prepared metadata owner map, publication-reader role, exact restricted installer graph, public schema privileges, and protected publication read functions. V22/V23 do not replace this preparation. |
| V22 | Original V1-V21 plus `firefly_product_definition_insurance_quote_read`, protected guard, exact twelve-relation owner/RLS map, private native command context and original authority-profile constraint, and the exact restricted installer predicates in its preflight. The administrator-owned insurance reader bootstrap is a separate prerequisite. |
| V23 | Original V1-V22 plus `firefly_product_definition_deposit_enrollment_read`, the same protected owner/installer prerequisites, and the exact V22 authority-profile constraint checked through the database deparser. Its separate reader-role bootstrap creates no login or business admission. |

A missing new reader role causes a preflight refusal; it must not break default application startup merely because a disabled feature's source was added. This is why these unpublished extensions are staged here instead of the default migration location. The preexisting V20/V21 deployment prerequisites and complete default startup still require independent qualification.

No optional migration history, installer launcher, or activation configuration is introduced. A future native gate must freeze the exact source, original history, role/owner catalogues, separate installer identity, function bodies, granted privileges, ConnectionFactory/transaction graph, original deadlines, rollback/replay behavior, and cleanup before executing either file. Do not copy these files into a runtime migration directory or execute the role bootstraps based on this document alone.

## Deposit publication preparation candidate

V24 is an additional staged source prerequisite after V22/V23. It creates a protected, immutable native preparation admission, additive SELECT policies, a security-barrier cost identity/digest projection, and restrictive business-write policies while that admission exists in the transaction. It adds no reader login, role membership, business-write grant, default migration, feature activation, or publication authority. Existing V1–V23 SQL bytes are preserved.

The internal preparation service requires the caller's original budget, exact parsed PUBLISH intent, stable allocated publication resources, and current publisher authorization. Its physical transaction is **read-write and repeatable-read** because native admission metadata is inserted. Business reads use Spring Data projections; private cost terms are not selected or returned. The context stores separate actor, permit, original-request, and native time bounds and checks the earliest boundary again through a deferred commit trigger. Its GUC contains only a lookup ID; authority comes from the protected row's exact request and current transaction/backend/login binding.

The approved-selection join is declared in a security-barrier, security-invoker snapshot view. Its read-only mapped entity is queried through a derived Spring Data finder with exact tenant/root/review/public-version/cost-version predicates. The view uses the existing command-role RLS and safe cost identity view; its owner does not supply additional privileges to the snapshot reader.

This source has controlled Java tests only. PostgreSQL grammar, installation/catalogue/privilege behavior, native expiry/forgery/deferred-commit behavior, and cleanup require a separate reviewed gate. A command session can run `SET CONSTRAINTS preparation_finite_commit IMMEDIATE`, fire the deferred check while live, then wait before committing. The inherited V20 lifecycle uses the same deferrable-trigger pattern. The owned R2DBC budget bounds its controlled client flow; it does not establish protection against adversarial raw-session commit after expiry. That native acceptance requirement remains unresolved and requires a separate design and qualification; it has not been narrowed to the client-only guarantee.

The unconditional deposit PUBLISH refusal remains: second-pass native snapshot/CAS and owner-deadline binding, the genuine Masters SDK adapter, and actual integration are still missing. A prepared snapshot or a historical native admission does not establish current document authority or operational product admission.
