package io.fireflysoftware.core.product.core.services.definition;

import org.flywaydb.core.Flyway;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

/** Follows the repository's original20 administrator preparation and restricted forward installer. */
final class ProductPublicationMigrationFixture {
    private ProductPublicationMigrationFixture() {}

    static void migrate(PostgreSQLContainer postgres) throws Exception {
        if (!postgres.isRunning()) {
            throw new IllegalStateException("Fresh running test container required");
        }
        migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), postgres.getDatabaseName());
    }

    static void migrate(String jdbcUrl, String owner, String ownerPassword, String database) throws Exception {
        Flyway.configure()
                .dataSource(jdbcUrl, owner, ownerPassword)
                .locations("classpath:db/migration")
                .target("20")
                .load()
                .migrate();
        String installer = "definition_publication_migrator";
        String password = UUID.randomUUID().toString();
        try (var connection = DriverManager.getConnection(
                jdbcUrl, owner, ownerPassword);
                var statement = connection.createStatement()) {
            try (var identity = statement.executeQuery("SELECT current_user,session_user,current_database()")) {
                if (!identity.next() || !owner.equals(identity.getString(1))
                        || !owner.equals(identity.getString(2))
                        || !database.equals(identity.getString(3))) {
                    throw new IllegalStateException("Exact fresh test administrator required");
                }
            }
            statement.execute("CREATE ROLE firefly_product_definition_publication_read NOLOGIN NOINHERIT"
                    + " NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION");
            statement.execute("CREATE ROLE firefly_product_definition_insurance_quote_read NOLOGIN NOINHERIT"
                    + " NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION");
            statement.execute(transaction("prepare-publication-metadata-owners.sql.template"));
            String bootstrap = transaction("bootstrap-publication-installer.sql.template");
            int assertion = bootstrap.indexOf("\nSELECT NOT pg_catalog.has_database_privilege");
            if (assertion < 0) {
                throw new IllegalStateException("Exact publication installer template required");
            }
            // The trailing psql condition is checked by V21's unchanged SQL preflight in the installer session.
            bootstrap = bootstrap.substring(0, assertion) + "\nCOMMIT;";
            bootstrap = bootstrap.replace(":\"publication_migrator_login\"", identifier(installer))
                    .replace(":'publication_migrator_password'", literal(password))
                    .replace(":\"publication_migrator_database\"", identifier(database));
            if (bootstrap.contains(":\"") || bootstrap.contains(":'") || bootstrap.contains("\\")) {
                throw new IllegalStateException("Unresolved publication installer fixture input");
            }
            statement.execute(bootstrap);
        }
        Flyway complete = Flyway.configure()
                .dataSource(jdbcUrl, installer, password)
                .locations("classpath:db/migration")
                .load();
        complete.migrate();
        complete.validate();
        if (complete.info().pending().length != 0) {
            throw new IllegalStateException("Complete forward migration chain required");
        }
    }

    private static String transaction(String name) throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path directory = Path.of("ops/database/publication-metadata");
        if (!Files.isDirectory(root.resolve(directory))) {
            root = root.getParent();
        }
        if (root == null || !Files.isDirectory(root.resolve(directory))) {
            throw new IllegalStateException("Original administrator preparation source required");
        }
        String template = Files.readString(root.resolve(directory).resolve(name));
        int start = template.indexOf("\nBEGIN;");
        if (start < 0 || !template.stripTrailing().endsWith("COMMIT;")) {
            throw new IllegalStateException("Original administrator transaction required");
        }
        return template.substring(start + 1);
    }

    private static String identifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String literal(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
