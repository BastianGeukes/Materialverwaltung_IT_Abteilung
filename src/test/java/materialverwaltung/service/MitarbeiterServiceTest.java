package materialverwaltung.service;

import materialverwaltung.dao.jdbc.JdbcMitarbeiterDAO;
import materialverwaltung.db.ConnectionConfig;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.exception.FachlicheException;
import materialverwaltung.exception.FachlicherFehler;
import materialverwaltung.model.Abteilung;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests ohne laufende Datenbank.
 *
 * <p>Die ConnectionFactory zeigt auf einen Port, an dem keine Datenbank läuft.
 * Ungültige Eingaben müssen deshalb abgelehnt werden, <em>bevor</em> eine Verbindung
 * geöffnet wird – sonst käme eine DatenzugriffsException statt der fachlichen Ablehnung.
 * Die Regeln gegen PostgreSQL prüft {@code MitarbeiterServiceIT}.</p>
 */
class MitarbeiterServiceTest {

    private static final ConnectionFactory KEINE_DATENBANK = new ConnectionFactory(new ConnectionConfig(
            "jdbc:postgresql://127.0.0.1:1/keine_datenbank?connectTimeout=2", "niemand", "kein-passwort"));

    private final MitarbeiterService service = new MitarbeiterService(new JdbcMitarbeiterDAO(), KEINE_DATENBANK);

    private static void assertUngueltig(Executable aufruf) {
        FachlicheException e = assertThrows(FachlicheException.class, aufruf);
        assertEquals(FachlicherFehler.UNGUELTIGE_EINGABE, e.getFehler());
    }

    @Test
    void konstruktorVerlangtAbhaengigkeiten() {
        assertThrows(NullPointerException.class, () -> new MitarbeiterService(null, KEINE_DATENBANK));
        assertThrows(NullPointerException.class, () -> new MitarbeiterService(new JdbcMitarbeiterDAO(), null));
    }

    @Test
    void fehlenderOderLeererNameWirdOhneDatenbankzugriffAbgelehnt() {
        assertUngueltig(() -> service.anlegen(null, Abteilung.IT));
        assertUngueltig(() -> service.anlegen("", Abteilung.IT));
        assertUngueltig(() -> service.anlegen("   \t ", null));
        assertUngueltig(() -> service.bearbeiten(1, "  ", null));
    }

    @Test
    void zuLangerNameWirdAbgelehnt() {
        assertUngueltig(() -> service.anlegen("x".repeat(101), null));
        // Länge zählt nach dem Trimmen
        assertUngueltig(() -> service.anlegen(" " + "x".repeat(101) + " ", null));
    }

    @Test
    void laengeWirdInCodepointsGezaehlt() {
        // 51 Emojis = 51 Codepoints, aber 102 char-Werte -> muss die Prüfung bestehen
        // und erst beim Datenbankzugriff scheitern (hier: keine Datenbank erreichbar)
        String emojis = "😀".repeat(51);
        assertThrows(DatenzugriffsException.class, () -> service.anlegen(emojis, null));
        assertUngueltig(() -> service.anlegen("😀".repeat(101), null));
    }

    @Test
    void nichtpositiveIdsSindUngueltigeEingaben() {
        assertUngueltig(() -> service.holeNachId(0));
        assertUngueltig(() -> service.holeNachId(-5));
        assertUngueltig(() -> service.bearbeiten(0, "Anna", null));
        assertUngueltig(() -> service.loeschen(-1));
    }

    @Test
    void suchbegriffMussEinBisHundertZeichenHaben() {
        assertUngueltig(() -> service.suchen(null));
        assertUngueltig(() -> service.suchen("   "));
        assertUngueltig(() -> service.suchen("a".repeat(101)));
    }

    @Test
    void nichtErreichbareDatenbankBleibtTechnischerFehler() {
        assertThrows(DatenzugriffsException.class, () -> service.holeNachId(1));
        assertThrows(DatenzugriffsException.class, service::auflisten);
        assertThrows(DatenzugriffsException.class, () -> service.loeschen(1));
    }
}
