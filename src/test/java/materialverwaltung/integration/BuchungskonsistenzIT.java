package materialverwaltung.integration;

import materialverwaltung.dao.jdbc.JdbcArtikelDAO;
import materialverwaltung.dao.jdbc.JdbcLagerbewegungDAO;
import materialverwaltung.dao.jdbc.JdbcMitarbeiterDAO;
import materialverwaltung.db.ConnectionConfig;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.db.DatabaseMigration;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.exception.UnzureichenderBestandException;
import materialverwaltung.model.Buchungsergebnis;
import materialverwaltung.service.MaterialService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T20 – Nachweis der Datenkonsistenz vollständiger Buchungsabläufe (Commit, Rollback,
 * konkurrierende Buchungen) mit echten JDBC-DAOs, MaterialService und PostgreSQL.
 *
 * <p>Alle Prüfungen lesen den Datenbestand über <em>neu geöffnete</em> Verbindungen,
 * nicht über die Verbindung des Service. Damit wird nachgewiesen, was tatsächlich
 * bestätigt (committet) bzw. zurückgerollt wurde.</p>
 *
 * <p>Ausgangslage jedes Tests: Artikel „Maus“ (ID 1) mit vorhandener Historie,
 * Mitarbeiter „Anna“ (ID 1) und „Ben“ (ID 2). Geprüft werden daher jeweils die
 * <em>zusätzlichen</em> Bewegungen.</p>
 */
@Testcontainers
class BuchungskonsistenzIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static ConnectionConfig config;

    private MaterialService service;

    private final int maus = 1;
    private final int anna = 1;
    private final int ben = 2;

    @BeforeAll
    static void datenbankVorbereiten() {
        config = new ConnectionConfig(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        DatabaseMigration.migrate(config);
    }

    @BeforeEach
    void ausgangslageHerstellen() throws SQLException {
        sql("TRUNCATE \"Lagerbewegung\", \"Artikel\", \"Mitarbeiter\" RESTART IDENTITY");
        sql("INSERT INTO \"Mitarbeiter\" (\"Name\") VALUES ('Anna'), ('Ben')");
        sql("INSERT INTO \"Artikel\" (\"Bezeichnung\") VALUES ('Maus')");
        service = neuerService();
        // Vorhandene Historie, damit „zusätzliche“ Bewegungen eindeutig unterscheidbar sind
        service.einlagern(maus, anna, 10);
        service.ausgeben(maus, ben, 10);
        assertEquals(0, bestand(maus));
        assertEquals(2, anzahlBewegungen());
    }

    // ---------- Normalfall: Eingang 5, Ausgang 2 ----------

    @Test
    void eingangFuenfAusgangZweiErgibtBestandDreiUndGenauZweiZusaetzlicheBewegungen() throws SQLException {
        int bewegungenVorher = anzahlBewegungen();
        int letzteIdVorher = hoechsteBewegungsId();

        Buchungsergebnis eingang = service.einlagern(maus, anna, 5);
        Buchungsergebnis ausgang = service.ausgeben(maus, ben, 2);

        assertEquals(5, eingang.neuerBestand());
        assertEquals(3, ausgang.neuerBestand());

        // Prüfung über neue Verbindungen
        assertEquals(3, bestand(maus), "Bestand 3");
        assertEquals(bewegungenVorher + 2, anzahlBewegungen(), "genau zwei zusätzliche Bewegungen");
        assertEquals(List.of(
                        "Artikel " + maus + "|Mitarbeiter " + anna + "|Eingang|5",
                        "Artikel " + maus + "|Mitarbeiter " + ben + "|Ausgang|2"),
                bewegungenNach(letzteIdVorher), "korrekt zugeordnet: Erfasser Anna, Empfänger Ben");
        assertEquals(eingang.bewegung().getLagerbewegungId(), letzteIdVorher + 1);
        assertEquals(ausgang.bewegung().getLagerbewegungId(), letzteIdVorher + 2);
        assertBestandGleichHistorie(maus);
    }

    // ---------- Commit ----------

    @Test
    void erfolgreicherCommitIstUeberNeuGeoeffneteVerbindungSichtbar() throws SQLException {
        // Eine Verbindung, die schon vor der Buchung offen ist, sieht den Commit ebenfalls (READ COMMITTED)
        try (Connection schonOffen = new ConnectionFactory(config).openConnection()) {
            assertEquals(0, bestand(schonOffen, maus));

            Buchungsergebnis ergebnis = service.einlagern(maus, anna, 7);

            try (Connection neu = new ConnectionFactory(config).openConnection()) {
                assertEquals(7, bestand(neu, maus), "neu geöffnete Verbindung sieht den bestätigten Bestand");
                assertTrue(bewegungVorhanden(neu, ergebnis.bewegung().getLagerbewegungId()),
                        "neu geöffnete Verbindung sieht die bestätigte Bewegung");
            }
            assertEquals(7, bestand(schonOffen, maus), "auch eine bereits offene Verbindung sieht den Commit");
        }
    }

    // ---------- Rollback (festgelegter Test aus T20) ----------

    @Test
    void fehlerBeimZweitenSchreibschrittFuehrtZumVollstaendigenRollback() throws SQLException {
        // Zusätzlicher Artikel ohne Bewegungen
        int kabel = legeArtikelAn("Testkabel ohne Bewegungen", 4);
        int bewegungenVorher = anzahlBewegungen();
        long bestandsUpdatesVorher;

        // Nur in der Testdatenbank: vorübergehende Constraint, die genau den Bewegungseintrag dieses Artikels ablehnt,
        // und ein Zähler, der beweist, dass das Bestands-Update vorher tatsächlich ausgeführt wurde.
        // (Eine Sequenz wird von einem Rollback nicht zurückgesetzt.)
        sql("ALTER TABLE \"Lagerbewegung\" ADD CONSTRAINT t20_bewegung_abgelehnt CHECK (\"ArtikelID\" <> " + kabel + ")");
        sql("CREATE SEQUENCE t20_bestandsupdates");
        sql("CREATE FUNCTION t20_zaehle_update() RETURNS trigger LANGUAGE plpgsql AS "
                + "$$ BEGIN PERFORM nextval('t20_bestandsupdates'); RETURN NEW; END $$");
        sql("CREATE TRIGGER t20_bestandsupdate_zaehlen AFTER UPDATE OF \"Bestand\" ON \"Artikel\" "
                + "FOR EACH ROW WHEN (NEW.\"ArtikelID\" = " + kabel + ") EXECUTE FUNCTION t20_zaehle_update()");
        try {
            bestandsUpdatesVorher = bestandsUpdatesGezaehlt();

            DatenzugriffsException eingangFehler = assertThrows(DatenzugriffsException.class,
                    () -> service.einlagern(kabel, anna, 3));
            DatenzugriffsException ausgangFehler = assertThrows(DatenzugriffsException.class,
                    () -> service.ausgeben(kabel, ben, 1));

            assertEquals("23514", eingangFehler.getSqlState(), "das echte Insert scheitert an der Constraint");
            assertEquals("23514", ausgangFehler.getSqlState());
            assertEquals(bestandsUpdatesVorher + 2, bestandsUpdatesGezaehlt(),
                    "das Bestands-Update ist vor dem scheiternden Insert jeweils gelungen");
        } finally {
            // Zuverlässig entfernen, auch wenn eine Prüfung oben scheitert
            sql("DROP TRIGGER IF EXISTS t20_bestandsupdate_zaehlen ON \"Artikel\"");
            sql("DROP FUNCTION IF EXISTS t20_zaehle_update()");
            sql("DROP SEQUENCE IF EXISTS t20_bestandsupdates");
            sql("ALTER TABLE \"Lagerbewegung\" DROP CONSTRAINT IF EXISTS t20_bewegung_abgelehnt");
        }

        // Prüfung über neue Verbindungen: ursprünglicher Bestand, keine zusätzliche Bewegung
        assertEquals(4, bestand(kabel), "ursprünglicher Bestand wiederhergestellt");
        assertEquals(0, bewegungenVon(kabel), "keine Bewegung für den Artikel");
        assertEquals(bewegungenVorher, anzahlBewegungen(), "keine zusätzliche Bewegung insgesamt");
        assertFalse(constraintVorhanden("t20_bewegung_abgelehnt"), "Constraint wurde entfernt");

        // Nach dem Entfernen funktionieren Buchungen für den Artikel wieder normal
        assertEquals(7, service.einlagern(kabel, anna, 3).neuerBestand());
    }

    // ---------- Konkurrierende Ausgaben ----------

    @RepeatedTest(10)
    void zweiGleichzeitigeAusgabenEntnehmenZusammenNichtMehrAlsDenBestand() throws Exception {
        service.einlagern(maus, anna, 5);
        int bewegungenVorher = anzahlBewegungen();

        List<Object> ergebnisse = gleichzeitig(2, () -> service.ausgeben(maus, ben, 4));

        long erfolgreich = ergebnisse.stream().filter(Buchungsergebnis.class::isInstance).count();
        long abgelehnt = ergebnisse.stream().filter(UnzureichenderBestandException.class::isInstance).count();
        assertEquals(1, erfolgreich, "genau eine Ausgabe gelingt: " + ergebnisse);
        assertEquals(1, abgelehnt, "die andere wird wegen unzureichenden Bestands abgelehnt");
        assertEquals(1, bestand(maus), "Endbestand 5 − 4 = 1");
        assertEquals(bewegungenVorher + 1, anzahlBewegungen(), "nur eine zusätzliche Ausgangsbewegung");
        assertBestandGleichHistorie(maus);
    }

    @Test
    void vieleGleichzeitigeAusgabenEntnehmenNieMehrAlsDenBestand() throws Exception {
        service.einlagern(maus, anna, 20);
        AtomicInteger entnommen = new AtomicInteger();

        List<Object> ergebnisse = gleichzeitig(12, () -> {
            Buchungsergebnis b = service.ausgeben(maus, ben, 3);
            entnommen.addAndGet(3);
            return b;
        });

        long erfolgreich = ergebnisse.stream().filter(Buchungsergebnis.class::isInstance).count();
        assertEquals(6, erfolgreich, "20 / 3 -> höchstens 6 Ausgaben");
        assertEquals(18, entnommen.get());
        assertEquals(2, bestand(maus));
        assertTrue(ergebnisse.stream().allMatch(e -> e instanceof Buchungsergebnis
                || e instanceof UnzureichenderBestandException), "nur fachliche Ablehnungen: " + ergebnisse);
        assertBestandGleichHistorie(maus);
    }

    // ---------- Hilfsmethoden ----------

    private MaterialService neuerService() {
        return new MaterialService(new JdbcArtikelDAO(), new JdbcMitarbeiterDAO(), new JdbcLagerbewegungDAO(),
                new ConnectionFactory(config));
    }

    @FunctionalInterface
    private interface Buchung {
        Buchungsergebnis ausfuehren();
    }

    /**
     * Startet die Buchungen möglichst gleichzeitig (gemeinsames Startsignal) und liefert je
     * Buchung das Ergebnis oder die geworfene Exception.
     */
    private static List<Object> gleichzeitig(int anzahl, Buchung buchung) throws Exception {
        CountDownLatch bereit = new CountDownLatch(anzahl);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(anzahl);
        try {
            List<Future<Buchungsergebnis>> laeufe = new ArrayList<>();
            for (int i = 0; i < anzahl; i++) {
                laeufe.add(executor.submit(() -> {
                    bereit.countDown();
                    start.await();
                    return buchung.ausfuehren();
                }));
            }
            assertTrue(bereit.await(30, TimeUnit.SECONDS));
            start.countDown();

            List<Object> ergebnisse = new ArrayList<>();
            for (Future<Buchungsergebnis> lauf : laeufe) {
                try {
                    ergebnisse.add(lauf.get(60, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    ergebnisse.add(e.getCause());
                }
            }
            return ergebnisse;
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertBestandGleichHistorie(int artikelId) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COALESCE(SUM(CASE WHEN \"Bewegungsart\" = 'Eingang' THEN \"Menge\" ELSE -\"Menge\" END), 0) "
                             + "FROM \"Lagerbewegung\" WHERE \"ArtikelID\" = ?")) {
            ps.setInt(1, artikelId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(rs.getLong(1), bestand(artikelId), "Bestand = Summe Eingänge − Summe Ausgänge");
            }
        }
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

    /** Bestand über eine neu geöffnete Verbindung. */
    private static int bestand(int artikelId) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection()) {
            return bestand(con, artikelId);
        }
    }

    private static int bestand(Connection con, int artikelId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT \"Bestand\" FROM \"Artikel\" WHERE \"ArtikelID\" = ?")) {
            ps.setInt(1, artikelId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Artikel " + artikelId + " fehlt");
                return rs.getInt(1);
            }
        }
    }

    private static int anzahlBewegungen() throws SQLException {
        return zahl("SELECT COUNT(*) FROM \"Lagerbewegung\"");
    }

    private static int hoechsteBewegungsId() throws SQLException {
        return zahl("SELECT COALESCE(MAX(\"BewegungsID\"), 0) FROM \"Lagerbewegung\"");
    }

    private static int bewegungenVon(int artikelId) throws SQLException {
        return zahl("SELECT COUNT(*) FROM \"Lagerbewegung\" WHERE \"ArtikelID\" = " + artikelId);
    }

    private static long bestandsUpdatesGezaehlt() throws SQLException {
        // last_value ist 1 vor dem ersten nextval; is_called unterscheidet „noch nie“ von „einmal“
        return zahl("SELECT CASE WHEN is_called THEN last_value ELSE 0 END FROM t20_bestandsupdates");
    }

    private static boolean constraintVorhanden(String name) throws SQLException {
        return zahl("SELECT COUNT(*) FROM pg_constraint WHERE conname = '" + name + "'") > 0;
    }

    private static int zahl(String abfrage) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(abfrage)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static boolean bewegungVorhanden(Connection con, int bewegungsId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM \"Lagerbewegung\" WHERE \"BewegungsID\" = ?")) {
            ps.setInt(1, bewegungsId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Bewegungen mit ID größer als {@code nachId}, aufsteigend: „Artikel x|Mitarbeiter y|Art|Menge“. */
    private static List<String> bewegungenNach(int nachId) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT \"ArtikelID\", \"MitarbeiterID\", \"Bewegungsart\", \"Menge\" FROM \"Lagerbewegung\" "
                             + "WHERE \"BewegungsID\" > ? ORDER BY \"BewegungsID\"")) {
            ps.setInt(1, nachId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> zeilen = new ArrayList<>();
                while (rs.next()) {
                    zeilen.add("Artikel " + rs.getInt(1) + "|Mitarbeiter " + rs.getInt(2) + "|"
                            + rs.getString(3) + "|" + rs.getInt(4));
                }
                return zeilen;
            }
        }
    }
}
