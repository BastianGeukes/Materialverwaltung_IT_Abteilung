package materialverwaltung.integration;

import materialverwaltung.dao.jdbc.JdbcArtikelDAO;
import materialverwaltung.db.ConnectionConfig;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.db.DatabaseMigration;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.model.ArtikelBO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class ArtikelDAOIT {
    @Container
    static final PostgreSQLContainer POSTGES =
            new PostgreSQLContainer("postgres:17-alpine");

    static ConnectionFactory connectionFactory;

    @BeforeAll
    static void datenbankVorbereiten() {
        ConnectionConfig config = new ConnectionConfig(
                POSTGES.getJdbcUrl(),
                POSTGES.getUsername(),
                POSTGES.getPassword()
        );

        DatabaseMigration.migrate(config);
        connectionFactory = new ConnectionFactory(config);
    }

    //Test-Methoden
    @Test
    void anlegenMitgueltigerBezeichnung() {
        JdbcArtikelDAO dao = new JdbcArtikelDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                //Ausführen
                ArtikelBO angelegt =
                        dao.anlegen(con, "Maus");

                //Rückgabe prüfen
                assertNotNull(angelegt);
                assertTrue(angelegt.getArtikelId() > 0);
                assertEquals("Maus", angelegt.getBezeichnung());
                assertEquals(0, angelegt.getBestand());

                //Gespeicherten Datensatz erneut lesen
                var gefunden = dao.findeNachId(con, angelegt.getArtikelId());

                assertTrue(gefunden.isPresent());
                ArtikelBO geladen = gefunden.orElseThrow();

                assertEquals(angelegt.getArtikelId(),
                        geladen.getArtikelId());
                assertEquals("Maus", geladen.getBezeichnung());
                assertEquals(0, geladen.getBestand());
            } finally {
                con.rollback();
            }
        } catch (SQLException e) {
            throw new DatenzugriffsException("Es konnte kein Artikel mit Bezeichnung angelegt werden", e);
        }
    }

    @Test
    void anlegenGleicheBezeichnungZweimal() throws SQLException {
        JdbcArtikelDAO dao = new JdbcArtikelDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                // Vorbereiten: Die Bezeichnung ist bereits vergeben.
                dao.anlegen(con, "Maus");

                // Ausführen: Die zweite Anlage muss scheitern.
                DatenzugriffsException exception = assertThrows(
                        DatenzugriffsException.class,
                        () -> dao.anlegen(con, "Maus")
                );

                // Prüfen: Es war eine Verletzung der Eindeutigkeit.
                assertInstanceOf(
                        SQLException.class,
                        exception.getCause(),
                        "Ursache muss erhalten bleiben"
                );
                assertEquals("23505", exception.getSqlState());
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void findeNachIdTest() throws SQLException {
        JdbcArtikelDAO dao = new JdbcArtikelDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                //Vorbereiten
                dao.anlegen(con, "Lan Kabel");
                ArtikelBO erwartet = dao.anlegen(con, "Maus");

                //Ausführen
                var gefunden =
                        dao.findeNachId(con, erwartet.getArtikelId());

                var nichtGefunden =
                        dao.findeNachId(con, Integer.MAX_VALUE);

                //Prüfen
                assertTrue(gefunden.isPresent());
                ArtikelBO geladen = gefunden.orElseThrow();

                assertEquals(erwartet.getArtikelId()
                        , geladen.getArtikelId());
                assertEquals("Maus"
                        , geladen.getBezeichnung());
                assertEquals(erwartet.getBestand()
                        , geladen.getBestand());

                assertFalse(nichtGefunden.isPresent());
                assertEquals(nichtGefunden, Optional.empty());
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void findeAlleMitMehrerenMitarbeitern() throws SQLException {
        JdbcArtikelDAO dao = new JdbcArtikelDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                //Vorbereiten
                ArtikelBO a1 = dao.anlegen(con, "Maus");
                ArtikelBO a2 = dao.anlegen(con, "Lan Kabel");
                ArtikelBO a3 = dao.anlegen(con, "Stuhl");
                ArtikelBO a4 = dao.anlegen(con, "Monitor");

                // Ausführen
                List<ArtikelBO> gefunden = dao.findeAlle(con);

                //Prüfen
                List<Integer> erwarteteIds = List.of(
                        a1.getArtikelId(),
                        a2.getArtikelId(),
                        a3.getArtikelId(),
                        a4.getArtikelId()
                );

                List<Integer> gefundeneIds = gefunden.stream()
                        .map(ArtikelBO::getArtikelId)
                        .toList();

                assertFalse(gefunden.isEmpty());
                assertEquals(erwarteteIds, gefundeneIds);
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void findeAlleMitLeererTabelle() throws SQLException {
        JdbcArtikelDAO dao = new JdbcArtikelDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                //Vorbereiten
                List<ArtikelBO> gefunden = dao.findeAlle(con);

                //Prüfen
                assertTrue(gefunden.isEmpty());
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void sucheNachBezeichnungTest() throws SQLException {
        JdbcArtikelDAO dao = new JdbcArtikelDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                //Vorbereiten
                ArtikelBO a1 = dao.anlegen(con, "USB-Maus");
                dao.anlegen(con, "Tastatur");
                ArtikelBO a2 = dao.anlegen(con, "Mauspad");

                //Ausführen
                List<ArtikelBO> gefunden = dao.sucheNachBezeichnung(con, "mAuS");
                List<ArtikelBO> nichtGefunden = dao.sucheNachBezeichnung(
                        con, "IchBinAbsolutAmEndeUndWillDasAllesNichtMehrMachen");

                //Prüfen
                List<Integer> erwarteteIds = List.of(
                        a1.getArtikelId(),
                        a2.getArtikelId()
                );

                List<Integer> gefundeneIds = gefunden.stream()
                        .map(ArtikelBO::getArtikelId)
                        .toList();

                assertFalse(gefunden.isEmpty());
                assertEquals(erwarteteIds, gefundeneIds);
                assertTrue(nichtGefunden.isEmpty());
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void sucheNachBezeichnungMitSonderzeichenTest() throws SQLException {
        JdbcArtikelDAO dao = new JdbcArtikelDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                //Vorbereiten
                ArtikelBO a1 = dao.anlegen(con, "Maus%_!\\Schnell");
                ArtikelBO a2 = dao.anlegen(con, "Tastatur%_!\\");
                ArtikelBO a3 = dao.anlegen(con, "%_!\\Stuhl");

                dao.anlegen(con, "\\%A_!Mauspad");
                dao.anlegen(con, "Tastatur%X_!\\");
                dao.anlegen(con, "Tastatur%X_!\\IchKannNichtMehr");

                //Ausführen
                List<ArtikelBO> gefunden =
                        dao.sucheNachBezeichnung(con, "%_!\\");

                //Prüfen
                List<Integer> erwarteteIds = List.of(
                        a1.getArtikelId(),
                        a2.getArtikelId(),
                        a3.getArtikelId()
                );

                List<Integer> gefundeneIds = gefunden.stream()
                        .map(ArtikelBO::getArtikelId)
                        .toList();

                assertFalse(gefunden.isEmpty());
                assertEquals(erwarteteIds, gefundeneIds);

            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void umbenennenTest() throws SQLException {
        JdbcArtikelDAO dao = new JdbcArtikelDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                //Vorbereiten
                ArtikelBO a1 = dao.anlegen(con, "USB-Maus");

                assertTrue(
                        dao.findeNachIdMitSperre(con, a1.getArtikelId()).isPresent()
                );
                assertTrue(dao.aendereBestand(con, a1.getArtikelId(), 7));
                assertEquals(
                        7,
                        dao.findeNachId(con, a1.getArtikelId()).orElseThrow().getBestand()
                );

                //Ausführen
                boolean umbenannt = dao.umbenennen(con, a1.getArtikelId(), "Maus");
                boolean nichtUmbenannt = dao.umbenennen(con, Integer.MAX_VALUE, "IchExestiereInEinerSimulation");

                //Prüfen
                var umbenannterArtikel = dao.findeNachId(con, a1.getArtikelId());
                ArtikelBO u1 = umbenannterArtikel.orElseThrow();

                assertTrue(umbenannt);
                assertFalse(nichtUmbenannt);
                assertEquals("Maus", u1.getBezeichnung());
                assertEquals(7, u1.getBestand());
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void umbenennenMitBezeichnungAndererArtikel() throws SQLException {
        JdbcArtikelDAO dao = new JdbcArtikelDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                //Vorbereiten
                dao.anlegen(con, "USB-Maus");
                ArtikelBO t1 = dao.anlegen(con, "Maus");

                //Ausführen
                DatenzugriffsException ex = assertThrows(DatenzugriffsException.class,
                        () -> dao.umbenennen(con, t1.getArtikelId(), "USB-Maus"));

                //Prüfen
                assertInstanceOf(
                        SQLException.class,
                        ex.getCause(),
                        "Ursache muss erhalten bleiben"
                );
                assertEquals("23505", ex.getSqlState());
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void loeschenTest() throws SQLException {
        JdbcArtikelDAO dao = new JdbcArtikelDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                //Vorbereiten
                ArtikelBO a1 = dao.anlegen(con, "USB-Maus");

                //Prüfen
                assertTrue(dao.loeschen(con, a1.getArtikelId()));
                assertTrue(dao.findeNachId(con, a1.getArtikelId()).isEmpty());

                assertFalse(dao.loeschen(con, a1.getArtikelId()));
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void loeschenArtikelMitLagerbewegung() throws SQLException {
        JdbcArtikelDAO dao = new JdbcArtikelDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                ArtikelBO artikel =
                        dao.anlegen(con, "USB-Maus");
                int artikelId = artikel.getArtikelId();

                // Mitarbeiter vorbereiten und seine erzeugte ID lesen
                int mitarbeiterId;
                String mitarbeiterSql = """
                        INSERT INTO "Mitarbeiter" ("Name", "Abteilung")
                        VALUES (?, ?)
                        RETURNING "MitarbeiterID"
                        """;

                try (var statement = con.prepareStatement(mitarbeiterSql)) {
                    statement.setString(1, "Mitarbeiter");
                    statement.setString(2, "IT");

                    try (var resultset = statement.executeQuery()) {
                        assertTrue(resultset.next());
                        mitarbeiterId = resultset.getInt("MitarbeiterID");
                    }
                }

                //Bewegung mit Verweis auf den Artikel anlegen
                String bewegungSql = """
                        INSERT INTO "Lagerbewegung"
                            ("ArtikelID", "MitarbeiterID", "Menge", "Bewegungsart")
                        VALUES (?, ?, ?, ?)
                        """;

                try (var statement = con.prepareStatement(bewegungSql)) {
                    statement.setInt(1, artikelId);
                    statement.setInt(2, mitarbeiterId);
                    statement.setInt(3, 1);
                    statement.setString(4, "Eingang");

                    assertEquals(1, statement.executeUpdate());
                }

                //Datenbank muss Löschen verhindern
                DatenzugriffsException exception = assertThrows(
                        DatenzugriffsException.class,
                        () -> dao.loeschen(con, artikelId)
                );

                assertInstanceOf(SQLException.class, exception.getCause());
                assertEquals("23503", exception.getSqlState());
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void findeIdMitSperreVorhandenTest() throws SQLException {
        JdbcArtikelDAO dao = new JdbcArtikelDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                //vorbereiten
                ArtikelBO erwartet = dao.anlegen(con, "USB-Maus");

                //Ausführen
                var gefunden = dao.findeNachIdMitSperre(con, erwartet.getArtikelId());
                var nichtGefunden = dao.findeNachIdMitSperre(con, Integer.MAX_VALUE);

                //Prüfen
                assertTrue(gefunden.isPresent());
                ArtikelBO geladen = gefunden.orElseThrow();

                assertEquals(erwartet.getArtikelId()
                        , geladen.getArtikelId());
                assertEquals("USB-Maus"
                        , geladen.getBezeichnung());
                assertEquals(erwartet.getBestand()
                        , geladen.getBestand());

                assertFalse(nichtGefunden.isPresent());
                assertEquals(nichtGefunden, Optional.empty());
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void findeNachIdMitSperreBlockiertAndereTransaktion() throws SQLException {

        JdbcArtikelDAO dao = new JdbcArtikelDAO();

        try (Connection conA = connectionFactory.openConnection()) {
            conA.setAutoCommit(false);

            // Vorbereitung abschließen: Beide Connections müssen
            // denselben bereits gespeicherten Artikel sehen können.
            ArtikelBO artikel = dao.anlegen(conA, "Artikel-Sperrtest");
            int id = artikel.getArtikelId();
            conA.commit();

            try {
                try (Connection conB = connectionFactory.openConnection()) {
                    conB.setAutoCommit(false);

                    try {
                        // Transaktion A sperrt die Artikelzeile.
                        assertTrue(
                                dao.findeNachIdMitSperre(conA, id).isPresent()
                        );

                        // B darf höchstens 500 ms auf eine Sperre warten.
                        try (var statement = conB.createStatement()) {
                            statement.execute(
                                    "SET LOCAL lock_timeout = '500ms'"
                            );
                        }

                        // A hält die Sperre noch: B muss scheitern.
                        DatenzugriffsException exception = assertThrows(
                                DatenzugriffsException.class,
                                () -> dao.findeNachIdMitSperre(conB, id)
                        );

                        assertInstanceOf(
                                SQLException.class, exception.getCause()
                        );
                        assertEquals("55P03", exception.getSqlState());

                        // Nach dem SQL-Fehler muss B zurückrollen.
                        conB.rollback();

                        // A beendet seine Transaktion und gibt die Sperre frei.
                        conA.commit();

                        // SET LOCAL galt nur für die vorherige Transaktion.
                        try (var statement = conB.createStatement()) {
                            statement.execute(
                                    "SET LOCAL lock_timeout = '500ms'"
                            );
                        }

                        // Jetzt muss B den Artikel sperren und lesen können.
                        ArtikelBO geladen =
                                dao.findeNachIdMitSperre(conB, id)
                                        .orElseThrow();

                        assertEquals(id, geladen.getArtikelId());
                        assertEquals(
                                "Artikel-Sperrtest", geladen.getBezeichnung()
                        );
                    } finally {
                        conB.rollback();
                    }
                }
            } finally {
                conA.rollback();

                dao.loeschen(conA, id);
                conA.commit();
            }
        }
    }

    @Test
    void aendereBestandSetztNeuenWert() throws SQLException {
        JdbcArtikelDAO dao = new JdbcArtikelDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                // Vorbereiten und Artikelzeile sperren.
                ArtikelBO artikel = dao.anlegen(con, "Bestand-Testartikel");
                int id = artikel.getArtikelId();

                assertTrue(dao.findeNachIdMitSperre(con, id).isPresent());
                assertTrue(dao.aendereBestand(con, id, 7));
                assertEquals(
                        7, dao.findeNachId(con, id).orElseThrow().getBestand()
                );

                // Ausführen: 7 durch 3 ersetzen.
                boolean geaendert = dao.aendereBestand(con, id, 3);

                // Prüfen
                ArtikelBO geladen = dao.findeNachId(con, id).orElseThrow();

                assertTrue(geaendert);
                assertEquals(3, geladen.getBestand());
                assertEquals("Bestand-Testartikel", geladen.getBezeichnung());
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void aendereBestandAufNullStueck() throws SQLException {
        JdbcArtikelDAO dao = new JdbcArtikelDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                // Vorbereiten: Artikel mit positivem Bestand.
                ArtikelBO artikel = dao.anlegen(con, "Artikel zum Leeren");
                int id = artikel.getArtikelId();

                assertTrue(dao.findeNachIdMitSperre(con, id).isPresent());
                assertTrue(dao.aendereBestand(con, id, 7));
                assertEquals(
                        7, dao.findeNachId(con, id).orElseThrow().getBestand()
                );

                // Ausführen
                boolean geaendert = dao.aendereBestand(con, id, 0);

                // Prüfen
                ArtikelBO geladen = dao.findeNachId(con, id).orElseThrow();

                assertTrue(geaendert);
                assertEquals(0, geladen.getBestand());
                assertEquals("Artikel zum Leeren", geladen.getBezeichnung());
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void aendereBestandBeiNichtVorhandenerId() throws SQLException {
        JdbcArtikelDAO dao = new JdbcArtikelDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                // Eine bekannte ID erzeugen und den Artikel wieder löschen.
                ArtikelBO artikel = dao.anlegen(con, "Artikel zum Entfernen");
                int id = artikel.getArtikelId();

                assertTrue(dao.loeschen(con, id));
                assertTrue(dao.findeNachIdMitSperre(con, id).isEmpty());

                // Ausführen
                boolean geaendert = dao.aendereBestand(con, id, 5);

                // Prüfen
                assertFalse(geaendert);
                assertTrue(dao.findeNachId(con, id).isEmpty());
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void aendereBestandAufNegativenWertScheitert() throws SQLException {
        JdbcArtikelDAO dao = new JdbcArtikelDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                // Vorbereiten
                ArtikelBO artikel = dao.anlegen(con, "Negativbestand-Test");
                int id = artikel.getArtikelId();

                assertTrue(dao.findeNachIdMitSperre(con, id).isPresent());
                assertTrue(dao.aendereBestand(con, id, 5));

                // Zustand vor dem erwarteten Fehler sichern.
                var savepoint = con.setSavepoint();

                DatenzugriffsException exception = assertThrows(
                        DatenzugriffsException.class,
                        () -> dao.aendereBestand(con, id, -2)
                );

                assertInstanceOf(SQLException.class, exception.getCause());
                assertEquals("23514", exception.getSqlState());

                // Nach dem SQL-Fehler die Transaktion wieder nutzbar machen.
                con.rollback(savepoint);

                ArtikelBO geladen = dao.findeNachId(con, id).orElseThrow();
                assertEquals(5, geladen.getBestand());
            } finally {
                con.rollback();
            }
        }
    }
}
