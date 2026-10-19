package materialverwaltung.dao;

import materialverwaltung.model.Bewegungsart;
import materialverwaltung.model.LagerbewegungBO;

import java.sql.Connection;
import java.util.List;

public interface LagerbewegungDAO {

    /**
     * Legt eine Lagerbewgung an zur Logistischen Nachverfolgung von Bestandsänderungen
     * @param connection DB Verbindung
     * @param artikelId Artikel ID
     * @param mitarbeiterId Mitarbeiter ID
     * @param bewegungsart Bewegungsart
     * @param menge Menge
     * @return LagerbewegungBO
     */
    public LagerbewegungBO anlegen(
            Connection connection, int artikelId, int mitarbeiterId, Bewegungsart bewegungsart, int menge);

    /**
     * Findet alle Lagerbewegungen
     * @param connection DB Verbindung
     * @return List von LagerbewegungBOs
     */
    public List<LagerbewegungBO> findeAlle(
            Connection connection);
}
