INSERT INTO "Artikel" ("Bezeichnung")
                SELECT CASE
                           WHEN nr = 42 THEN 'T23-Zielartikel'
                           ELSE 'Messartikel-' || nr
                       END
                FROM generate_series(?, ?) AS daten(nr);