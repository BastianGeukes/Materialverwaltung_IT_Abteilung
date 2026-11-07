package materialverwaltung.ui;

import materialverwaltung.model.Historieneintrag;
import materialverwaltung.service.MaterialService;

import java.io.PrintStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Lesbare Konsolenausgabe der Bewegungshistorie (T19).
 *
 * <p>Jede Bewegung wird als kurzer Block mit verständlichen Bezeichnungen ausgegeben,
 * die neueste zuerst (Reihenfolge aus T15):</p>
 * <pre>
 * Bewegung 12 – Ausgang am 07.11.2026 um 14:30:15
 *   Artikel:    [ID 1] Maus
 *   Menge:      2
 *   Empfänger:  [ID 2] Ben
 * </pre>
 *
 * <p>Ein Block passt sich auch an lange Bezeichnungen und Namen (bis 100 Zeichen) an,
 * ohne dass Spalten verrutschen.</p>
 */
public class HistorieUI {

    public static final String MENUEPUNKT = "Bewegungshistorie anzeigen";

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter UHRZEIT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final MaterialService service;
    private final PrintStream ausgabe;
    private final ZoneId zeitzone;

    /**
     * @param zeitzone Zeitzone, in der die Zeitpunkte angezeigt werden
     */
    public HistorieUI(MaterialService service, PrintStream ausgabe, ZoneId zeitzone) {
        this.service = Objects.requireNonNull(service, "service");
        this.ausgabe = Objects.requireNonNull(ausgabe, "ausgabe");
        this.zeitzone = Objects.requireNonNull(zeitzone, "zeitzone");
    }

    /**
     * Ergänzt im Hauptmenü den Punkt „Bewegungshistorie anzeigen“. Die Zeitpunkte werden
     * in der Zeitzone des Systems angezeigt.
     */
    public static void einbinden(KonsolenUI konsole, MaterialService service) {
        einbinden(konsole, service, ZoneId.systemDefault());
    }

    public static void einbinden(KonsolenUI konsole, MaterialService service, ZoneId zeitzone) {
        HistorieUI ui = new HistorieUI(service, konsole.getAusgabe(), zeitzone);
        konsole.getHauptmenue().punktHinzufuegen(MENUEPUNKT, ui::anzeigen);
    }

    void anzeigen() {
        List<Historieneintrag> historie = service.historie();
        ausgabe.println("--- Bewegungshistorie ---");
        if (historie.isEmpty()) {
            ausgabe.println("Es sind noch keine Lagerbewegungen vorhanden. "
                    + "Buchungen erscheinen hier, sobald Material eingelagert oder ausgegeben wurde.");
            return;
        }

        ausgabe.println(historie.size() + (historie.size() == 1 ? " Bewegung" : " Bewegungen")
                + ", die neueste zuerst:");
        for (Historieneintrag eintrag : historie) {
            ausgabe.println();
            zeigeEintrag(eintrag);
        }
    }

    private void zeigeEintrag(Historieneintrag e) {
        var zeitpunkt = e.zeitpunkt().atZoneSameInstant(zeitzone);
        ausgabe.println("Bewegung " + e.bewegung().getLagerbewegungId() + " – " + bewegungsartText(e)
                + " am " + DATUM.format(zeitpunkt) + " um " + UHRZEIT.format(zeitpunkt));
        ausgabe.println("  Artikel:    [ID " + e.artikelId() + "] " + e.artikelBezeichnung());
        ausgabe.println("  Menge:      " + e.menge());
        ausgabe.printf("  %-11s [ID %d] %s%n", e.mitarbeiterRolle() + ":", e.mitarbeiterId(), e.mitarbeiterName());
    }

    private static String bewegungsartText(Historieneintrag e) {
        return switch (e.bewegungsart()) {
            case EINGANG -> "Eingang";
            case AUSGANG -> "Ausgang";
        };
    }
}
