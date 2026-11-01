package materialverwaltung.integration;

import materialverwaltung.dao.jdbc.JdbcMitarbeiterDAO;
import materialverwaltung.db.ConnectionConfig;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.db.DatabaseMigration;
import materialverwaltung.model.Abteilung;
import materialverwaltung.service.MitarbeiterService;
import materialverwaltung.ui.KonsolenUI;
import materialverwaltung.ui.MitarbeiterUI;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abläufe der Mitarbeiterverwaltung in der Konsole (T16) mit echtem
 * MitarbeiterService und PostgreSQL-Testdatenbank.
 *
 * <p>Die Eingaben simulieren einen Benutzer: Hauptmenü 1 = Mitarbeiterverwaltung,
 * im Untermenü 1 = anlegen, 2 = auflisten, 3 = suchen, 4 = bearbeiten, 5 = löschen,
 * 0 = zurück. Abteilung 0 = keine, 1 = IT, 2 = Personal, … 7 = Logistik.</p>
 */
@Testcontainers
class MitarbeiterUIIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static final String TECHNISCHER_FEHLER = "Ein technischer Fehler ist aufgetreten";

    static ConnectionConfig config;

    private MitarbeiterService service;

    @BeforeAll
    static void datenbankVorbereiten() {
        config = new ConnectionConfig(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        DatabaseMigration.migrate(config);
    }

    @BeforeEach
    void leereTabellen() throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             Statement st = con.createStatement()) {
            st.execute("TRUNCATE \"Lagerbewegung\", \"Artikel\", \"Mitarbeiter\" RESTART IDENTITY");
        }
        service = new MitarbeiterService(new JdbcMitarbeiterDAO(), new ConnectionFactory(config));
    }

    // ---------- Anlegen und Auflisten ----------

    @Test
    void anlegenUndAuflisten() throws SQLException {
        String ausgabe = bedienen(service,
                "1",                    // Hauptmenü: Mitarbeiterverwaltung
                "1", "  Anna Müller ", "1",   // anlegen, Name, Abteilung IT
                "2",                    // auflisten
                "0", "0");

        assertTrue(ausgabe.contains("Mitarbeiter angelegt: [ID 1] Anna Müller (IT)"), ausgabe);
        assertTrue(ausgabe.contains("     1  Anna Müller"), "Liste zeigt ID und Namen");
        assertEquals("Anna Müller|IT", mitarbeiterInDatenbank(1));
        assertTrue(ausgabe.contains("Programm beendet."));
    }

    @Test
    void anlegenOhneAbteilung() throws SQLException {
        String ausgabe = bedienen(service, "1", "1", "Ben", "0", "0", "0");

        assertTrue(ausgabe.contains("Mitarbeiter angelegt: [ID 1] Ben (keine Abteilung)"), ausgabe);
        assertEquals("Ben|null", mitarbeiterInDatenbank(1));
    }

    @Test
    void leereListeWirdVerstaendlichGemeldet() {
        String ausgabe = bedienen(service, "1", "2", "0", "0");
        assertTrue(ausgabe.contains("Es sind noch keine Mitarbeiter vorhanden."), ausgabe);
    }

    @Test
    void gleichnamigeMitarbeiterSindUeberIdUnterscheidbar() {
        String ausgabe = bedienen(service,
                "1",
                "1", "Chris Schmidt", "1",
                "1", "Chris Schmidt", "7",
                "2",
                "0", "0");

        assertTrue(ausgabe.contains("[ID 1] Chris Schmidt (IT)"), ausgabe);
        assertTrue(ausgabe.contains("[ID 2] Chris Schmidt (Logistik)"), ausgabe);
        assertTrue(ausgabe.contains("     1  Chris Schmidt"), ausgabe);
        assertTrue(ausgabe.contains("     2  Chris Schmidt"), ausgabe);
    }

    // ---------- Suchen ----------

    @Test
    void suchenZeigtTrefferUndMeldetLeereSuche() {
        service.anlegen("Anna Becker", null);
        service.anlegen("Tom Krüger", null);

        String ausgabe = bedienen(service,
                "1",
                "3", "ANN",
                "3", "xyz",
                "0", "0");

        assertTrue(ausgabe.contains("Gefundene Mitarbeiter: 1"), ausgabe);
        assertTrue(ausgabe.contains("Anna Becker"));
        assertFalse(ausgabe.contains("Tom Krüger"), "nur Treffer werden angezeigt");
        assertTrue(ausgabe.contains("Keine Mitarbeiter gefunden, deren Name \"xyz\" enthält."), ausgabe);
    }

    // ---------- Bearbeiten ----------

    @Test
    void bearbeitenAendertNameUndAbteilung() throws SQLException {
        service.anlegen("Eva", null);

        String ausgabe = bedienen(service,
                "1",
                "4", "1", "Eva Schulz", "j", "7",   // ID 1, neuer Name, Abteilung ändern -> Logistik
                "0", "0");

        assertTrue(ausgabe.contains("Bisher: [ID 1] Eva (keine Abteilung)"), ausgabe);
        assertTrue(ausgabe.contains("Mitarbeiter gespeichert: [ID 1] Eva Schulz (Logistik)"), ausgabe);
        assertEquals("Eva Schulz|LOGISTIK", mitarbeiterInDatenbank(1));
    }

    @Test
    void bearbeitenMitLeeremNamenBehaeltBisherigeWerte() throws SQLException {
        service.anlegen("Felix", Abteilung.IT);

        String ausgabe = bedienen(service, "1", "4", "1", "", "n", "0", "0");

        assertTrue(ausgabe.contains("Mitarbeiter gespeichert: [ID 1] Felix (IT)"), ausgabe);
        assertEquals("Felix|IT", mitarbeiterInDatenbank(1));
    }

    // ---------- Löschen ----------

    @Test
    void loeschenErfordertBestaetigung() throws SQLException {
        service.anlegen("Gina", null);

        String ausgabe = bedienen(service,
                "1",
                "5", "1", "n",          // abgelehnt
                "0", "0");

        assertTrue(ausgabe.contains("[ID 1] Gina (keine Abteilung) wirklich löschen?"), ausgabe);
        assertTrue(ausgabe.contains("Löschen abgebrochen."), ausgabe);
        assertEquals(1, anzahlMitarbeiter());

        ausgabe = bedienen(service, "1", "5", "1", "j", "0", "0");

        assertTrue(ausgabe.contains("Mitarbeiter [ID 1] Gina (keine Abteilung) wurde gelöscht."), ausgabe);
        assertEquals(0, anzahlMitarbeiter());
    }

    // ---------- Abgelehnte Aktionen und Fehler ----------

    @Test
    void nachAbgelehntenAktionenBleibtDieAnwendungBedienbar() throws SQLException {
        service.anlegen("Ida", null);
        legeLagerbewegungAn(1);

        String ausgabe = bedienen(service,
                "1",
                "5", "99",                        // unbekannte ID
                "4", "42",                        // unbekannte ID beim Bearbeiten
                "1", "x".repeat(101), "0",        // Name zu lang
                "5", "1", "j",                    // Mitarbeiter mit Lagerbewegung
                "1", "Jan", "1",                  // danach funktioniert alles weiter
                "0", "0");

        assertTrue(ausgabe.contains("Fehler: Mitarbeiter mit der ID 99 wurde nicht gefunden."), ausgabe);
        assertTrue(ausgabe.contains("Fehler: Mitarbeiter mit der ID 42 wurde nicht gefunden."), ausgabe);
        assertTrue(ausgabe.contains("Fehler: Der Name darf höchstens 100 Zeichen lang sein"), ausgabe);
        assertTrue(ausgabe.contains("Fehler: Mitarbeiter 1 kann nicht gelöscht werden, "
                + "weil Lagerbewegungen auf ihn verweisen."), ausgabe);
        assertTrue(ausgabe.contains("Mitarbeiter angelegt: [ID 2] Jan (IT)"), ausgabe);
        assertTrue(ausgabe.contains("Programm beendet."));
        assertEquals(2, anzahlMitarbeiter(), "Ida bleibt erhalten, Jan kommt hinzu");
    }

    @Test
    void ungueltigeEingabenWerdenErneutAbgefragt() throws SQLException {
        String ausgabe = bedienen(service,
                "1",
                "1", "   ", "Kai", "abc", "99", "2",   // leerer Name, keine Zahl, außerhalb, dann Personal
                "0", "0");

        assertTrue(ausgabe.contains("Die Eingabe darf nicht leer sein."), ausgabe);
        assertTrue(ausgabe.contains("\"abc\" ist keine ganze Zahl"), ausgabe);
        assertTrue(ausgabe.contains("Die Zahl 99 liegt außerhalb des gültigen Bereichs"), ausgabe);
        assertEquals("Kai|PERSONAL", mitarbeiterInDatenbank(1));
    }

    @Test
    void abbrechenWaehrendDerEingabeSpeichertNichts() throws SQLException {
        String ausgabe = bedienen(service, "1", "1", "abbrechen", "0", "0");

        assertTrue(ausgabe.contains("Eingabe abgebrochen."), ausgabe);
        assertEquals(0, anzahlMitarbeiter());
    }

    @Test
    void technischeFehlerWerdenNeutralGemeldet() {
        ConnectionConfig falschesPasswort =
                new ConnectionConfig(config.url(), config.user(), "falsches-passwort");
        MitarbeiterService ohneZugang =
                new MitarbeiterService(new JdbcMitarbeiterDAO(), new ConnectionFactory(falschesPasswort));

        String ausgabe = bedienen(ohneZugang, "1", "2", "1", "Lena", "0", "0", "0");

        assertEquals(2, ausgabe.split(TECHNISCHER_FEHLER, -1).length - 1, ausgabe);
        assertFalse(ausgabe.contains("falsches-passwort"), "keine Zugangsdaten anzeigen");
        assertFalse(ausgabe.contains("SQLState"), "keine SQL-Details anzeigen");
        assertTrue(ausgabe.contains("Programm beendet."), "Anwendung bleibt bedienbar");
    }

    // ---------- Hilfsmethoden ----------

    /** Startet die Konsole mit den angegebenen Eingabezeilen und liefert die gesamte Ausgabe. */
    private static String bedienen(MitarbeiterService service, String... zeilen) {
        byte[] eingabe = (String.join("\n", zeilen) + "\n").getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream puffer = new ByteArrayOutputStream();

        KonsolenUI konsole = new KonsolenUI(new ByteArrayInputStream(eingabe),
                new PrintStream(puffer, true, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        MitarbeiterUI.einbinden(konsole, service);
        konsole.starten();

        return puffer.toString(StandardCharsets.UTF_8);
    }

    private static int anzahlMitarbeiter() throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM \"Mitarbeiter\"")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Liefert „Name|Abteilung“ direkt aus der Datenbank. */
    private static String mitarbeiterInDatenbank(int id) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT \"Name\", \"Abteilung\" FROM \"Mitarbeiter\" WHERE \"MitarbeiterID\" = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Mitarbeiter " + id + " fehlt in der Datenbank");
                return rs.getString(1) + "|" + rs.getString(2);
            }
        }
    }

    private static void legeLagerbewegungAn(int mitarbeiterId) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection()) {
            int artikelId;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO \"Artikel\" (\"Bezeichnung\", \"Bestand\") VALUES ('Testartikel', 1) "
                            + "RETURNING \"ArtikelID\"");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                artikelId = rs.getInt(1);
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO \"Lagerbewegung\" (\"ArtikelID\", \"MitarbeiterID\", \"Menge\", \"Bewegungsart\") "
                            + "VALUES (?, ?, 1, 'Eingang')")) {
                ps.setInt(1, artikelId);
                ps.setInt(2, mitarbeiterId);
                ps.executeUpdate();
            }
        }
    }
}
