package materialverwaltung.dao.jdbc;

import materialverwaltung.dao.ArtikelDAO;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.model.ArtikelBO;
import materialverwaltung.model.MitarbeiterBO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcArtikelDAO implements ArtikelDAO {
    /**
     * Methode legt einen Artikel in der Datenbank an
     * Liefert das BO dieses zurück
     *
     * @param connection  DB Verbindung
     * @param bezeichnung Bezeichnung des Artikels
     * @return ArtikelBO
     */
    @Override
    public ArtikelBO anlegen(Connection connection, String bezeichnung) {
        String anlegenString = """
                INSERT INTO "Artikel" ("Bezeichnung")
                VALUES (?)
                RETURNING "Bestand","ArtikelID"
                """;

        try(PreparedStatement artikelAnlegen = connection.prepareStatement(anlegenString)){
            artikelAnlegen.setString(1, bezeichnung);

            try (ResultSet rs = artikelAnlegen.executeQuery()){
                if (rs.next()) {
                    int artikelId = rs.getInt("ArtikelID");
                    int bestand = rs.getInt("Bestand");

                    return new ArtikelBO(artikelId, bezeichnung, bestand);
                }
                throw new SQLException("Keine Artikeldaten zurückgegeben");
            }
        } catch (SQLException e){
            throw new DatenzugriffsException("Artikel konnte nicht angelegt werden", e);
        }
    }

    /**
     * Findet einen Artikel in der Datenbank mithilfer der ID <br>
     * liefert einen Optional vom Typen ArtikelBO
     *
     * @param connection DB Verbindung
     * @param artikelId  Artikel ID
     * @return Optional (ArtikelBO)
     */
    @Override
    public Optional<ArtikelBO> findeNachId(Connection connection, int artikelId) {
        String findeString = """
                SELECT "Bezeichnung", "Bestand"
                FROM "Artikel"
                WHERE "ArtikelID" = ?
                """;

        try(PreparedStatement mitarbeiterFinden = connection.prepareStatement(findeString)) {
            mitarbeiterFinden.setInt(1, artikelId);
            try (ResultSet rs = mitarbeiterFinden.executeQuery()) {
                if (rs.next()) {
                    String bezeichnung = rs.getString("Bezeichnung");
                    int bestand = rs.getInt("Bestand");

                    return Optional.of(new ArtikelBO(artikelId, bezeichnung, bestand));
                } else {
                    return Optional.empty();
                }
            }
        } catch (SQLException e){
            throw new DatenzugriffsException(
                    "Artikel mit ID " + artikelId
                    + "konte nicht geladen werden", e);
        }
    }

    /**
     * Gibt alle Artikel zurück die in der Datenbank gespeichert sind
     *
     * @param connection DB Verbindung
     * @return List von ArtikelBOs
     */
    @Override
    public List<ArtikelBO> findeAlle(Connection connection) {
        String findeString = """
                SELECT *
                FROM "Artikel"
                ORDER BY "ArtikelID" ASC
                """;

        try(PreparedStatement alleMitarbeiterFinden = connection.prepareStatement(findeString)) {
            try(ResultSet rs = alleMitarbeiterFinden.executeQuery()) {
                List<ArtikelBO> artikelList = new ArrayList<>();
                while (rs.next()) {
                    int artikelId = rs.getInt("ArtikelID");
                    String bezeichnung = rs.getString("Bezeichnung");
                    int bestand = rs.getInt("Bestand");

                    artikelList.add(new ArtikelBO(artikelId, bezeichnung, bestand));
                }
                return artikelList;
            }
        } catch (SQLException e){
            throw new DatenzugriffsException("Artikel konnten nicht geladen werden", e);
        }
    }

    /**
     * Suche für Artikel mithilfe eines Suchbregriffs
     * Suche verwendet Teilbegriffe ohne Beachtung der Groß-/Kleinschreibung <br>
     * liefert alle Artikel zurück die mithilfe dieser Suche gefunden wurden
     *
     * @param connection  DB Verbindung
     * @param suchbegriff Teilbegriff mit dem gesucht wird
     * @return List von ArtikelBOs
     */
    @Override
    public List<ArtikelBO> sucheNachBezeichnung(Connection connection, String suchbegriff) {
        String suchString =
                "SELECT * FROM \"Artikel\" "
                    + "WHERE LOWER(\"Bezeichnung\") LIKE LOWER(?) ESCAPE '!' "
                    +  "ORDER BY \"ArtikelID\" ASC";
        String suchmuster = suchbegriff
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");

        try (PreparedStatement sucheMitTeilbegriff = connection.prepareStatement(suchString)) {
            sucheMitTeilbegriff.setString(1, "%" + suchmuster + "%");

            try (ResultSet rs = sucheMitTeilbegriff.executeQuery()) {
                List<ArtikelBO> artikelList = new ArrayList<>();
                while (rs.next()) {
                    int artikelId = rs.getInt("ArtikelID");
                    String bezeichnung = rs.getString("Bezeichnung");
                    int bestand = rs.getInt("Bestand");
                    artikelList.add(new ArtikelBO(artikelId, bezeichnung, bestand));
                }
                return artikelList;
            }
        } catch (SQLException e){
            throw new DatenzugriffsException("Suche nach Artikel Namen mit Teilbegriff fehlgeschlagen", e);
        }
    }

    /**
     * Ändert die Bezechnung eines Artikels, wenn er existiert
     *
     * @param connection  DB Verbindung
     * @param artikelId   Artikel ID
     * @param bezeichnung Bezeichnung der die alte Ersetzt
     * @return boolean
     */
    @Override
    public boolean umbenennen(Connection connection, int artikelId, String bezeichnung) {
        String umbenennenString =
                "UPDATE \"Artikel\" "
                    + "SET \"Bezeichnung\" = ? "
                    + "WHERE \"ArtikelID\" = ?";

        try (PreparedStatement artikelUmbenennen = connection.prepareStatement(umbenennenString)) {
            artikelUmbenennen.setString(1, bezeichnung);
            artikelUmbenennen.setInt(2, artikelId);
            return artikelUmbenennen.executeUpdate() == 1;
        } catch (SQLException e){
            throw new DatenzugriffsException("Umbenennen des Mitarbeiters Fehlgeschlagen", e);
        }
    }

    /**
     * Löscht einen Artikel mithilfe der ID
     *
     * @param connection DB Verbindung
     * @param artikelId
     * @return boolean
     */
    @Override
    public boolean loeschen(Connection connection, int artikelId) {
        String loeschenString =
                "DELETE FROM \"Artikel\" WHERE \"ArtikelID\" = ?";

        try (PreparedStatement artikelLoeschen = connection.prepareStatement(loeschenString)) {
            artikelLoeschen.setInt(1, artikelId);
            return artikelLoeschen.executeUpdate() == 1;
        } catch (SQLException e){
            throw new DatenzugriffsException("Die Datenbank konnte die Löschung des Artikels nicht durchführen" , e);
        }
    }

    /**
     * Sucht einen Bestimmten mithilfe einer Id
     *
     * @param connection DB Verbindung
     * @param artikelId  Artikel Id
     * @return Optional (ArtikelBO)
     */
    @Override
    public Optional<ArtikelBO> findeNachIdMitSperre(Connection connection, int artikelId) {
        String findeString = """
        SELECT "Bezeichnung", "Bestand"
        FROM "Artikel"
        WHERE "ArtikelID" = ?
        FOR UPDATE
        """;

        try(PreparedStatement mitarbeiterFinden = connection.prepareStatement(findeString)) {
            mitarbeiterFinden.setInt(1, artikelId);
            try (ResultSet rs = mitarbeiterFinden.executeQuery()) {
                if (rs.next()) {
                    String bezeichnung = rs.getString("Bezeichnung");
                    int bestand = rs.getInt("Bestand");

                    return Optional.of(new ArtikelBO(artikelId, bezeichnung, bestand));
                } else {
                    return Optional.empty();
                }
            }
        } catch (SQLException e){
            throw new DatenzugriffsException(
                    "Artikel mit ID " + artikelId
                            + "konte nicht geladen werden", e);
        }
    }

    /**
     * Ändert einen Bestand von einem Artikel <br>
     * Darf nur innerhalb einer Burchungstransaktion nach dem Sperren dieser Artikelzeile verwendet werden
     *
     * @param connection   DB Verbindung
     * @param artikelId    Artikel Id
     * @param neuerBestand neuer Bestand
     * @return boolean
     */
    @Override
    public boolean aendereBestand(Connection connection, int artikelId, int neuerBestand) {
        String anderungsString =
                "UPDATE \"Artikel\" "
                + "SET \"Bestand\" = ? "
                + "WHERE \"ArtikelID\" = ?";

        try (PreparedStatement bestandAendern = connection.prepareStatement(anderungsString)) {
            bestandAendern.setInt(1, neuerBestand);
            bestandAendern.setInt(2, artikelId);
            return bestandAendern.executeUpdate() == 1;
        } catch (SQLException e){
            throw new DatenzugriffsException("Bestandsänderung Fehlgeschlagen" , e);
        }
    }
}
