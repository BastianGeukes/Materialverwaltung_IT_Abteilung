package materialverwaltung.ui;

import materialverwaltung.model.ArtikelBO;
import materialverwaltung.service.ArtikelService;

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;

/**
 * Konsolenfunktionen der Artikelverwaltung und Bestandsanzeige (T17).
 *
 * <p>Alle Abläufe verwenden den {@link ArtikelService}. Fach- und technische Fehler
 * werden einheitlich vom {@link Menue} gemeldet; danach bleibt die Anwendung bedienbar.</p>
 *
 * <p>Artikel werden immer mit ID, Bezeichnung und Bestand angezeigt. Beim Umbenennen
 * ist nur die Bezeichnung veränderbar – den Bestand ändern ausschließlich Materialbuchungen.</p>
 */
public class ArtikelUI {

    public static final String MENUETITEL = "Artikelverwaltung";

    private final ArtikelService service;
    private final KonsolenEingabe eingabe;
    private final PrintStream ausgabe;

    public ArtikelUI(ArtikelService service, KonsolenEingabe eingabe, PrintStream ausgabe) {
        this.service = Objects.requireNonNull(service, "service");
        this.eingabe = Objects.requireNonNull(eingabe, "eingabe");
        this.ausgabe = Objects.requireNonNull(ausgabe, "ausgabe");
    }

    /**
     * Legt das Untermenü „Artikelverwaltung“ im Hauptmenü der Konsole an.
     */
    public static void einbinden(KonsolenUI konsole, ArtikelService service) {
        new ArtikelUI(service, konsole.getEingabe(), konsole.getAusgabe())
                .menuepunkteHinzufuegen(konsole.untermenueHinzufuegen(MENUETITEL));
    }

    /**
     * Ergänzt die Menüpunkte der Artikelverwaltung im übergebenen Menü.
     */
    public void menuepunkteHinzufuegen(Menue menue) {
        menue.punktHinzufuegen("Artikel anlegen", this::anlegen)
                .punktHinzufuegen("Alle Artikel mit Bestand auflisten", this::auflisten)
                .punktHinzufuegen("Bestand eines Artikels anzeigen", this::anzeigen)
                .punktHinzufuegen("Artikel suchen", this::suchen)
                .punktHinzufuegen("Artikel umbenennen", this::umbenennen)
                .punktHinzufuegen("Artikel löschen", this::loeschen);
    }

    void anlegen() {
        String bezeichnung = eingabe.lesePflichttext("Bezeichnung");
        ArtikelBO angelegt = service.anlegen(bezeichnung);
        ausgabe.println("Artikel angelegt: " + beschreibe(angelegt));
    }

    void auflisten() {
        List<ArtikelBO> alle = service.auflisten();
        if (alle.isEmpty()) {
            ausgabe.println("Es sind noch keine Artikel vorhanden.");
            return;
        }
        zeigeTabelle(alle);
    }

    void anzeigen() {
        int id = eingabe.lesePositiveGanzzahl("Artikel-ID");
        ArtikelBO artikel = service.holeNachId(id);
        ausgabe.println(beschreibe(artikel));
    }

    void suchen() {
        String suchbegriff = eingabe.lesePflichttext("Suchbegriff (Teil der Bezeichnung)");
        List<ArtikelBO> treffer = service.suchen(suchbegriff);
        if (treffer.isEmpty()) {
            ausgabe.println("Keine Artikel gefunden, deren Bezeichnung \"" + suchbegriff.strip() + "\" enthält.");
            return;
        }
        ausgabe.println("Gefundene Artikel: " + treffer.size());
        zeigeTabelle(treffer);
    }

    void umbenennen() {
        int id = eingabe.lesePositiveGanzzahl("Artikel-ID");
        ArtikelBO bisher = service.holeNachId(id);
        ausgabe.println("Bisher: " + beschreibe(bisher));
        ausgabe.println("Hinweis: Nur die Bezeichnung kann geändert werden. "
                + "Der Bestand ändert sich nur durch Einlagerung oder Ausgabe.");

        String neueBezeichnung = eingabe.leseZeile(
                "Neue Bezeichnung (leer lassen für keine Änderung)");
        if (neueBezeichnung.isEmpty()) {
            ausgabe.println("Keine Änderung. Der Artikel bleibt " + beschreibe(bisher) + ".");
            return;
        }

        ArtikelBO umbenannt = service.umbenennen(id, neueBezeichnung);
        ausgabe.println("Artikel umbenannt: " + beschreibe(umbenannt));
    }

    void loeschen() {
        int id = eingabe.lesePositiveGanzzahl("Artikel-ID");
        ArtikelBO artikel = service.holeNachId(id);

        if (!eingabe.leseJaNein("Artikel " + beschreibe(artikel) + " wirklich löschen?")) {
            ausgabe.println("Löschen abgebrochen. Der Artikel wurde nicht gelöscht.");
            return;
        }
        service.loeschen(id);
        ausgabe.println("Artikel " + beschreibe(artikel) + " wurde gelöscht.");
    }

    private void zeigeTabelle(List<ArtikelBO> artikel) {
        ausgabe.printf("%6s  %-40s  %10s%n", "ID", "Bezeichnung", "Bestand");
        ausgabe.println("-".repeat(62));
        for (ArtikelBO a : artikel) {
            ausgabe.printf("%6d  %-40s  %10d%n", a.getArtikelId(), a.getBezeichnung(), a.getBestand());
        }
    }

    /** z. B. „[ID 3] Maus (Bestand: 5)“. */
    static String beschreibe(ArtikelBO a) {
        return "[ID " + a.getArtikelId() + "] " + a.getBezeichnung() + " (Bestand: " + a.getBestand() + ")";
    }
}
