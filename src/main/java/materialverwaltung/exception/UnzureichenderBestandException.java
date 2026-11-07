package materialverwaltung.exception;

import java.io.Serial;

/**
 * Eine Materialausgabe übersteigt den verfügbaren Bestand (T01, Abschnitt 8; T14).
 *
 * <p>Der Fehlercode ist immer {@link FachlicherFehler#UNZUREICHENDER_BESTAND}.
 * Angeforderte Menge und verfügbarer Bestand stehen für verständliche
 * Rückmeldungen in der Oberfläche zur Verfügung.</p>
 */
public class UnzureichenderBestandException extends FachlicheException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int artikelId;
    private final int angefordert;
    private final int verfuegbar;

    public UnzureichenderBestandException(int artikelId, int angefordert, int verfuegbar) {
        super(FachlicherFehler.UNZUREICHENDER_BESTAND,
                "Unzureichender Bestand: Für Artikel " + artikelId + " wurden " + angefordert
                        + " Stück angefordert, verfügbar sind nur " + verfuegbar + ".");
        this.artikelId = artikelId;
        this.angefordert = angefordert;
        this.verfuegbar = verfuegbar;
    }

    public int getArtikelId() {
        return artikelId;
    }

    public int getAngefordert() {
        return angefordert;
    }

    public int getVerfuegbar() {
        return verfuegbar;
    }
}
