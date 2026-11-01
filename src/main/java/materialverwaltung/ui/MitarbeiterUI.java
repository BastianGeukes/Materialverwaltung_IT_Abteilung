package materialverwaltung.ui;

import materialverwaltung.model.Abteilung;
import materialverwaltung.model.MitarbeiterBO;
import materialverwaltung.service.MitarbeiterService;

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;

/**
 * Konsolenfunktionen der Mitarbeiterverwaltung (T16).
 *
 * <p>Alle Abläufe verwenden den {@link MitarbeiterService}. Fach- und technische
 * Fehler werden nicht hier, sondern einheitlich vom {@link Menue} gemeldet; danach
 * bleibt die Anwendung bedienbar.</p>
 *
 * <p>Mitarbeiter werden immer mit ihrer ID angezeigt, damit gleichnamige Mitarbeiter
 * unterscheidbar sind.</p>
 */
public class MitarbeiterUI {

    public static final String MENUETITEL = "Mitarbeiterverwaltung";

    private final MitarbeiterService service;
    private final KonsolenEingabe eingabe;
    private final PrintStream ausgabe;

    public MitarbeiterUI(MitarbeiterService service, KonsolenEingabe eingabe, PrintStream ausgabe) {
        this.service = Objects.requireNonNull(service, "service");
        this.eingabe = Objects.requireNonNull(eingabe, "eingabe");
        this.ausgabe = Objects.requireNonNull(ausgabe, "ausgabe");
    }

    /**
     * Legt das Untermenü „Mitarbeiterverwaltung“ im Hauptmenü der Konsole an.
     */
    public static void einbinden(KonsolenUI konsole, MitarbeiterService service) {
        new MitarbeiterUI(service, konsole.getEingabe(), konsole.getAusgabe())
                .menuepunkteHinzufuegen(konsole.untermenueHinzufuegen(MENUETITEL));
    }

    /**
     * Ergänzt die Menüpunkte der Mitarbeiterverwaltung im übergebenen Menü.
     */
    public void menuepunkteHinzufuegen(Menue menue) {
        menue.punktHinzufuegen("Mitarbeiter anlegen", this::anlegen)
                .punktHinzufuegen("Alle Mitarbeiter auflisten", this::auflisten)
                .punktHinzufuegen("Mitarbeiter suchen", this::suchen)
                .punktHinzufuegen("Mitarbeiter bearbeiten", this::bearbeiten)
                .punktHinzufuegen("Mitarbeiter löschen", this::loeschen);
    }

    void anlegen() {
        String name = eingabe.lesePflichttext("Name");
        Abteilung abteilung = waehleAbteilung(null);

        MitarbeiterBO angelegt = service.anlegen(name, abteilung);
        ausgabe.println("Mitarbeiter angelegt: " + beschreibe(angelegt));
    }

    void auflisten() {
        List<MitarbeiterBO> alle = service.auflisten();
        if (alle.isEmpty()) {
            ausgabe.println("Es sind noch keine Mitarbeiter vorhanden.");
            return;
        }
        zeigeTabelle(alle);
    }

    void suchen() {
        String suchbegriff = eingabe.lesePflichttext("Suchbegriff (Teil des Namens)");
        List<MitarbeiterBO> treffer = service.suchen(suchbegriff);
        if (treffer.isEmpty()) {
            ausgabe.println("Keine Mitarbeiter gefunden, deren Name \"" + suchbegriff.strip() + "\" enthält.");
            return;
        }
        ausgabe.println("Gefundene Mitarbeiter: " + treffer.size());
        zeigeTabelle(treffer);
    }

    void bearbeiten() {
        int id = eingabe.lesePositiveGanzzahl("Mitarbeiter-ID");
        MitarbeiterBO bisher = service.holeNachId(id);
        ausgabe.println("Bisher: " + beschreibe(bisher));

        String neuerName = eingabe.leseZeile("Neuer Name (leer lassen für \"" + bisher.getName() + "\")");
        if (neuerName.isEmpty()) {
            neuerName = bisher.getName();
        }

        Abteilung neueAbteilung = bisher.getAbteilung();
        if (eingabe.leseJaNein("Abteilung ändern? (bisher: " + abteilungText(bisher.getAbteilung()) + ")")) {
            neueAbteilung = waehleAbteilung(bisher.getAbteilung());
        }

        MitarbeiterBO geaendert = service.bearbeiten(id, neuerName, neueAbteilung);
        ausgabe.println("Mitarbeiter gespeichert: " + beschreibe(geaendert));
    }

    void loeschen() {
        int id = eingabe.lesePositiveGanzzahl("Mitarbeiter-ID");
        MitarbeiterBO mitarbeiter = service.holeNachId(id);

        if (!eingabe.leseJaNein("Mitarbeiter " + beschreibe(mitarbeiter) + " wirklich löschen?")) {
            ausgabe.println("Löschen abgebrochen. Der Mitarbeiter wurde nicht gelöscht.");
            return;
        }
        service.loeschen(id);
        ausgabe.println("Mitarbeiter " + beschreibe(mitarbeiter) + " wurde gelöscht.");
    }

    /**
     * Lässt eine Abteilung aus der Liste wählen; 0 bedeutet „keine Abteilung“.
     */
    private Abteilung waehleAbteilung(Abteilung bisher) {
        Abteilung[] abteilungen = Abteilung.values();
        ausgabe.println("Abteilungen:");
        ausgabe.println("  0) keine Abteilung");
        for (int i = 0; i < abteilungen.length; i++) {
            String markierung = abteilungen[i] == bisher ? "  (bisher)" : "";
            ausgabe.println("  " + (i + 1) + ") " + abteilungText(abteilungen[i]) + markierung);
        }
        int auswahl = eingabe.leseGanzzahl("Abteilung", 0, abteilungen.length);
        return auswahl == 0 ? null : abteilungen[auswahl - 1];
    }

    private void zeigeTabelle(List<MitarbeiterBO> mitarbeiter) {
        ausgabe.printf("%6s  %-40s  %s%n", "ID", "Name", "Abteilung");
        ausgabe.println("-".repeat(70));
        for (MitarbeiterBO m : mitarbeiter) {
            ausgabe.printf("%6d  %-40s  %s%n", m.getMitarbeiterId(), m.getName(), abteilungText(m.getAbteilung()));
        }
    }

    /** z. B. „[ID 3] Anna Müller (IT)“ – die ID macht gleichnamige Mitarbeiter unterscheidbar. */
    static String beschreibe(MitarbeiterBO m) {
        return "[ID " + m.getMitarbeiterId() + "] " + m.getName() + " (" + abteilungText(m.getAbteilung()) + ")";
    }

    static String abteilungText(Abteilung abteilung) {
        if (abteilung == null) {
            return "keine Abteilung";
        }
        return switch (abteilung) {
            case IT -> "IT";
            case PERSONAL -> "Personal";
            case FINANZEN -> "Finanzen";
            case EINKAUF -> "Einkauf";
            case VERTRIEB -> "Vertrieb";
            case MARKETING -> "Marketing";
            case LOGISTIK -> "Logistik";
            case PRODUKTION -> "Produktion";
            case VERWALTUNG -> "Verwaltung";
            case GESCHAEFTSFUEHRUNG -> "Geschäftsführung";
        };
    }
}
