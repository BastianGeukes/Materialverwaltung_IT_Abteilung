package materialverwaltung.ui;

import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.exception.EingabeendeException;
import materialverwaltung.exception.FachlicheException;
import materialverwaltung.exception.FachlicherFehler;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MenueTest {

    private final ByteArrayOutputStream puffer = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(puffer, true, StandardCharsets.UTF_8);

    private Menue menue(String eingabeText) {
        KonsolenEingabe eingabe = new KonsolenEingabe(new BufferedReader(new StringReader(eingabeText)), out);
        return new Menue("Test", "Zurück", eingabe, out);
    }

    private String ausgabe() {
        return puffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void zeigtNummerierteMenuepunkteUndVerlassen() {
        menue("0\n").punktHinzufuegen("Erster", () -> { }).punktHinzufuegen("Zweiter", () -> { }).anzeigen();

        assertTrue(ausgabe().contains("=== Test ==="));
        assertTrue(ausgabe().contains("1) Erster"));
        assertTrue(ausgabe().contains("2) Zweiter"));
        assertTrue(ausgabe().contains("0) Zurück"));
    }

    @Test
    void fuehrtGewaehlteAktionAusUndZeigtMenueErneut() {
        List<String> aufrufe = new ArrayList<>();
        menue("2\n1\n2\n0\n")
                .punktHinzufuegen("A", () -> aufrufe.add("A"))
                .punktHinzufuegen("B", () -> aufrufe.add("B"))
                .anzeigen();

        assertEquals(List.of("B", "A", "B"), aufrufe);
    }

    @Test
    void ungueltigeAuswahlFuehrtZuRueckmeldungOhneAktion() {
        List<String> aufrufe = new ArrayList<>();
        menue("7\nxyz\n0\n").punktHinzufuegen("A", () -> aufrufe.add("A")).anzeigen();

        assertTrue(aufrufe.isEmpty());
        assertTrue(ausgabe().contains("Die Zahl 7 liegt außerhalb des gültigen Bereichs (von 0 bis 1)"));
        assertTrue(ausgabe().contains("\"xyz\" ist keine ganze Zahl"));
    }

    @Test
    void abbruchInnerhalbEinerAktionFuehrtZurueckInsMenue() {
        List<Integer> gelesen = new ArrayList<>();
        // Menü und Aktion lesen aus derselben Eingabe
        KonsolenEingabe eingabe =
                new KonsolenEingabe(new BufferedReader(new StringReader("1\nabbrechen\n1\n42\n0\n")), out);
        new Menue("Test", "Zurück", eingabe, out)
                .punktHinzufuegen("Zahl lesen", () -> gelesen.add(eingabe.lesePositiveGanzzahl("ID")))
                .anzeigen();

        assertEquals(List.of(42), gelesen);
        assertTrue(ausgabe().contains("Eingabe abgebrochen."));
    }

    @Test
    void abbruchBeiDerMenueauswahlVerlaesstDasMenue() {
        List<String> aufrufe = new ArrayList<>();
        menue("abbrechen\n").punktHinzufuegen("A", () -> aufrufe.add("A")).anzeigen();
        assertTrue(aufrufe.isEmpty());
    }

    @Test
    void fachlicheExceptionWirdVerstaendlichGemeldet() {
        menue("1\n0\n").punktHinzufuegen("Fehler", () -> {
            throw new FachlicheException(FachlicherFehler.ARTIKEL_NICHT_GEFUNDEN, "Artikel 5 wurde nicht gefunden.");
        }).anzeigen();

        assertTrue(ausgabe().contains("Fehler: Artikel 5 wurde nicht gefunden."));
    }

    @Test
    void technischerFehlerWirdNeutralOhneSqlDetailsGemeldet() {
        menue("1\n0\n").punktHinzufuegen("DB", () -> {
            throw new DatenzugriffsException("Abfrage fehlgeschlagen",
                    new SQLException("FATAL: password authentication failed for user geheim", "28P01"));
        }).anzeigen();

        assertTrue(ausgabe().contains(Menue.TECHNISCHER_FEHLER));
        assertFalse(ausgabe().contains("password"));
        assertFalse(ausgabe().contains("geheim"));
        assertFalse(ausgabe().contains("28P01"));
    }

    @Test
    void eingabeendeWirdAnDenAufruferWeitergegeben() {
        Menue m = menue("1\n").punktHinzufuegen("A", () -> { });
        assertThrows(EingabeendeException.class, m::anzeigen);
    }

    @Test
    void untermenueKehrtZumUebergeordnetenMenueZurueck() {
        KonsolenEingabe eingabe =
                new KonsolenEingabe(new BufferedReader(new StringReader("1\n1\n0\n0\n")), out);
        List<String> aufrufe = new ArrayList<>();
        Menue unter = new Menue("Unter", "Zurück zum Hauptmenü", eingabe, out)
                .punktHinzufuegen("Aktion", () -> aufrufe.add("Aktion"));
        Menue haupt = new Menue("Haupt", "Beenden", eingabe, out)
                .punktHinzufuegen("Unter", unter::anzeigen);

        haupt.anzeigen();

        assertEquals(List.of("Aktion"), aufrufe);
        String text = ausgabe();
        // Nach dem Verlassen des Untermenüs wird das Hauptmenü erneut angezeigt
        assertTrue(text.lastIndexOf("=== Haupt ===") > text.lastIndexOf("=== Unter ==="));
    }
}
