package materialverwaltung.service;

import materialverwaltung.dao.MitarbeiterDAO;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.exception.FachlicheException;
import materialverwaltung.exception.FachlicherFehler;
import materialverwaltung.model.Abteilung;
import materialverwaltung.model.MitarbeiterBO;

import java.util.List;
import java.util.Objects;

/**
 * Mitarbeiterverwaltung mit den Fachregeln aus T01.
 *
 * <ul>
 *     <li>Namen werden getrimmt; leere und zu lange Namen werden abgelehnt.</li>
 *     <li>Die Abteilung ist optional ({@code null}); gleiche Namen sind zulässig.</li>
 *     <li>Fehlende Mitarbeiter und Löschungen trotz vorhandener Lagerbewegungen
 *         führen zu einer {@link FachlicheException}.</li>
 *     <li>Sonstige Datenbankfehler werden als {@link DatenzugriffsException} weitergegeben.</li>
 * </ul>
 *
 * <p>Jeder Aufruf öffnet eine eigene Verbindung und schließt sie bei Erfolg und Fehler
 * wieder. Schreibende Aufrufe bestehen aus genau einer SQL-Anweisung und laufen daher
 * als eigene Transaktion im Auto-Commit-Modus.</p>
 */
public class MitarbeiterService {

    /** Name des Fremdschlüssels von Lagerbewegung auf Mitarbeiter (V1__schema_anlegen.sql). */
    static final String FK_LAGERBEWEGUNG_MITARBEITER = "fk_Lagerbewegung_Mitarbeiter";

    private final MitarbeiterDAO mitarbeiterDAO;
    private final Datenbankzugriff datenbank;

    public MitarbeiterService(MitarbeiterDAO mitarbeiterDAO, ConnectionFactory connectionFactory) {
        this.mitarbeiterDAO = Objects.requireNonNull(mitarbeiterDAO, "mitarbeiterDAO");
        this.datenbank = new Datenbankzugriff(connectionFactory);
    }

    /**
     * Legt einen Mitarbeiter an.
     *
     * @param name      Pflichtfeld, wird getrimmt, höchstens 100 Zeichen
     * @param abteilung optional, {@code null} für keine Abteilung
     */
    public MitarbeiterBO anlegen(String name, Abteilung abteilung) {
        String geprueft = Eingabepruefung.pflichttext(name, "Der Name");
        return datenbank.mitVerbindung(con -> mitarbeiterDAO.anlegen(con, geprueft, abteilung));
    }

    /**
     * Liefert den Mitarbeiter mit der angegebenen ID.
     *
     * @throws FachlicheException MITARBEITER_NICHT_GEFUNDEN, wenn es ihn nicht gibt
     */
    public MitarbeiterBO holeNachId(int mitarbeiterId) {
        int id = Eingabepruefung.id(mitarbeiterId, "Mitarbeiter-ID");
        return datenbank.mitVerbindung(con -> mitarbeiterDAO.findeNachId(con, id))
                .orElseThrow(() -> nichtGefunden(id));
    }

    /**
     * Liefert alle Mitarbeiter, aufsteigend nach ID sortiert.
     */
    public List<MitarbeiterBO> auflisten() {
        return datenbank.mitVerbindung(mitarbeiterDAO::findeAlle);
    }

    /**
     * Sucht Mitarbeiter, deren Name den Suchbegriff enthält (ohne Beachtung der
     * Groß-/Kleinschreibung). Ohne Treffer wird eine leere Liste geliefert.
     */
    public List<MitarbeiterBO> suchen(String suchbegriff) {
        String geprueft = Eingabepruefung.suchbegriff(suchbegriff);
        return datenbank.mitVerbindung(con -> mitarbeiterDAO.sucheNachName(con, geprueft));
    }

    /**
     * Ändert Name und Abteilung eines Mitarbeiters.
     *
     * @return der Mitarbeiter mit den gespeicherten Werten
     * @throws FachlicheException MITARBEITER_NICHT_GEFUNDEN, wenn es ihn nicht gibt
     */
    public MitarbeiterBO bearbeiten(int mitarbeiterId, String name, Abteilung abteilung) {
        int id = Eingabepruefung.id(mitarbeiterId, "Mitarbeiter-ID");
        String geprueft = Eingabepruefung.pflichttext(name, "Der Name");

        boolean geaendert = datenbank.mitVerbindung(con -> mitarbeiterDAO.bearbeiten(con, id, geprueft, abteilung));
        if (!geaendert) {
            throw nichtGefunden(id);
        }
        return new MitarbeiterBO(id, geprueft, abteilung);
    }

    /**
     * Löscht einen Mitarbeiter, sofern keine Lagerbewegung auf ihn verweist.
     *
     * @throws FachlicheException MITARBEITER_NICHT_GEFUNDEN oder MITARBEITER_HAT_BEWEGUNGEN
     */
    public void loeschen(int mitarbeiterId) {
        int id = Eingabepruefung.id(mitarbeiterId, "Mitarbeiter-ID");

        boolean geloescht;
        try {
            geloescht = datenbank.mitVerbindung(con -> mitarbeiterDAO.loeschen(con, id));
        } catch (DatenzugriffsException e) {
            if (Datenbankzugriff.verletzt(e, Datenbankzugriff.SQLSTATE_FREMDSCHLUESSEL, FK_LAGERBEWEGUNG_MITARBEITER)) {
                throw new FachlicheException(FachlicherFehler.MITARBEITER_HAT_BEWEGUNGEN,
                        "Mitarbeiter " + id + " kann nicht gelöscht werden, "
                                + "weil Lagerbewegungen auf ihn verweisen.", e);
            }
            throw e;
        }
        if (!geloescht) {
            throw nichtGefunden(id);
        }
    }

    private static FachlicheException nichtGefunden(int id) {
        return new FachlicheException(FachlicherFehler.MITARBEITER_NICHT_GEFUNDEN,
                "Mitarbeiter mit der ID " + id + " wurde nicht gefunden.");
    }
}
