package materialverwaltung.service;

import materialverwaltung.dao.MitarbeiterDAO;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.exception.FachlicheException;
import materialverwaltung.exception.FachlicherFehler;
import materialverwaltung.model.Abteilung;
import materialverwaltung.model.MitarbeiterBO;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

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

    private static final String SQLSTATE_FREMDSCHLUESSEL = "23503";

    private final MitarbeiterDAO mitarbeiterDAO;
    private final ConnectionFactory connectionFactory;

    public MitarbeiterService(MitarbeiterDAO mitarbeiterDAO, ConnectionFactory connectionFactory) {
        this.mitarbeiterDAO = Objects.requireNonNull(mitarbeiterDAO, "mitarbeiterDAO");
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
    }

    /**
     * Legt einen Mitarbeiter an.
     *
     * @param name      Pflichtfeld, wird getrimmt, höchstens 100 Zeichen
     * @param abteilung optional, {@code null} für keine Abteilung
     */
    public MitarbeiterBO anlegen(String name, Abteilung abteilung) {
        String geprueft = Eingabepruefung.pflichttext(name, "Der Name");
        return mitVerbindung(con -> mitarbeiterDAO.anlegen(con, geprueft, abteilung));
    }

    /**
     * Liefert den Mitarbeiter mit der angegebenen ID.
     *
     * @throws FachlicheException MITARBEITER_NICHT_GEFUNDEN, wenn es ihn nicht gibt
     */
    public MitarbeiterBO holeNachId(int mitarbeiterId) {
        int id = Eingabepruefung.id(mitarbeiterId, "Mitarbeiter-ID");
        return mitVerbindung(con -> mitarbeiterDAO.findeNachId(con, id))
                .orElseThrow(() -> nichtGefunden(id));
    }

    /**
     * Liefert alle Mitarbeiter, aufsteigend nach ID sortiert.
     */
    public List<MitarbeiterBO> auflisten() {
        return mitVerbindung(mitarbeiterDAO::findeAlle);
    }

    /**
     * Sucht Mitarbeiter, deren Name den Suchbegriff enthält (ohne Beachtung der
     * Groß-/Kleinschreibung). Ohne Treffer wird eine leere Liste geliefert.
     */
    public List<MitarbeiterBO> suchen(String suchbegriff) {
        String geprueft = Eingabepruefung.suchbegriff(suchbegriff);
        return mitVerbindung(con -> mitarbeiterDAO.sucheNachName(con, geprueft));
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

        boolean geaendert = mitVerbindung(con -> mitarbeiterDAO.bearbeiten(con, id, geprueft, abteilung));
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
            geloescht = mitVerbindung(con -> mitarbeiterDAO.loeschen(con, id));
        } catch (DatenzugriffsException e) {
            if (istVerweisAusLagerbewegung(e)) {
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

    /**
     * Öffnet eine Verbindung, führt die Arbeit aus und schließt die Verbindung
     * zuverlässig – auch wenn die Arbeit mit einer Exception endet.
     */
    private <T> T mitVerbindung(Function<Connection, T> arbeit) {
        try (Connection con = connectionFactory.openConnection()) {
            return arbeit.apply(con);
        } catch (SQLException e) {
            // Kann nur beim Schließen der Verbindung auftreten
            throw new DatenzugriffsException("Die Datenbankverbindung konnte nicht geschlossen werden.", e);
        }
    }

    /**
     * Prüft, ob der Datenbankfehler genau der Fremdschlüssel von Lagerbewegung auf
     * Mitarbeiter ist. Andere Konflikte bleiben technische Fehler (T01, Abschnitt 8).
     */
    private static boolean istVerweisAusLagerbewegung(DatenzugriffsException e) {
        if (!SQLSTATE_FREMDSCHLUESSEL.equals(e.getSqlState())) {
            return false;
        }
        if (e.getCause() instanceof PSQLException psql) {
            ServerErrorMessage details = psql.getServerErrorMessage();
            return details != null && FK_LAGERBEWEGUNG_MITARBEITER.equalsIgnoreCase(details.getConstraint());
        }
        return false;
    }

    private static FachlicheException nichtGefunden(int id) {
        return new FachlicheException(FachlicherFehler.MITARBEITER_NICHT_GEFUNDEN,
                "Mitarbeiter mit der ID " + id + " wurde nicht gefunden.");
    }
}
