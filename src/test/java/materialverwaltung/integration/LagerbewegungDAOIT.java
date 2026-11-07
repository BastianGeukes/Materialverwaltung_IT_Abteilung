package materialverwaltung.integration;

import materialverwaltung.dao.jdbc.JdbcArtikelDAO;
import materialverwaltung.dao.jdbc.JdbcLagerbewegungDAO;
import materialverwaltung.dao.jdbc.JdbcMitarbeiterDAO;
import materialverwaltung.db.ConnectionConfig;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.db.DatabaseMigration;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.model.Abteilung;
import materialverwaltung.model.ArtikelBO;
import materialverwaltung.model.Bewegungsart;
import materialverwaltung.model.LagerbewegungBO;
import materialverwaltung.model.MitarbeiterBO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class LagerbewegungDAOIT {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    static ConnectionFactory connectionFactory;

    private final JdbcArtikelDAO daoArtikel = new JdbcArtikelDAO();
    private final JdbcMitarbeiterDAO daoMitarbeiter =
            new JdbcMitarbeiterDAO();
    private final JdbcLagerbewegungDAO daoLagerbewegung =
            new JdbcLagerbewegungDAO();

    @BeforeAll
    static void datenbankVorbereiten() {
        ConnectionConfig config = new ConnectionConfig(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );

        DatabaseMigration.migrate(config);
        connectionFactory = new ConnectionFactory(config);
    }

    @Test
    void anlegenTestEingangUndAusgangAnlegen() throws SQLException {
        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                // Vorbereiten
                ArtikelBO artikel = daoArtikel.anlegen(con, "Maus");
                MitarbeiterBO mitarbeiter = daoMitarbeiter.anlegen(
                        con,
                        "IchHasseDasAllesUndWillDasAllesNichtmehr",
                        Abteilung.IT
                );

                // Ausführen
                LagerbewegungBO angelegtEingang =
                        daoLagerbewegung.anlegen(
                                con,
                                artikel.getArtikelId(),
                                mitarbeiter.getMitarbeiterId(),
                                Bewegungsart.EINGANG,
                                10
                        );

                LagerbewegungBO angelegtAusgang =
                        daoLagerbewegung.anlegen(
                                con,
                                artikel.getArtikelId(),
                                mitarbeiter.getMitarbeiterId(),
                                Bewegungsart.AUSGANG,
                                10
                        );

                // Rückgaben prüfen
                assertNotNull(angelegtEingang);
                assertNotNull(angelegtAusgang);

                assertTrue(angelegtEingang.getLagerbewegungId() > 0);
                assertTrue(angelegtAusgang.getLagerbewegungId() > 0);
                assertNotEquals(
                        angelegtEingang.getLagerbewegungId(),
                        angelegtAusgang.getLagerbewegungId()
                );

                assertEquals(
                        artikel.getArtikelId(),
                        angelegtEingang.getArtikelId()
                );
                assertEquals(
                        mitarbeiter.getMitarbeiterId(),
                        angelegtEingang.getMitarbeiterId()
                );
                assertEquals(
                        Bewegungsart.EINGANG,
                        angelegtEingang.getBewegungsart()
                );
                assertEquals(10, angelegtEingang.getMenge());
                assertNotNull(angelegtEingang.getZeitpunkt());

                assertEquals(
                        artikel.getArtikelId(),
                        angelegtAusgang.getArtikelId()
                );
                assertEquals(
                        mitarbeiter.getMitarbeiterId(),
                        angelegtAusgang.getMitarbeiterId()
                );
                assertEquals(
                        Bewegungsart.AUSGANG,
                        angelegtAusgang.getBewegungsart()
                );
                assertEquals(10, angelegtAusgang.getMenge());
                assertNotNull(angelegtAusgang.getZeitpunkt());

                // Gespeicherte Daten erneut lesen
                List<LagerbewegungBO> gefunden =
                        daoLagerbewegung.findeAlle(con);

                assertEquals(2, gefunden.size());

                // Gleicher Zeitpunkt: höhere Bewegungs-ID zuerst.
                pruefeBewegung(angelegtAusgang, gefunden.get(0));
                pruefeBewegung(angelegtEingang, gefunden.get(1));
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void zeitpunktTest() throws SQLException {
        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                ArtikelBO artikel = daoArtikel.anlegen(con, "Maus");
                MitarbeiterBO mitarbeiter = daoMitarbeiter.anlegen(
                        con,
                        "IchHasseDasAllesUndWillDasAllesNichtmehr",
                        Abteilung.IT
                );

                LagerbewegungBO angelegt = daoLagerbewegung.anlegen(
                        con,
                        artikel.getArtikelId(),
                        mitarbeiter.getMitarbeiterId(),
                        Bewegungsart.EINGANG,
                        10
                );

                List<LagerbewegungBO> gelesen =
                        daoLagerbewegung.findeAlle(con);

                assertEquals(1, gelesen.size());

                LagerbewegungBO bewegung = gelesen.getFirst();

                assertEquals(
                        angelegt.getLagerbewegungId(),
                        bewegung.getLagerbewegungId()
                );

                // Vor toInstant() auf null prüfen.
                assertNotNull(angelegt.getZeitpunkt());
                assertNotNull(bewegung.getZeitpunkt());

                assertEquals(
                        angelegt.getZeitpunkt().toInstant(),
                        bewegung.getZeitpunkt().toInstant()
                );
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void mehrereArtikelMitarbeiterTest() throws SQLException {
        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                MitarbeiterBO m1 = daoMitarbeiter.anlegen(
                        con, "Ich Kann", Abteilung.IT
                );
                MitarbeiterBO m2 = daoMitarbeiter.anlegen(
                        con, "Das Alles", Abteilung.PRODUKTION
                );
                MitarbeiterBO m3 = daoMitarbeiter.anlegen(
                        con, "Nicht Mehr", Abteilung.EINKAUF
                );

                ArtikelBO a1 = daoArtikel.anlegen(
                        con, "blablabalblabvlablabla"
                );
                ArtikelBO a2 = daoArtikel.anlegen(
                        con, "haahahhahahahahahaah"
                );
                ArtikelBO a3 = daoArtikel.anlegen(
                        con, "ahhhhhhhhhhhhhhhhhhh"
                );

                LagerbewegungBO l1 = daoLagerbewegung.anlegen(
                        con,
                        a1.getArtikelId(),
                        m1.getMitarbeiterId(),
                        Bewegungsart.EINGANG,
                        10
                );

                LagerbewegungBO l2 = daoLagerbewegung.anlegen(
                        con,
                        a2.getArtikelId(),
                        m2.getMitarbeiterId(),
                        Bewegungsart.AUSGANG,
                        100
                );

                LagerbewegungBO l3 = daoLagerbewegung.anlegen(
                        con,
                        a3.getArtikelId(),
                        m3.getMitarbeiterId(),
                        Bewegungsart.EINGANG,
                        1000
                );

                List<LagerbewegungBO> gelesen =
                        daoLagerbewegung.findeAlle(con);

                assertEquals(3, gelesen.size());

                // now() liefert innerhalb derselben Transaktion
                // denselben Zeitpunkt. Damit prüfen wir gezielt
                // die zweite Sortierung nach Bewegungs-ID.
                assertNotNull(l1.getZeitpunkt());
                assertNotNull(l2.getZeitpunkt());
                assertNotNull(l3.getZeitpunkt());

                assertEquals(
                        l1.getZeitpunkt().toInstant(),
                        l2.getZeitpunkt().toInstant()
                );
                assertEquals(
                        l2.getZeitpunkt().toInstant(),
                        l3.getZeitpunkt().toInstant()
                );

                List<LagerbewegungBO> erwartet = List.of(l3, l2, l1);

                for (int i = 0; i < erwartet.size(); i++) {
                    pruefeBewegung(erwartet.get(i), gelesen.get(i));
                }

                // Zuordnung ausdrücklich gegen die Testdaten prüfen.
                assertEquals(
                        List.of(
                                a3.getArtikelId(),
                                a2.getArtikelId(),
                                a1.getArtikelId()
                        ),
                        gelesen.stream()
                                .map(LagerbewegungBO::getArtikelId)
                                .toList()
                );

                assertEquals(
                        List.of(
                                m3.getMitarbeiterId(),
                                m2.getMitarbeiterId(),
                                m1.getMitarbeiterId()
                        ),
                        gelesen.stream()
                                .map(LagerbewegungBO::getMitarbeiterId)
                                .toList()
                );

                assertEquals(
                        List.of(1000, 100, 10),
                        gelesen.stream()
                                .map(LagerbewegungBO::getMenge)
                                .toList()
                );

                assertEquals(
                        List.of(
                                Bewegungsart.EINGANG,
                                Bewegungsart.AUSGANG,
                                Bewegungsart.EINGANG
                        ),
                        gelesen.stream()
                                .map(LagerbewegungBO::getBewegungsart)
                                .toList()
                );
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void unbekannteArtikelundMitarbeiterIdTest() throws SQLException {
        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                ArtikelBO artikel = daoArtikel.anlegen(
                        con, "ichWeißAuchNichtMehr"
                );
                MitarbeiterBO mitarbeiter = daoMitarbeiter.anlegen(
                        con, "SchreibSchreibSchreib", Abteilung.IT
                );

                Savepoint savepoint = con.setSavepoint();

                DatenzugriffsException keineArtikelId = assertThrows(
                        DatenzugriffsException.class,
                        () -> daoLagerbewegung.anlegen(
                                con,
                                Integer.MAX_VALUE,
                                mitarbeiter.getMitarbeiterId(),
                                Bewegungsart.EINGANG,
                                10
                        )
                );

                assertInstanceOf(
                        SQLException.class, keineArtikelId.getCause()
                );
                assertEquals("23503", keineArtikelId.getSqlState());

                // PostgreSQL-Fehlerzustand zurücksetzen.
                // Artikel und Mitarbeiter bleiben erhalten.
                con.rollback(savepoint);

                DatenzugriffsException keineMitarbeiterId = assertThrows(
                        DatenzugriffsException.class,
                        () -> daoLagerbewegung.anlegen(
                                con,
                                artikel.getArtikelId(),
                                Integer.MAX_VALUE,
                                Bewegungsart.AUSGANG,
                                100
                        )
                );

                assertInstanceOf(
                        SQLException.class, keineMitarbeiterId.getCause()
                );
                assertEquals("23503", keineMitarbeiterId.getSqlState());

                con.rollback(savepoint);

                assertTrue(daoLagerbewegung.findeAlle(con).isEmpty());
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void mengeTest() throws SQLException {
        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                // Diese Vorbereitungen fehlten in deiner aktuellen Klasse.
                ArtikelBO a = daoArtikel.anlegen(
                        con, "mirfälltnichtsmehrein"
                );
                MitarbeiterBO m = daoMitarbeiter.anlegen(
                        con, "m", Abteilung.EINKAUF
                );

                Savepoint savepoint = con.setSavepoint();

                DatenzugriffsException mengeNull = assertThrows(
                        DatenzugriffsException.class,
                        () -> daoLagerbewegung.anlegen(
                                con,
                                a.getArtikelId(),
                                m.getMitarbeiterId(),
                                Bewegungsart.EINGANG,
                                0
                        )
                );

                assertInstanceOf(SQLException.class, mengeNull.getCause());
                assertEquals("23514", mengeNull.getSqlState());

                con.rollback(savepoint);

                DatenzugriffsException mengeNegativ = assertThrows(
                        DatenzugriffsException.class,
                        () -> daoLagerbewegung.anlegen(
                                con,
                                a.getArtikelId(),
                                m.getMitarbeiterId(),
                                Bewegungsart.AUSGANG,
                                -100
                        )
                );

                assertInstanceOf(
                        SQLException.class, mengeNegativ.getCause()
                );
                assertEquals("23514", mengeNegativ.getSqlState());

                con.rollback(savepoint);

                assertTrue(daoLagerbewegung.findeAlle(con).isEmpty());
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void leereTabelleTest() throws SQLException {
        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                List<LagerbewegungBO> leereListe =
                        daoLagerbewegung.findeAlle(con);

                assertNotNull(leereListe);
                assertTrue(leereListe.isEmpty());
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void artikelBestandUnveraendertTest() throws SQLException {
        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                ArtikelBO erstellt = daoArtikel.anlegen(con, "Maus");
                MitarbeiterBO m = daoMitarbeiter.anlegen(
                        con, "M", Abteilung.EINKAUF
                );

                // Einen Bestand größer 0 vorbereiten.
                daoArtikel.findeNachIdMitSperre(
                        con, erstellt.getArtikelId()
                ).orElseThrow();

                assertTrue(daoArtikel.aendereBestand(
                        con, erstellt.getArtikelId(), 100
                ));

                assertEquals(
                        100,
                        daoArtikel.findeNachId(
                                con, erstellt.getArtikelId()
                        ).orElseThrow().getBestand()
                );

                // Eingang darf den Bestand nicht verändern.
                daoLagerbewegung.anlegen(
                        con,
                        erstellt.getArtikelId(),
                        m.getMitarbeiterId(),
                        Bewegungsart.EINGANG,
                        100
                );

                assertEquals(
                        100,
                        daoArtikel.findeNachId(
                                con, erstellt.getArtikelId()
                        ).orElseThrow().getBestand()
                );

                // Auch Ausgang darf den Bestand nicht verändern.
                daoLagerbewegung.anlegen(
                        con,
                        erstellt.getArtikelId(),
                        m.getMitarbeiterId(),
                        Bewegungsart.AUSGANG,
                        100
                );

                assertEquals(
                        100,
                        daoArtikel.findeNachId(
                                con, erstellt.getArtikelId()
                        ).orElseThrow().getBestand()
                );

                assertEquals(2, daoLagerbewegung.findeAlle(con).size());
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void historieNachZeitpunktSortiertTest() throws SQLException {
        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                ArtikelBO artikel = daoArtikel.anlegen(con, "Maus");
                MitarbeiterBO mitarbeiter = daoMitarbeiter.anlegen(
                        con, "M", Abteilung.EINKAUF
                );

                OffsetDateTime alt =
                        OffsetDateTime.parse("2026-01-01T10:00:00Z");
                OffsetDateTime mittel =
                        OffsetDateTime.parse("2026-01-02T10:00:00Z");
                OffsetDateTime neu =
                        OffsetDateTime.parse("2026-01-03T10:00:00Z");

                // Absichtlich nicht in zeitlicher Reihenfolge anlegen:
                // Eine reine Sortierung nach ID würde hier scheitern.
                int neueId = legeBewegungMitZeitpunktAn(
                        con,
                        artikel.getArtikelId(),
                        mitarbeiter.getMitarbeiterId(),
                        neu
                );
                int alteId = legeBewegungMitZeitpunktAn(
                        con,
                        artikel.getArtikelId(),
                        mitarbeiter.getMitarbeiterId(),
                        alt
                );
                int mittlereId = legeBewegungMitZeitpunktAn(
                        con,
                        artikel.getArtikelId(),
                        mitarbeiter.getMitarbeiterId(),
                        mittel
                );

                List<LagerbewegungBO> gelesen =
                        daoLagerbewegung.findeAlle(con);

                assertEquals(3, gelesen.size());

                assertEquals(
                        List.of(neueId, mittlereId, alteId),
                        gelesen.stream()
                                .map(LagerbewegungBO::getLagerbewegungId)
                                .toList()
                );

                for (LagerbewegungBO bewegung : gelesen) {
                    assertNotNull(bewegung.getZeitpunkt());
                }

                assertEquals(
                        List.of(
                                neu.toInstant(),
                                mittel.toInstant(),
                                alt.toInstant()
                        ),
                        gelesen.stream()
                                .map(b -> b.getZeitpunkt().toInstant())
                                .toList()
                );
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void bewegungWirdDurchRollbackEntferntTest() throws SQLException {
        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                ArtikelBO artikel = daoArtikel.anlegen(con, "Maus");
                MitarbeiterBO mitarbeiter = daoMitarbeiter.anlegen(
                        con, "M", Abteilung.EINKAUF
                );

                // Nur die danach angelegte Bewegung zurückrollen.
                Savepoint savepoint = con.setSavepoint();

                LagerbewegungBO angelegt = daoLagerbewegung.anlegen(
                        con,
                        artikel.getArtikelId(),
                        mitarbeiter.getMitarbeiterId(),
                        Bewegungsart.EINGANG,
                        10
                );

                List<LagerbewegungBO> vorRollback =
                        daoLagerbewegung.findeAlle(con);

                assertEquals(1, vorRollback.size());
                pruefeBewegung(angelegt, vorRollback.getFirst());
                assertFalse(con.getAutoCommit());

                // Ein eigener Commit im DAO würde den Savepoint
                // ungültig machen und diesen Test fehlschlagen lassen.
                con.rollback(savepoint);

                assertTrue(daoLagerbewegung.findeAlle(con).isEmpty());

                // Die Vorbereitung vor dem Savepoint bleibt erhalten.
                assertTrue(daoArtikel.findeNachId(
                        con, artikel.getArtikelId()
                ).isPresent());

                assertTrue(daoMitarbeiter.findeNachId(
                        con, mitarbeiter.getMitarbeiterId()
                ).isPresent());
            } finally {
                con.rollback();
            }
        }
    }

    // Vergleicht alle fachlichen Felder und die generierte ID.
    private static void pruefeBewegung(
            LagerbewegungBO erwartet,
            LagerbewegungBO gelesen
    ) {
        assertNotNull(erwartet);
        assertNotNull(gelesen);

        assertEquals(
                erwartet.getLagerbewegungId(),
                gelesen.getLagerbewegungId()
        );
        assertEquals(erwartet.getArtikelId(), gelesen.getArtikelId());
        assertEquals(
                erwartet.getMitarbeiterId(),
                gelesen.getMitarbeiterId()
        );
        assertEquals(
                erwartet.getBewegungsart(),
                gelesen.getBewegungsart()
        );
        assertEquals(erwartet.getMenge(), gelesen.getMenge());

        assertNotNull(erwartet.getZeitpunkt());
        assertNotNull(gelesen.getZeitpunkt());
        assertEquals(
                erwartet.getZeitpunkt().toInstant(),
                gelesen.getZeitpunkt().toInstant()
        );
    }

    // Testdaten mit festem Zeitpunkt für den Sortierungstest.
    // Der DAO setzt den Zeitpunkt sonst automatisch über die Datenbank.
    private static int legeBewegungMitZeitpunktAn(
            Connection con,
            int artikelId,
            int mitarbeiterId,
            OffsetDateTime zeitpunkt
    ) throws SQLException {
        String sql = """
                INSERT INTO "Lagerbewegung"
                    ("ArtikelID", "MitarbeiterID", "Menge",
                     "Bewegungsart", "Bewegungszeitpunkt")
                VALUES (?, ?, ?, ?, ?)
                RETURNING "BewegungsID"
                """;

        try (PreparedStatement statement = con.prepareStatement(sql)) {
            statement.setInt(1, artikelId);
            statement.setInt(2, mitarbeiterId);
            statement.setInt(3, 1);
            statement.setString(4, "Eingang");
            statement.setObject(5, zeitpunkt);

            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt("BewegungsID");
            }
        }
    }
}