package materialverwaltung.exception;

import java.io.Serial;
import java.sql.SQLException;

/**
 * Der Commit einer Transaktion ist gescheitert, z. B. durch einen Verbindungsabbruch.
 *
 * <p>Der Ausgang ist dann unklar: Die Änderung kann gespeichert sein oder nicht
 * (T01, Abschnitt 7). Die Oberfläche darf in diesem Fall weder Erfolg noch eine sichere
 * Nichtbuchung behaupten und fordert zur Prüfung von Bestand und Historie auf.
 * Es erfolgt keine automatische Wiederholung.</p>
 */
public class UnklarerAusgangException extends DatenzugriffsException {
    @Serial
    private static final long serialVersionUID = 1L;

    public UnklarerAusgangException(String message, SQLException cause) {
        super(message, cause);
    }
}
