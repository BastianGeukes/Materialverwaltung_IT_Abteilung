package materialverwaltung;

import materialverwaltung.db.ConnectionConfig;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.db.DatabaseMigration;
import org.flywaydb.core.api.FlywayException;

public class Main {
    public static void main(String[] args) {

        // Konfiguration einmal einlesen, Migration und Anwendung nutzen dieselben Werte
        ConnectionConfig config;
        try {
            config = ConnectionConfig.fromEnvironment();
        } catch (IllegalStateException e) {
            System.err.println("Bitte DATABASE_URL, DATABASE_USERNAME und DATABASE_PASSWORD konfigurieren. "
                    + e.getMessage());
            System.exit(1);
            return;
        }

        // Datenbank-Migration durch Flyway, bevor DAOs, Services und UI erstellt werden
        try {
            DatabaseMigration.migrate(config);
        } catch (FlywayException e) {
            System.err.println("Die Datenbank konnte nicht vorbereitet werden: " + e.getMessage());
            System.exit(1);
        }

        ConnectionFactory connectionFactory = new ConnectionFactory(config);
        // TODO: DAOs, Services und UI mit connectionFactory aufbauen
    }
}