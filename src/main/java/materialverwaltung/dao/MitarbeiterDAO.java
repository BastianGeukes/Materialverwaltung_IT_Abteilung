package materialverwaltung.dao;

import materialverwaltung.model.Abteilung;
import materialverwaltung.model.MitarbeiterBO;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface MitarbeiterDAO {

    /**
     * Legt einen Mitarbeiter an
     *
     * @param connection DB Verbindung
     * @param name       Name
     * @param abteilung  Abteilung
     * @return MitarbeiterBO des angelegten Mitarbeiters
     */
    public MitarbeiterBO anlegen(
            Connection connection, String name, Abteilung abteilung);

    /**
     * Sucht Mitarbeiter mithilfe einer Id
     *
     * @param connection    DB Verbindung
     * @param mitarbeiterId Id
     * @return Optional (MitarbeiterBO falls gefunden)
     */
    public Optional<MitarbeiterBO> findeNachId(
            Connection connection, int mitarbeiterId);

    /**
     * Findet alle Mitarbeiter
     *
     * @param connection DB Verbindung
     * @return Liste von allen Mitarbeitern
     */
    public List<MitarbeiterBO> findeAlle(
            Connection connection);

    /**
     * Suche für Mitarbeiter mithilfe eines Suchbregriffs
     * Suche verwendet Teilbegriffe ohne Beachtung der Groß-/Kleinschreibung <br>
     *
     * @param connection  DB Verbindung
     * @param suchbegriff Suchbegriff
     * @return List von MitarbeiterBOs
     */
    public List<MitarbeiterBO> sucheNachName(
            Connection connection, String suchbegriff);

    /**
     * Ändert den Namen und die Abteilung eines Mitarbeiters
     *
     * @param connection    DB Verbindung
     * @param mitarbeiterId ID
     * @param name          Name
     * @param abteilung     Abteilung
     * @return boolean
     */
    public boolean bearbeiten(
            Connection connection, int mitarbeiterId, String name, Abteilung abteilung);

    /**
     * Ändert die Abteilung eines Mitarbeiters
     *
     * @param connection    DB Verbindung
     * @param mitarbeiterId ID
     * @param abteilung     Abteilung
     * @return boolean
     */
    public boolean bearbeiten(
            Connection connection, int mitarbeiterId, Abteilung abteilung);

    /**
     * Ändert den Namen eines Mitarbeiters
     *
     * @param connection    DB Verbindung
     * @param mitarbeiterId ID
     * @param name          Name
     * @return boolean
     */
    public boolean bearbeiten(
            Connection connection, int mitarbeiterId, String name);

    /**
     * Löscht einen Mitarbeiter, falls keine Lagerbewegung vorhanden ist
     *
     * @param connection    DB Verbindung
     * @param mitarbeiterId ID
     * @return boolean
     */
    public boolean loeschen(
            Connection connection, int mitarbeiterId);
}
