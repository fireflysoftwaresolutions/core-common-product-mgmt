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
