package materialverwaltung.service;

import materialverwaltung.db.ConnectionConfig;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.db.DatabaseMigration;
import materialverwaltung.exception.DatenzugriffsException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prüft die gemeinsame Transaktionsbehandlung der Services gegen PostgreSQL:
 * Commit bei Erfolg, vollständiger Rollback bei Fehlern, Verbindungen werden geschlossen.
 */
@Testcontainers
class DatenbankzugriffIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static ConnectionConfig config;

    private final List<Connection> geoeffnet = new ArrayList<>();
    private Datenbankzugriff datenbank;

    @BeforeAll
    static void datenbankVorbereiten() {
        config = new ConnectionConfig(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        DatabaseMigration.migrate(config);
    }

    @BeforeEach
    void vorbereiten() throws SQLException {
        try (Connection con = new ConnectionFactory(config).openConnection();
             Statement st = con.createStatement()) {
            st.execute("TRUNCATE \"Lagerbewegung\", \"Artikel\", \"Mitarbeiter\" RESTART IDENTITY");
        }
        datenbank = new Datenbankzugriff(new ConnectionFactory(config) {
            @Override
            public Connection openConnection() {
                Connection con = super.openConnection();
                geoeffnet.add(con);
                return con;
            }
        });
    }

    @Test
    void transaktionWirdBeiErfolgBestaetigt() throws SQLException {
        String ergebnis = datenbank.inTransaktion(con -> {
            fuegeArtikelEin(con, "A");
            fuegeArtikelEin(con, "B");
            return "fertig";
        });

        assertEquals("fertig", ergebnis);
        assertEquals(2, anzahlArtikel(), "beide Schritte sind gespeichert");
        assertVerbindungenGeschlossen();
    }

    @Test
    void fehlerImZweitenSchrittRolltDenErstenZurueck() throws SQLException {
        DatenzugriffsException e = assertThrows(DatenzugriffsException.class, () ->
                datenbank.inTransaktion(con -> {
                    fuegeArtikelEin(con, "A");
                    fuegeArtikelEin(con, "A"); // verletzt uq_Artikel_Bezeichnung wirklich
                    return null;
                }));

        assertEquals("23505", e.getSqlState(), "die ursprüngliche Ursache wird weitergegeben");
        assertTrue(Datenbankzugriff.verletzt(e, Datenbankzugriff.SQLSTATE_EINDEUTIGKEIT, "uq_Artikel_Bezeichnung"));
        assertFalse(Datenbankzugriff.verletzt(e, Datenbankzugriff.SQLSTATE_EINDEUTIGKEIT, "andere_bedingung"));
        assertEquals(0, anzahlArtikel(), "auch der erste Schritt wurde zurückgerollt");
        assertVerbindungenGeschlossen();
    }

    @Test
    void fachlicherAbbruchRolltEbenfallsZurueck() throws SQLException {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                datenbank.inTransaktion(con -> {
                    fuegeArtikelEin(con, "A");
                    throw new IllegalStateException("abgelehnt");
                }));

        assertEquals("abgelehnt", e.getMessage());
        assertEquals(0, anzahlArtikel());
        assertVerbindungenGeschlossen();
    }

    @Test
    void mitVerbindungSchliesstAuchBeiFehlern() {
        assertThrows(IllegalStateException.class, () -> datenbank.mitVerbindung(con -> {
            throw new IllegalStateException("Fehler");
        }));
        assertEquals("ok", datenbank.mitVerbindung(con -> "ok"));
        assertVerbindungenGeschlossen();
    }

    private void assertVerbindungenGeschlossen() {
        assertFalse(geoeffnet.isEmpty());
        for (Connection con : geoeffnet) {
            assertDoesNotThrow(() -> assertTrue(con.isClosed()));
        }
    }

    private static void fuegeArtikelEin(Connection con, String bezeichnung) {
        try (var ps = con.prepareStatement("INSERT INTO \"Artikel\" (\"Bezeichnung\") VALUES (?)")) {
            ps.setString(1, bezeichnung);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatenzugriffsException("Einfügen fehlgeschlagen", e);
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
}
