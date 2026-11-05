package materialverwaltung.integration;

import materialverwaltung.dao.jdbc.JdbcArtikelDAO;
import materialverwaltung.db.ConnectionConfig;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.db.DatabaseMigration;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.exception.FachlicheException;
import materialverwaltung.exception.FachlicherFehler;
import materialverwaltung.model.ArtikelBO;
import materialverwaltung.service.ArtikelService;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Service-Integrationstests für T12 mit echtem Artikel-DAO und PostgreSQL (Testcontainers).
 * Die Ergebnisse werden zusätzlich direkt in der Datenbank nachgeprüft.
 */
@Testcontainers
class ArtikelServiceIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static ConnectionConfig config;

    private ZaehlendeConnectionFactory connectionFactory;
    private ArtikelService service;

    @BeforeAll
    static void datenbankVorbereiten() {
        config = new ConnectionConfig(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        DatabaseMigration.migrate(config);
    }

    @BeforeEach
    void leereTabellen() throws SQLException {
        sql("TRUNCATE \"Lagerbewegung\", \"Artikel\", \"Mitarbeiter\" RESTART IDENTITY");
        connectionFactory = new ZaehlendeConnectionFactory(config);
        service = new ArtikelService(new JdbcArtikelDAO(), connectionFactory);
    }

    // ---------- Anlegen ----------

    @Test
    void neuerArtikelWirdGetrimmtUndStartetMitBestandNull() throws SQLException {
        ArtikelBO angelegt = service.anlegen("   USB-C  Kabel \t");

        assertTrue(angelegt.getArtikelId() > 0);
        assertEquals("USB-C  Kabel", angelegt.getBezeichnung(), "nur äußerer Leerraum wird entfernt");
        assertEquals(0, angelegt.getBestand());
        assertEquals("USB-C  Kabel|0", artikelInDatenbank(angelegt.getArtikelId()));
    }

    @Test
    void doppelteBezeichnungWirdFachlichAbgelehnt() throws SQLException {
        service.anlegen("Maus");

        FachlicheException e = assertFachlich(FachlicherFehler.ARTIKELBEZEICHNUNG_VERGEBEN,
                () -> service.anlegen("  Maus "));

        assertTrue(e.getMessage().contains("\"Maus\""), e.getMessage());
        DatenzugriffsException ursache = assertInstanceOf(DatenzugriffsException.class, e.getCause());
        assertEquals("23505", ursache.getSqlState());
        assertEquals(1, anzahlArtikel(), "kein weiterer Artikel");
    }

    @Test
    void bezeichnungIstGrossKleinschreibungsabhaengigEindeutig() throws SQLException {
        service.anlegen("Maus");
        service.anlegen("maus");

        assertEquals(2, anzahlArtikel());
    }

    @Test
    void leereUndZuLangeBezeichnungenWerdenAbgelehntUndNichtGespeichert() throws SQLException {
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.anlegen(null));
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.anlegen("   "));
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.anlegen("x".repeat(101)));
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.anlegen("🖱".repeat(101)));
        assertEquals(0, anzahlArtikel());

        String hundert = "🖱".repeat(100); // 100 Codepoints, 200 char-Werte
        ArtikelBO angelegt = service.anlegen(hundert);
        assertEquals(hundert + "|0", artikelInDatenbank(angelegt.getArtikelId()));
    }

    // ---------- Lesen und Suchen ----------

    @Test
    void holeNachIdLiefertArtikelMitBestand() throws SQLException {
        int id = service.anlegen("Monitor").getArtikelId();
        setzeBestand(id, 7);

        ArtikelBO geladen = service.holeNachId(id);

        assertEquals("Monitor", geladen.getBezeichnung());
        assertEquals(7, geladen.getBestand());
    }

    @Test
    void unbekannteUndUngueltigeIds() {
        assertFachlich(FachlicherFehler.ARTIKEL_NICHT_GEFUNDEN, () -> service.holeNachId(999));
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.holeNachId(0));
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.holeNachId(-1));
    }

    @Test
    void auflistenLiefertAlleNachIdSortiert() {
        assertTrue(service.auflisten().isEmpty());

        int a = service.anlegen("Tastatur").getArtikelId();
        int b = service.anlegen("Adapter").getArtikelId();
        int c = service.anlegen("Maus").getArtikelId();

        List<Integer> ids = service.auflisten().stream().map(ArtikelBO::getArtikelId).toList();
        assertEquals(List.of(a, b, c), ids);
    }

    @Test
    void sucheFindetTeilbegriffeOhneGrossKleinschreibung() {
        service.anlegen("HDMI-Kabel");
        service.anlegen("USB-Kabel");
        service.anlegen("Maus");

        assertEquals(List.of("HDMI-Kabel", "USB-Kabel"), bezeichnungen(service.suchen("  KABEL ")));
        assertTrue(service.suchen("Drucker").isEmpty());
    }

    @Test
    void sonderzeichenSindKeineWildcards() {
        service.anlegen("Kabel_1m");
        service.anlegen("Kabel 1m");
        service.anlegen("Rabatt 50%");

        assertEquals(List.of("Kabel_1m"), bezeichnungen(service.suchen("l_1")));
        assertEquals(List.of("Rabatt 50%"), bezeichnungen(service.suchen("%")));
    }

    // ---------- Umbenennen ----------

    @Test
    void umbenennenAendertNurDieBezeichnung() throws SQLException {
        int id = service.anlegen("Maus").getArtikelId();
        setzeBestand(id, 5);

        ArtikelBO umbenannt = service.umbenennen(id, "  Funkmaus ");

        assertEquals("Funkmaus", umbenannt.getBezeichnung());
        assertEquals(5, umbenannt.getBestand(), "Rückgabe enthält den aktuellen Bestand");
        assertEquals("Funkmaus|5", artikelInDatenbank(id), "Bestand bleibt 5");
    }

    @Test
    void eigeneUnveraenderteBezeichnungDarfGespeichertWerden() throws SQLException {
        int id = service.anlegen("Headset").getArtikelId();

        ArtikelBO umbenannt = service.umbenennen(id, "Headset");

        assertEquals("Headset", umbenannt.getBezeichnung());
        assertEquals("Headset|0", artikelInDatenbank(id));
    }

    @Test
    void umbenennenAufFremdeBezeichnungWirdAbgelehntUndAendertNichts() throws SQLException {
        service.anlegen("Maus");
        int id = service.anlegen("Tastatur").getArtikelId();

        assertFachlich(FachlicherFehler.ARTIKELBEZEICHNUNG_VERGEBEN, () -> service.umbenennen(id, "Maus"));

        assertEquals("Tastatur|0", artikelInDatenbank(id));
    }

    @Test
    void umbenennenUnbekannterOderUngueltigerArtikel() throws SQLException {
        int id = service.anlegen("Webcam").getArtikelId();

        assertFachlich(FachlicherFehler.ARTIKEL_NICHT_GEFUNDEN, () -> service.umbenennen(4711, "Neu"));
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.umbenennen(id, "   "));
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.umbenennen(id, "w".repeat(101)));
        assertEquals("Webcam|0", artikelInDatenbank(id));
    }

    // ---------- Löschen ----------

    @Test
    void loeschenEntferntArtikelOhneBewegungen() throws SQLException {
        int id = service.anlegen("Toner").getArtikelId();

        service.loeschen(id);

        assertEquals(0, anzahlArtikel());
        assertFachlich(FachlicherFehler.ARTIKEL_NICHT_GEFUNDEN, () -> service.holeNachId(id));
    }

    @Test
    void loeschenUnbekannterArtikelErgibtFachlichenFehler() {
        assertFachlich(FachlicherFehler.ARTIKEL_NICHT_GEFUNDEN, () -> service.loeschen(12345));
    }

    @Test
    void referenzierterArtikelMitBestandNullDarfNichtGeloeschtWerden() throws SQLException {
        int id = service.anlegen("Dockingstation").getArtikelId();
        legeLagerbewegungAn(id);
        assertEquals(0, service.holeNachId(id).getBestand());

        FachlicheException e = assertFachlich(FachlicherFehler.ARTIKEL_HAT_BEWEGUNGEN, () -> service.loeschen(id));

        DatenzugriffsException ursache = assertInstanceOf(DatenzugriffsException.class, e.getCause());
        assertEquals("23503", ursache.getSqlState());
        assertEquals("Dockingstation|0", artikelInDatenbank(id), "Artikel existiert weiterhin");
    }

    // ---------- Technische Fehler und Ressourcen ----------

    @Test
    void sonstigeDatenbankfehlerBleibenTechnisch() {
        ConnectionConfig falschesPasswort =
                new ConnectionConfig(config.url(), config.user(), config.password() + "-falsch");
        ArtikelService ohneZugang = new ArtikelService(new JdbcArtikelDAO(), new ConnectionFactory(falschesPasswort));

        assertThrows(DatenzugriffsException.class, ohneZugang::auflisten);
        assertThrows(DatenzugriffsException.class, () -> ohneZugang.anlegen("Maus"));
        assertThrows(DatenzugriffsException.class, () -> ohneZugang.umbenennen(1, "Maus"));
        assertThrows(DatenzugriffsException.class, () -> ohneZugang.loeschen(1));
    }

    @Test
    void verbindungenWerdenBeiErfolgUndFehlerGeschlossen() throws SQLException {
        int id = service.anlegen("Kabel").getArtikelId();           // 1
        service.holeNachId(id);                                     // 2
        service.auflisten();                                        // 3
        service.suchen("Kab");                                      // 4
        service.umbenennen(id, "Kabel 2m");                         // 5

        assertThrows(FachlicheException.class, () -> service.anlegen("Kabel 2m"));        // 6 doppelt
        assertThrows(FachlicheException.class, () -> service.umbenennen(999, "X"));       // 7 fehlt
        assertThrows(FachlicheException.class, () -> service.loeschen(999));              // 8 fehlt
        legeLagerbewegungAn(id);
        assertThrows(FachlicheException.class, () -> service.loeschen(id));               // 9 geschützt

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

    private static List<String> bezeichnungen(List<ArtikelBO> artikel) {
        return artikel.stream().map(ArtikelBO::getBezeichnung).toList();
    }

    private static void sql(String anweisung) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             Statement st = con.createStatement()) {
            st.execute(anweisung);
        }
    }

    private static int anzahlArtikel() throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM \"Artikel\"")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Liefert „Bezeichnung|Bestand“ direkt aus der Datenbank. */
    private static String artikelInDatenbank(int artikelId) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT \"Bezeichnung\", \"Bestand\" FROM \"Artikel\" WHERE \"ArtikelID\" = ?")) {
            ps.setInt(1, artikelId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Artikel " + artikelId + " fehlt in der Datenbank");
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

    /** Legt direkt per SQL einen Mitarbeiter und eine Lagerbewegung auf den Artikel an. */
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

    /** Echte ConnectionFactory, die sich alle geöffneten Verbindungen merkt. */
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
