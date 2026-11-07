package materialverwaltung.service;

import materialverwaltung.dao.jdbc.JdbcArtikelDAO;
import materialverwaltung.dao.jdbc.JdbcLagerbewegungDAO;
import materialverwaltung.dao.jdbc.JdbcMitarbeiterDAO;
import materialverwaltung.db.ConnectionConfig;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.exception.FachlicheException;
import materialverwaltung.exception.FachlicherFehler;
import materialverwaltung.exception.UnzureichenderBestandException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests ohne laufende Datenbank (Eingang und Ausgabe): Ungültige IDs und Mengen müssen abgelehnt
 * werden, bevor eine Verbindung geöffnet wird. Die Buchungen gegen PostgreSQL
 * prüft {@code MaterialServiceIT}.
 */
class MaterialServiceTest {

    private static final ConnectionFactory KEINE_DATENBANK = new ConnectionFactory(new ConnectionConfig(
            "jdbc:postgresql://127.0.0.1:1/keine_datenbank?connectTimeout=2", "niemand", "kein-passwort"));

    private final MaterialService service = new MaterialService(
            new JdbcArtikelDAO(), new JdbcMitarbeiterDAO(), new JdbcLagerbewegungDAO(), KEINE_DATENBANK);

    private static void assertUngueltig(Executable aufruf) {
        FachlicheException e = assertThrows(FachlicheException.class, aufruf);
        assertEquals(FachlicherFehler.UNGUELTIGE_EINGABE, e.getFehler());
    }

    @Test
    void konstruktorVerlangtAlleAbhaengigkeiten() {
        assertThrows(NullPointerException.class, () -> new MaterialService(
                null, new JdbcMitarbeiterDAO(), new JdbcLagerbewegungDAO(), KEINE_DATENBANK));
        assertThrows(NullPointerException.class, () -> new MaterialService(
                new JdbcArtikelDAO(), null, new JdbcLagerbewegungDAO(), KEINE_DATENBANK));
        assertThrows(NullPointerException.class, () -> new MaterialService(
                new JdbcArtikelDAO(), new JdbcMitarbeiterDAO(), null, KEINE_DATENBANK));
        assertThrows(NullPointerException.class, () -> new MaterialService(
                new JdbcArtikelDAO(), new JdbcMitarbeiterDAO(), new JdbcLagerbewegungDAO(), null));
    }

    @Test
    void mengeMussPositivSein() {
        assertUngueltig(() -> service.einlagern(1, 1, 0));
        assertUngueltig(() -> service.einlagern(1, 1, -5));
        assertUngueltig(() -> service.einlagern(1, 1, Integer.MIN_VALUE));
        assertUngueltig(() -> service.ausgeben(1, 1, 0));
        assertUngueltig(() -> service.ausgeben(1, 1, -5));
    }

    @Test
    void idsMuessenPositivSein() {
        assertUngueltig(() -> service.einlagern(0, 1, 1));
        assertUngueltig(() -> service.einlagern(-1, 1, 1));
        assertUngueltig(() -> service.einlagern(1, 0, 1));
        assertUngueltig(() -> service.einlagern(1, -3, 1));
        assertUngueltig(() -> service.ausgeben(0, 1, 1));
        assertUngueltig(() -> service.ausgeben(1, 0, 1));
    }

    @Test
    void gueltigeAngabenErreichenDieDatenbank() {
        // Prüfung bestanden -> Verbindungsaufbau scheitert technisch
        assertThrows(DatenzugriffsException.class, () -> service.einlagern(1, 1, 1));
        assertThrows(DatenzugriffsException.class, () -> service.einlagern(1, 1, Integer.MAX_VALUE));
        assertThrows(DatenzugriffsException.class, () -> service.ausgeben(1, 1, 1));
    }

    @Test
    void unzureichenderBestandIstEineFachlicheException() {
        var e = new UnzureichenderBestandException(3, 6, 5);
        assertInstanceOf(FachlicheException.class, e);
        assertEquals(FachlicherFehler.UNZUREICHENDER_BESTAND, e.getFehler());
        assertEquals(3, e.getArtikelId());
        assertEquals(6, e.getAngefordert());
        assertEquals(5, e.getVerfuegbar());
        assertEquals("Unzureichender Bestand: Für Artikel 3 wurden 6 Stück angefordert, verfügbar sind nur 5.",
                e.getMessage());
    }
}
