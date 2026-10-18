package materialverwaltung.ui;

import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.exception.EingabeAbgebrochenException;
import materialverwaltung.exception.EingabeendeException;
import materialverwaltung.exception.FachlicheException;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ein Konsolenmenü mit nummerierten Menüpunkten.
 *
 * <p>Punkt 0 verlässt das Menü (Zurück bzw. Beenden). Ein Menüpunkt kann eine
 * fachliche Aktion oder ein weiteres Menü ({@code untermenue::anzeigen}) sein.
 * Nach jeder Aktion wird das Menü erneut angezeigt.</p>
 *
 * <p>Fehler aus einer Aktion werden hier einheitlich gemeldet: Fachfehler mit ihrer
 * Meldung, technische Fehler neutral ohne SQL-Details. Ein Abbruch der Eingabe führt
 * zurück zur Menüauswahl.</p>
 */
public class Menue {

    static final String TECHNISCHER_FEHLER =
            "Ein technischer Fehler ist aufgetreten. Bitte versuchen Sie es später erneut.";

    private record Menuepunkt(String bezeichnung, MenueAktion aktion) {
    }

    private final String titel;
    private final String verlassenText;
    private final KonsolenEingabe eingabe;
    private final PrintStream ausgabe;
    private final List<Menuepunkt> punkte = new ArrayList<>();

    public Menue(String titel, String verlassenText, KonsolenEingabe eingabe, PrintStream ausgabe) {
        this.titel = Objects.requireNonNull(titel);
        this.verlassenText = Objects.requireNonNull(verlassenText);
        this.eingabe = Objects.requireNonNull(eingabe);
        this.ausgabe = Objects.requireNonNull(ausgabe);
    }

    /**
     * Ergänzt einen Menüpunkt. Die Nummerierung folgt der Reihenfolge des Hinzufügens.
     */
    public Menue punktHinzufuegen(String bezeichnung, MenueAktion aktion) {
        punkte.add(new Menuepunkt(Objects.requireNonNull(bezeichnung), Objects.requireNonNull(aktion)));
        return this;
    }

    /**
     * Zeigt das Menü so lange an, bis 0 gewählt oder die Auswahl abgebrochen wird.
     * Das Ende der Eingabe ({@link EingabeendeException}) wird an den Aufrufer weitergegeben.
     */
    public void anzeigen() {
        while (true) {
            zeigeOptionen();

            int auswahl;
            try {
                auswahl = eingabe.leseGanzzahl("Auswahl", 0, punkte.size());
            } catch (EingabeAbgebrochenException e) {
                return;
            }

            if (auswahl == 0) {
                return;
            }
            fuehreAus(punkte.get(auswahl - 1));
        }
    }

    private void zeigeOptionen() {
        ausgabe.println();
        ausgabe.println("=== " + titel + " ===");
        for (int i = 0; i < punkte.size(); i++) {
            ausgabe.println((i + 1) + ") " + punkte.get(i).bezeichnung());
        }
        ausgabe.println("0) " + verlassenText);
    }

    private void fuehreAus(Menuepunkt punkt) {
        try {
            punkt.aktion().ausfuehren();
        } catch (EingabeAbgebrochenException e) {
            ausgabe.println("Eingabe abgebrochen.");
        } catch (FachlicheException e) {
            ausgabe.println("Fehler: " + e.getMessage());
        } catch (DatenzugriffsException e) {
            ausgabe.println(TECHNISCHER_FEHLER);
        }
    }
}
