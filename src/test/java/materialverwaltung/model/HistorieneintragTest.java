package materialverwaltung.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class HistorieneintragTest {

    private static final OffsetDateTime ZEITPUNKT = OffsetDateTime.of(2026, 11, 7, 10, 30, 0, 0, ZoneOffset.UTC);

    @Test
    void eingangHatDenErfasserAlsMitarbeiterrolle() {
        Historieneintrag eintrag = new Historieneintrag(
                new LagerbewegungBO(1, 10, 20, Bewegungsart.EINGANG, 5, ZEITPUNKT), "Maus", "Anna");

        assertEquals("Erfasser", eintrag.mitarbeiterRolle());
        assertEquals(10, eintrag.artikelId());
        assertEquals("Maus", eintrag.artikelBezeichnung());
        assertEquals(20, eintrag.mitarbeiterId());
        assertEquals("Anna", eintrag.mitarbeiterName());
        assertEquals(Bewegungsart.EINGANG, eintrag.bewegungsart());
        assertEquals(5, eintrag.menge());
        assertEquals(ZEITPUNKT, eintrag.zeitpunkt());
    }

    @Test
    void ausgangHatDenEmpfaengerAlsMitarbeiterrolle() {
        Historieneintrag eintrag = new Historieneintrag(
                new LagerbewegungBO(2, 10, 21, Bewegungsart.AUSGANG, 3, ZEITPUNKT), "Maus", "Ben");

        assertEquals("Empfänger", eintrag.mitarbeiterRolle());
    }

    @Test
    void pflichtangabenDuerfenNichtFehlen() {
        LagerbewegungBO bewegung = new LagerbewegungBO(1, 10, 20, Bewegungsart.EINGANG, 5, ZEITPUNKT);
        assertThrows(NullPointerException.class, () -> new Historieneintrag(null, "Maus", "Anna"));
        assertThrows(NullPointerException.class, () -> new Historieneintrag(bewegung, null, "Anna"));
        assertThrows(NullPointerException.class, () -> new Historieneintrag(bewegung, "Maus", null));
    }
}
