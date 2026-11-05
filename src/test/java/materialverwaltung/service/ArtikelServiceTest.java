package materialverwaltung.service;

import materialverwaltung.dao.jdbc.JdbcArtikelDAO;
import materialverwaltung.db.ConnectionConfig;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.exception.FachlicheException;
import materialverwaltung.exception.FachlicherFehler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests ohne laufende Datenbank.
 *
 * <p>Die ConnectionFactory zeigt auf einen Port, an dem keine Datenbank läuft.
 * Ungültige Eingaben müssen deshalb abgelehnt werden, <em>bevor</em> eine Verbindung
 * geöffnet wird. Die Regeln gegen PostgreSQL prüft {@code ArtikelServiceIT}.</p>
 */
class ArtikelServiceTest {

    private static final ConnectionFactory KEINE_DATENBANK = new ConnectionFactory(new ConnectionConfig(
            "jdbc:postgresql://127.0.0.1:1/keine_datenbank?connectTimeout=2", "niemand", "kein-passwort"));

    private final ArtikelService service = new ArtikelService(new JdbcArtikelDAO(), KEINE_DATENBANK);

    private static void assertUngueltig(Executable aufruf) {
        FachlicheException e = assertThrows(FachlicheException.class, aufruf);
        assertEquals(FachlicherFehler.UNGUELTIGE_EINGABE, e.getFehler());
    }

    @Test
    void konstruktorVerlangtAbhaengigkeiten() {
        assertThrows(NullPointerException.class, () -> new ArtikelService(null, KEINE_DATENBANK));
        assertThrows(NullPointerException.class, () -> new ArtikelService(new JdbcArtikelDAO(), null));
    }

    @Test
    void fehlendeOderLeereBezeichnungWirdOhneDatenbankzugriffAbgelehnt() {
        assertUngueltig(() -> service.anlegen(null));
        assertUngueltig(() -> service.anlegen(""));
        assertUngueltig(() -> service.anlegen(" \t  "));
        assertUngueltig(() -> service.umbenennen(1, "   "));
        assertUngueltig(() -> service.umbenennen(1, null));
    }

    @Test
    void zuLangeBezeichnungWirdAbgelehnt() {
        assertUngueltig(() -> service.anlegen("x".repeat(101)));
        assertUngueltig(() -> service.umbenennen(1, "x".repeat(101)));
        // 100 Zeichen sind erlaubt -> Prüfung besteht, erst die Datenbank fehlt
        assertThrows(DatenzugriffsException.class, () -> service.anlegen("x".repeat(100)));
    }

    @Test
    void nichtpositiveIdsSindUngueltigeEingaben() {
        assertUngueltig(() -> service.holeNachId(0));
        assertUngueltig(() -> service.holeNachId(-1));
        assertUngueltig(() -> service.umbenennen(0, "Maus"));
        assertUngueltig(() -> service.loeschen(-7));
    }

    @Test
    void suchbegriffMussEinBisHundertZeichenHaben() {
        assertUngueltig(() -> service.suchen(null));
        assertUngueltig(() -> service.suchen(""));
        assertUngueltig(() -> service.suchen("a".repeat(101)));
    }

    @Test
    void nichtErreichbareDatenbankBleibtTechnischerFehler() {
        assertThrows(DatenzugriffsException.class, () -> service.holeNachId(1));
        assertThrows(DatenzugriffsException.class, service::auflisten);
        assertThrows(DatenzugriffsException.class, () -> service.umbenennen(1, "Maus"));
        assertThrows(DatenzugriffsException.class, () -> service.loeschen(1));
    }
}
