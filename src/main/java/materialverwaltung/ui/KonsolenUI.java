package materialverwaltung.ui;

import materialverwaltung.exception.EingabeendeException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * Einstiegspunkt der Konsolenoberfläche.
 *
 * <p>Ein- und Ausgabe werden als Ströme übergeben, damit die Oberfläche in Tests
 * ohne echte Konsole ausgeführt werden kann. Fachliche Bereiche werden später
 * über {@link #untermenueHinzufuegen(String)} bzw. {@link #getHauptmenue()} ergänzt.</p>
 */
public class KonsolenUI {

    private final PrintStream ausgabe;
    private final KonsolenEingabe eingabe;
    private final Menue hauptmenue;

    public KonsolenUI(InputStream in, PrintStream out) {
        this(in, out, konsolenZeichensatz());
    }

    public KonsolenUI(InputStream in, PrintStream out, Charset zeichensatz) {
        this.ausgabe = Objects.requireNonNull(out);
        this.eingabe = new KonsolenEingabe(
                new BufferedReader(new InputStreamReader(Objects.requireNonNull(in), zeichensatz)), out);
        this.hauptmenue = new Menue("Materialverwaltung – Hauptmenü", "Programm beenden", eingabe, out);
    }

    /**
     * Startet die Menüführung. Kehrt zurück, wenn der Benutzer das Programm beendet
     * oder die Eingabe endet.
     */
    public void starten() {
        ausgabe.println("Willkommen in der Materialverwaltung der IT-Abteilung.");
        ausgabe.println("Hinweis: Mit \"" + KonsolenEingabe.ABBRUCHWORT
                + "\" kann jede Eingabe abgebrochen werden.");
        try {
            hauptmenue.anzeigen();
            ausgabe.println("Programm beendet. Auf Wiedersehen!");
        } catch (EingabeendeException e) {
            ausgabe.println();
            ausgabe.println("Eingabe beendet. Programm wird beendet.");
        }
    }

    /**
     * Legt ein Untermenü an, das im Hauptmenü als eigener Punkt erscheint.
     */
    public Menue untermenueHinzufuegen(String titel) {
        Menue untermenue = new Menue(titel, "Zurück zum Hauptmenü", eingabe, ausgabe);
        hauptmenue.punktHinzufuegen(titel, untermenue::anzeigen);
        return untermenue;
    }

    public Menue getHauptmenue() {
        return hauptmenue;
    }

    public KonsolenEingabe getEingabe() {
        return eingabe;
    }

    public PrintStream getAusgabe() {
        return ausgabe;
    }

    private static Charset konsolenZeichensatz() {
        // Ab Java 25 gibt "stdin.encoding" die Kodierung der Konsoleneingabe an.
        String name = System.getProperty("stdin.encoding");
        try {
            return name != null ? Charset.forName(name) : Charset.defaultCharset();
        } catch (IllegalArgumentException e) {
            return Charset.defaultCharset();
        }
    }
}
