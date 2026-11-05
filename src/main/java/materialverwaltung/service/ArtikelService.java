package materialverwaltung.service;

import materialverwaltung.dao.ArtikelDAO;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.exception.FachlicheException;
import materialverwaltung.exception.FachlicherFehler;
import materialverwaltung.model.ArtikelBO;

import java.util.List;
import java.util.Objects;

/**
 * Artikelverwaltung mit den Fachregeln aus T01.
 *
 * <ul>
 *     <li>Die Bezeichnung ist Pflicht, wird getrimmt, ist höchstens 100 Zeichen lang und
 *         eindeutig (Groß-/Kleinschreibung wird beachtet: „Maus“ und „maus“ sind verschieden).</li>
 *     <li>Neue Artikel starten mit Bestand 0; den Bestand ändern nur Materialbuchungen.</li>
 *     <li>Umbenennen ändert nur die Bezeichnung; die eigene unveränderte Bezeichnung
 *         darf erneut gespeichert werden.</li>
 *     <li>Fehlende Artikel, vergebene Bezeichnungen und Löschungen trotz vorhandener
 *         Lagerbewegungen führen zu einer {@link FachlicheException}.</li>
 *     <li>Sonstige Datenbankfehler werden als {@link DatenzugriffsException} weitergegeben.</li>
 * </ul>
 *
 * <p>Die Eindeutigkeit prüft die Datenbank über {@code uq_Artikel_Bezeichnung}. So bleibt
 * sie auch bei gleichzeitigen Anlagen gewahrt.</p>
 */
public class ArtikelService {

    /** Constraint-Namen aus V1__schema_anlegen.sql. */
    static final String UQ_ARTIKEL_BEZEICHNUNG = "uq_Artikel_Bezeichnung";
    static final String FK_LAGERBEWEGUNG_ARTIKEL = "fk_Lagerbewegung_Artikel";

    private final ArtikelDAO artikelDAO;
    private final Datenbankzugriff datenbank;

    public ArtikelService(ArtikelDAO artikelDAO, ConnectionFactory connectionFactory) {
        this.artikelDAO = Objects.requireNonNull(artikelDAO, "artikelDAO");
        this.datenbank = new Datenbankzugriff(connectionFactory);
    }

    /**
     * Legt einen Artikel mit Bestand 0 an.
     *
     * @throws FachlicheException UNGUELTIGE_EINGABE oder ARTIKELBEZEICHNUNG_VERGEBEN
     */
    public ArtikelBO anlegen(String bezeichnung) {
        String geprueft = Eingabepruefung.pflichttext(bezeichnung, "Die Bezeichnung");
        try {
            return datenbank.mitVerbindung(con -> artikelDAO.anlegen(con, geprueft));
        } catch (DatenzugriffsException e) {
            throw uebersetzeBezeichnungskonflikt(e, geprueft);
        }
    }

    /**
     * Liefert den Artikel mit der angegebenen ID.
     *
     * @throws FachlicheException ARTIKEL_NICHT_GEFUNDEN, wenn es ihn nicht gibt
     */
    public ArtikelBO holeNachId(int artikelId) {
        int id = Eingabepruefung.id(artikelId, "Artikel-ID");
        return datenbank.mitVerbindung(con -> artikelDAO.findeNachId(con, id))
                .orElseThrow(() -> nichtGefunden(id));
    }

    /**
     * Liefert alle Artikel mit ihrem Bestand, aufsteigend nach ID sortiert.
     */
    public List<ArtikelBO> auflisten() {
        return datenbank.mitVerbindung(artikelDAO::findeAlle);
    }

    /**
     * Sucht Artikel, deren Bezeichnung den Suchbegriff enthält (ohne Beachtung der
     * Groß-/Kleinschreibung). Ohne Treffer wird eine leere Liste geliefert.
     */
    public List<ArtikelBO> suchen(String suchbegriff) {
        String geprueft = Eingabepruefung.suchbegriff(suchbegriff);
        return datenbank.mitVerbindung(con -> artikelDAO.sucheNachBezeichnung(con, geprueft));
    }

    /**
     * Ändert die Bezeichnung eines Artikels. Der Bestand bleibt unverändert.
     *
     * @return der Artikel mit neuer Bezeichnung und aktuellem Bestand
     * @throws FachlicheException ARTIKEL_NICHT_GEFUNDEN oder ARTIKELBEZEICHNUNG_VERGEBEN
     */
    public ArtikelBO umbenennen(int artikelId, String bezeichnung) {
        int id = Eingabepruefung.id(artikelId, "Artikel-ID");
        String geprueft = Eingabepruefung.pflichttext(bezeichnung, "Die Bezeichnung");

        try {
            return datenbank.inTransaktion(con -> {
                if (!artikelDAO.umbenennen(con, id, geprueft)) {
                    throw nichtGefunden(id);
                }
                // Innerhalb derselben Transaktion lesen, um den aktuellen Bestand zurückzugeben
                return artikelDAO.findeNachId(con, id).orElseThrow(() -> nichtGefunden(id));
            });
        } catch (DatenzugriffsException e) {
            throw uebersetzeBezeichnungskonflikt(e, geprueft);
        }
    }

    /**
     * Löscht einen Artikel, sofern keine Lagerbewegung auf ihn verweist – auch bei Bestand 0.
     *
     * @throws FachlicheException ARTIKEL_NICHT_GEFUNDEN oder ARTIKEL_HAT_BEWEGUNGEN
     */
    public void loeschen(int artikelId) {
        int id = Eingabepruefung.id(artikelId, "Artikel-ID");

        boolean geloescht;
        try {
            geloescht = datenbank.mitVerbindung(con -> artikelDAO.loeschen(con, id));
        } catch (DatenzugriffsException e) {
            if (Datenbankzugriff.verletzt(e, Datenbankzugriff.SQLSTATE_FREMDSCHLUESSEL, FK_LAGERBEWEGUNG_ARTIKEL)) {
                throw new FachlicheException(FachlicherFehler.ARTIKEL_HAT_BEWEGUNGEN,
                        "Artikel " + id + " kann nicht gelöscht werden, "
                                + "weil Lagerbewegungen auf ihn verweisen.", e);
            }
            throw e;
        }
        if (!geloescht) {
            throw nichtGefunden(id);
        }
    }

    /**
     * Übersetzt eine Verletzung von {@code uq_Artikel_Bezeichnung} in einen Fachfehler;
     * alle anderen Datenbankfehler bleiben technisch.
     */
    private static RuntimeException uebersetzeBezeichnungskonflikt(DatenzugriffsException e, String bezeichnung) {
        if (Datenbankzugriff.verletzt(e, Datenbankzugriff.SQLSTATE_EINDEUTIGKEIT, UQ_ARTIKEL_BEZEICHNUNG)) {
            return new FachlicheException(FachlicherFehler.ARTIKELBEZEICHNUNG_VERGEBEN,
                    "Die Bezeichnung \"" + bezeichnung + "\" ist bereits an einen anderen Artikel vergeben.", e);
        }
        return e;
    }

    private static FachlicheException nichtGefunden(int id) {
        return new FachlicheException(FachlicherFehler.ARTIKEL_NICHT_GEFUNDEN,
                "Artikel mit der ID " + id + " wurde nicht gefunden.");
    }
}
