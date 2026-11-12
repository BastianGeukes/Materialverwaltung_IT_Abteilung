# T23 – Laufzeit der Artikelsuche bei 100 und 10.000 Artikeln

## Fragestellung

Wie verändert sich die Laufzeit von `ArtikelService.suchen("zielartikel")`, wenn der Datenbestand von 100 auf 10.000 Artikel wächst und die Suche weiterhin genau einen Treffer liefert?

## Messumgebung

| Bestandteil | Verwendete Umgebung                                                           |
|---|-------------------------------------------------------------------------------|
| Messrechner | Windows 11, Prozessor AMD RYZEN AI 7, Arbeitsspeicher 32GB                    |
| Java | JDK 25                                                                        |
| Datenbank | PostgreSQL 17, Container-Image `postgres:17-alpine`                           |
| Messaufbau | JUnit und Testcontainers mit dem echten `ArtikelService` und `JdbcArtikelDAO` |

Die Messung verwendet einen eigenen PostgreSQL-Container. Flyway legt darin dasselbe Schema wie für die Anwendung an. Die lokale Anwendungsdatenbank wird für die Messung nicht verwendet.

## Daten und Vorgehen

Das SQL-Skript `artikel_hinzufuegen.sql` erzeugt zunächst 100 Artikel. Der Artikel mit der laufenden Nummer 42 erhält die Bezeichnung `T23-Zielartikel`. Alle anderen Artikel heißen `Messartikel-<Nummer>`. Für die zweite Messreihe ergänzt das Skript die Nummern 101 bis 10.000. So bleibt der Zielartikel in beiden Beständen derselbe.

Für jeden Datenbestand führt die Klasse `ArtikelsucheMessung` denselben Ablauf aus:

1. Den Service zehnmal zum Aufwärmen aufrufen. Diese Aufrufe gehen nicht in die Auswertung ein.
2. Die Suche 50-mal ausführen. Die Zeitmessung mit `System.nanoTime()` beginnt unmittelbar vor `service.suchen("zielartikel")` und endet, sobald die Ergebnisliste vorliegt.
3. Nach jedem gemessenen Aufruf prüfen, dass die Liste genau einen Treffer enthält und dessen Bezeichnung `T23-Zielartikel` ist.

Gemessen wird die **gesamte Service-Laufzeit** einschließlich Verbindungsaufbau, SQL-Abfrage und Verarbeitung des Ergebnisses. Der Aufbau der Testdaten, die Trefferprüfung und das Schreiben der CSV zählen nicht zur Messzeit.

## Ergebnisse

| Anzahl Artikel | Treffer pro Suche | Gemessene Aufrufe | Median der Service-Laufzeit |
|---:|---:|---:|---:|
| 100 | 1 | 50 | 25,58 ms |
| 10.000 | 1 | 50 | 29,49 ms |

Der Median wurde für jede Messreihe separat berechnet. Bei 50 sortierten Einzelzeiten ist er der Mittelwert aus dem 25. und 26. Wert.

**Auswertung:** Beim Bestand von 10.000 Artikeln lag der Median in diesem Lauf um **3,91 ms** höher als bei 100 Artikeln. Beide Suchen lieferten denselben erwarteten Treffer. Da die Messung den gesamten Service-Aufruf umfasst, lässt sich aus der Differenz nicht ableiten, wie viel zusätzliche Zeit allein die SQL-Abfrage benötigt hat.

## Messung wiederholen

Voraussetzungen sind ein installiertes JDK 25 und eine laufende Docker-Umgebung. Im Projektverzeichnis unter PowerShell ausführen:

```powershell
.\mvnw.cmd "-Dit.test=ArtikelsucheMessung" verify
```

Die Konsole zeigt die beiden Mediane an. Die 100 Einzelmessungen stehen anschließend in `target/t23/artikelsuche.csv` mit den Spalten `artikelanzahl`, `durchlauf` und `dauer_ns`. Ein neuer Lauf überschreibt diese Datei.

Die Messklasse wird im normalen CI-Build nicht automatisch ausgeführt. Es gibt keine Laufzeitgrenze, die zum Bestehen unterschritten werden muss.