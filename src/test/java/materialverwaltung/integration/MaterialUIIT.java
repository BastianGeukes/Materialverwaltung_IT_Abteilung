package materialverwaltung.integration;

import materialverwaltung.dao.jdbc.JdbcArtikelDAO;
import materialverwaltung.dao.jdbc.JdbcLagerbewegungDAO;
import materialverwaltung.dao.jdbc.JdbcMitarbeiterDAO;
import materialverwaltung.db.ConnectionConfig;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.db.DatabaseMigration;
import materialverwaltung.service.ArtikelService;
import materialverwaltung.service.MaterialService;
import materialverwaltung.service.MitarbeiterService;
import materialverwaltung.ui.KonsolenUI;
import materialverwaltung.ui.MaterialUI;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abläufe für Einlagerung und Ausgabe in der Konsole (T18) mit echten Services
 * und PostgreSQL-Testdatenbank.
 *
 * <p>Eingaben: Hauptmenü 1 = Materialbuchung; im Untermenü 1 = einlagern, 2 = ausgeben,
 * 0 = zurück. Testdaten: Artikel 1 „Maus“, Mitarbeiter 1 „Anna“ und 2 „Ben“.</p>
 */
@Testcontainers
class MaterialUIIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static final String TECHNISCHER_FEHLER = "Ein technischer Fehler ist aufgetreten";
    private static final String AUSGANG_UNKLAR = "Es ist unklar, ob die Aktion ausgeführt wurde";

    static ConnectionConfig config;

    private ConnectionFactory factory;

    @BeforeAll
    static void datenbankVorbereiten() {
        config = new ConnectionConfig(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        DatabaseMigration.migrate(config);
    }

    @BeforeEach
    void testdatenAnlegen() throws SQLException {
        sql("TRUNCATE \"Lagerbewegung\", \"Artikel\", \"Mitarbeiter\" RESTART IDENTITY");
        sql("INSERT INTO \"Artikel\" (\"Bezeichnung\") VALUES ('Maus')");
        sql("INSERT INTO \"Mitarbeiter\" (\"Name\", \"Abteilung\") VALUES ('Anna', 'IT'), ('Ben', NULL)");
        factory = new ConnectionFactory(config);
    }

    // ---------- Erfolgreiche Buchungen ----------

    @Test
    void eingangFragtNachDemErfasserUndZeigtDenNeuenBestand() throws SQLException {
        String ausgabe = bedienen(factory, "1", "1", "1", "1", "5", "0", "0");

        assertTrue(ausgabe.contains("Artikel: [ID 1] Maus (Bestand: 0)"), ausgabe);
        assertTrue(ausgabe.contains("Erfasser (Mitarbeiter-ID): "), "fragt nach dem Erfasser");
        assertTrue(ausgabe.contains("Erfasser: [ID 1] Anna (IT)"), ausgabe);
        assertTrue(ausgabe.contains("Eingang gebucht: 5 Stück [ID 1] Maus, erfasst von [ID 1] Anna."), ausgabe);
        assertTrue(ausgabe.contains("Neuer Bestand: 5"), ausgabe);

        assertEquals(5, bestand());
        assertEquals(List.of("Eingang|5|1"), bewegungen());
    }

    @Test
    void ausgabeFragtNachDemEmpfaengerUndZeigtDenNeuenBestand() throws SQLException {
        String ausgabe = bedienen(factory,
                "1",
                "1", "1", "1", "5",     // Eingang 5 durch Anna
                "2", "1", "2", "3",     // Ausgabe 3 an Ben
                "0", "0");

        assertTrue(ausgabe.contains("Empfänger (Mitarbeiter-ID): "), "fragt nach dem Empfänger");
        assertTrue(ausgabe.contains("Artikel: [ID 1] Maus (Bestand: 5)"), ausgabe);
        assertTrue(ausgabe.contains("Empfänger: [ID 2] Ben (keine Abteilung)"), ausgabe);
        assertTrue(ausgabe.contains("Ausgabe gebucht: 3 Stück [ID 1] Maus an [ID 2] Ben."), ausgabe);
        assertTrue(ausgabe.contains("Neuer Bestand: 2"), ausgabe);

        assertEquals(2, bestand());
        assertEquals(List.of("Eingang|5|1", "Ausgang|3|2"), bewegungen());
    }

    @Test
    void ausgabeDesGesamtenBestandsIstMoeglich() throws SQLException {
        String ausgabe = bedienen(factory, "1", "1", "1", "1", "4", "2", "1", "2", "4", "0", "0");

        assertTrue(ausgabe.contains("Neuer Bestand: 0"), ausgabe);
        assertEquals(0, bestand());
    }

    // ---------- Ungültige Zahlen und Mengen ----------

    @Test
    void ungueltigeZahlenUndMengenWerdenVerstaendlichBehandelt() throws SQLException {
        String ausgabe = bedienen(factory,
                "1", "1",
                "abc", "0", "1",                                 // Artikel-ID
                "x", "1",                                        // Erfasser
                "0", "-3", "1.5", "99999999999", "2",            // Menge
                "0", "0");

        assertTrue(ausgabe.contains("\"abc\" ist keine ganze Zahl"), ausgabe);
        assertTrue(ausgabe.contains("Die Zahl 0 liegt außerhalb des gültigen Bereichs (mindestens 1)"), ausgabe);
        assertTrue(ausgabe.contains("\"x\" ist keine ganze Zahl"), ausgabe);
        assertTrue(ausgabe.contains("Die Zahl -3 liegt außerhalb des gültigen Bereichs (mindestens 1)"), ausgabe);
        assertTrue(ausgabe.contains("\"1.5\" ist keine ganze Zahl"), ausgabe);
        assertTrue(ausgabe.contains("Die Zahl 99999999999 ist zu groß bzw. zu klein"), ausgabe);
        assertTrue(ausgabe.contains("Neuer Bestand: 2"), ausgabe);
        assertEquals(2, bestand());
    }

    // ---------- Fachliche Ablehnungen ----------

    @Test
    void ueberausgabeWirdFachlichAbgelehntUndDieKonsoleBleibtBedienbar() throws SQLException {
        String ausgabe = bedienen(factory,
                "1",
                "1", "1", "1", "2",     // Eingang 2
                "2", "1", "2", "5",     // Ausgabe 5 -> zu viel
                "1", "1", "1", "1",     // danach weiter bedienbar
                "0", "0");

        assertTrue(ausgabe.contains("Fehler: Unzureichender Bestand: Für Artikel 1 wurden 5 Stück angefordert, "
                + "verfügbar sind nur 2."), ausgabe);
        assertFalse(ausgabe.contains("Ausgabe gebucht"), ausgabe);
        assertFalse(ausgabe.contains(TECHNISCHER_FEHLER), "fachliche Ablehnung ist kein technischer Fehler");
        assertTrue(ausgabe.contains("Neuer Bestand: 3"), "nachfolgender Eingang funktioniert");
        assertEquals(3, bestand());
        assertEquals(List.of("Eingang|2|1", "Eingang|1|1"), bewegungen());
    }

    @Test
    void unbekannteIdsWerdenFachlichAbgelehnt() throws SQLException {
        String ausgabe = bedienen(factory,
                "1",
                "1", "99",              // unbekannter Artikel
                "2", "1", "99",         // unbekannter Empfänger
                "0", "0");

        assertTrue(ausgabe.contains("Fehler: Artikel mit der ID 99 wurde nicht gefunden."), ausgabe);
        assertTrue(ausgabe.contains("Fehler: Mitarbeiter mit der ID 99 wurde nicht gefunden."), ausgabe);
        assertTrue(ausgabe.contains("Programm beendet."));
        assertEquals(0, bestand());
        assertTrue(bewegungen().isEmpty());
    }

    @Test
    void bestandsueberlaufWirdFachlichAbgelehnt() throws SQLException {
        sql("UPDATE \"Artikel\" SET \"Bestand\" = " + (Integer.MAX_VALUE - 1));

        String ausgabe = bedienen(factory, "1", "1", "1", "1", "5", "0", "0");

        assertTrue(ausgabe.contains("Fehler: Der Eingang von 5 Stück würde den maximal möglichen Bestand"), ausgabe);
        assertEquals(Integer.MAX_VALUE - 1, bestand());
        assertTrue(bewegungen().isEmpty());
    }

    @Test
    void abbrechenWaehrendDerEingabeBuchtNichts() throws SQLException {
        String ausgabe = bedienen(factory, "1", "1", "1", "1", "abbrechen", "0", "0");

        assertTrue(ausgabe.contains("Eingabe abgebrochen."), ausgabe);
        assertEquals(0, bestand());
        assertTrue(bewegungen().isEmpty());
    }

    // ---------- Technische Fehler und unklarer Ausgang ----------

    @Test
    void technischeFehlerSindVonFachlichenAblehnungenUnterscheidbar() {
        ConnectionFactory ohneZugang = new ConnectionFactory(
                new ConnectionConfig(config.url(), config.user(), "falsches-passwort"));

        String ausgabe = bedienen(ohneZugang, "1", "1", "1", "0", "0");

        assertTrue(ausgabe.contains(TECHNISCHER_FEHLER), ausgabe);
        assertFalse(ausgabe.contains("Fehler: "), "keine fachliche Fehlermeldung");
        assertFalse(ausgabe.contains("falsches-passwort"));
        assertFalse(ausgabe.contains("SQLState"));
        assertTrue(ausgabe.contains("Programm beendet."), "Konsole bleibt bedienbar");
    }

    @Test
    void scheiterndesCommitMeldetUnklarenAusgangOhneErfolgOderNichtbuchungZuBehaupten() throws SQLException {
        // Eine erst beim Commit geprüfte Bedingung lässt den Commit wirklich in der Datenbank scheitern
        sql("ALTER TABLE \"Lagerbewegung\" ADD CONSTRAINT test_commit_scheitert FOREIGN KEY (\"Menge\") "
                + "REFERENCES \"Artikel\" (\"ArtikelID\") DEFERRABLE INITIALLY DEFERRED NOT VALID");
        String ausgabe;
        try {
            ausgabe = bedienen(factory, "1", "1", "1", "1", "999", "0", "0");
        } finally {
            sql("ALTER TABLE \"Lagerbewegung\" DROP CONSTRAINT test_commit_scheitert");
        }

        assertTrue(ausgabe.contains(AUSGANG_UNKLAR), ausgabe);
        assertTrue(ausgabe.contains("Bitte prüfen Sie Bestand und Historie"), ausgabe);
        assertFalse(ausgabe.contains("Eingang gebucht"), "kein Erfolg behaupten");
        assertFalse(ausgabe.contains("Neuer Bestand"), "kein neuer Bestand behaupten");
        assertFalse(ausgabe.contains(TECHNISCHER_FEHLER), "kein allgemeiner Fehler, der eine Nichtbuchung nahelegt");
        assertTrue(ausgabe.contains("Programm beendet."), "Konsole bleibt bedienbar");
        assertEquals(0, bestand(), "hier tatsächlich nicht gespeichert – die Oberfläche darf es aber nicht wissen");
    }

    // ---------- Hilfsmethoden ----------

    private static String bedienen(ConnectionFactory factory, String... zeilen) {
        byte[] eingabe = (String.join("\n", zeilen) + "\n").getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream puffer = new ByteArrayOutputStream();

        KonsolenUI konsole = new KonsolenUI(new ByteArrayInputStream(eingabe),
                new PrintStream(puffer, true, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        MaterialUI.einbinden(konsole,
                new MaterialService(new JdbcArtikelDAO(), new JdbcMitarbeiterDAO(), new JdbcLagerbewegungDAO(), factory),
                new ArtikelService(new JdbcArtikelDAO(), factory),
                new MitarbeiterService(new JdbcMitarbeiterDAO(), factory));
        konsole.starten();

        return puffer.toString(StandardCharsets.UTF_8);
    }

    private static void sql(String anweisung) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             Statement st = con.createStatement()) {
            st.execute(anweisung);
        }
    }

    private static int bestand() throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT \"Bestand\" FROM \"Artikel\" WHERE \"ArtikelID\" = 1")) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    /** Bewegungen als „Bewegungsart|Menge|MitarbeiterID“, älteste zuerst. */
    private static List<String> bewegungen() throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT \"Bewegungsart\", \"Menge\", \"MitarbeiterID\" FROM \"Lagerbewegung\" ORDER BY \"BewegungsID\"");
             ResultSet rs = ps.executeQuery()) {
            List<String> zeilen = new ArrayList<>();
            while (rs.next()) {
                zeilen.add(rs.getString(1) + "|" + rs.getInt(2) + "|" + rs.getInt(3));
            }
            return zeilen;
        }
    }
}
