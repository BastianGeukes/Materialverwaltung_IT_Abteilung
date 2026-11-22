package materialverwaltung.dao.jdbc;

final class SqlSuchmuster {
    private SqlSuchmuster() {}
    public static String teilbegriff(String suchbegriff) {
        return "%"  + suchbegriff
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_")
                + "%";

    }
}
