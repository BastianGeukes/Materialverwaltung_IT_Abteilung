package materialverwaltung.db;

import org.flywaydb.core.Flyway;

public final class DatabaseMigration {
    private DatabaseMigration() {
    }

    public static void migrate(ConnectionConfig config) {
        migrate(config.url(), config.user(), config.password());
    }

    public static void migrate(
            String url,
            String user,
            String password
    ) {
        Flyway flyway = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration/")
                .load();
        flyway.migrate();
    }
}