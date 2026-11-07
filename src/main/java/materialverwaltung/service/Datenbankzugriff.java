package materialverwaltung.service;

import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.exception.UnklarerAusgangException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Gemeinsame Verbindungs- und Transaktionsbehandlung der Services (T01, Abschnitt 7 und 8).
 *
 * <ul>
 *     <li>Jeder Aufruf öffnet eine eigene Verbindung und schließt sie bei Erfolg und Fehler.</li>
 *     <li>{@link #inTransaktion} führt mehrere Schritte atomar aus: Commit bei Erfolg,
 *         sonst vollständiger Rollback. Ein Rollback-Fehler verdrängt die ursprüngliche
 *         Ursache nicht, sondern wird als unterdrückter Fehler angehängt.</li>
 *     <li>Scheitert der Commit selbst, ist der Ausgang unklar: dann wird eine
 *         {@link UnklarerAusgangException} geworfen.</li>
 * </ul>
 */
final class Datenbankzugriff {

    static final String SQLSTATE_EINDEUTIGKEIT = "23505";
    static final String SQLSTATE_FREMDSCHLUESSEL = "23503";

    /**
     * Meldung, wenn der Commit selbst scheitert (z. B. Verbindungsabbruch). Der Ausgang ist
     * dann unklar: Die Änderung kann gespeichert sein oder nicht (T01, Abschnitt 7).
     */
    static final String COMMIT_UNKLAR = "Die Bestätigung der Transaktion ist fehlgeschlagen. "
            + "Ob die Änderung gespeichert wurde, ist unklar.";

    private final ConnectionFactory connectionFactory;

    Datenbankzugriff(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
    }

    /**
     * Führt die Arbeit auf einer eigenen Verbindung im Auto-Commit-Modus aus
     * (für Lesezugriffe und einzelne schreibende SQL-Anweisungen).
     */
    <T> T mitVerbindung(Function<Connection, T> arbeit) {
        try (Connection con = connectionFactory.openConnection()) {
            return arbeit.apply(con);
        } catch (SQLException e) {
            // Kann nur beim Schließen der Verbindung auftreten
            throw new DatenzugriffsException("Die Datenbankverbindung konnte nicht geschlossen werden.", e);
        }
    }

    /**
     * Führt die Arbeit als eine Transaktion aus. Endet sie mit einer Exception,
     * wird zurückgerollt und die ursprüngliche Exception weitergegeben.
     */
    <T> T inTransaktion(Function<Connection, T> arbeit) {
        return mitVerbindung(con -> {
            try {
                con.setAutoCommit(false);
            } catch (SQLException e) {
                throw new DatenzugriffsException("Die Transaktion konnte nicht gestartet werden.", e);
            }

            T ergebnis;
            try {
                ergebnis = arbeit.apply(con);
            } catch (RuntimeException | Error e) {
                rollback(con, e);
                throw e;
            }

            try {
                con.commit();
            } catch (SQLException e) {
                UnklarerAusgangException fehler = new UnklarerAusgangException(COMMIT_UNKLAR, e);
                rollback(con, fehler);
                throw fehler;
            }
            return ergebnis;
        });
    }

    private static void rollback(Connection con, Throwable ursache) {
        try {
            con.rollback();
        } catch (SQLException rollbackFehler) {
            ursache.addSuppressed(rollbackFehler);
        }
    }

    /**
     * Prüft, ob der Datenbankfehler genau die angegebene Bedingung verletzt.
     * Andere Konflikte bleiben technische Fehler (T01, Abschnitt 8).
     */
    static boolean verletzt(DatenzugriffsException e, String sqlState, String constraint) {
        if (!sqlState.equals(e.getSqlState())) {
            return false;
        }
        if (e.getCause() instanceof PSQLException psql) {
            ServerErrorMessage details = psql.getServerErrorMessage();
            return details != null && constraint.equalsIgnoreCase(details.getConstraint());
        }
        return false;
    }
}
