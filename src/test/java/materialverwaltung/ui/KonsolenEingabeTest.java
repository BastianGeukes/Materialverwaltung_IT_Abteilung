package materialverwaltung.ui;

import materialverwaltung.exception.EingabeAbgebrochenException;
import materialverwaltung.exception.EingabeendeException;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class KonsolenEingabeTest {

    private final ByteArrayOutputStream puffer = new ByteArrayOutputStream();

    private KonsolenEingabe eingabe(String text) {
        PrintStream out = new PrintStream(puffer, true, StandardCharsets.UTF_8);
        return new KonsolenEingabe(new BufferedReader(new StringReader(text)), out);
    }

    private String ausgabe() {
        return puffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void ganzzahlImBereichWirdAkzeptiert() {
        assertEquals(3, eingabe("3\n").leseGanzzahl("Zahl", 0, 5));
    }

    @Test
    void ganzzahlWirdVorDerPruefungNormalisiert() {
        assertEquals(4, eingabe("   4  \n").leseGanzzahl("Zahl", 0, 5));
    }

    @Test
    void nichtnumerischeEingabeWirdAbgelehntUndErneutAbgefragt() {
        assertEquals(2, eingabe("abc\n2\n").leseGanzzahl("Zahl", 0, 5));
        assertTrue(ausgabe().contains("\"abc\" ist keine ganze Zahl"));
    }

    @Test
    void kommazahlWirdAbgelehnt() {
        assertEquals(1, eingabe("1.5\n1\n").leseGanzzahl("Zahl", 0, 5));
        assertTrue(ausgabe().contains("keine ganze Zahl"));
    }

    @Test
    void wertAusserhalbDesBereichsWirdAbgelehnt() {
        assertEquals(5, eingabe("6\n-1\n5\n").leseGanzzahl("Zahl", 0, 5));
        assertTrue(ausgabe().contains("Die Zahl 6 liegt außerhalb des gültigen Bereichs (von 0 bis 5)"));
        assertTrue(ausgabe().contains("Die Zahl -1 liegt außerhalb"));
    }

    @Test
    void zuGrosseZahlFuerIntWirdAbgelehnt() {
        assertEquals(7, eingabe("99999999999999999999\n7\n").lesePositiveGanzzahl("Menge"));
        assertTrue(ausgabe().contains("zu groß bzw. zu klein"));
    }

    @Test
    void leereEingabeBeiZahlWirdAbgelehnt() {
        assertEquals(1, eingabe("\n1\n").leseGanzzahl("Zahl", 0, 5));
        assertTrue(ausgabe().contains("Bitte eine Zahl eingeben"));
    }

    @Test
    void positiveGanzzahlLehntNullUndNegativeAb() {
        assertEquals(12, eingabe("0\n-3\n12\n").lesePositiveGanzzahl("ID"));
        assertTrue(ausgabe().contains("(mindestens 1)"));
    }

    @Test
    void abbruchwortWirftEingabeAbgebrochenException() {
        assertThrows(EingabeAbgebrochenException.class,
                () -> eingabe("Abbrechen\n").leseGanzzahl("Zahl", 0, 5));
    }

    @Test
    void eingabeendeWirftEingabeendeException() {
        assertThrows(EingabeendeException.class, () -> eingabe("").leseZeile("Text"));
        assertThrows(EingabeendeException.class, () -> eingabe("x\n").leseGanzzahl("Zahl", 0, 5));
    }

    @Test
    void pflichttextWirdGetrimmtUndLeereEingabeAbgelehnt() {
        assertEquals("Maus  Pad", eingabe("   \n  Maus  Pad  \n").lesePflichttext("Bezeichnung"));
        assertTrue(ausgabe().contains("Die Eingabe darf nicht leer sein."));
    }

    @Test
    void optionalerTextLeerErgibtNull() {
        assertNull(eingabe("   \n").leseOptionalenText("Abteilung"));
        assertEquals("IT", eingabe(" IT \n").leseOptionalenText("Abteilung"));
    }

    @Test
    void jaNeinAkzeptiertNurGueltigeAntworten() {
        assertTrue(eingabe("vielleicht\nJa\n").leseJaNein("Löschen?"));
        assertTrue(ausgabe().contains("Bitte mit \"j\" oder \"n\" antworten."));
        assertFalse(eingabe("n\n").leseJaNein("Löschen?"));
    }

    @Test
    void ungueltigerBereichIstProgrammierfehler() {
        assertThrows(IllegalArgumentException.class, () -> eingabe("1\n").leseGanzzahl("Zahl", 5, 1));
    }
}
