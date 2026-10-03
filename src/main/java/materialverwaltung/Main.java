package materialverwaltung;

import materialverwaltung.db.DatabaseMigration;
import org.flywaydb.core.api.FlywayException;

public class Main {
    public static void main(String[] args) {

        //Datenbank Migration durch Flyway bevor DAOs, Services und UI erstellt werden
        String url = System.getenv("DATABASE_URL");
        String username = System.getenv("DATABASE_USERNAME");
        String password = System.getenv("DATABASE_PASSWORD");

        if (url == null || url.isBlank()
                || username == null || username.isBlank()
                || password == null) {
            System.err.println("Bitte DB_URL, DB_USERNAME, DB_PASSWORD konfigurieren");
            System.exit(1);
        }

        try {
            DatabaseMigration.migrate(url, username, password);
        } catch (FlywayException e) {
            System.err.println(
                    "Die Datenbank konnte nicht vorbereitet werden" + e.getMessage()
            );
            System.exit(1);
        }


    }
}
