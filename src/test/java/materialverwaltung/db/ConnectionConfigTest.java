package materialverwaltung.db;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Reine Unit-Tests ohne Datenbank und ohne Docker. */
class ConnectionConfigTest {

    @Test
    void liestUmgebungsvariablen() {
        ConnectionConfig config = ConnectionConfig.fromEnvironment(Map.of(
                "DATABASE_URL", "jdbc:postgresql://localhost/x",
                "DATABASE_USERNAME", "u",
                "DATABASE_PASSWORD", "p"));

        assertEquals("jdbc:postgresql://localhost/x", config.url());
        assertEquals("u", config.user());
        assertEquals("p", config.password());
    }

    @Test
    void leeresPasswortIstErlaubt() {
        ConnectionConfig config = ConnectionConfig.fromEnvironment(Map.of(
                "DATABASE_URL", "jdbc:postgresql://localhost/x",
                "DATABASE_USERNAME", "u",
                "DATABASE_PASSWORD", ""));

        assertEquals("", config.password());
    }

    @Test
    void fehlendeUmgebungsvariableWirdGemeldet() {
        assertThrows(IllegalStateException.class, () -> ConnectionConfig.fromEnvironment(Map.of()));
        assertThrows(IllegalStateException.class, () -> ConnectionConfig.fromEnvironment(Map.of(
                "DATABASE_URL", "jdbc:postgresql://localhost/x",
                "DATABASE_USERNAME", "u")));
    }

    @Test
    void toStringEnthaeltKeinPasswort() {
        ConnectionConfig config = new ConnectionConfig("jdbc:postgresql://localhost/x", "u", "geheim123");

        assertFalse(config.toString().contains("geheim123"));
    }

    @Test
    void passwortImUrlParameterWirdMaskiert() {
        ConnectionConfig config = new ConnectionConfig(
                "jdbc:postgresql://localhost/x?user=u&password=geheim123", "u", "x");

        assertFalse(config.toString().contains("geheim123"));
    }
}