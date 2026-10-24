package materialverwaltung.db;

import materialverwaltung.exception.DatenzugriffsException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Öffnet Datenbankverbindungen anhand einer explizit übergebenen Konfiguration.
 *
 * <p>Der Aufrufer ist für das Schließen verantwortlich und nutzt dafür try-with-resources:
 * <pre>{@code
 * try (Connection con = factory.openConnection()) {
 *     ...
 * }
 * }</pre>
 */
public class ConnectionFactory {

    private final ConnectionConfig config;

    public ConnectionFactory(ConnectionConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Factory für die Anwendung: nutzt die DATABASE_*-Umgebungsvariablen. */
    public static ConnectionFactory fromEnvironment() {
        return new ConnectionFactory(ConnectionConfig.fromEnvironment());
    }

    /**
     * Öffnet eine neue Verbindung. Jeder Aufruf liefert eine eigene Verbindung.
     *
     * @throws DatenzugriffsException wenn der Verbindungsaufbau scheitert (Ursache bleibt erhalten,
     *                                    die Meldung enthält kein Passwort)
     */
    public Connection openConnection() {
        try {
            return DriverManager.getConnection(config.url(), config.user(), config.password());
        } catch (SQLException e) {
            throw new DatenzugriffsException(
                    "Datenbankverbindung zu " + config.urlOhneGeheimnisse() + " als Benutzer '"
                            + config.user() + "' fehlgeschlagen (SQLState " + e.getSQLState() + ").",
                    e);
        }
    }
}