package materialverwaltung.db;

import org.flywaydb.core.Flyway;

public final class DatabaseMigration {
    private DatabaseMigration() {
    }

    public static void migrate(
            String url,
            String benutzer,
            String passwort
    ) {
        Flyway flyway = Flyway.configure()
                .dataSource(url, benutzer, passwort)
                .locations("classpath:db/migration/")
                .load();
        flyway.migrate();
    }
}
