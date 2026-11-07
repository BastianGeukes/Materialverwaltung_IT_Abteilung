package materialverwaltung.ui;

import materialverwaltung.model.ArtikelBO;
import materialverwaltung.model.Buchungsergebnis;
import materialverwaltung.model.MitarbeiterBO;
import materialverwaltung.service.ArtikelService;
import materialverwaltung.service.MaterialService;
import materialverwaltung.service.MitarbeiterService;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Konsolenfunktionen für Einlagerung und Ausgabe von Material (T18).
 *
 * <p>Artikel und Mitarbeiter werden über ihre IDs ausgewählt und zur Kontrolle mit
 * Namen angezeigt. Beim Eingang wird nach dem Erfasser, bei der Ausgabe nach dem
 * Empfänger gefragt. Gebucht wird ausschließlich über den {@link MaterialService},
 * der Artikel und Mitarbeiter innerhalb der Buchungstransaktion erneut prüft.</p>
 *
 * <p>Fachliche Ablehnungen („Fehler: …“), technische Fehler (neutrale Meldung) und ein
 * unklarer Buchungsausgang werden vom {@link Menue} unterscheidbar gemeldet; danach
 * bleibt die Konsole bedienbar.</p>
 */
public class MaterialUI {

    public static final String MENUETITEL = "Materialbuchung";

    private final MaterialService materialService;
    private final ArtikelService artikelService;
    private final MitarbeiterService mitarbeiterService;
    private final KonsolenEingabe eingabe;
    private final PrintStream ausgabe;

    public MaterialUI(MaterialService materialService, ArtikelService artikelService,
                      MitarbeiterService mitarbeiterService, KonsolenEingabe eingabe, PrintStream ausgabe) {
        this.materialService = Objects.requireNonNull(materialService, "materialService");
        this.artikelService = Objects.requireNonNull(artikelService, "artikelService");
        this.mitarbeiterService = Objects.requireNonNull(mitarbeiterService, "mitarbeiterService");
        this.eingabe = Objects.requireNonNull(eingabe, "eingabe");
        this.ausgabe = Objects.requireNonNull(ausgabe, "ausgabe");
    }

    /**
     * Legt das Untermenü „Materialbuchung“ im Hauptmenü der Konsole an.
     */
    public static void einbinden(KonsolenUI konsole, MaterialService materialService,
                                 ArtikelService artikelService, MitarbeiterService mitarbeiterService) {
        new MaterialUI(materialService, artikelService, mitarbeiterService, konsole.getEingabe(), konsole.getAusgabe())
                .menuepunkteHinzufuegen(konsole.untermenueHinzufuegen(MENUETITEL));
    }

    /**
     * Ergänzt die Menüpunkte für Einlagerung und Ausgabe im übergebenen Menü.
     */
    public void menuepunkteHinzufuegen(Menue menue) {
        menue.punktHinzufuegen("Material einlagern (Eingang)", this::einlagern)
                .punktHinzufuegen("Material ausgeben (Ausgang)", this::ausgeben);
    }

    void einlagern() {
        ausgabe.println("--- Material einlagern ---");
        ArtikelBO artikel = waehleArtikel();
        MitarbeiterBO erfasser = waehleMitarbeiter("Erfasser");
        int menge = eingabe.lesePositiveGanzzahl("Menge");

        Buchungsergebnis ergebnis = materialService.einlagern(
                artikel.getArtikelId(), erfasser.getMitarbeiterId(), menge);

        ausgabe.println("Eingang gebucht: " + menge + " Stück " + artikelText(artikel)
                + ", erfasst von " + mitarbeiterText(erfasser) + ".");
        ausgabe.println("Neuer Bestand: " + ergebnis.neuerBestand());
    }

    void ausgeben() {
        ausgabe.println("--- Material ausgeben ---");
        ArtikelBO artikel = waehleArtikel();
        MitarbeiterBO empfaenger = waehleMitarbeiter("Empfänger");
        int menge = eingabe.lesePositiveGanzzahl("Menge");

        Buchungsergebnis ergebnis = materialService.ausgeben(
                artikel.getArtikelId(), empfaenger.getMitarbeiterId(), menge);

        ausgabe.println("Ausgabe gebucht: " + menge + " Stück " + artikelText(artikel)
                + " an " + mitarbeiterText(empfaenger) + ".");
        ausgabe.println("Neuer Bestand: " + ergebnis.neuerBestand());
    }

    /** Fragt die Artikel-ID ab und zeigt den Artikel mit aktuellem Bestand zur Kontrolle. */
    private ArtikelBO waehleArtikel() {
        int id = eingabe.lesePositiveGanzzahl("Artikel-ID");
        ArtikelBO artikel = artikelService.holeNachId(id);
        ausgabe.println("Artikel: " + ArtikelUI.beschreibe(artikel));
        return artikel;
    }

    /** Fragt die Mitarbeiter-ID in der angegebenen Rolle ab und zeigt den Mitarbeiter zur Kontrolle. */
    private MitarbeiterBO waehleMitarbeiter(String rolle) {
        int id = eingabe.lesePositiveGanzzahl(rolle + " (Mitarbeiter-ID)");
        MitarbeiterBO mitarbeiter = mitarbeiterService.holeNachId(id);
        ausgabe.println(rolle + ": " + MitarbeiterUI.beschreibe(mitarbeiter));
        return mitarbeiter;
    }

    private static String artikelText(ArtikelBO a) {
        return "[ID " + a.getArtikelId() + "] " + a.getBezeichnung();
    }

    private static String mitarbeiterText(MitarbeiterBO m) {
        return "[ID " + m.getMitarbeiterId() + "] " + m.getName();
    }
}
