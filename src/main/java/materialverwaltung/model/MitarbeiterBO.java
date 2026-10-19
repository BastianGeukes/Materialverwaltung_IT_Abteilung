package materialverwaltung.model;

public class MitarbeiterBO {
    private final int mitarbeiterId;
    private final String name;
    private final Abteilung abteilung;

    public MitarbeiterBO(int mitarbeiterId, String name, Abteilung abteilung) {
        this.mitarbeiterId = mitarbeiterId;
        this.name = name;
        this.abteilung = abteilung;
    }

    public int getMitarbeiterId() {
        return mitarbeiterId;
    }

    public String getName() {
        return name;
    }

    public Abteilung getAbteilung() {
        return abteilung;
    }
}
