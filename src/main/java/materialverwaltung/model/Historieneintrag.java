package materialverwaltung.model;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Eine Zeile der Bewegungshistorie (T01, Abschnitt 6; T15).
 *
 * <p>Enthält die unveränderte Lagerbewegung sowie die <em>aktuellen</em> Namen von
 * Artikel und Mitarbeiter. Beim Eingang ist der Mitarbeiter der Erfasser,
 * beim Ausgang der Empfänger.</p>
 *
 * @param bewegung           die gespeicherte Lagerbewegung
 * @param artikelBezeichnung aktuelle Bezeichnung des Artikels
 * @param mitarbeiterName    aktueller Name des Mitarbeiters
 */
public record Historieneintrag(LagerbewegungBO bewegung, String artikelBezeichnung, String mitarbeiterName) {

    public static final String ROLLE_ERFASSER = "Erfasser";
    public static final String ROLLE_EMPFAENGER = "Empfänger";

    public Historieneintrag {
        Objects.requireNonNull(bewegung, "bewegung");
        Objects.requireNonNull(artikelBezeichnung, "artikelBezeichnung");
        Objects.requireNonNull(mitarbeiterName, "mitarbeiterName");
    }

    public int artikelId() {
        return bewegung.getArtikelId();
    }

    public int mitarbeiterId() {
        return bewegung.getMitarbeiterId();
    }

    public Bewegungsart bewegungsart() {
        return bewegung.getBewegungsart();
    }

    public int menge() {
        return bewegung.getMenge();
    }

    public OffsetDateTime zeitpunkt() {
        return bewegung.getZeitpunkt();
    }

    /**
     * Rolle des Mitarbeiters: „Erfasser“ beim Eingang, „Empfänger“ beim Ausgang.
     */
    public String mitarbeiterRolle() {
        return switch (bewegung.getBewegungsart()) {
            case EINGANG -> ROLLE_ERFASSER;
            case AUSGANG -> ROLLE_EMPFAENGER;
        };
    }
}
