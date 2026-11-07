package materialverwaltung.model;

import java.util.Objects;

/**
 * Ergebnis einer Materialbuchung (T01, Abschnitt 6).
 *
 * @param bewegung     die gespeicherte Lagerbewegung mit ID und Zeitpunkt
 * @param neuerBestand der Bestand unmittelbar nach dieser Buchung; spätere Buchungen
 *                     können ihn wieder verändern
 */
public record Buchungsergebnis(LagerbewegungBO bewegung, int neuerBestand) {

    public Buchungsergebnis {
        Objects.requireNonNull(bewegung, "bewegung");
    }
}
