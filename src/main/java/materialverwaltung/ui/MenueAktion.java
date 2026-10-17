package materialverwaltung.ui;

/**
 * Eine Funktion, die über einen Menüpunkt ausgeführt wird.
 * Fachliche Funktionen (T16–T19) werden als solche Aktionen ergänzt.
 */
@FunctionalInterface
public interface MenueAktion {
    void ausfuehren();
}
