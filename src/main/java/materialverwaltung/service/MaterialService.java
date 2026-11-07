package materialverwaltung.service;

import materialverwaltung.dao.ArtikelDAO;
import materialverwaltung.dao.LagerbewegungDAO;
import materialverwaltung.dao.MitarbeiterDAO;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.exception.DatenzugriffsException;
import materialverwaltung.exception.FachlicheException;
import materialverwaltung.exception.FachlicherFehler;
import materialverwaltung.exception.UnzureichenderBestandException;
import materialverwaltung.model.ArtikelBO;
import materialverwaltung.model.Bewegungsart;
import materialverwaltung.model.Buchungsergebnis;
import materialverwaltung.model.Historieneintrag;
import materialverwaltung.model.LagerbewegungBO;
import materialverwaltung.model.MitarbeiterBO;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Materialbuchungen und Bewegungshistorie nach T01 (Abschnitt 3, 6 und 7).
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
 * <p>Bei einer Ausgabe wird der Bestand in Schritt 5 unter der Artikelsperre geprüft;
 * eine Überausgabe löst eine {@link UnzureichenderBestandException} aus.</p>
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
     * Bucht eine Materialausgabe: vermindert den Bestand und speichert genau eine
     * Ausgangsbewegung. Der Mitarbeiter ist der Empfänger.
     *
     * <p>Der verfügbare Bestand wird unter derselben Artikelsperre geprüft, die auch die
     * Änderung schützt. Eine Ausgabe genau in Bestandshöhe ist erlaubt.</p>
     *
     * @param artikelId    positiver Artikel
     * @param empfaengerId positiver Mitarbeiter, der das Material erhält
     * @param menge        positive Menge
     * @return die gespeicherte Bewegung und der Bestand unmittelbar nach der Buchung
     * @throws UnzureichenderBestandException wenn die Menge den Bestand übersteigt
     * @throws FachlicheException             UNGUELTIGE_EINGABE, ARTIKEL_NICHT_GEFUNDEN
     *                                        oder MITARBEITER_NICHT_GEFUNDEN
     */
    public Buchungsergebnis ausgeben(int artikelId, int empfaengerId, int menge) {
        int artikel = Eingabepruefung.id(artikelId, "Artikel-ID");
        int empfaenger = Eingabepruefung.id(empfaengerId, "Mitarbeiter-ID");
        int geprueftMenge = Eingabepruefung.menge(menge);

        return datenbank.inTransaktion(con -> {
            ArtikelBO gesperrt = sperreArtikel(con, artikel);
            pruefeMitarbeiter(con, empfaenger);

            int bisher = gesperrt.getBestand();
            if (geprueftMenge > bisher) {
                throw new UnzureichenderBestandException(artikel, geprueftMenge, bisher);
            }
            int neuerBestand = bisher - geprueftMenge;

            return speichereBuchung(con, artikel, empfaenger, Bewegungsart.AUSGANG, geprueftMenge, neuerBestand);
        });
    }

    /**
     * Liefert die Bewegungshistorie mit den aktuellen Artikel- und Mitarbeiternamen.
     *
     * <p>Sortierung wie vom DAO festgelegt: Zeitpunkt absteigend, bei gleichem Zeitpunkt
     * Bewegungs-ID absteigend. Eine leere Historie ist ein gültiges Ergebnis.</p>
     *
     * <p>Es werden drei Abfragen auf einer Verbindung ausgeführt (keine Abfrage pro Zeile).
     * Die Bewegungen werden zuerst gelesen: Artikel und Mitarbeiter, auf die sie verweisen,
     * können danach wegen der Fremdschlüssel nicht mehr gelöscht werden und sind daher
     * in den folgenden Abfragen immer enthalten.</p>
     */
    public List<Historieneintrag> historie() {
        return datenbank.mitVerbindung(con -> {
            List<LagerbewegungBO> bewegungen = lagerbewegungDAO.findeAlle(con);
            if (bewegungen.isEmpty()) {
                return List.of();
            }
            Map<Integer, String> artikelNamen = artikelDAO.findeAlle(con).stream()
                    .collect(Collectors.toMap(ArtikelBO::getArtikelId, ArtikelBO::getBezeichnung));
            Map<Integer, String> mitarbeiterNamen = mitarbeiterDAO.findeAlle(con).stream()
                    .collect(Collectors.toMap(MitarbeiterBO::getMitarbeiterId, MitarbeiterBO::getName));

            List<Historieneintrag> eintraege = new ArrayList<>(bewegungen.size());
            for (LagerbewegungBO bewegung : bewegungen) {
                eintraege.add(new Historieneintrag(bewegung,
                        name(artikelNamen, bewegung.getArtikelId(), "Artikel"),
                        name(mitarbeiterNamen, bewegung.getMitarbeiterId(), "Mitarbeiter")));
            }
            return List.copyOf(eintraege);
        });
    }

    private static String name(Map<Integer, String> namen, int id, String art) {
        String name = namen.get(id);
        if (name == null) {
            // Durch die Fremdschlüssel ausgeschlossen – würde auf einen Programmfehler hinweisen
            throw new IllegalStateException(art + " " + id + " einer Lagerbewegung fehlt in der Datenbank.");
        }
        return name;
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
