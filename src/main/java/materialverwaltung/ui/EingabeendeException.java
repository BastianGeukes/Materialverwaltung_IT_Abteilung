package materialverwaltung.ui;

import java.io.Serial;

/**
 * Der Eingabestrom ist beendet (z. B. Strg+D / Strg+Z oder Ende einer Testeingabe).
 * Die Konsole beendet sich daraufhin sauber.
 */
public class EingabeendeException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public EingabeendeException() {
        super("Eingabeende erreicht");
    }
}
