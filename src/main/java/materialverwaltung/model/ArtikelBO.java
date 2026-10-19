package materialverwaltung.model;

public class ArtikelBO {
    private final int artikelId;
    private final String bezeichnung;
    private final int bestand;

    public ArtikelBO(int artikelId, String bezeichnung, int bestand) {
        this.artikelId = artikelId;
        this.bezeichnung = bezeichnung;
        this.bestand = bestand;
    }

    public int getArtikelId() {
        return artikelId;
    }

    public String getBezeichnung() {
        return bezeichnung;
    }

    public int getBestand() {
        return bestand;
    }
}
