package materialverwaltung.ui;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KonsolenUITest {

    private final ByteArrayOutputStream puffer = new ByteArrayOutputStream();

    private KonsolenUI ui(String eingabe) {
        return new KonsolenUI(
                new ByteArrayInputStream(eingabe.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(puffer, true, StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
    }

    private String ausgabe() {
        return puffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void beendenUeberHauptmenue() {
        ui("0\n").starten();

        assertTrue(ausgabe().contains("Hauptmenü"));
        assertTrue(ausgabe().contains("0) Programm beenden"));
        assertTrue(ausgabe().contains("Programm beendet."));
    }

    @Test
    void eingabeendeBeendetSauber() {
        assertDoesNotThrow(() -> ui("").starten());
        assertTrue(ausgabe().contains("Eingabe beendet. Programm wird beendet."));
    }

    @Test
    void eingabeendeInnerhalbEinerAktionBeendetSauber() {
        KonsolenUI ui = ui("1\n");
        ui.getHauptmenue().punktHinzufuegen("ID lesen", () -> ui.getEingabe().lesePositiveGanzzahl("ID"));

        assertDoesNotThrow(ui::starten);
        assertTrue(ausgabe().contains("Eingabe beendet. Programm wird beendet."));
    }

    @Test
    void untermenueErscheintImHauptmenueUndFuehrtZurueck() {
        KonsolenUI ui = ui("1\n1\n0\n0\n");
        List<String> aufrufe = new ArrayList<>();
        ui.untermenueHinzufuegen("Mitarbeiterverwaltung")
                .punktHinzufuegen("Mitarbeiter auflisten", () -> aufrufe.add("auflisten"));

        ui.starten();

        assertEquals(List.of("auflisten"), aufrufe);
        assertTrue(ausgabe().contains("1) Mitarbeiterverwaltung"));
        assertTrue(ausgabe().contains("0) Zurück zum Hauptmenü"));
        assertTrue(ausgabe().contains("Programm beendet."));
    }

    @Test
    void umlauteWerdenKorrektGelesen() {
        KonsolenUI ui = ui("1\nGrößenänderung\n0\n");
        List<String> texte = new ArrayList<>();
        ui.getHauptmenue().punktHinzufuegen("Text", () -> texte.add(ui.getEingabe().lesePflichttext("Text")));

        ui.starten();

        assertEquals(List.of("Größenänderung"), texte);
    }
}
