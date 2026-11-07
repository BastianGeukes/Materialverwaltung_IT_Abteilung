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
import materialverwaltung.exception.UnzureichenderBestandException;
import materialverwaltung.model.Bewegungsart;
import materialverwaltung.model.Buchungsergebnis;
import materialverwaltung.model.Historieneintrag;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integrationstests für Materialeingang (T13), Materialausgabe (T14) und Bewegungshistorie (T15)
 * mit echten DAOs und PostgreSQL.
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
    private int ben;

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
        ben = legeMitarbeiterAn("Ben");
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

    // ========== Materialausgabe (T14) ==========

    @Test
    void teilausgabeVermindertBestandUndSpeichertAusgangMitEmpfaenger() throws SQLException {
        service.einlagern(maus, anna, 5);

        Buchungsergebnis ergebnis = service.ausgeben(maus, ben, 2);

        assertEquals(3, ergebnis.neuerBestand());
        LagerbewegungBO bewegung = ergebnis.bewegung();
        assertEquals(Bewegungsart.AUSGANG, bewegung.getBewegungsart());
        assertEquals(2, bewegung.getMenge());
        assertEquals(ben, bewegung.getMitarbeiterId(), "Mitarbeiter ist der Empfänger");
        assertEquals(maus, bewegung.getArtikelId());
        assertTrue(bewegung.getLagerbewegungId() > 0);
        assertNotNull(bewegung.getZeitpunkt());

        assertEquals(3, bestand(maus));
        assertEquals(List.of("Eingang|5|" + anna, "Ausgang|2|" + ben), bewegungen(maus),
                "Eingang 5, Ausgang 2 -> genau zwei Bewegungen");
        assertBestandPasstZurHistorie(maus);
    }

    @Test
    void vollstaendigeEntnahmeGenauInBestandshoeheIstErlaubt() throws SQLException {
        service.einlagern(maus, anna, 5);

        Buchungsergebnis ergebnis = service.ausgeben(maus, ben, 5);

        assertEquals(0, ergebnis.neuerBestand());
        assertEquals(0, bestand(maus));
        assertEquals(2, bewegungen(maus).size());
        assertBestandPasstZurHistorie(maus);
    }

    @Test
    void ueberausgabeLoestUnzureichenderBestandExceptionOhneAenderungAus() throws SQLException {
        service.einlagern(maus, anna, 5);

        UnzureichenderBestandException e = assertThrows(UnzureichenderBestandException.class,
                () -> service.ausgeben(maus, ben, 6));

        assertInstanceOf(FachlicheException.class, e, "Unterklasse von FachlicheException");
        assertEquals(FachlicherFehler.UNZUREICHENDER_BESTAND, e.getFehler());
        assertEquals(maus, e.getArtikelId());
        assertEquals(6, e.getAngefordert());
        assertEquals(5, e.getVerfuegbar());
        assertTrue(e.getMessage().contains("verfügbar sind nur 5"), e.getMessage());

        assertEquals(5, bestand(maus), "keine Änderung");
        assertEquals(List.of("Eingang|5|" + anna), bewegungen(maus), "keine Ausgangsbewegung");
    }

    @Test
    void ausgabeBeiBestandNullWirdAbgelehnt() throws SQLException {
        UnzureichenderBestandException e = assertThrows(UnzureichenderBestandException.class,
                () -> service.ausgeben(maus, ben, 1));

        assertEquals(0, e.getVerfuegbar());
        assertKeineAenderung(0);
    }

    @Test
    void ausgabeMitUngueltigenAngabenOderFehlendenReferenzenHinterlaesstKeineTeilbuchung() throws SQLException {
        service.einlagern(maus, anna, 5);

        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.ausgeben(maus, ben, 0));
        assertFachlich(FachlicherFehler.UNGUELTIGE_EINGABE, () -> service.ausgeben(maus, ben, -2));
        assertFachlich(FachlicherFehler.ARTIKEL_NICHT_GEFUNDEN, () -> service.ausgeben(999, ben, 1));
        assertFachlich(FachlicherFehler.MITARBEITER_NICHT_GEFUNDEN, () -> service.ausgeben(maus, 999, 1));

        assertEquals(5, bestand(maus));
        assertEquals(1, anzahlBewegungen());
    }

    @Test
    void technischerFehlerBeiDerAusgabeHinterlaesstKeineTeilbuchung() throws SQLException {
        service.einlagern(maus, anna, 5);
        // Das Speichern der Ausgangsbewegung scheitert wirklich in der Datenbank,
        // nachdem der Bestand bereits vermindert wurde.
        sql("ALTER TABLE \"Lagerbewegung\" ADD CONSTRAINT test_kein_ausgang CHECK (\"Bewegungsart\" <> 'Ausgang')");
        try {
            DatenzugriffsException e = assertThrows(DatenzugriffsException.class,
                    () -> service.ausgeben(maus, ben, 3));

            assertEquals("23514", e.getSqlState());
            assertEquals(5, bestand(maus), "Bestandsminderung wurde zurückgerollt");
            assertEquals(1, anzahlBewegungen());
        } finally {
            sql("ALTER TABLE \"Lagerbewegung\" DROP CONSTRAINT test_kein_ausgang");
        }
    }

    @Test
    void zweiGleichzeitigeAusgabenVonJeVierBeiBestandFuenf() throws Exception {
        // Beispiel aus T01: genau eine Ausgabe gelingt, Endbestand 1
        for (int runde = 0; runde < 5; runde++) {
            sql("TRUNCATE \"Lagerbewegung\"");
            setzeBestand(maus, 5);

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                List<Future<Buchungsergebnis>> ausgaben = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    ausgaben.add(executor.submit(() -> {
                        start.await();
                        return service.ausgeben(maus, ben, 4);
                    }));
                }
                start.countDown();

                int erfolgreich = 0;
                int abgelehnt = 0;
                for (Future<Buchungsergebnis> ausgabe : ausgaben) {
                    try {
                        assertEquals(1, ausgabe.get(30, TimeUnit.SECONDS).neuerBestand());
                        erfolgreich++;
                    } catch (ExecutionException e) {
                        assertInstanceOf(UnzureichenderBestandException.class, e.getCause());
                        abgelehnt++;
                    }
                }
                assertEquals(1, erfolgreich, "genau eine Ausgabe gelingt (Runde " + runde + ")");
                assertEquals(1, abgelehnt);
            } finally {
                executor.shutdownNow();
            }
            assertEquals(1, bestand(maus), "Endbestand 1 (Runde " + runde + ")");
            assertEquals(1, anzahlBewegungen());
        }
    }

    @Test
    void parallelAusgabenGebenNieMehrAlsDenBestandAus() throws Exception {
        service.einlagern(maus, anna, 50);
        int threads = 8;
        int versucheProThread = 10;
        AtomicInteger erfolgreich = new AtomicInteger();
        AtomicInteger abgelehnt = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> laeufe = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                laeufe.add(executor.submit(() -> {
                    for (int i = 0; i < versucheProThread; i++) {
                        try {
                            service.ausgeben(maus, ben, 1);
                            erfolgreich.incrementAndGet();
                        } catch (UnzureichenderBestandException e) {
                            abgelehnt.incrementAndGet();
                        }
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

        assertEquals(50, erfolgreich.get(), "genau der Bestand wird ausgegeben");
        assertEquals(30, abgelehnt.get());
        assertEquals(0, bestand(maus));
        assertEquals(51, anzahlBewegungen(), "1 Eingang + 50 Ausgänge");
        assertBestandPasstZurHistorie(maus);
    }

    @Test
    void verbindungenWerdenAuchBeiAusgabenGeschlossen() throws SQLException {
        service.einlagern(maus, anna, 2);
        service.ausgeben(maus, ben, 1);
        assertThrows(UnzureichenderBestandException.class, () -> service.ausgeben(maus, ben, 5));
        assertThrows(FachlicheException.class, () -> service.ausgeben(maus, 999, 1));

        assertEquals(4, connectionFactory.geoeffnet.size());
        for (Connection con : connectionFactory.geoeffnet) {
            assertTrue(con.isClosed());
        }
    }

    // ========== Bewegungshistorie (T15) ==========

    @Test
    void leereHistorieIstEinGueltigesErgebnis() {
        assertEquals(List.of(), service.historie());
    }

    @Test
    void historieEnthaeltAlleAngabenMitDerRichtigenMitarbeiterrolle() throws SQLException {
        int tastatur = legeArtikelAn("Tastatur", 0);
        Buchungsergebnis eingang = service.einlagern(maus, anna, 5);
        Buchungsergebnis ausgang = service.ausgeben(maus, ben, 2);
        Buchungsergebnis eingang2 = service.einlagern(tastatur, ben, 7);

        List<Historieneintrag> historie = service.historie();

        assertEquals(3, historie.size());
        // Neueste Bewegung zuerst
        Historieneintrag e0 = historie.get(0);
        assertEquals(eingang2.bewegung().getLagerbewegungId(), e0.bewegung().getLagerbewegungId());
        assertEintrag(e0, tastatur, "Tastatur", ben, "Ben", Bewegungsart.EINGANG, 7, "Erfasser");

        Historieneintrag e1 = historie.get(1);
        assertEquals(ausgang.bewegung().getLagerbewegungId(), e1.bewegung().getLagerbewegungId());
        assertEintrag(e1, maus, "Maus", ben, "Ben", Bewegungsart.AUSGANG, 2, "Empfänger");

        Historieneintrag e2 = historie.get(2);
        assertEquals(eingang.bewegung().getLagerbewegungId(), e2.bewegung().getLagerbewegungId());
        assertEintrag(e2, maus, "Maus", anna, "Anna", Bewegungsart.EINGANG, 5, "Erfasser");

        // Zeitpunkte stimmen mit den gespeicherten Bewegungen überein
        assertEquals(eingang.bewegung().getZeitpunkt().toInstant(), e2.zeitpunkt().toInstant());
        for (Historieneintrag eintrag : historie) {
            assertNotNull(eintrag.zeitpunkt());
        }
    }

    @Test
    void reihenfolgeZeitpunktAbsteigendUndBeiGleichstandIdAbsteigend() throws SQLException {
        // Zeitpunkte direkt setzen, um die festgelegte Sortierung gezielt zu prüfen
        int a = legeBewegungAn(maus, anna, "Eingang", 1, "2026-11-01T10:00:00Z");
        int b = legeBewegungAn(maus, anna, "Eingang", 2, "2026-11-03T10:00:00Z");
        int c = legeBewegungAn(maus, ben, "Ausgang", 1, "2026-11-02T10:00:00Z");
        int d = legeBewegungAn(maus, anna, "Eingang", 3, "2026-11-03T10:00:00Z"); // gleicher Zeitpunkt wie b

        List<Integer> ids = service.historie().stream()
                .map(e -> e.bewegung().getLagerbewegungId()).toList();

        assertEquals(List.of(d, b, c, a), ids);
    }

    @Test
    void historieZeigtDieAktuellenNamenUndBleibtSonstUnveraendert() throws SQLException {
        Buchungsergebnis eingang = service.einlagern(maus, anna, 5);
        sql("UPDATE \"Artikel\" SET \"Bezeichnung\" = 'Funkmaus' WHERE \"ArtikelID\" = " + maus);
        sql("UPDATE \"Mitarbeiter\" SET \"Name\" = 'Anna Schulz' WHERE \"MitarbeiterID\" = " + anna);

        Historieneintrag eintrag = service.historie().getFirst();

        assertEquals("Funkmaus", eintrag.artikelBezeichnung(), "aktuelle Artikelbezeichnung");
        assertEquals("Anna Schulz", eintrag.mitarbeiterName(), "aktueller Mitarbeitername");
        assertEquals(eingang.bewegung().getLagerbewegungId(), eintrag.bewegung().getLagerbewegungId());
        assertEquals(5, eintrag.menge(), "die Bewegung selbst bleibt unverändert");
        assertEquals(Bewegungsart.EINGANG, eintrag.bewegungsart());
    }

    @Test
    void historieOeffnetGenauEineVerbindungUndSchliesstSie() throws SQLException {
        service.einlagern(maus, anna, 1);
        connectionFactory.geoeffnet.clear();

        service.historie();
        service.historie();

        assertEquals(2, connectionFactory.geoeffnet.size());
        for (Connection con : connectionFactory.geoeffnet) {
            assertTrue(con.isClosed());
        }
    }

    @Test
    void historieBeiTechnischemFehler() {
        ConnectionConfig falschesPasswort =
                new ConnectionConfig(config.url(), config.user(), config.password() + "-falsch");
        MaterialService ohneZugang = new MaterialService(new JdbcArtikelDAO(), new JdbcMitarbeiterDAO(),
                new JdbcLagerbewegungDAO(), new ConnectionFactory(falschesPasswort));

        assertThrows(DatenzugriffsException.class, ohneZugang::historie);
    }

    private static void assertEintrag(Historieneintrag e, int artikelId, String bezeichnung, int mitarbeiterId,
                                      String name, Bewegungsart art, int menge, String rolle) {
        assertEquals(artikelId, e.artikelId());
        assertEquals(bezeichnung, e.artikelBezeichnung());
        assertEquals(mitarbeiterId, e.mitarbeiterId());
        assertEquals(name, e.mitarbeiterName());
        assertEquals(art, e.bewegungsart());
        assertEquals(menge, e.menge());
        assertEquals(rolle, e.mitarbeiterRolle());
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

    /** Bestand = Summe aller Eingänge − Summe aller Ausgänge (T01, Abschnitt 3). */
    private static void assertBestandPasstZurHistorie(int artikelId) throws SQLException {
        assertEquals(summe(artikelId, "Eingang") - summe(artikelId, "Ausgang"), bestand(artikelId),
                "Bestand muss zur Bewegungshistorie passen");
    }

    private static long summeEingaenge(int artikelId) throws SQLException {
        return summe(artikelId, "Eingang");
    }

    private static long summe(int artikelId, String bewegungsart) throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COALESCE(SUM(\"Menge\"), 0) FROM \"Lagerbewegung\" "
                             + "WHERE \"ArtikelID\" = ? AND \"Bewegungsart\" = ?")) {
            ps.setInt(1, artikelId);
            ps.setString(2, bewegungsart);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Legt eine Bewegung mit festem Zeitpunkt direkt per SQL an und liefert ihre ID. */
    private static int legeBewegungAn(int artikelId, int mitarbeiterId, String art, int menge, String zeitpunkt)
            throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO \"Lagerbewegung\" (\"ArtikelID\", \"MitarbeiterID\", \"Menge\", \"Bewegungsart\", "
                             + "\"Bewegungszeitpunkt\") VALUES (?, ?, ?, ?, ?::timestamptz) RETURNING \"BewegungsID\"")) {
            ps.setInt(1, artikelId);
            ps.setInt(2, mitarbeiterId);
            ps.setInt(3, menge);
            ps.setString(4, art);
            ps.setString(5, zeitpunkt);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
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
