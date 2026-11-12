package materialverwaltung.speedAnalysis;

import materialverwaltung.dao.jdbc.JdbcArtikelDAO;
import materialverwaltung.db.ConnectionConfig;
import materialverwaltung.db.ConnectionFactory;
import materialverwaltung.db.DatabaseMigration;
import materialverwaltung.service.ArtikelService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
public class ArtikelsucheMessung {
    @Container
    static final PostgreSQLContainer POSTGES =
            new PostgreSQLContainer("postgres:17-alpine");

    static ConnectionFactory connectionFactory;


    @BeforeAll
    static void datenbankVorbereiten() {
        ConnectionConfig config = new ConnectionConfig(
                POSTGES.getJdbcUrl(),
                POSTGES.getUsername(),
                POSTGES.getPassword()
        );

        DatabaseMigration.migrate(config);
        connectionFactory = new ConnectionFactory(config);
    }

    //Speed Tests

    @Test
    void artikelsucheMessung() throws SQLException, IOException {
        long[] hundert;
        long[] zehntausend;
        long[] zehntausendMitIndex;

        try (Connection con = connectionFactory.openConnection()) {
            ArtikelService service = new ArtikelService(
                    new JdbcArtikelDAO(), connectionFactory
            );

            artikelHinzufuegen(con, 1, 100);
            hundert = messeSuche(service);

            artikelHinzufuegen(con, 101, 10_000);

            try (var statement = con.createStatement()) {
                // Vorher: PostgreSQL kennt die aktuelle Größe der Tabelle.
                statement.execute("ANALYZE \"Artikel\"");
                planSpeichern(con, "plan-ohne-index.txt");
                zehntausend = messeSuche(service);

                // Zusatzindex nur in der Messdatenbank anlegen.
                // Hypothese: Trigramm-Index könnte die Teilstring suche nach "%zielartikel%" beschleunigen,
                // weil PostgreSQL anhand von Dreiergruppen mögliche Treffer eingrenzen kann,
                // statt alle 10.000 Artikelbezeichnungen einzeln zu prüfen.
                statement.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
                statement.execute("""
                        CREATE INDEX idx_artikel_bezeichnung_trgm
                        ON "Artikel" USING gin (LOWER("Bezeichnung") gin_trgm_ops)
                        """);

                // Nachher: gleicher Bestand, gleicher Suchbegriff, gleiche Messmethode.
                statement.execute("ANALYZE \"Artikel\"");
                planSpeichern(con, "plan-mit-index.txt");
                zehntausendMitIndex = messeSuche(service);
            }
        }

        Path datei = Path.of("target", "t23", "artikelsuche.csv");
        Files.createDirectories(datei.getParent());

        StringBuilder csv = new StringBuilder("artikelanzahl,durchlauf,dauer_ns\n");
        csvZeilenHinzufuegen(csv, 100, hundert);
        csvZeilenHinzufuegen(csv, 10_000, zehntausend);
        Files.writeString(datei, csv);

        System.out.printf("Median bei 100 Artikeln:    %.3f ms%n", medianMs(hundert));
        System.out.printf("Median bei 10.000 Artikeln: %.3f ms%n", medianMs(zehntausend));
        System.out.printf("10.000 Artikel mit Zusatzindex:  %.3f ms%n", medianMs(zehntausendMitIndex));
        System.out.println("Messwerte: " + datei.toAbsolutePath());
    }


    //Hilfsmethode

    private static void artikelHinzufuegen(Connection con, int von, int bis) throws SQLException, IOException {
        String pfad = "/db/messung/artikel_hinzufuegen.sql";
        String sql;

        try (InputStream datei = ArtikelsucheMessung.class.getResourceAsStream(pfad)) {
            if (datei == null) {
                throw new IOException("SQL-Datei nicht gefunden: " + pfad);
            }
            sql = new String(datei.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (PreparedStatement statement = con.prepareStatement(sql)) {
            statement.setInt(1, von);
            statement.setInt(2, bis);
            int eingefuegt = statement.executeUpdate();
            assertEquals(bis - von + 1, eingefuegt);
        }
    }

    private static long[] messeSuche(ArtikelService service) {
        String suchbegriff = "zielartikel";

        for (int i = 0; i < 10; i++) {
            service.suchen(suchbegriff);
        }

        long[] dauerNs = new long[50];

        for (int i = 0; i < dauerNs.length; i++) {
            long start = System.nanoTime();
            var treffer = service.suchen(suchbegriff);
            dauerNs[i] = System.nanoTime() - start;

            assertEquals(1, treffer.size());
            assertEquals("T23-Zielartikel", treffer.getFirst().getBezeichnung());
        }

        return dauerNs;
    }

    private static void csvZeilenHinzufuegen(
            StringBuilder csv, int artikelanzahl, long[] dauerNs
    ) {
        for (int i = 0; i < dauerNs.length; i++) {
            csv.append(artikelanzahl).append(',')
                    .append(i + 1).append(',')
                    .append(dauerNs[i]).append('\n');
        }
    }

    // Bei genau 50 Werten liegt der Median zwischen dem 25. und 26. Wert.
    private static double medianMs(long[] dauerNs) {
        long[] sortiert = dauerNs.clone();
        Arrays.sort(sortiert);

        double medianNs = (sortiert[24] + sortiert[25]) / 2.0;
        return medianNs / 1_000_000.0;
    }

    private static void planSpeichern(Connection con, String dateiname)
            throws SQLException, IOException {

        String sql = """
                EXPLAIN (ANALYZE, BUFFERS)
                SELECT * FROM "Artikel"
                WHERE LOWER("Bezeichnung") LIKE LOWER(?) ESCAPE '!'
                ORDER BY "ArtikelID" ASC
                """;

        StringBuilder plan = new StringBuilder();
        try (PreparedStatement statement = con.prepareStatement(sql)) {
            statement.setString(1, "%zielartikel%");

            try (var rs = statement.executeQuery()) {
                while (rs.next()) {
                    plan.append(rs.getString(1)).append('\n');
                }
            }
        }

        Path ordner = Files.createDirectories(Path.of("target", "t23"));
        Files.writeString(ordner.resolve(dateiname), plan);
    }
}
