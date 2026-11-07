package materialverwaltung;

import materialverwaltung.dao.jdbc.JdbcArtikelDAO;
import materialverwaltung.dao.jdbc.JdbcLagerbewegungDAO;
import materialverwaltung.dao.jdbc.JdbcMitarbeiterDAO;
import materialverwaltung.db.ConnectionConfig;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.db.DatabaseMigration;
import materialverwaltung.service.ArtikelService;
import materialverwaltung.service.MaterialService;
import materialverwaltung.service.MitarbeiterService;
import materialverwaltung.ui.*;
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

        //JDBC-DAOs erstellen
        JdbcArtikelDAO artikelDAO = new JdbcArtikelDAO();
        JdbcMitarbeiterDAO mitarbeiterDAO = new JdbcMitarbeiterDAO();
        JdbcLagerbewegungDAO lagerbewegungDAO = new JdbcLagerbewegungDAO();

        //Services mit denselben DAOs und derselben ConnectionFactory
        ArtikelService artikelService =
                new ArtikelService(artikelDAO, connectionFactory);

        MitarbeiterService mitarbeiterService =
                new MitarbeiterService(mitarbeiterDAO, connectionFactory);

        MaterialService materialService =
                new MaterialService(artikelDAO,mitarbeiterDAO,lagerbewegungDAO,connectionFactory);

        //Konsole erstellen und Bereiche einbinden
        KonsolenUI konsole = new KonsolenUI(System.in, System.out);

        MitarbeiterUI.einbinden(konsole,mitarbeiterService);
        ArtikelUI.einbinden(konsole,artikelService);
        MaterialUI.einbinden(
                konsole,materialService, artikelService, mitarbeiterService
        );
        HistorieUI.einbinden(konsole, materialService);

        //Anwendung starten
        konsole.starten();
    }
}