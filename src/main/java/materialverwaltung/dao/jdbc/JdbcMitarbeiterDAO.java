package materialverwaltung.dao.jdbc;

import materialverwaltung.dao.MitarbeiterDAO;
import materialverwaltung.model.Abteilung;
import materialverwaltung.model.MitarbeiterBO;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public class JdbcMitarbeiterDAO implements MitarbeiterDAO {

    /**
     * Legt einen Mitarbeiter an
     *
     * @param connection DB Verbindung
     * @param name       Name
     * @param abteilung  Abteilung
     * @return MitarbeiterBO des angelegten Mitarbeiters
     */
    @Override
    public MitarbeiterBO anlegen(Connection connection, String name, Abteilung abteilung) {
        return null;
    }

    /**
     * Sucht Mitarbeiter mithilfe einer Id
     *
     * @param connection    DB Verbindung
     * @param mitarbeiterId Id
     * @return Optional (MitarbeiterBO falls gefunden)
     */
    @Override
    public Optional<MitarbeiterBO> findeNachId(Connection connection, int mitarbeiterId) {
        return Optional.empty();
    }

    /**
     * Findet alle Mitarbeiter
     *
     * @param connection DB Verbindung
     * @return Liste von allen Mitarbeitern
     */
    @Override
    public List<MitarbeiterBO> findeAlle(Connection connection) {
        return List.of();
    }

    /**
     * Suche für Mitarbeiter mithilfe eines Suchbregriffs
     * Suche verwendet Teilbegriffe ohne Beachtung der Groß-/Kleinschreibung <br>
     *
     * @param connection  DB Verbindung
     * @param suchbegriff Suchbegriff
     * @return List von MitarbeiterBOs
     */
    @Override
    public List<MitarbeiterBO> sucheNachName(Connection connection, String suchbegriff) {
        return List.of();
    }

    /**
     * Ändert den Namen und die Abteilung eines Mitarbeiters
     *
     * @param connection    DB Verbindung
     * @param mitarbeiterId ID
     * @param name          Name
     * @param abteilung     Abteilung
     * @return boolean
     */
    @Override
    public boolean bearbeiten(Connection connection, int mitarbeiterId, String name, Abteilung abteilung) {
        return false;
    }

    /**
     * Ändert die Abteilung eines Mitarbeiters
     *
     * @param connection    DB Verbindung
     * @param mitarbeiterId ID
     * @param abteilung     Abteilung
     * @return boolean
     */
    @Override
    public boolean bearbeiten(Connection connection, int mitarbeiterId, Abteilung abteilung) {
        return false;
    }

    /**
     * Ändert den Namen eines Mitarbeiters
     *
     * @param connection    DB Verbindung
     * @param mitarbeiterId ID
     * @param name          Name
     * @return boolean
     */
    @Override
    public boolean bearbeiten(Connection connection, int mitarbeiterId, String name) {
        return false;
    }

    /**
     * Löscht einen Mitarbeiter, falls keine Lagerbewegung vorhanden ist
     *
     * @param connection    DB Verbindung
     * @param mitarbeiterId ID
     * @return boolean
     */
    @Override
    public boolean loeschen(Connection connection, int mitarbeiterId) {
        return false;
    }
}
