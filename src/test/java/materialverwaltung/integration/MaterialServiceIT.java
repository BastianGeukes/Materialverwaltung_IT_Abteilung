package materialverwaltung.integration;

import materialverwaltung.dao.jdbc.JdbcArtikelDAO;
import materialverwaltung.dao.jdbc.JdbcLagerbewegungDAO;
import materialverwaltung.dao.jdbc.JdbcMitarbeiterDAO;
import materialverwaltung.db.ConnectionConfig;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.db.DatabaseMigration;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.exception.FachlicheException;
import materialverwaltung.exception.FachlicherFehler;
import materialverwaltung.model.Bewegungsart;
import materialverwaltung.model.Buchungsergebnis;
import materialverwaltung.model.LagerbewegungBO;
import materialverwaltung.service.MaterialService;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integrationstests für den Materialeingang (T13) mit echten DAOs und PostgreSQL.
 * Bestand und Lagerbewegungen werden direkt in der Datenbank nachgeprüft.
 */
@Testcontainers
class MaterialServiceIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static ConnectionConfig config;

    private ZaehlendeConnectionFactory connectionFactory;
    private MaterialService service;

    private int maus;
    private int anna;

    @BeforeAll
    static void datenbankVorbereiten() {
        config = new ConnectionConfig(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        DatabaseMigration.migrate(config);
    }

    @BeforeEach
    void vorbereiten() throws SQLException {
        sql("TRUNCATE \"Lagerbewegung\", \"Artikel\", \"Mitarbeiter\" RESTART IDENTITY");
        maus = legeArtikelAn("Maus", 0);
        anna = legeMitarbeiterAn("Anna");
        connectionFactory = new ZaehlendeConnectionFactory(config);
        service = new MaterialService(new JdbcArtikelDAO(), new JdbcMitarbeiterDAO(),
                new JdbcLagerbewegungDAO(), connectionFactory);
    }

    // ---------- Erfolgreiche Eingänge ----------

    @Test
    void eingangErhoehtBestandUndSpeichertGenauEineBewegung() throws SQLException {
        OffsetDateTime vorher = OffsetDateTime.now().minusMinutes(1);

        Buchungsergebnis ergebnis = service.einlagern(maus, anna, 5);

        assertEquals(5, ergebnis.neuerBestand());
        LagerbewegungBO bewegung = ergebnis.bewegung();
        assertTrue(bewegung.getLagerbewegungId() > 0);
        assertEquals(maus, bewegung.getArtikelId());
        assertEquals(anna, bewegung.getMitarbeiterId(), "Mitarbeiter ist der Erfasser");
        assertEquals(Bewegungsart.EINGANG, bewegung.getBewegungsart());
        assertEquals(5, bewegung.getMenge());
        assertNotNull(bewegung.getZeitpunkt());
        assertTrue(bewegung.getZeitpunkt().isAfter(vorher));

        assertEquals(5, bestand(maus));
        assertEquals(List.of("Eingang|5|" + anna), bewegungen(maus));
    }

    @Test
    void mehrereEingaengeSummierenSich() throws SQLException {
        assertEquals(5, service.einlagern(maus, anna, 5).neuerBestand());
        assertEquals(8, service.einlagern(maus, anna, 3).neuerBestand());

        assertEquals(8, bestand(maus));
        assertEquals(2, bewegungen(maus).size());
        assertEquals(8, summeEingaenge(maus), "Bestand = Summe aller Eingänge");
    }

    @Test
    void eingangBisGenauZumMaximalenBestandIstErlaubt() throws SQLException {
        setzeBestand(maus, Integer.MAX_VALUE - 2);

        Buchungsergebnis ergebnis = service.einlagern(maus, anna, 2);

        assertEquals(Integer.MAX_VALUE, ergebnis.neuerBestand());
        assertEquals(Integer.MAX_VALUE, bestand(maus));
    }

    // ---------- Ungültige Angaben und fehlende Referenzen ----------

    @Test
    void ungueltigeMengeWirdOhneDatenaenderungAbgelehnt() throws SQLException {
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.einlagern(maus, anna, 0));
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.einlagern(maus, anna, -4));

        assertKeineAenderung(0);
        assertTrue(connectionFactory.geoeffnet.isEmpty(), "Prüfung erfolgt vor dem Öffnen der Verbindung");
    }

    @Test
    void unbekannterArtikelWirdAbgelehnt() throws SQLException {
        assertFachlich(FachlicherFehler.ARTIKEL_NICHT_GEFUNDEN, () -> service.einlagern(999, anna, 5));
        assertEquals(0, anzahlBewegungen());
    }

    @Test
    void unbekannterMitarbeiterWirdOhneTeilbuchungAbgelehnt() throws SQLException {
        assertFachlich(FachlicherFehler.MITARBEITER_NICHT_GEFUNDEN, () -> service.einlagern(maus, 999, 5));
        assertKeineAenderung(0);
    }

    @Test
    void bestandsueberlaufWirdOhneDatenaenderungAbgelehnt() throws SQLException {
        setzeBestand(maus, Integer.MAX_VALUE - 2);

        FachlicheException e = assertFachlich(FachlicherFehler.BESTANDSUEBERLAUF,
                () -> service.einlagern(maus, anna, 3));
        assertTrue(e.getMessage().contains(String.valueOf(Integer.MAX_VALUE)), e.getMessage());
        assertFachlich(FachlicherFehler.BESTANDSUEBERLAUF, () -> service.einlagern(maus, anna, Integer.MAX_VALUE));

        assertKeineAenderung(Integer.MAX_VALUE - 2);
    }

    // ---------- Transaktion, Rollback und Sperre ----------

    @Test
    void fehlerBeimSpeichernDerBewegungRolltDieBestandsaenderungZurueck() throws SQLException {
        service.einlagern(maus, anna, 5);
        // Der zweite Schreibschritt (INSERT der Lagerbewegung) soll wirklich in der Datenbank scheitern:
        // Eine zusätzliche Prüfregel lässt nur Mengen unter 1000 zu. Die Bestandsänderung davor gelingt.
        sql("ALTER TABLE \"Lagerbewegung\" ADD CONSTRAINT test_menge_blockiert CHECK (\"Menge\" < 1000)");
        try {
            DatenzugriffsException e = assertThrows(DatenzugriffsException.class,
                    () -> service.einlagern(maus, anna, 1000));

            assertEquals("23514", e.getSqlState(), "Check-Verletzung bleibt ein technischer Fehler");
            assertEquals(5, bestand(maus), "Bestandserhöhung wurde zurückgerollt");
            assertEquals(1, anzahlBewegungen(), "keine weitere Bewegung");
        } finally {
            sql("ALTER TABLE \"Lagerbewegung\" DROP CONSTRAINT test_menge_blockiert");
        }
    }

    @Test
    void buchungWartetAufDieArtikelsperre() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection andere = new ConnectionFactory(config).openConnection()) {
            // Eine andere Transaktion hält die Sperre auf derselben Artikelzeile
            andere.setAutoCommit(false);
            try (PreparedStatement ps = andere.prepareStatement(
                    "SELECT 1 FROM \"Artikel\" WHERE \"ArtikelID\" = ? FOR UPDATE")) {
                ps.setInt(1, maus);
                ps.executeQuery().close();
            }

            Future<Buchungsergebnis> buchung = executor.submit(() -> service.einlagern(maus, anna, 4));

            assertThrows(TimeoutException.class, () -> buchung.get(700, TimeUnit.MILLISECONDS),
                    "Die Buchung muss warten, solange die Artikelzeile gesperrt ist");

            andere.commit(); // Sperre freigeben
            assertEquals(4, buchung.get(10, TimeUnit.SECONDS).neuerBestand());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(4, bestand(maus));
    }

    @Test
    void parallelEingaengeGehenNichtVerloren() throws Exception {
        int threads = 4;
        int buchungenProThread = 15;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> laeufe = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                laeufe.add(executor.submit(() -> {
                    for (int i = 0; i < buchungenProThread; i++) {
                        service.einlagern(maus, anna, 1);
                    }
                    return null;
                }));
            }
            for (Future<?> lauf : laeufe) {
                lauf.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        int erwartet = threads * buchungenProThread;
        assertEquals(erwartet, bestand(maus), "kein verlorenes Update");
        assertEquals(erwartet, anzahlBewegungen());
        assertEquals(erwartet, summeEingaenge(maus));
    }

    @Test
    void sonstigeDatenbankfehlerBleibenTechnisch() {
        ConnectionConfig falschesPasswort =
                new ConnectionConfig(config.url(), config.user(), config.password() + "-falsch");
        MaterialService ohneZugang = new MaterialService(new JdbcArtikelDAO(), new JdbcMitarbeiterDAO(),
                new JdbcLagerbewegungDAO(), new ConnectionFactory(falschesPasswort));

        assertThrows(DatenzugriffsException.class, () -> ohneZugang.einlagern(maus, anna, 1));
    }

    @Test
    void verbindungenWerdenBeiErfolgUndFehlerGeschlossen() throws SQLException {
        service.einlagern(maus, anna, 1);
        assertThrows(FachlicheException.class, () -> service.einlagern(999, anna, 1));
        assertThrows(FachlicheException.class, () -> service.einlagern(maus, 999, 1));
        setzeBestand(maus, Integer.MAX_VALUE);
        assertThrows(FachlicheException.class, () -> service.einlagern(maus, anna, 1));

        assertEquals(4, connectionFactory.geoeffnet.size());
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

    private void assertKeineAenderung(int erwarteterBestand) throws SQLException {
        assertEquals(erwarteterBestand, bestand(maus), "Bestand unverändert");
        assertEquals(0, anzahlBewegungen(), "keine Bewegung gespeichert");
    }

    private static void sql(String anweisung) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             Statement st = con.createStatement()) {
            st.execute(anweisung);
        }
    }

    private static int legeArtikelAn(String bezeichnung, int bestand) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO \"Artikel\" (\"Bezeichnung\", \"Bestand\") VALUES (?, ?) RETURNING \"ArtikelID\"")) {
            ps.setString(1, bezeichnung);
            ps.setInt(2, bestand);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int legeMitarbeiterAn(String name) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO \"Mitarbeiter\" (\"Name\") VALUES (?) RETURNING \"MitarbeiterID\"")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void setzeBestand(int artikelId, int bestand) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE \"Artikel\" SET \"Bestand\" = ? WHERE \"ArtikelID\" = ?")) {
            ps.setInt(1, bestand);
            ps.setInt(2, artikelId);
            assertEquals(1, ps.executeUpdate());
        }
    }

    private static int bestand(int artikelId) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT \"Bestand\" FROM \"Artikel\" WHERE \"ArtikelID\" = ?")) {
            ps.setInt(1, artikelId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private static int anzahlBewegungen() throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM \"Lagerbewegung\"")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Bewegungen eines Artikels als „Bewegungsart|Menge|MitarbeiterID“, älteste zuerst. */
    private static List<String> bewegungen(int artikelId) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT \"Bewegungsart\", \"Menge\", \"MitarbeiterID\" FROM \"Lagerbewegung\" "
                             + "WHERE \"ArtikelID\" = ? ORDER BY \"BewegungsID\"")) {
            ps.setInt(1, artikelId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> zeilen = new ArrayList<>();
                while (rs.next()) {
                    zeilen.add(rs.getString(1) + "|" + rs.getInt(2) + "|" + rs.getInt(3));
                }
                return zeilen;
            }
        }
    }

    private static long summeEingaenge(int artikelId) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COALESCE(SUM(\"Menge\"), 0) FROM \"Lagerbewegung\" "
                             + "WHERE \"ArtikelID\" = ? AND \"Bewegungsart\" = 'Eingang'")) {
            ps.setInt(1, artikelId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Echte ConnectionFactory, die sich alle geöffneten Verbindungen merkt. */
    private static final class ZaehlendeConnectionFactory extends ConnectionFactory {
        final List<Connection> geoeffnet = Collections.synchronizedList(new ArrayList<>());

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
