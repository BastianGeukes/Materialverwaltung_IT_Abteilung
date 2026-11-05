package materialverwaltung.integration;

import materialverwaltung.dao.jdbc.JdbcArtikelDAO;
import materialverwaltung.db.ConnectionConfig;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.db.DatabaseMigration;
import materialverwaltung.service.ArtikelService;
import materialverwaltung.ui.ArtikelUI;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abläufe der Artikelverwaltung in der Konsole (T17) mit echtem ArtikelService
 * und PostgreSQL-Testdatenbank.
 *
 * <p>Eingaben: Hauptmenü 1 = Artikelverwaltung; im Untermenü 1 = anlegen,
 * 2 = auflisten, 3 = Bestand anzeigen, 4 = suchen, 5 = umbenennen, 6 = löschen, 0 = zurück.</p>
 */
@Testcontainers
class ArtikelUIIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static final String TECHNISCHER_FEHLER = "Ein technischer Fehler ist aufgetreten";

    static ConnectionConfig config;

    private ArtikelService service;

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
        service = new ArtikelService(new JdbcArtikelDAO(), new ConnectionFactory(config));
    }

    // ---------- Anzeige mit ID, Bezeichnung und Bestand ----------

    @Test
    void anlegenUndAuflistenZeigtIdBezeichnungUndBestand() throws SQLException {
        String ausgabe = bedienen(service,
                "1",
                "1", "  Funkmaus ",
                "1", "Tastatur",
                "2",
                "0", "0");

        assertTrue(ausgabe.contains("Artikel angelegt: [ID 1] Funkmaus (Bestand: 0)"), ausgabe);
        assertTrue(ausgabe.contains("Artikel angelegt: [ID 2] Tastatur (Bestand: 0)"), ausgabe);
        assertTrue(ausgabe.contains("Bezeichnung"), "Tabellenkopf");
        assertTrue(ausgabe.contains("Bestand"));
        assertTrue(zeileVorhanden(ausgabe, 1, "Funkmaus", 0), ausgabe);
        assertTrue(zeileVorhanden(ausgabe, 2, "Tastatur", 0), ausgabe);
        assertEquals("Funkmaus|0", artikelInDatenbank(1));
    }

    @Test
    void bestandsanzeigeZeigtAktuellenBestand() throws SQLException {
        int id = service.anlegen("Monitor").getArtikelId();
        setzeBestand(id, 12);

        String ausgabe = bedienen(service, "1", "3", String.valueOf(id), "2", "0", "0");

        assertTrue(ausgabe.contains("[ID 1] Monitor (Bestand: 12)"), ausgabe);
        assertTrue(zeileVorhanden(ausgabe, 1, "Monitor", 12), ausgabe);
    }

    @Test
    void leereListeWirdVerstaendlichGemeldet() {
        String ausgabe = bedienen(service, "1", "2", "0", "0");
        assertTrue(ausgabe.contains("Es sind noch keine Artikel vorhanden."), ausgabe);
    }

    // ---------- Suchen ----------

    @Test
    void suchenZeigtNurTrefferUndMeldetLeereSuche() {
        service.anlegen("HDMI-Kabel");
        service.anlegen("USB-Kabel");
        service.anlegen("Maus");

        String ausgabe = bedienen(service, "1", "4", "kabel", "4", "Drucker", "0", "0");

        assertTrue(ausgabe.contains("Gefundene Artikel: 2"), ausgabe);
        assertTrue(ausgabe.contains("HDMI-Kabel"));
        assertTrue(ausgabe.contains("USB-Kabel"));
        assertFalse(ausgabe.contains("Maus"), "nur Treffer werden angezeigt");
        assertTrue(ausgabe.contains("Keine Artikel gefunden, deren Bezeichnung \"Drucker\" enthält."), ausgabe);
    }

    // ---------- Umbenennen ----------

    @Test
    void umbenennenAendertNurDieBezeichnung() throws SQLException {
        int id = service.anlegen("Maus").getArtikelId();
        setzeBestand(id, 5);

        String ausgabe = bedienen(service, "1", "5", "1", "Funkmaus", "0", "0");

        assertTrue(ausgabe.contains("Bisher: [ID 1] Maus (Bestand: 5)"), ausgabe);
        assertTrue(ausgabe.contains("Nur die Bezeichnung kann geändert werden"), ausgabe);
        assertTrue(ausgabe.contains("Artikel umbenannt: [ID 1] Funkmaus (Bestand: 5)"), ausgabe);
        assertEquals("Funkmaus|5", artikelInDatenbank(id), "Bestand bleibt unverändert");
    }

    @Test
    void umbenennenOhneEingabeAendertNichts() throws SQLException {
        service.anlegen("Headset");

        String ausgabe = bedienen(service, "1", "5", "1", "", "0", "0");

        assertTrue(ausgabe.contains("Keine Änderung. Der Artikel bleibt [ID 1] Headset (Bestand: 0)."), ausgabe);
        assertEquals("Headset|0", artikelInDatenbank(1));
    }

    @Test
    void eigeneBezeichnungDarfErneutGespeichertWerden() throws SQLException {
        service.anlegen("Webcam");

        String ausgabe = bedienen(service, "1", "5", "1", "Webcam", "0", "0");

        assertTrue(ausgabe.contains("Artikel umbenannt: [ID 1] Webcam (Bestand: 0)"), ausgabe);
        assertEquals("Webcam|0", artikelInDatenbank(1));
    }

    // ---------- Dubletten ----------

    @Test
    void dublettenWerdenVerstaendlichErklaert() throws SQLException {
        service.anlegen("Maus");
        service.anlegen("Tastatur");

        String ausgabe = bedienen(service,
                "1",
                "1", " Maus ",                 // doppelt beim Anlegen
                "5", "2", "Maus",              // doppelt beim Umbenennen
                "0", "0");

        String meldung = "Fehler: Die Bezeichnung \"Maus\" ist bereits an einen anderen Artikel vergeben.";
        assertEquals(2, ausgabe.split(Pattern.quote(meldung), -1).length - 1, ausgabe);
        assertEquals(2, anzahlArtikel(), "kein weiterer Artikel");
        assertEquals("Tastatur|0", artikelInDatenbank(2), "Umbenennung wurde nicht gespeichert");
    }

    // ---------- Löschen ----------

    @Test
    void loeschenErfordertBestaetigung() throws SQLException {
        service.anlegen("Toner");

        String ausgabe = bedienen(service, "1", "6", "1", "n", "0", "0");

        assertTrue(ausgabe.contains("Artikel [ID 1] Toner (Bestand: 0) wirklich löschen?"), ausgabe);
        assertTrue(ausgabe.contains("Löschen abgebrochen."), ausgabe);
        assertEquals(1, anzahlArtikel());

        ausgabe = bedienen(service, "1", "6", "1", "j", "0", "0");

        assertTrue(ausgabe.contains("Artikel [ID 1] Toner (Bestand: 0) wurde gelöscht."), ausgabe);
        assertEquals(0, anzahlArtikel());
    }

    @Test
    void geschuetzteLoeschungWirdVerstaendlichErklaert() throws SQLException {
        int id = service.anlegen("Dockingstation").getArtikelId();
        legeLagerbewegungAn(id);

        String ausgabe = bedienen(service, "1", "6", "1", "j", "2", "0", "0");

        assertTrue(ausgabe.contains("Fehler: Artikel 1 kann nicht gelöscht werden, "
                + "weil Lagerbewegungen auf ihn verweisen."), ausgabe);
        assertTrue(zeileVorhanden(ausgabe, 1, "Dockingstation", 0), "Artikel ist weiterhin da");
        assertEquals(1, anzahlArtikel());
    }

    // ---------- Fehler und Bedienbarkeit ----------

    @Test
    void nachAbgelehntenAktionenBleibtDieAnwendungBedienbar() throws SQLException {
        String ausgabe = bedienen(service,
                "1",
                "3", "99",                        // unbekannte ID anzeigen
                "5", "42",                        // unbekannte ID umbenennen
                "6", "7",                         // unbekannte ID löschen
                "1", "x".repeat(101),             // Bezeichnung zu lang
                "1", "abbrechen",                 // Eingabe abbrechen
                "1", "Kabel",                     // danach funktioniert alles weiter
                "0", "0");

        assertTrue(ausgabe.contains("Fehler: Artikel mit der ID 99 wurde nicht gefunden."), ausgabe);
        assertTrue(ausgabe.contains("Fehler: Artikel mit der ID 42 wurde nicht gefunden."), ausgabe);
        assertTrue(ausgabe.contains("Fehler: Artikel mit der ID 7 wurde nicht gefunden."), ausgabe);
        assertTrue(ausgabe.contains("Fehler: Die Bezeichnung darf höchstens 100 Zeichen lang sein"), ausgabe);
        assertTrue(ausgabe.contains("Eingabe abgebrochen."), ausgabe);
        assertTrue(ausgabe.contains("Artikel angelegt: [ID 1] Kabel (Bestand: 0)"), ausgabe);
        assertTrue(ausgabe.contains("Programm beendet."));
        assertEquals(1, anzahlArtikel());
    }

    @Test
    void technischeFehlerWerdenNeutralGemeldet() {
        ConnectionConfig falschesPasswort = new ConnectionConfig(config.url(), config.user(), "falsches-passwort");
        ArtikelService ohneZugang = new ArtikelService(new JdbcArtikelDAO(), new ConnectionFactory(falschesPasswort));

        String ausgabe = bedienen(ohneZugang, "1", "2", "1", "Maus", "0", "0");

        assertEquals(2, ausgabe.split(TECHNISCHER_FEHLER, -1).length - 1, ausgabe);
        assertFalse(ausgabe.contains("falsches-passwort"));
        assertFalse(ausgabe.contains("SQLState"));
        assertTrue(ausgabe.contains("Programm beendet."), "Anwendung bleibt bedienbar");
    }

    // ---------- Hilfsmethoden ----------

    private static String bedienen(ArtikelService service, String... zeilen) {
        byte[] eingabe = (String.join("\n", zeilen) + "\n").getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream puffer = new ByteArrayOutputStream();

        KonsolenUI konsole = new KonsolenUI(new ByteArrayInputStream(eingabe),
                new PrintStream(puffer, true, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        ArtikelUI.einbinden(konsole, service);
        konsole.starten();

        return puffer.toString(StandardCharsets.UTF_8);
    }

    /** Prüft, ob die Tabelle eine Zeile mit genau dieser ID, Bezeichnung und diesem Bestand enthält. */
    private static boolean zeileVorhanden(String ausgabe, int id, String bezeichnung, int bestand) {
        return ausgabe.lines().anyMatch(zeile -> zeile.matches(
                "\\s*" + id + "\\s+" + Pattern.quote(bezeichnung) + "\\s+" + bestand + "\\s*"));
    }

    private static int anzahlArtikel() throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM \"Artikel\"")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String artikelInDatenbank(int id) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT \"Bezeichnung\", \"Bestand\" FROM \"Artikel\" WHERE \"ArtikelID\" = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Artikel " + id + " fehlt in der Datenbank");
                return rs.getString(1) + "|" + rs.getInt(2);
            }
        }
    }

    /** Setzt den Bestand direkt per SQL (Materialbuchungen folgen erst mit T13/T14). */
    private static void setzeBestand(int artikelId, int bestand) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE \"Artikel\" SET \"Bestand\" = ? WHERE \"ArtikelID\" = ?")) {
            ps.setInt(1, bestand);
            ps.setInt(2, artikelId);
            assertEquals(1, ps.executeUpdate());
        }
    }

    private static void legeLagerbewegungAn(int artikelId) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection()) {
            int mitarbeiterId;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO \"Mitarbeiter\" (\"Name\") VALUES ('Testperson') RETURNING \"MitarbeiterID\"");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                mitarbeiterId = rs.getInt(1);
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO \"Lagerbewegung\" (\"ArtikelID\", \"MitarbeiterID\", \"Menge\", \"Bewegungsart\") "
                            + "VALUES (?, ?, 1, 'Eingang')")) {
                ps.setInt(1, artikelId);
                ps.setInt(2, mitarbeiterId);
                assertEquals(1, ps.executeUpdate());
            }
        }
    }
}
