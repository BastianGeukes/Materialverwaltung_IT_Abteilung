package materialverwaltung.ui;

import materialverwaltung.exception.EingabeAbgebrochenException;
import materialverwaltung.exception.EingabeendeException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Wiederverwendbare Eingabehilfen für die Konsole.
 *
 * <p>Alle Eingaben werden mit {@link String#strip()} normalisiert. Die Eingabe des
 * Abbruchworts ({@value #ABBRUCHWORT}, ohne Beachtung der Groß-/Kleinschreibung)
 * führt zu einer {@link EingabeAbgebrochenException}, das Ende des Eingabestroms
 * zu einer {@link EingabeendeException}.</p>
 *
 * <p>Ungültige Eingaben werden mit einer verständlichen Meldung abgelehnt und
 * erneut abgefragt.</p>
 */
public class KonsolenEingabe {

    public static final String ABBRUCHWORT = "abbrechen";

    private static final Pattern GANZZAHL = Pattern.compile("[+-]?\\d+");

    private final BufferedReader eingabe;
    private final PrintStream ausgabe;

    public KonsolenEingabe(BufferedReader eingabe, PrintStream ausgabe) {
        this.eingabe = Objects.requireNonNull(eingabe);
        this.ausgabe = Objects.requireNonNull(ausgabe);
    }

    /**
     * Liest eine Zeile und gibt sie ohne äußeren Leerraum zurück (kann leer sein).
     */
    public String leseZeile(String aufforderung) {
        ausgabe.print(aufforderung + ": ");
        ausgabe.flush();

        String zeile;
        try {
            zeile = eingabe.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException("Eingabe konnte nicht gelesen werden", e);
        }
        if (zeile == null) {
            throw new EingabeendeException();
        }

        String normalisiert = zeile.strip();
        if (normalisiert.equalsIgnoreCase(ABBRUCHWORT)) {
            throw new EingabeAbgebrochenException();
        }
        return normalisiert;
    }

    /**
     * Liest eine ganze Zahl im Bereich {@code min} bis {@code max} (jeweils einschließlich).
     */
    public int leseGanzzahl(String aufforderung, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min darf nicht größer als max sein");
        }
        while (true) {
            String text = leseZeile(aufforderung);

            if (text.isEmpty()) {
                ausgabe.println("Bitte eine Zahl eingeben (" + bereich(min, max) + ").");
                continue;
            }
            if (!GANZZAHL.matcher(text).matches()) {
                ausgabe.println("\"" + text + "\" ist keine ganze Zahl. Bitte eine Zahl eingeben ("
                        + bereich(min, max) + ").");
                continue;
            }

            int wert;
            try {
                wert = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                // Ziffernfolge, aber zu groß bzw. zu klein für int
                ausgabe.println("Die Zahl " + text + " ist zu groß bzw. zu klein ("
                        + bereich(min, max) + ").");
                continue;
            }

            if (wert < min || wert > max) {
                ausgabe.println("Die Zahl " + wert + " liegt außerhalb des gültigen Bereichs ("
                        + bereich(min, max) + ").");
                continue;
            }
            return wert;
        }
    }

    /**
     * Liest eine positive ganze Zahl, z. B. eine ID oder eine Menge.
     */
    public int lesePositiveGanzzahl(String aufforderung) {
        return leseGanzzahl(aufforderung, 1, Integer.MAX_VALUE);
    }

    /**
     * Liest einen nicht leeren Text. Leere Eingaben werden erneut abgefragt.
     * Weitere Fachregeln (z. B. Länge) prüft der Service.
     */
    public String lesePflichttext(String aufforderung) {
        while (true) {
            String text = leseZeile(aufforderung);
            if (!text.isEmpty()) {
                return text;
            }
            ausgabe.println("Die Eingabe darf nicht leer sein.");
        }
    }

    /**
     * Liest einen optionalen Text. Eine leere Eingabe ergibt {@code null}.
     */
    public String leseOptionalenText(String aufforderung) {
        String text = leseZeile(aufforderung + " (optional, leer lassen zum Überspringen)");
        return text.isEmpty() ? null : text;
    }

    /**
     * Fragt eine Bestätigung ab. Akzeptiert j/ja bzw. n/nein.
     */
    public boolean leseJaNein(String frage) {
        while (true) {
            String antwort = leseZeile(frage + " (j/n)").toLowerCase();
            switch (antwort) {
                case "j", "ja" -> {
                    return true;
                }
                case "n", "nein" -> {
                    return false;
                }
                default -> ausgabe.println("Bitte mit \"j\" oder \"n\" antworten.");
            }
        }
    }

    private static String bereich(int min, int max) {
        if (max == Integer.MAX_VALUE) {
            return "mindestens " + min;
        }
        return "von " + min + " bis " + max;
    }
}
