package materialverwaltung.service;

import materialverwaltung.dao.ArtikelDAO;
import materialverwaltung.dao.LagerbewegungDAO;
import materialverwaltung.dao.MitarbeiterDAO;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.exception.FachlicheException;
import materialverwaltung.exception.FachlicherFehler;
import materialverwaltung.model.ArtikelBO;
import materialverwaltung.model.Bewegungsart;
import materialverwaltung.model.Buchungsergebnis;
import materialverwaltung.model.LagerbewegungBO;

import java.sql.Connection;
import java.util.Objects;

/**
 * Materialbuchungen nach T01 (Abschnitt 3 und 7).
 *
 * <p>Eine Buchung läuft vollständig in einer Transaktion auf einer Verbindung:</p>
 * <ol>
 *     <li>IDs und Menge prüfen (vor dem Öffnen der Verbindung).</li>
 *     <li>Verbindung öffnen und Auto-Commit ausschalten.</li>
 *     <li>Artikel mit {@code findeNachIdMitSperre} lesen und sperren.</li>
 *     <li>Existenz des Mitarbeiters auf derselben Verbindung prüfen.</li>
 *     <li>Bestand unter der Artikelsperre prüfen und neuen Bestand berechnen.</li>
 *     <li>Artikelbestand ändern.</li>
 *     <li>Genau eine Lagerbewegung speichern.</li>
 *     <li>Commit ausführen, Verbindung schließen, Buchungsergebnis zurückgeben.</li>
 * </ol>
 *
 * <p>Jeder Fehler nach Transaktionsbeginn führt zu einem vollständigen Rollback –
 * es entsteht nie eine Teilbuchung. Fachliche Ablehnungen sind {@link FachlicheException}s,
 * sonstige Datenbankfehler bleiben {@link DatenzugriffsException}s.</p>
 */
public class MaterialService {

    private final ArtikelDAO artikelDAO;
    private final MitarbeiterDAO mitarbeiterDAO;
    private final LagerbewegungDAO lagerbewegungDAO;
    private final Datenbankzugriff datenbank;

    public MaterialService(ArtikelDAO artikelDAO, MitarbeiterDAO mitarbeiterDAO,
                           LagerbewegungDAO lagerbewegungDAO, ConnectionFactory connectionFactory) {
        this.artikelDAO = Objects.requireNonNull(artikelDAO, "artikelDAO");
        this.mitarbeiterDAO = Objects.requireNonNull(mitarbeiterDAO, "mitarbeiterDAO");
        this.lagerbewegungDAO = Objects.requireNonNull(lagerbewegungDAO, "lagerbewegungDAO");
        this.datenbank = new Datenbankzugriff(connectionFactory);
    }

    /**
     * Bucht einen Materialeingang: erhöht den Bestand und speichert genau eine
     * Eingangsbewegung. Der Mitarbeiter ist der Erfasser.
     *
     * @param artikelId  positiver Artikel
     * @param erfasserId positiver Mitarbeiter, der den Eingang erfasst
     * @param menge      positive Menge
     * @return die gespeicherte Bewegung und der Bestand unmittelbar nach der Buchung
     * @throws FachlicheException UNGUELTIGE_EINGABE, ARTIKEL_NICHT_GEFUNDEN,
     *                            MITARBEITER_NICHT_GEFUNDEN oder BESTANDSUEBERLAUF
     */
    public Buchungsergebnis einlagern(int artikelId, int erfasserId, int menge) {
        int artikel = Eingabepruefung.id(artikelId, "Artikel-ID");
        int erfasser = Eingabepruefung.id(erfasserId, "Mitarbeiter-ID");
        int geprueftMenge = Eingabepruefung.menge(menge);

        return datenbank.inTransaktion(con -> {
            ArtikelBO gesperrt = sperreArtikel(con, artikel);
            pruefeMitarbeiter(con, erfasser);

            int bisher = gesperrt.getBestand();
            if (geprueftMenge > Integer.MAX_VALUE - bisher) {
                throw new FachlicheException(FachlicherFehler.BESTANDSUEBERLAUF,
                        "Der Eingang von " + geprueftMenge + " Stück würde den maximal möglichen Bestand von "
                                + Integer.MAX_VALUE + " überschreiten (aktueller Bestand: " + bisher + ").");
            }
            int neuerBestand = bisher + geprueftMenge;

            return speichereBuchung(con, artikel, erfasser, Bewegungsart.EINGANG, geprueftMenge, neuerBestand);
        });
    }

    /**
     * Liest den Artikel mit {@code SELECT ... FOR UPDATE}. Die Sperre gilt bis zum
     * Ende der Transaktion und schützt die Bestandsprüfung vor parallelen Buchungen.
     */
    private ArtikelBO sperreArtikel(Connection con, int artikelId) {
        return artikelDAO.findeNachIdMitSperre(con, artikelId)
                .orElseThrow(() -> new FachlicheException(FachlicherFehler.ARTIKEL_NICHT_GEFUNDEN,
                        "Artikel mit der ID " + artikelId + " wurde nicht gefunden."));
    }

    private void pruefeMitarbeiter(Connection con, int mitarbeiterId) {
        if (mitarbeiterDAO.findeNachId(con, mitarbeiterId).isEmpty()) {
            throw new FachlicheException(FachlicherFehler.MITARBEITER_NICHT_GEFUNDEN,
                    "Mitarbeiter mit der ID " + mitarbeiterId + " wurde nicht gefunden.");
        }
    }

    /**
     * Ändert den Bestand und speichert genau eine Lagerbewegung – beides auf
     * derselben Verbindung innerhalb der laufenden Transaktion.
     */
    private Buchungsergebnis speichereBuchung(Connection con, int artikelId, int mitarbeiterId,
                                              Bewegungsart art, int menge, int neuerBestand) {
        if (!artikelDAO.aendereBestand(con, artikelId, neuerBestand)) {
            // Kann unter der Sperre nicht passieren; sicherheitshalber keine Teilbuchung zulassen
            throw new FachlicheException(FachlicherFehler.ARTIKEL_NICHT_GEFUNDEN,
                    "Artikel mit der ID " + artikelId + " wurde nicht gefunden.");
        }
        LagerbewegungBO bewegung = lagerbewegungDAO.anlegen(con, artikelId, mitarbeiterId, art, menge);
        return new Buchungsergebnis(bewegung, neuerBestand);
    }
}
