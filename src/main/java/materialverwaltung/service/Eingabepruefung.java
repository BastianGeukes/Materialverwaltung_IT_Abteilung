package materialverwaltung.service;

import materialverwaltung.exception.FachlicheException;
import materialverwaltung.exception.FachlicherFehler;

/**
 * Gemeinsame Prüf- und Normalisierungsregeln der Services (T01, Abschnitt 3 und 4).
 *
 * <p>Texte werden mit {@link String#strip()} normalisiert. Die Länge wird in
 * Unicode-Codepoints gezählt. Verstöße führen zu einer {@link FachlicheException}
 * mit dem Code {@link FachlicherFehler#UNGUELTIGE_EINGABE}.</p>
 */
final class Eingabepruefung {

    static final int MAX_TEXTLAENGE = 100;

    private Eingabepruefung() {
    }

    /**
     * Prüft einen Pflichttext (z. B. Name oder Bezeichnung) und gibt ihn normalisiert zurück.
     */
    static String pflichttext(String wert, String feldname) {
        if (wert == null) {
            throw ungueltig(feldname + " darf nicht fehlen.");
        }
        String normalisiert = wert.strip();
        if (normalisiert.isEmpty()) {
            throw ungueltig(feldname + " darf nicht leer sein.");
        }
        pruefeLaenge(normalisiert, feldname);
        return normalisiert;
    }

    /**
     * Prüft einen Suchbegriff: normalisiert, 1 bis 100 Zeichen.
     */
    static String suchbegriff(String wert) {
        if (wert == null || wert.strip().isEmpty()) {
            throw ungueltig("Der Suchbegriff darf nicht leer sein.");
        }
        String normalisiert = wert.strip();
        pruefeLaenge(normalisiert, "Der Suchbegriff");
        return normalisiert;
    }

    /**
     * Prüft, dass eine ID positiv ist.
     */
    static int id(int id, String bezeichnung) {
        if (id <= 0) {
            throw ungueltig("Die " + bezeichnung + " muss eine positive Zahl sein.");
        }
        return id;
    }

    private static void pruefeLaenge(String text, String feldname) {
        int laenge = text.codePointCount(0, text.length());
        if (laenge > MAX_TEXTLAENGE) {
            throw ungueltig(feldname + " darf höchstens " + MAX_TEXTLAENGE
                    + " Zeichen lang sein (eingegeben: " + laenge + ").");
        }
    }

    private static FachlicheException ungueltig(String meldung) {
        return new FachlicheException(FachlicherFehler.UNGUELTIGE_EINGABE, meldung);
    }
}
