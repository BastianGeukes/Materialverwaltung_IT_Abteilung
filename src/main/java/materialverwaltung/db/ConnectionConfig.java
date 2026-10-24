package materialverwaltung.db;

import java.util.Map;
import java.util.Objects;

/**
 * Verbindungsdaten für die Datenbank. Das Passwort wird nie in toString() ausgegeben.
 */
public record ConnectionConfig(String url, String user, String password) {

    public ConnectionConfig {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(password, "password");
        if (url.isBlank()) {
            throw new IllegalArgumentException("Die Datenbank-URL darf nicht leer sein.");
        }
    }

    /** Liest DATABASE_URL, DATABASE_USERNAME und DATABASE_PASSWORD aus der Prozessumgebung. */
    public static ConnectionConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    /** Wie oben, aber mit übergebener Map (gut testbar). */
    public static ConnectionConfig fromEnvironment(Map<String, String> env) {
        String passwort = env.get("DATABASE_PASSWORD");
        if (passwort == null) {
            throw new IllegalStateException("Umgebungsvariable DATABASE_PASSWORD ist nicht gesetzt.");
        }
        return new ConnectionConfig(
                pflicht(env, "DATABASE_URL"),
                pflicht(env, "DATABASE_USERNAME"),
                passwort);
    }

    private static String pflicht(Map<String, String> env, String name) {
        String wert = env.get(name);
        if (wert == null || wert.isBlank()) {
            throw new IllegalStateException("Umgebungsvariable " + name + " ist nicht gesetzt.");
        }
        return wert;
    }

    @Override
    public String toString() {
        return "ConnectionConfig[url=" + urlOhneGeheimnisse() + ", user=" + user + ", password=***]";
    }

    /** URL ohne password=...-Parameter, sicher für Fehlermeldungen und Logs. */
    String urlOhneGeheimnisse() {
        return url.replaceAll("(?i)(password|passwd|pwd)=[^&;]*", "$1=***");
    }
}