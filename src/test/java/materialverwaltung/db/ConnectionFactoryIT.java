package materialverwaltung.db;

import materialverwaltung.exception.DatenzugriffsException;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** Integrationstest gegen ein echtes PostgreSQL (benötigt Docker). Läuft über Failsafe (mvnw verify). */
@Testcontainers
class ConnectionFactoryIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private ConnectionConfig gueltigeConfig() {
        return new ConnectionConfig(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Test
    void oeffnetVerbindungMitUebergebenerKonfiguration() throws SQLException {
        ConnectionFactory factory = new ConnectionFactory(gueltigeConfig());

        try (Connection con = factory.openConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void verbindungLaesstSichSchliessen() throws SQLException {
        Connection con = new ConnectionFactory(gueltigeConfig()).openConnection();

        con.close();

        assertTrue(con.isClosed());
    }

    @Test
    void fehlgeschlagenerVerbindungsaufbauWirdDatenzugriffsExceptionOhnePasswort() {
        String geheim = "sehr-geheimes-passwort";
        ConnectionConfig falsch = new ConnectionConfig(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), geheim);

        DatenzugriffsException ex = assertThrows(DatenzugriffsException.class,
                () -> new ConnectionFactory(falsch).openConnection());

        assertInstanceOf(SQLException.class, ex.getCause(), "Ursache muss erhalten bleiben");
        assertNotNull(ex.getSqlState());
        assertFalse(ex.getMessage().contains(geheim));
    }

    @Test
    void passwortImUrlParameterErscheintNichtInDerMeldung() {
        ConnectionConfig config = new ConnectionConfig(
                "jdbc:postgresql://localhost:1/db?user=x&password=geheim123", "x", "geheim123");

        DatenzugriffsException ex = assertThrows(DatenzugriffsException.class,
                () -> new ConnectionFactory(config).openConnection());

        assertFalse(ex.getMessage().contains("geheim123"));
    }
}