package materialverwaltung.integration;

import materialverwaltung.dao.jdbc.JdbcMitarbeiterDAO;
import materialverwaltung.db.ConnectionConfig;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.db.DatabaseMigration;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.model.Abteilung;
import materialverwaltung.model.MitarbeiterBO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static materialverwaltung.model.Abteilung.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class MitarbeiterDAOIT {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    static ConnectionFactory connectionFactory;

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

    //Test-Methoden
    @Test
    void anlegenTestMitNameUndAbteilung() {
        JdbcMitarbeiterDAO dao = new JdbcMitarbeiterDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                // Ausführen
                MitarbeiterBO angelegt =
                        dao.anlegen(con, "Bastian Tester", IT);

                // Rückgabe prüfen
                assertNotNull(angelegt);
                assertTrue(angelegt.getMitarbeiterId() > 0);
                assertEquals("Bastian Tester", angelegt.getName());
                assertEquals(IT, angelegt.getAbteilung());

                // Gespeicherten Datensatz erneut lesen
                var gefunden = dao.findeNachId(con, angelegt.getMitarbeiterId());

                assertTrue(gefunden.isPresent());
                MitarbeiterBO geladen = gefunden.orElseThrow();

                assertEquals(angelegt.getMitarbeiterId(),
                        geladen.getMitarbeiterId());
                assertEquals("Bastian Tester", geladen.getName());
                assertEquals(IT, geladen.getAbteilung());
            } finally {
                con.rollback();
            }
        } catch (SQLException e) {
            throw new DatenzugriffsException(
                    "Es Konnte kein Mitarbeiter mit Name und Abteilung angelegt werden", e);
        }
    }

    @Test
    void anlegenTestMitNameUndAbteilungNull() {
        JdbcMitarbeiterDAO dao = new JdbcMitarbeiterDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                // Ausführen
                MitarbeiterBO angelegt =
                        dao.anlegen(con, "Bastian Tester", null);

                //Rückgabe Prüfen
                assertNotNull(angelegt);
                assertTrue(angelegt.getMitarbeiterId() > 0);
                assertEquals("Bastian Tester", angelegt.getName());
                assertNull(angelegt.getAbteilung());

                //Datensatz erneut lesen
                var gefunden = dao.findeNachId(con, angelegt.getMitarbeiterId());
                assertTrue(gefunden.isPresent());
                MitarbeiterBO geladen = gefunden.orElseThrow();

                assertEquals(angelegt.getMitarbeiterId(),
                        geladen.getMitarbeiterId());
                assertEquals("Bastian Tester", geladen.getName());
                assertNull(geladen.getAbteilung());
            } finally {
                con.rollback();
            }

        } catch (SQLException e) {
            throw new DatenzugriffsException(
                    "Es Konnte kein Mitarbeiter mit namen und leerer Abteilung angelegt werden", e);
        }
    }

    @Test
    void anlegenTestMitZweiMitarbeiternMitGleichenNamen() {
        JdbcMitarbeiterDAO dao = new JdbcMitarbeiterDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                // Ausführen
                MitarbeiterBO mitarbeiter1 = dao.anlegen(con, "Bastian Tester", IT);
                MitarbeiterBO mitarbeiter2 = dao.anlegen(con, "Bastian Tester", IT);

                // Gespeicherte Datensätze erneut lesen
                var gefunden1 = dao.findeNachId(con, mitarbeiter1.getMitarbeiterId());
                var gefunden2 = dao.findeNachId(con, mitarbeiter2.getMitarbeiterId());

                assertTrue(gefunden1.isPresent());
                assertTrue(gefunden2.isPresent());
                MitarbeiterBO geladen1 = gefunden1.orElseThrow();
                MitarbeiterBO geladen2 = gefunden2.orElseThrow();

                assertNotEquals(geladen1.getMitarbeiterId(), geladen2.getMitarbeiterId());

            } finally {
                con.rollback();
            }
        } catch (SQLException e) {
            throw new DatenzugriffsException(
                    "Es Konnten keine zwei Mitarbeiter mit gleichem Namen angelegt werden", e);
        }
    }


    @Test
    void findeNachIdLiefertGesuchtenMitarbeiterOderOptionalEmptyWennNichtVorhanden() {
        JdbcMitarbeiterDAO dao = new JdbcMitarbeiterDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                // Vorbereiten: zwei verschiedene Mitarbeiter
                dao.anlegen(con, "Anderer Mitarbeiter", IT);
                MitarbeiterBO erwartet =
                        dao.anlegen(con, "Bastian Tester", IT);

                // Ausführen
                var gefunden =
                        dao.findeNachId(con, erwartet.getMitarbeiterId());

                var nichtGefunden =
                        dao.findeNachId(con, Integer.MAX_VALUE);

                // Prüfen
                assertTrue(gefunden.isPresent());
                MitarbeiterBO geladen = gefunden.orElseThrow();

                assertEquals(erwartet.getMitarbeiterId(),
                        geladen.getMitarbeiterId());
                assertEquals("Bastian Tester", geladen.getName());
                assertEquals(IT, geladen.getAbteilung());

                assertFalse(nichtGefunden.isPresent());
                assertEquals(nichtGefunden, Optional.empty());
            } finally {
                con.rollback();
            }
        } catch (SQLException e) {
            throw new DatenzugriffsException(
                    "FindeNachId Methode liefert nicht den gesuchten Mitarbeiter", e);
        }
    }

    @Test
    void findeAlleMitLeererTabelle() {
        JdbcMitarbeiterDAO dao = new JdbcMitarbeiterDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                //Vorbereiten
                List<MitarbeiterBO> mitarbeiterListe = dao.findeAlle(con);

                //Prüfen
                assertTrue(mitarbeiterListe.isEmpty());
            } finally {
                con.rollback();
            }
        } catch (SQLException e) {
            throw new DatenzugriffsException(
                    "FindeAlle Methode mit leerer Tabelle liefert keine leere Liste", e);
        }
    }

    @Test
    void findeAlleMitMehrerenMitarbeitern() {
        JdbcMitarbeiterDAO dao = new JdbcMitarbeiterDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                //Vorbereiten
                MitarbeiterBO m1 = dao.anlegen(con, "Bastian Tester", IT);
                MitarbeiterBO m2 = dao.anlegen(con, "Bastian Tester2", IT);
                MitarbeiterBO m3 = dao.anlegen(con, "Bastian Tester3", EINKAUF);
                MitarbeiterBO m4 = dao.anlegen(con, "Bastian Tester4", PERSONAL);
                MitarbeiterBO m5 = dao.anlegen(con, "Bastian Tester5", FINANZEN);
                MitarbeiterBO m6 = dao.anlegen(con, "Bastian Tester6", PERSONAL);

                //Ausführen
                List<MitarbeiterBO> gefunden = dao.findeAlle(con);

                //Prüfen
                List<Integer> erwarteteIds = List.of(
                        m1.getMitarbeiterId(),
                        m2.getMitarbeiterId(),
                        m3.getMitarbeiterId(),
                        m4.getMitarbeiterId(),
                        m5.getMitarbeiterId(),
                        m6.getMitarbeiterId()
                );

                List<Integer> gefundeneIds = gefunden.stream()
                        .map(MitarbeiterBO::getMitarbeiterId)
                        .toList();

                assertFalse(gefunden.isEmpty());
                assertEquals(erwarteteIds, gefundeneIds);
            } finally {
                con.rollback();
            }
        } catch (SQLException e) {
            throw new DatenzugriffsException(
                    "FindeAlle mit mehreren Mitarbeitern liefert keine liste von diesen", e);
        }
    }

    @Test
    void sucheNachNameTest() {
        JdbcMitarbeiterDAO dao = new JdbcMitarbeiterDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                //Vorbereiten
                MitarbeiterBO m1 = dao.anlegen(con, "Anna", null);
                MitarbeiterBO m2 = dao.anlegen(con, "Hanna", null);
                dao.anlegen(con, "Peter", null);


                //Ausführen
                List<MitarbeiterBO> gefunden = dao.sucheNachName(con, "aNn");
                List<MitarbeiterBO> nichtGefunden = dao.sucheNachName(con, "IchKannDasAlleNichtMehr");

                //Prüfen
                List<Integer> erwarteteIds = List.of(
                        m1.getMitarbeiterId(),
                        m2.getMitarbeiterId()
                );

                List<Integer> gefundeneIds = gefunden.stream()
                        .map(MitarbeiterBO::getMitarbeiterId)
                        .toList();

                assertFalse(gefunden.isEmpty());
                assertTrue(nichtGefunden.isEmpty());
                assertEquals(erwarteteIds, gefundeneIds);

            } finally {
                con.rollback();
            }
        } catch (SQLException e) {
            throw new DatenzugriffsException(
                    "sucheNachName Methode gibt keine Richtigen Daten zurück", e);
        }
    }

    @Test
    void sucheNachNameBesondereSymbole() throws SQLException {
        JdbcMitarbeiterDAO dao = new JdbcMitarbeiterDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                // Diese Mitarbeiter enthalten genau die Zeichenfolge "%_!".
                MitarbeiterBO m1 = dao.anlegen(con, "%_!Anna", null);
                MitarbeiterBO m2 = dao.anlegen(con, "Hanna%_!", null);
                MitarbeiterBO m3 = dao.anlegen(con, "Peter%_!Test", null);

                // Kontrollfälle: dürfen nicht gefunden werden.
                dao.anlegen(con, "Normal!Name", null);
                dao.anlegen(con, "%X!Anna", null);
                dao.anlegen(con, "Anna", null);

                // Ausführen
                List<MitarbeiterBO> gefunden =
                        dao.sucheNachName(con, "%_!");

                // Prüfen: genau diese drei IDs, in dieser Reihenfolge.
                List<Integer> erwarteteIds = List.of(
                        m1.getMitarbeiterId(),
                        m2.getMitarbeiterId(),
                        m3.getMitarbeiterId()
                );

                List<Integer> gefundeneIds = gefunden.stream()
                        .map(MitarbeiterBO::getMitarbeiterId)
                        .toList();

                assertEquals(erwarteteIds, gefundeneIds);
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void sucheNachNameMitBackslash() throws SQLException {
        JdbcMitarbeiterDAO dao = new JdbcMitarbeiterDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                // Enthalten einen tatsächlichen Backslash.
                MitarbeiterBO m1 =
                        dao.anlegen(con, "Anna\\Team", null);
                MitarbeiterBO m2 =
                        dao.anlegen(con, "Team\\Hanna", null);

                // Dürfen nicht gefunden werden.
                dao.anlegen(con, "AnnaTeam", null);
                dao.anlegen(con, "Anna/Team", null);

                List<MitarbeiterBO> gefunden =
                        dao.sucheNachName(con, "\\");

                List<Integer> erwarteteIds = List.of(
                        m1.getMitarbeiterId(),
                        m2.getMitarbeiterId()
                );

                List<Integer> gefundeneIds = gefunden.stream()
                        .map(MitarbeiterBO::getMitarbeiterId)
                        .toList();

                assertEquals(erwarteteIds, gefundeneIds);
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void bearbeitenNameUndAbteilung() throws SQLException {
        JdbcMitarbeiterDAO dao = new JdbcMitarbeiterDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                //Vorbereiten
                MitarbeiterBO mitarbeiter = dao.anlegen(con, "Mitarbeiter Nichtverändert", IT);

                //Ausführen
                boolean bearbeiten = dao.bearbeiten(con, mitarbeiter.getMitarbeiterId(), "Mitarbeiter Verändert", PRODUKTION);

                //Prüfen
                var gefunden = dao.findeNachId(con, mitarbeiter.getMitarbeiterId());

                assertTrue(gefunden.isPresent());
                MitarbeiterBO geladen = gefunden.orElseThrow();

                assertTrue(bearbeiten);
                assertEquals("Mitarbeiter Verändert", geladen.getName());
                assertEquals(PRODUKTION, geladen.getAbteilung());

            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void bearbeitenName() throws SQLException {
        JdbcMitarbeiterDAO dao = new JdbcMitarbeiterDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                //Vorbereiten
                MitarbeiterBO mitarbeiter = dao.anlegen(con, "Mitarbeiter Nichtverändert", IT);

                //Ausführen
                boolean bearbeiten = dao.bearbeiten(con, mitarbeiter.getMitarbeiterId(), "Mitarbeiter Verändert");

                //Prüfen
                var gefunden = dao.findeNachId(con, mitarbeiter.getMitarbeiterId());

                assertTrue(gefunden.isPresent());
                MitarbeiterBO geladen = gefunden.orElseThrow();

                assertTrue(bearbeiten);
                assertEquals("Mitarbeiter Verändert", geladen.getName());

                assertEquals("Mitarbeiter Verändert", geladen.getName());
                assertEquals(IT, geladen.getAbteilung());
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void bearbeitenAbteilung() throws SQLException {
        JdbcMitarbeiterDAO dao = new JdbcMitarbeiterDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                //Vorbereiten
                MitarbeiterBO mitarbeiter = dao.anlegen(con, "Mitarbeiter Nichtverändert", IT);

                //Ausführen
                boolean bearbeiten = dao.bearbeiten(con, mitarbeiter.getMitarbeiterId(), PRODUKTION);

                //Prüfen
                var gefunden = dao.findeNachId(con, mitarbeiter.getMitarbeiterId());

                assertTrue(gefunden.isPresent());
                MitarbeiterBO geladen = gefunden.orElseThrow();

                assertTrue(bearbeiten);
                assertEquals(PRODUKTION, geladen.getAbteilung());

                assertEquals(PRODUKTION, geladen.getAbteilung());
                assertEquals("Mitarbeiter Nichtverändert", geladen.getName());
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void bearbeitenAbteilungAufNull() throws SQLException {
        JdbcMitarbeiterDAO dao = new JdbcMitarbeiterDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                // Mitarbeiter hat zunächst eine Abteilung.
                MitarbeiterBO mitarbeiter =
                        dao.anlegen(con, "Testmitarbeiter", IT);

                boolean bearbeitet = dao.bearbeiten(
                        con,
                        mitarbeiter.getMitarbeiterId(),
                        (Abteilung) null
                );

                assertTrue(bearbeitet);

                MitarbeiterBO geladen = dao.findeNachId(
                        con, mitarbeiter.getMitarbeiterId()
                ).orElseThrow();

                assertNull(geladen.getAbteilung());
                assertEquals("Testmitarbeiter", geladen.getName());
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void bearbeitenNichtVorhandeneId() throws SQLException {
        JdbcMitarbeiterDAO dao = new JdbcMitarbeiterDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                //Ausführen
                boolean keinMitarbeiter = dao.bearbeiten(con, Integer.MAX_VALUE, "Kein Mitarbeiter", PRODUKTION);

                //Prüfen
                assertFalse(keinMitarbeiter);

            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void loeschenOhneBewegungUndDanachErneut() throws SQLException {
        JdbcMitarbeiterDAO dao = new JdbcMitarbeiterDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                //Vorbereiten
                MitarbeiterBO mitarbeiter =
                        dao.anlegen(con, "Testmitarbeiter", IT);
                int id = mitarbeiter.getMitarbeiterId();

                //Prüfen
                assertTrue(dao.loeschen(con, id));
                assertTrue(dao.findeNachId(con, id).isEmpty());

                //ID existiert nicht mehr.
                assertFalse(dao.loeschen(con, id));
            } finally {
                con.rollback();
            }
        }
    }

    @Test
    void loeschenMitLagerbewegungScheitert() throws SQLException {
        JdbcMitarbeiterDAO dao = new JdbcMitarbeiterDAO();

        try (Connection con = connectionFactory.openConnection()) {
            con.setAutoCommit(false);

            try {
                MitarbeiterBO mitarbeiter =
                        dao.anlegen(con, "Testmitarbeiter", IT);
                int mitarbeiterId = mitarbeiter.getMitarbeiterId();

                // Artikel vorbereiten und seine erzeugte ID lesen.
                int artikelId;
                String artikelSql = """
                        INSERT INTO "Artikel" ("Bezeichnung", "Bestand")
                        VALUES (?, ?)
                        RETURNING "ArtikelID"
                        """;

                try (var statement = con.prepareStatement(artikelSql)) {
                    statement.setString(1, "Testartikel");
                    statement.setInt(2, 1);

                    try (var resultSet = statement.executeQuery()) {
                        assertTrue(resultSet.next());
                        artikelId = resultSet.getInt("ArtikelID");
                    }
                }

                // Bewegung mit Verweis auf den Mitarbeiter anlegen.
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

                // Die Datenbank muss das Löschen verhindern.
                DatenzugriffsException exception = assertThrows(
                        DatenzugriffsException.class,
                        () -> dao.loeschen(con, mitarbeiterId)
                );

                assertInstanceOf(SQLException.class, exception.getCause());
                assertEquals("23503", exception.getSqlState());
            } finally {
                // Entfernt Mitarbeiter, Artikel und Lagerbewegung.
                con.rollback();
            }
        }
    }
}
