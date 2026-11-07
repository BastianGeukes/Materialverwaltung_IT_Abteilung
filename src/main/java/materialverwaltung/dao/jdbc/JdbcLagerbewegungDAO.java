package materialverwaltung.dao.jdbc;

import materialverwaltung.dao.LagerbewegungDAO;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.model.Bewegungsart;
import materialverwaltung.model.LagerbewegungBO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class JdbcLagerbewegungDAO implements LagerbewegungDAO {
    /**
     * Legt eine Lagerbewgung an zur Logistischen Nachverfolgung von Bestandsänderungen
     *
     * @param connection    DB Verbindung
     * @param artikelId     Artikel ID
     * @param mitarbeiterId Mitarbeiter ID
     * @param bewegungsart  Bewegungsart
     * @param menge         Menge
     * @return LagerbewegungBO
     */
    @Override
    public LagerbewegungBO anlegen(Connection connection, int artikelId, int mitarbeiterId, Bewegungsart bewegungsart, int menge) {
        String anlegenString = """
                INSERT INTO "Lagerbewegung" ("ArtikelID", "MitarbeiterID", "Menge", "Bewegungsart")
                VALUES (?, ?, ?, ?)
                RETURNING "BewegungsID", "Bewegungszeitpunkt"
                """;

        try(PreparedStatement lagerbewegungAnlegen = connection.prepareStatement(anlegenString)) {
            lagerbewegungAnlegen.setInt(1, artikelId);
            lagerbewegungAnlegen.setInt(2, mitarbeiterId);
            lagerbewegungAnlegen.setInt(3, menge);
            String bewegungsartText = switch (bewegungsart) {
                case EINGANG -> "Eingang";
                case AUSGANG -> "Ausgang";
            };
            lagerbewegungAnlegen.setString(4, bewegungsartText);

            try(ResultSet rs = lagerbewegungAnlegen.executeQuery()){
                if(rs.next()) {
                    int bewegungsId = rs.getInt("BewegungsID");
                    OffsetDateTime zeitpunkt = rs.getObject("Bewegungszeitpunkt", OffsetDateTime.class);
                    return new LagerbewegungBO(bewegungsId, artikelId, mitarbeiterId, bewegungsart, menge,  zeitpunkt);
                }
                throw new SQLException("Keine Lagerbewegungsdaten zurückgegeben");
            }
        } catch (SQLException e) {
            throw new DatenzugriffsException("Lagerbewegung konnte nicht angelegt werden" , e);
        }
    }

    /**
     * Findet alle Lagerbewegungen
     *
     * @param connection DB Verbindung
     * @return List von LagerbewegungBOs
     */
    @Override
    public List<LagerbewegungBO> findeAlle(Connection connection) {
        String findeAlleString = """
                SELECT *
                FROM "Lagerbewegung"
                ORDER BY "Bewegungszeitpunkt" DESC, "BewegungsID" DESC
                """;

        try(PreparedStatement findeAlleLagerbewegungen = connection.prepareStatement(findeAlleString)) {
            try(ResultSet rs = findeAlleLagerbewegungen.executeQuery()) {
                List<LagerbewegungBO> lagerbewegungen = new ArrayList<>();
                while (rs.next()) {
                    int bewegungsId = rs.getInt("BewegungsID");
                    int artikelId = rs.getInt("ArtikelID");
                    int mitarbeiterId = rs.getInt("MitarbeiterID");
                    int menge = rs.getInt("Menge");
                    Bewegungsart bewegungsart = switch (rs.getString("Bewegungsart")) {
                        case "Eingang" -> Bewegungsart.EINGANG;
                        case "Ausgang" -> Bewegungsart.AUSGANG;
                        default -> throw new SQLException("Unbekannte Bewegungsart");
                    };
                    OffsetDateTime zeitpunkt =  rs.getObject("Bewegungszeitpunkt", OffsetDateTime.class);

                    lagerbewegungen.add(new LagerbewegungBO(bewegungsId, artikelId, mitarbeiterId, bewegungsart, menge,  zeitpunkt));
                }
                return lagerbewegungen;
            }
        } catch (SQLException e) {
            throw new DatenzugriffsException("Lagerbewegungen konnten nicht geladen werden", e);
        }
    }
}
