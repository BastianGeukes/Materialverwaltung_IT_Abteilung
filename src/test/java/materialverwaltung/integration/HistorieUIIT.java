package materialverwaltung.integration;

import materialverwaltung.dao.jdbc.JdbcArtikelDAO;
import materialverwaltung.dao.jdbc.JdbcLagerbewegungDAO;
import materialverwaltung.dao.jdbc.JdbcMitarbeiterDAO;
import materialverwaltung.db.ConnectionConfig;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.db.DatabaseMigration;
import materialverwaltung.service.MaterialService;
import materialverwaltung.ui.HistorieUI;
import materialverwaltung.ui.KonsolenUI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Konsolenausgabe der Bewegungshistorie (T19), geprüft mit tatsächlichen Bewegungen
 * aus der PostgreSQL-Testdatenbank. Hauptmenü 1 = Bewegungshistorie anzeigen.
 */
@Testcontainers
class HistorieUIIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    static ConnectionConfig config;

    private MaterialService service;

    @BeforeAll
    static void datenbankVorbereiten() {
        config = new ConnectionConfig(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        DatabaseMigration.migrate(config);
    }

    @BeforeEach
    void testdatenAnlegen() throws SQLException {
        sql("TRUNCATE \"Lagerbewegung\", \"Artikel\", \"Mitarbeiter\" RESTART IDENTITY");
        sql("INSERT INTO \"Artikel\" (\"Bezeichnung\") VALUES ('Maus'), ('Tastatur')");
        sql("INSERT INTO \"Mitarbeiter\" (\"Name\") VALUES ('Anna'), ('Ben')");
        service = new MaterialService(new JdbcArtikelDAO(), new JdbcMitarbeiterDAO(),
                new JdbcLagerbewegungDAO(), new ConnectionFactory(config));
    }

    @Test
    void leereHistorieZeigtEinenVerstaendlichenHinweis() {
        String ausgabe = bedienen(service, "1", "0");

        assertTrue(ausgabe.contains("Es sind noch keine Lagerbewegungen vorhanden."), ausgabe);
        assertTrue(ausgabe.contains("Programm beendet."));
    }

    @Test
    void alleAngabenErscheinenMitVerstaendlichenBezeichnungen() {
        service.einlagern(1, 1, 5);   // Maus, Erfasser Anna
        service.ausgeben(1, 2, 2);    // Maus, Empfänger Ben

        String ausgabe = bedienen(service, "1", "0");

        assertTrue(ausgabe.contains("2 Bewegungen, die neueste zuerst:"), ausgabe);
        String ausgang = block(ausgabe, "Bewegung 2 – Ausgang am ");
        assertTrue(ausgang.contains("  Artikel:    [ID 1] Maus"), ausgang);
        assertTrue(ausgang.contains("  Menge:      2"), ausgang);
        assertTrue(ausgang.contains("  Empfänger:  [ID 2] Ben"), ausgang);

        String eingang = block(ausgabe, "Bewegung 1 – Eingang am ");
        assertTrue(eingang.contains("  Artikel:    [ID 1] Maus"), eingang);
        assertTrue(eingang.contains("  Menge:      5"), eingang);
        assertTrue(eingang.contains("  Erfasser:   [ID 1] Anna"), eingang);
    }

    @Test
    void neuesteBewegungErscheintZuerst() {
        service.einlagern(1, 1, 5);
        service.einlagern(2, 2, 3);
        service.ausgeben(1, 2, 1);

        String ausgabe = bedienen(service, "1", "0");

        int b3 = ausgabe.indexOf("Bewegung 3 – Ausgang");
        int b2 = ausgabe.indexOf("Bewegung 2 – Eingang");
        int b1 = ausgabe.indexOf("Bewegung 1 – Eingang");
        assertTrue(b3 >= 0 && b2 >= 0 && b1 >= 0, ausgabe);
        assertTrue(b3 < b2 && b2 < b1, "neueste zuerst:\n" + ausgabe);
    }

    @Test
    void zeitpunktWirdLesbarInDerGewaehltenZeitzoneAngezeigt() throws SQLException {
        // 13:30:15 UTC = 14:30:15 in Berlin (Winterzeit)
        sql("INSERT INTO \"Lagerbewegung\" (\"ArtikelID\", \"MitarbeiterID\", \"Menge\", \"Bewegungsart\", "
                + "\"Bewegungszeitpunkt\") VALUES (1, 1, 4, 'Eingang', '2026-11-07T13:30:15Z')");

        String ausgabe = bedienen(service, "1", "0");

        assertTrue(ausgabe.contains("Bewegung 1 – Eingang am 07.11.2026 um 14:30:15"), ausgabe);
    }

    @Test
    void historieZeigtDieAktuellenNamen() throws SQLException {
        service.einlagern(1, 1, 5);
        sql("UPDATE \"Artikel\" SET \"Bezeichnung\" = 'Funkmaus' WHERE \"ArtikelID\" = 1");
        sql("UPDATE \"Mitarbeiter\" SET \"Name\" = 'Anna Schulz' WHERE \"MitarbeiterID\" = 1");

        String ausgabe = bedienen(service, "1", "0");

        assertTrue(ausgabe.contains("  Artikel:    [ID 1] Funkmaus"), ausgabe);
        assertTrue(ausgabe.contains("  Erfasser:   [ID 1] Anna Schulz"), ausgabe);
    }

    @Test
    void technischerFehlerWirdNeutralGemeldetUndKonsoleBleibtBedienbar() {
        MaterialService ohneZugang = new MaterialService(new JdbcArtikelDAO(), new JdbcMitarbeiterDAO(),
                new JdbcLagerbewegungDAO(),
                new ConnectionFactory(new ConnectionConfig(config.url(), config.user(), "falsches-passwort")));

        String ausgabe = bedienen(ohneZugang, "1", "0");

        assertTrue(ausgabe.contains("Ein technischer Fehler ist aufgetreten"), ausgabe);
        assertFalse(ausgabe.contains("falsches-passwort"));
        assertTrue(ausgabe.contains("Programm beendet."));
    }

    // ---------- Hilfsmethoden ----------

    private static String bedienen(MaterialService service, String... zeilen) {
        byte[] eingabe = (String.join("\n", zeilen) + "\n").getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream puffer = new ByteArrayOutputStream();

        KonsolenUI konsole = new KonsolenUI(new ByteArrayInputStream(eingabe),
                new PrintStream(puffer, true, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        HistorieUI.einbinden(konsole, service, BERLIN);
        konsole.starten();

        return puffer.toString(StandardCharsets.UTF_8);
    }

    /** Liefert den Block einer Bewegung: von ihrer Kopfzeile bis zur nächsten Leerzeile. */
    private static String block(String ausgabe, String kopf) {
        int start = ausgabe.indexOf(kopf);
        assertTrue(start >= 0, "Block „" + kopf + "“ fehlt:\n" + ausgabe);
        int ende = ausgabe.indexOf(System.lineSeparator() + System.lineSeparator(), start);
        return ende < 0 ? ausgabe.substring(start) : ausgabe.substring(start, ende);
    }

    private static void sql(String anweisung) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             Statement st = con.createStatement()) {
            st.execute(anweisung);
        }
    }
}
