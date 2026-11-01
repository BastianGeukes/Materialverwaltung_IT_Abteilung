package materialverwaltung.integration;

import materialverwaltung.dao.jdbc.JdbcMitarbeiterDAO;
import materialverwaltung.db.ConnectionConfig;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.db.DatabaseMigration;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.exception.FachlicheException;
import materialverwaltung.exception.FachlicherFehler;
import materialverwaltung.model.MitarbeiterBO;
import materialverwaltung.service.MitarbeiterService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static materialverwaltung.model.Abteilung.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Service-Integrationstests für T11 gegen ein echtes PostgreSQL (Testcontainers).
 * Die Ergebnisse werden zusätzlich direkt in der Datenbank nachgeprüft.
 */
@Testcontainers
class MitarbeiterServiceIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static ConnectionConfig config;

    private ZaehlendeConnectionFactory connectionFactory;
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
        connectionFactory = new ZaehlendeConnectionFactory(config);
        service = new MitarbeiterService(new JdbcMitarbeiterDAO(), connectionFactory);
    }

    // ---------- Anlegen ----------

    @Test
    void anlegenTrimmtNameUndSpeichertGenauDiesenWert() throws SQLException {
        MitarbeiterBO angelegt = service.anlegen("   Anna   Müller \t", IT);

        assertTrue(angelegt.getMitarbeiterId() > 0);
        assertEquals("Anna   Müller", angelegt.getName(), "nur äußerer Leerraum wird entfernt");
        assertEquals(IT, angelegt.getAbteilung());
        assertEquals("Anna   Müller", nameInDatenbank(angelegt.getMitarbeiterId()));
    }

    @Test
    void abteilungIstOptional() {
        MitarbeiterBO angelegt = service.anlegen("Ben", null);

        assertNull(angelegt.getAbteilung());
        assertNull(service.holeNachId(angelegt.getMitarbeiterId()).getAbteilung());
    }

    @Test
    void gleicheMitarbeiternamenSindZulaessig() throws SQLException {
        MitarbeiterBO erster = service.anlegen("Chris Schmidt", IT);
        MitarbeiterBO zweiter = service.anlegen("Chris Schmidt", LOGISTIK);

        assertNotEquals(erster.getMitarbeiterId(), zweiter.getMitarbeiterId());
        assertEquals(2, anzahlMitarbeiter());
    }

    @Test
    void leereNamenWerdenAbgelehntUndNichtGespeichert() throws SQLException {
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.anlegen(null, IT));
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.anlegen("", IT));
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.anlegen("    ", IT));

        assertEquals(0, anzahlMitarbeiter());
    }

    @Test
    void namenslaengeGrenzeHundertCodepoints() throws SQLException {
        String hundert = "😀".repeat(100); // 100 Codepoints, 200 char-Werte
        MitarbeiterBO angelegt = service.anlegen(hundert, null);
        assertEquals(hundert, nameInDatenbank(angelegt.getMitarbeiterId()));

        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.anlegen("😀".repeat(101), null));
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.anlegen("a".repeat(101), null));
        assertEquals(1, anzahlMitarbeiter());
    }

    // ---------- Lesen ----------

    @Test
    void holeNachIdLiefertGespeichertenMitarbeiter() {
        MitarbeiterBO angelegt = service.anlegen("Dana", EINKAUF);

        MitarbeiterBO geladen = service.holeNachId(angelegt.getMitarbeiterId());

        assertEquals(angelegt.getMitarbeiterId(), geladen.getMitarbeiterId());
        assertEquals("Dana", geladen.getName());
        assertEquals(EINKAUF, geladen.getAbteilung());
    }

    @Test
    void unbekannteIdErgibtFachlichenFehler() {
        assertFachlich(FachlicherFehler.MITARBEITER_NICHT_GEFUNDEN, () -> service.holeNachId(999));
    }

    @Test
    void nichtpositiveIdIstUngueltigeEingabe() {
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.holeNachId(0));
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.holeNachId(-1));
    }

    @Test
    void auflistenLiefertAlleNachIdSortiert() {
        assertTrue(service.auflisten().isEmpty());

        int a = service.anlegen("Zoe", null).getMitarbeiterId();
        int b = service.anlegen("Adam", IT).getMitarbeiterId();
        int c = service.anlegen("Mia", VERTRIEB).getMitarbeiterId();

        List<Integer> ids = service.auflisten().stream().map(MitarbeiterBO::getMitarbeiterId).toList();
        assertEquals(List.of(a, b, c), ids);
    }

    // ---------- Suchen ----------

    @Test
    void sucheFindetTeilbegriffeOhneGrossKleinschreibung() {
        service.anlegen("Anna Becker", IT);
        service.anlegen("Hannah Bauer", null);
        service.anlegen("Tom Krüger", LOGISTIK);

        List<String> treffer = service.suchen("  ANN ").stream().map(MitarbeiterBO::getName).toList();

        assertEquals(List.of("Anna Becker", "Hannah Bauer"), treffer);
    }

    @Test
    void sucheOhneTrefferLiefertLeereListe() {
        service.anlegen("Anna", IT);
        assertTrue(service.suchen("xyz").isEmpty());
    }

    @Test
    void sonderzeichenSindKeineWildcards() {
        service.anlegen("Max_Mustermann", null);
        service.anlegen("MaxiMustermann", null);
        service.anlegen("100% Einsatz", null);

        assertEquals(List.of("Max_Mustermann"), namen(service.suchen("x_M")));
        assertEquals(List.of("100% Einsatz"), namen(service.suchen("%")));
    }

    @Test
    void ungueltigerSuchbegriffWirdAbgelehnt() {
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.suchen("   "));
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.suchen("b".repeat(101)));
    }

    // ---------- Bearbeiten ----------

    @Test
    void bearbeitenAendertNameUndAbteilung() throws SQLException {
        int id = service.anlegen("Eva", IT).getMitarbeiterId();

        MitarbeiterBO geaendert = service.bearbeiten(id, "  Eva Schulz ", null);

        assertEquals("Eva Schulz", geaendert.getName());
        assertNull(geaendert.getAbteilung());
        MitarbeiterBO geladen = service.holeNachId(id);
        assertEquals("Eva Schulz", geladen.getName());
        assertNull(geladen.getAbteilung());
        assertEquals("Eva Schulz", nameInDatenbank(id));
    }

    @Test
    void bearbeitenUnbekannterMitarbeiterErgibtFachlichenFehler() {
        assertFachlich(FachlicherFehler.MITARBEITER_NICHT_GEFUNDEN, () -> service.bearbeiten(4711, "Frank", IT));
    }

    @Test
    void bearbeitenMitUngueltigemNamenAendertNichts() throws SQLException {
        int id = service.anlegen("Gina", IT).getMitarbeiterId();

        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.bearbeiten(id, "  ", PERSONAL));
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.bearbeiten(id, "g".repeat(101), null));

        assertEquals("Gina", nameInDatenbank(id));
        assertEquals(IT, service.holeNachId(id).getAbteilung());
    }

    // ---------- Löschen ----------

    @Test
    void loeschenEntferntMitarbeiterOhneBewegungen() throws SQLException {
        int id = service.anlegen("Hans", IT).getMitarbeiterId();

        service.loeschen(id);

        assertEquals(0, anzahlMitarbeiter());
        assertFachlich(FachlicherFehler.MITARBEITER_NICHT_GEFUNDEN, () -> service.holeNachId(id));
    }

    @Test
    void loeschenUnbekannterMitarbeiterErgibtFachlichenFehler() {
        assertFachlich(FachlicherFehler.MITARBEITER_NICHT_GEFUNDEN, () -> service.loeschen(12345));
    }

    @Test
    void loeschenMitLagerbewegungIstUnzulaessig() throws SQLException {
        int id = service.anlegen("Ida", LOGISTIK).getMitarbeiterId();
        legeLagerbewegungAn(id);

        FachlicheException e = assertFachlich(FachlicherFehler.MITARBEITER_HAT_BEWEGUNGEN, () -> service.loeschen(id));

        // Die technische Ursache bleibt erhalten
        DatenzugriffsException ursache = assertInstanceOf(DatenzugriffsException.class, e.getCause());
        assertEquals("23503", ursache.getSqlState());
        // Der Mitarbeiter existiert weiterhin
        assertEquals("Ida", service.holeNachId(id).getName());
    }

    // ---------- Technische Fehler und Ressourcen ----------

    @Test
    void sonstigeDatenbankfehlerBleibenTechnisch() {
        ConnectionConfig falschesPasswort =
                new ConnectionConfig(config.url(), config.user(), config.password() + "-falsch");
        MitarbeiterService ohneZugang =
                new MitarbeiterService(new JdbcMitarbeiterDAO(), new ConnectionFactory(falschesPasswort));

        assertThrows(DatenzugriffsException.class, ohneZugang::auflisten);
        assertThrows(DatenzugriffsException.class, () -> ohneZugang.anlegen("Jan", IT));
        assertThrows(DatenzugriffsException.class, () -> ohneZugang.loeschen(1));
    }

    @Test
    void verbindungenWerdenBeiErfolgUndFehlerGeschlossen() throws SQLException {
        // Erfolgreiche Aufrufe
        int id = service.anlegen("Kai", IT).getMitarbeiterId();
        service.holeNachId(id);
        service.auflisten();
        service.suchen("Kai");
        service.bearbeiten(id, "Kai Neu", null);

        // Fachliche Fehler, die erst in der Datenbank festgestellt werden
        assertThrows(FachlicheException.class, () -> service.holeNachId(999));
        assertThrows(FachlicheException.class, () -> service.bearbeiten(999, "Niemand", null));
        assertThrows(FachlicheException.class, () -> service.loeschen(999));
        legeLagerbewegungAn(id);
        assertThrows(FachlicheException.class, () -> service.loeschen(id));

        assertEquals(9, connectionFactory.geoeffnet.size());
        for (Connection con : connectionFactory.geoeffnet) {
            assertTrue(con.isClosed(), "Jede geöffnete Verbindung muss geschlossen sein");
        }
    }

    // ---------- Hilfsmethoden ----------

    private static FachlicheException assertFachlich(FachlicherFehler erwartet, Executable aufruf) {
        FachlicheException e = assertThrows(FachlicheException.class, aufruf);
        assertEquals(erwartet, e.getFehler());
        assertNotNull(e.getMessage());
        return e;
    }

    private static List<String> namen(List<MitarbeiterBO> mitarbeiter) {
        return mitarbeiter.stream().map(MitarbeiterBO::getName).toList();
    }

    private static int anzahlMitarbeiter() throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM \"Mitarbeiter\"")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String nameInDatenbank(int mitarbeiterId) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT \"Name\" FROM \"Mitarbeiter\" WHERE \"MitarbeiterID\" = ?")) {
            ps.setInt(1, mitarbeiterId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Mitarbeiter " + mitarbeiterId + " fehlt in der Datenbank");
                return rs.getString(1);
            }
        }
    }

    /**
     * Legt direkt per SQL einen Artikel und eine Lagerbewegung an, die auf den
     * Mitarbeiter verweist (der Lagerbewegungs-DAO folgt erst mit T10).
     */
    private static void legeLagerbewegungAn(int mitarbeiterId) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection()) {
            int artikelId;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO \"Artikel\" (\"Bezeichnung\", \"Bestand\") VALUES (?, 1) RETURNING \"ArtikelID\"")) {
                ps.setString(1, "Testartikel " + System.nanoTime());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    artikelId = rs.getInt(1);
                }
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

    /**
     * Echte ConnectionFactory, die sich alle geöffneten Verbindungen merkt,
     * damit geprüft werden kann, ob der Service sie wieder schließt.
     */
    private static final class ZaehlendeConnectionFactory extends ConnectionFactory {
        final List<Connection> geoeffnet = new ArrayList<>();

        ZaehlendeConnectionFactory(ConnectionConfig config) {
            super(config);
        }

        @Override
        public Connection openConnection() {
            Connection con = super.openConnection();
            geoeffnet.add(con);
            return con;
        }
    }
}
