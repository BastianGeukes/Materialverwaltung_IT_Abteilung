package materialverwaltung.dao.jdbc;

import materialverwaltung.dao.MitarbeiterDAO;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.model.Abteilung;
import materialverwaltung.model.MitarbeiterBO;

import java.sql.*;
import java.util.ArrayList;
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
        String anlegenString =
                "INSERT INTO \"Mitarbeiter\" (\"Name\", \"Abteilung\") VALUES (?, ?)";
        try(PreparedStatement mitarbeiterAnlegen = connection.prepareStatement(anlegenString, Statement.RETURN_GENERATED_KEYS)) {
            mitarbeiterAnlegen.setString(1, name);
            mitarbeiterAnlegen.setString(
                    2, abteilung == null ? null : abteilung.name());
            mitarbeiterAnlegen.executeUpdate();
            try (ResultSet resultSet = mitarbeiterAnlegen.getGeneratedKeys()) {
                if(resultSet.next()) {
                    int mitarbeiterID = resultSet.getInt(1);
                    return new MitarbeiterBO(mitarbeiterID, name, abteilung);
                }
                throw new SQLException(
                        "Kein Mitarbeiter ID zurückgegeben");
            }
        } catch (SQLException e) {
            throw new DatenzugriffsException(
                    "Mitarbeiter konnte nicht angelegt werden", e);
        }
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
        String suchString =
                "SELECT \"Name\",\"Abteilung\" FROM \"Mitarbeiter\" WHERE \"MitarbeiterID\" = ?";

        try(PreparedStatement mitarbeiterSuchen = connection.prepareStatement(suchString)){
            mitarbeiterSuchen.setInt(1, mitarbeiterId);
            try (ResultSet resultSet = mitarbeiterSuchen.executeQuery()) {
                if( resultSet.next()) {
                    String name = resultSet.getString("Name");
                    String abteilungText = resultSet.getString("Abteilung");
                    Abteilung abteilung = abteilungText == null
                            ? null
                            : Abteilung.valueOf(abteilungText);
                    return Optional.of(new MitarbeiterBO(mitarbeiterId, name, abteilung));
                } else {
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new DatenzugriffsException(
                    "Mitarbeiter mit ID " + mitarbeiterId
                            + " konnte nicht geladen werden", e);
        }
    }

    /**
     * Findet alle Mitarbeiter
     *
     * @param connection DB Verbindung
     * @return Liste von allen Mitarbeitern
     */
    @Override
    public List<MitarbeiterBO> findeAlle(Connection connection) {
        String suchString =
                "SELECT * FROM \"Mitarbeiter\" ORDER BY \"MitarbeiterID\" ASC";
        try(PreparedStatement allMitarbeiterSuchen = connection.prepareStatement(suchString)){
            try (ResultSet resultSet = allMitarbeiterSuchen.executeQuery()) {
                List<MitarbeiterBO> mitarbeiter = new ArrayList<>();
                while(resultSet.next()){
                    int mitarbeiterID = resultSet.getInt("MitarbeiterID");
                    String name = resultSet.getString("Name");
                    String abteilungText = resultSet.getString("Abteilung");
                    Abteilung abteilung = abteilungText == null
                            ? null
                            : Abteilung.valueOf(abteilungText);
                    mitarbeiter.add(new MitarbeiterBO(mitarbeiterID, name, abteilung));
                }
                return mitarbeiter;
            }
        } catch (SQLException e) {
            throw new DatenzugriffsException(
                    "Mitarbeiter konnten nicht geladen werden", e);
        }
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
        String sucheString =
                "SELECT * FROM \"Mitarbeiter\" "
                        + "WHERE LOWER(\"Name\") LIKE LOWER(?) ESCAPE '!' "
                        + "ORDER BY \"MitarbeiterID\" ASC";
        String suchmuster = suchbegriff
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        try(PreparedStatement sucheMitTeilbegriff = connection.prepareStatement(sucheString)){
            sucheMitTeilbegriff.setString(1, "%" + suchmuster + "%");
            try (ResultSet resultSet = sucheMitTeilbegriff.executeQuery()) {
                List<MitarbeiterBO> mitarbeiter = new ArrayList<>();
                while(resultSet.next()){
                    int mitarbeiterID = resultSet.getInt("MitarbeiterID");
                    String name = resultSet.getString("Name");
                    String abteilungText = resultSet.getString("Abteilung");
                    Abteilung abteilung = abteilungText == null
                            ? null
                            : Abteilung.valueOf(abteilungText);
                    mitarbeiter.add(new MitarbeiterBO(mitarbeiterID, name, abteilung));
                }
                return mitarbeiter;
            }
        } catch (SQLException e) {
            throw new DatenzugriffsException(
                    "Suche nach mitarbeiter Namen mit Teilbegriff fehlgeschlagen", e);
        }
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
        String bearbeitenString =
                "UPDATE \"Mitarbeiter\" SET \"Name\" = ?, \"Abteilung\" = ? WHERE \"MitarbeiterID\" = ?";

        try(PreparedStatement bearbeiteMitarbeiter = connection.prepareStatement(bearbeitenString)){
            bearbeiteMitarbeiter.setString(1, name);
            bearbeiteMitarbeiter.setString(2, abteilung == null ? null : abteilung.name());
            bearbeiteMitarbeiter.setInt(3, mitarbeiterId);
            return bearbeiteMitarbeiter.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatenzugriffsException("Bearbeiten des Mitarbeiters Fehlgeschlagen", e);
        }
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
        String bearbeitenString =
                "UPDATE \"Mitarbeiter\" SET \"Abteilung\" = ? WHERE \"MitarbeiterID\" = ?";

        try(PreparedStatement bearbeiteMitarbeiter = connection.prepareStatement(bearbeitenString)){
            bearbeiteMitarbeiter.setString(1, abteilung == null ? null : abteilung.name());
            bearbeiteMitarbeiter.setInt(2, mitarbeiterId);
            return bearbeiteMitarbeiter.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatenzugriffsException("Bearbeiten des Mitarbeiters Fehlgeschlagen", e);
        }
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
        String bearbeitenString =
                "UPDATE \"Mitarbeiter\" SET \"Name\" = ? WHERE \"MitarbeiterID\" = ?";

        try(PreparedStatement bearbeiteMitarbeiter = connection.prepareStatement(bearbeitenString)){
            bearbeiteMitarbeiter.setString(1, name);
            bearbeiteMitarbeiter.setInt(2, mitarbeiterId);
            return bearbeiteMitarbeiter.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatenzugriffsException("Bearbeiten des Mitarbeiters Fehlgeschlagen", e);
        }
    }

    /**
     * Löscht einen Mitarbeiter, falls keine Lagerbewegung vorhanden ist
     * Nur Verwenden nach Prüfung das für diesen Mitarbeiter keine Lagerbewegung vorhanden ist
     * @param connection    DB Verbindung
     * @param mitarbeiterId ID
     * @return boolean
     */
    @Override
    public boolean loeschen(Connection connection, int mitarbeiterId) {
        String loeschString =
                "DELETE FROM \"Mitarbeiter\" WHERE \"MitarbeiterID\" = ?";

        try(PreparedStatement mitarbeiterLoeschen = connection.prepareStatement(loeschString)){
            mitarbeiterLoeschen.setInt(1, mitarbeiterId);
            return mitarbeiterLoeschen.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatenzugriffsException("Die Datenbank konnte die Löschung nicht durchführen", e);
        }
    }
}
