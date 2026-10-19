package materialverwaltung.dao;

import materialverwaltung.model.ArtikelBO;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface ArtikelDAO {
    /**
     * Methode legt einen Artikel in der Datenbank an
     * Liefert das BO dieses zurück
     *
     * @param connection  DB Verbindung
     * @param bezeichnung Bezeichnung des Artikels
     * @return ArtikelBO
     */
    public ArtikelBO anlegen(
            Connection connection, String bezeichnung);

    /**
     * Findet einen Artikel in der Datenbank mithilfer der ID <br>
     * liefert einen Optional vom Typen ArtikelBO
     *
     * @param connection DB Verbindung
     * @param artikelId  Artikel ID
     * @return Optional (ArtikelBO)
     */
    public Optional<ArtikelBO> findeNachId(
            Connection connection, int artikelId);

    /**
     * Gibt alle Artikel zurück die in der Datenbank gespeichert sind
     *
     * @param connection DB Verbindung
     * @return List von ArtikelBOs
     */
    public List<ArtikelBO> findeAlle(
            Connection connection);

    /**
     * Suche für Artikel mithilfe eines Suchbregriffs
     * Suche verwendet Teilbegriffe ohne Beachtung der Groß-/Kleinschreibung <br>
     * liefert alle Artikel zurück die mithilfe dieser Suche gefunden wurden
     *
     * @param connection  DB Verbindung
     * @param suchbegriff Teilbegriff mit dem gesucht wird
     * @return List von ArtikelBOs
     */
    public List<ArtikelBO> sucheNachBezeichnung(
            Connection connection, String suchbegriff);

    /**
     * Ändert die Bezechnung eines Artikels, wenn er existiert
     *
     * @param connection  DB Verbindung
     * @param artikelId   Artikel ID
     * @param bezeichnung Bezeichnung der die alte Ersetzt
     * @return boolean
     */
    public boolean umbenennen(
            Connection connection, int artikelId, String bezeichnung);

    /**
     * Löscht einen Artikel mithilfe der ID
     *
     * @param connection DB Verbindung
     * @param artikelId
     * @return boolean
     */
    public boolean loeschen(
            Connection connection, int artikelId);

    /**
     * Sucht einen Bestimmten mithilfe einer Id
     *
     * @param connection DB Verbindung
     * @param artikelId  Artikel Id
     * @return Optional (ArtikelBO)
     */
    public Optional<ArtikelBO> findeNachIdMitSperre(
            Connection connection, int artikelId);

    /**
     * Ändert einen Bestand von einem Artikel <br>
     * Darf nur innerhalb einer Burchungstransaktion nach dem Sperren dieser Artikelzeile verwendet werden
     *
     * @param connection   DB Verbindung
     * @param artikelId    Artikel Id
     * @param neuerBestand neuer Bestand
     * @return boolean
     */
    public boolean aendereBestand(
            Connection connection, int artikelId, int neuerBestand);

}
