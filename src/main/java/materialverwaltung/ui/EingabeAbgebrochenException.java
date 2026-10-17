package materialverwaltung.ui;

import java.io.Serial;

/**
 * Der Benutzer hat die laufende Eingabe mit dem Abbruchwort abgebrochen.
 * Das Menü fängt diese Ausnahme ab und kehrt zur Menüauswahl zurück.
 */
public class EingabeAbgebrochenException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public EingabeAbgebrochenException() {
        super("Eingabe abgebrochen");
    }
}
