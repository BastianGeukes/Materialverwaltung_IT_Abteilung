package materialverwaltung.model;

import java.time.OffsetDateTime;

public class LagerbewegungBO {
    private final int lagerbewegungId;
    private final int artikelId;
    private final int mitarbeiterId;
    private final Bewegungsart bewegungsart;
    private final int menge;
    private final OffsetDateTime zeitpunkt;

    public LagerbewegungBO(int lagerbewegungId, int artikelId, int mitarbeiterId, Bewegungsart bewegungsart, int menge,  OffsetDateTime zeitpunkt) {
        this.lagerbewegungId = lagerbewegungId;
        this.artikelId = artikelId;
        this.mitarbeiterId = mitarbeiterId;
        this.bewegungsart = bewegungsart;
        this.menge = menge;
        this.zeitpunkt = zeitpunkt;
    }

    public int getLagerbewegungId() {
        return lagerbewegungId;
    }

    public int getMitarbeiterId() {
        return mitarbeiterId;
    }

    public int getArtikelId() {
        return artikelId;
    }

    public Bewegungsart getBewegungsart() {
        return bewegungsart;
    }

    public OffsetDateTime getZeitpunkt() {
        return zeitpunkt;
    }

    public int getMenge() {
        return menge;
    }
}
