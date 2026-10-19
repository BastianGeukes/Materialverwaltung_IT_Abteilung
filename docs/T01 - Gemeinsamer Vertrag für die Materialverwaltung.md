**T01 – Gemeinsamer Vertrag für die Materialverwaltung**

**1. Umfang und Zuständigkeiten**

Die Anwendung unterstützt:

- Artikel und Mitarbeiter anlegen, anzeigen, suchen, bearbeiten und löschen.
- Material einlagern und an Mitarbeiter ausgeben.
- Bestände und die Bewegungshistorie anzeigen.

Ausleihe, Rückgabe sowie das Bearbeiten oder Löschen vorhandener Lagerbewegungen gehören nicht zum Umfang.

| Bereich                                                     | Zuständig |
| ----------------------------------------------------------- | --------- |
| Datenbankschema, Migrationen und Verbindungen               | Bela      |
| BOs, DAO-Schnittstellen und SQL-Abfragen innerhalb der DAOs | Bastian   |
| Fachregeln, Services, Transaktionen und Konsole             | Jeronimo  |
| Vertrag, Tests des eigenen Bereichs und Reviews             | Gemeinsam |

Java 25 und PostgreSQL 17 bilden die technische Grundlage. Anwendungs- und Testdatenbank bleiben getrennt. Lokal ist Docker optional; GitHub Actions verwendet einen frischen PostgreSQL-Container. DAO- und Service-Tests arbeiten mit echten JDBC-Implementierungen und PostgreSQL, ohne Mocks.

**2. Datenfelder**

Die folgenden Namen bilden die gemeinsame Vorgabe für BOs und SQL. SQL-Bezeichner mit Großbuchstaben werden in Abfragen in doppelte Anführungszeichen gesetzt.

| BO              | Java-Feld       | Java-Typ       | SQL-Spalte      | Bedeutung                                     |
| --------------- | --------------- |----------------| --------------- | --------------------------------------------- |
| ArtikelBO       | artikelId       | int            | ArtikelID       | Automatisch erzeugter Primärschlüssel         |
|                 | bezeichnung     | String         | Bezeichnung     | Pflichtfeld, eindeutig, höchstens 100 Zeichen |
|                 | bestand         | int            | Bestand         | Gespeicherter Bestand, anfangs 0              |
| MitarbeiterBO   | mitarbeiterId   | int            | MitarbeiterID   | Automatisch erzeugter Primärschlüssel         |
|                 | name            | String         | Name            | Pflichtfeld, höchstens 100 Zeichen            |
|                 | abteilung       | Abteilung      | Abteilung       | Optional, höchstens 100 Zeichen               |
| LagerbewegungBO | lagerbewegungId | int            | LagerbewegungID | Automatisch erzeugter Primärschlüssel         |
|                 | artikelId       | int            | ArtikelID       | Fremdschlüssel auf Artikel                    |
|                 | mitarbeiterId   | int            | MitarbeiterID   | Fremdschlüssel auf Mitarbeiter                |
|                 | bewegungsart    | Bewegungsart   | Bewegungsart    | EINGANG oder AUSGANG                          |
|                 | menge           | int            | Menge           | Positive ganze Zahl                           |
|                 | zeitpunkt       | OffsetDateTime | Zeitpunkt       | Zeitpunkt der Buchung                         |

Die SQL-Typen sind `INTEGER`, `VARCHAR(100)` für die Stammdatentexte, `VARCHAR(7)` für die Bewegungsart und `TIMESTAMP WITH TIME ZONE` für den Zeitpunkt.

Die Datenbank erzeugt IDs und Zeitpunkte. Die DAOs geben die tatsächlich gespeicherten Werte zurück. `TIMESTAMP WITH TIME ZONE` bewahrt den Zeitpunkt, jedoch keine ursprüngliche geografische Zonenbezeichnung. Zeitvergleiche erfolgen daher über den tatsächlichen Zeitpunkt. [PostgreSQL-Dokumentation](https://www.postgresql.org/docs/17/datatype-datetime.html)

Ein Artikel und ein Mitarbeiter können jeweils in beliebig vielen Bewegungen vorkommen. Deshalb sind die Fremdschlüssel in `Lagerbewegung` weder automatisch erzeugt noch eindeutig. Tabellenvererbung ist nicht vorgesehen.

BOs transportieren Daten und enthalten keine Datenbankzugriffe. Vorgeschlagen sind unveränderliche Objekte mit Konstruktor und Gettern. Zum Anlegen werden Werte übergeben; dafür benötigt die Anwendung keine ungespeicherten BOs mit künstlichen IDs.

**3. Fachregeln**

| Bereich            | Vereinbarung                                                                                                       |
| ------------------ | ------------------------------------------------------------------------------------------------------------------ |
| Pflichttexte       | Bezeichnung und Name dürfen weder `null` noch leer oder ausschließlich Leerraum sein.                              |
| Normalisierung     | Äußeren Leerraum vor der Prüfung entfernen, beispielsweise mit `String.strip()`. Innerer Leerraum bleibt erhalten. |
| Textlänge          | Höchstens 100 Zeichen nach der Normalisierung. Einheitlich Unicode-Codepoints zählen.                              |
| Abteilung          | Fehlend oder nach Normalisierung leer bedeutet `null`.                                                             |
| Artikelbezeichnung | Eindeutig unter Beachtung der Groß-/Kleinschreibung: `Maus` und `maus` sind verschieden.                           |
| Mitarbeitername    | Gleiche Namen sind erlaubt; Mitarbeiter werden durch ihre ID unterschieden.                                        |
| IDs                | Positive `int`-Werte. Nichtpositive IDs sind ungültige Eingaben; unbekannte positive IDs sind fehlende Datensätze. |
| Neuer Artikel      | Bestand immer 0.                                                                                                   |
| Artikelbearbeitung | Nur die Bezeichnung verändern. Die eigene unveränderte Bezeichnung darf gespeichert werden.                        |
| Bestandsänderung   | Ausschließlich durch eine Materialbuchung.                                                                         |
| Menge              | Positive ganze Zahl, bei Eingang und Ausgang. Das Vorzeichen bestimmt nicht die Bewegungsrichtung.                 |
| Eingang            | Bestand erhöhen. Mitarbeiter bezeichnet den Erfasser.                                                              |
| Ausgang            | Bestand vermindern. Mitarbeiter bezeichnet den Empfänger.                                                          |
| Überausgabe        | Ablehnen. Ausgabe genau in Bestandshöhe ist erlaubt.                                                               |
| Bestandsüberlauf   | Eingang ablehnen, wenn der neue Bestand `Integer.MAX_VALUE` überschreiten würde.                                   |
| Löschen            | Artikel und Mitarbeiter nur löschen, wenn keine Bewegung auf sie verweist. Das gilt auch bei Bestand 0.            |
| Historie           | Bewegungen bleiben unverändert erhalten. Angezeigt werden die aktuellen Artikel- und Mitarbeiternamen.             |

Die Datenbank sichert Pflichtfelder, Eindeutigkeit, Fremdschlüssel, positive Mengen, nichtnegative Bestände und gültige Bewegungsarten zusätzlich ab.

Für jeden Artikel gilt bei korrekten Buchungen:

`Bestand = Summe aller Eingänge − Summe aller Ausgänge`

Diese Beziehung wird durch die gemeinsame Buchungstransaktion eingehalten. Ein einfacher SQL-`CHECK` auf dem Bestand prüft nicht automatisch die gesamte Bewegungshistorie.

**4. Suche und Rückgaben**

Die Suche verwendet Teilbegriffe ohne Beachtung der Groß-/Kleinschreibung:

- Artikelsuche durchsucht die Bezeichnung.
- Mitarbeitersuche durchsucht den Namen.
- Suchtexte werden normalisiert und auf 1–100 Zeichen geprüft.
- Für alle Datensätze gibt es eine eigene Auflisten-Operation.
- `%`, `_` und `\` werden als eingegebene Zeichen behandelt, nicht als frei verwendbare SQL-Wildcards.
- SQL-Parameter werden über `PreparedStatement` gebunden.

Artikel- und Mitarbeiterlisten werden nach ID aufsteigend sortiert. Die Historie wird nach Zeitpunkt absteigend und bei gleichem Zeitpunkt nach Bewegungs-ID absteigend sortiert.

| Situation                            | DAO                    | Service                                       |
| ------------------------------------ | ---------------------- | --------------------------------------------- |
| Einzelner Datensatz vorhanden        | `Optional` mit BO      | BO                                            |
| Einzelner Datensatz fehlt            | `Optional.empty()`     | FachlicheException                            |
| Suche ohne Treffer                   | Leere Liste            | Leere Liste                                   |
| Änderung/Löschung trifft keine Zeile | `false`                | FachlicheException                            |
| Änderung/Löschung erfolgreich        | `true`                 | Aktualisiertes BO bzw. normale Rückkehr       |
| SQL-Fehler                           | DatenzugriffsException | Gezielt übersetzen oder technisch weitergeben |

Listen und BO-Rückgaben sind niemals `null`. Nur die optionale Abteilung darf `null` sein. Ein SQL-Fehler darf nicht als leere Liste oder `false` verschluckt werden.

**5. DAO-Schnittstellen**

Jede DAO-Methode erhält als **ersten Parameter eine offene&#x20;****`Connection`**. In den Tabellen ist dieser gemeinsame Parameter zur besseren Lesbarkeit weggelassen.

**ArtikelDAO**

| Methode mit weiteren Parametern                   | Rückgabe              |
| ------------------------------------------------- | --------------------- |
| `anlegen(String bezeichnung)`                     | `ArtikelBO`           |
| `findeNachId(int artikelId)`                      | `Optional<ArtikelBO>` |
| `findeAlle()`                                     | `List<ArtikelBO>`     |
| `sucheNachBezeichnung(String suchbegriff)`        | `List<ArtikelBO>`     |
| `umbenennen(int artikelId, String bezeichnung)`   | `boolean`             |
| `loeschen(int artikelId)`                         | `boolean`             |
| `findeNachIdMitSperre(int artikelId)`             | `Optional<ArtikelBO>` |
| `aendereBestand(int artikelId, int neuerBestand)` | `boolean`             |

`aendereBestand` setzt einen bereits geprüften **absoluten neuen Bestand**. Die Methode darf ausschließlich innerhalb einer Buchungstransaktion nach dem Sperren derselben Artikelzeile verwendet werden.

`findeNachIdMitSperre` verwendet `SELECT ... FOR UPDATE`; dafür muss Auto-Commit ausgeschaltet sein.

**MitarbeiterDAO**

| Methode mit weiteren Parametern                                   | Rückgabe                  |
|-------------------------------------------------------------------| ------------------------- |
| `anlegen(String name, Abteilung abteilung)`                        | `MitarbeiterBO`           |
| `findeNachId(int mitarbeiterId)`                                  | `Optional<MitarbeiterBO>` |
| `findeAlle()`                                                     | `List<MitarbeiterBO>`     |
| `sucheNachName(String suchbegriff)`                               | `List<MitarbeiterBO>`     |
| `bearbeiten(int mitarbeiterId, String name, Abteilung abteilung)` | `boolean`                 |
| `loeschen(int mitarbeiterId)`                                     | `boolean`                 |

**LagerbewegungDAO**

| Methode mit weiteren Parametern                                                   | Rückgabe                |
| --------------------------------------------------------------------------------- | ----------------------- |
| `anlegen(int artikelId, int mitarbeiterId, Bewegungsart bewegungsart, int menge)` | `LagerbewegungBO`       |
| `findeAlle()`                                                                     | `List<LagerbewegungBO>` |

Der Bewegungs-DAO verändert keinen Artikelbestand. Anlageoperationen liefern den gespeicherten Datensatz einschließlich generierter ID und gegebenenfalls Zeitpunkt.

Die bearbeiten Methode in MitarbeiterDAO hat drei Varianten.
1. Name und Abteilung werden geändert
2. Nur der Name wird verändert
3. Nur die Abteilung wird verändert

**6. Service-Schnittstellen**

Services erhalten ihre DAO-Abhängigkeiten und die `ConnectionFactory` über Konstruktoren. Die UI übergibt ihnen keine Datenbankverbindungen.

**ArtikelService**

| Methode                                         | Rückgabe          |
| ----------------------------------------------- | ----------------- |
| `anlegen(String bezeichnung)`                   | `ArtikelBO`       |
| `holeNachId(int artikelId)`                     | `ArtikelBO`       |
| `auflisten()`                                   | `List<ArtikelBO>` |
| `suchen(String suchbegriff)`                    | `List<ArtikelBO>` |
| `umbenennen(int artikelId, String bezeichnung)` | `ArtikelBO`       |
| `loeschen(int artikelId)`                       | `void`            |

**MitarbeiterService**

| Methode                                                           | Rückgabe              |
|-------------------------------------------------------------------| --------------------- |
| `anlegen(String name, Abteilung abteilung)`                       | `MitarbeiterBO`       |
| `holeNachId(int mitarbeiterId)`                                   | `MitarbeiterBO`       |
| `auflisten()`                                                     | `List<MitarbeiterBO>` |
| `suchen(String suchbegriff)`                                      | `List<MitarbeiterBO>` |
| `bearbeiten(int mitarbeiterId, String name, Abteilung abteilung)` | `MitarbeiterBO`       |
| `loeschen(int mitarbeiterId)`                                     | `void`                |

Die bearbeiten Methode hat drei Varianten.
1. Name und Abteilung werden geändert
2. Nur der Name wird verändert
3. Nur die Abteilung wird verändert

**MaterialService**

| Methode                                                | Rückgabe                 |
| ------------------------------------------------------ | ------------------------ |
| `einlagern(int artikelId, int erfasserId, int menge)`  | `Buchungsergebnis`       |
| `ausgeben(int artikelId, int empfaengerId, int menge)` | `Buchungsergebnis`       |
| `historie()`                                           | `List<Historieneintrag>` |

Zwei kleine Rückgabetypen ergänzen die BOs:

- `Buchungsergebnis`: die gespeicherte `LagerbewegungBO` und `int neuerBestand`.
- `Historieneintrag`: die `LagerbewegungBO`, `String artikelBezeichnung` und `String mitarbeiterName`.

Der neue Bestand im Buchungsergebnis beschreibt den Stand unmittelbar nach dieser Buchung. Spätere Buchungen können ihn wieder verändern.

Für T04 wird folgender Verbindungsvertrag vorgeschlagen:

- Konstruktor: `ConnectionFactory(String url, String benutzer, String passwort)`
- Methode: `Connection openConnection()`

`openConnection()` liefert eine neue offene Verbindung mit Auto-Commit und Isolation `READ_COMMITTED` oder wirft `DatenzugriffsException`. Die aufrufende Stelle übernimmt das Schließen. `Main` übergibt die vorhandenen `DATABASE_*`-Umgebungsvariablen; Tests übergeben ihre getrennte Konfiguration.

**7. Transaktionen und Ressourcen**

Jeder schreibende Service-Aufruf besitzt eine eigene Transaktion auf einer Verbindung.

Eine Materialbuchung läuft folgendermaßen ab:

1. IDs und Menge prüfen.
2. Verbindung öffnen und Auto-Commit ausschalten.
3. Artikel mit `findeNachIdMitSperre` lesen und sperren.
4. Existenz des Mitarbeiters auf derselben Verbindung prüfen.
5. Bestand unter der Artikelsperre prüfen und neuen Bestand berechnen.
6. Artikelbestand ändern.
7. Genau eine Lagerbewegung speichern.
8. Commit ausführen.
9. Verbindung schließen und das Buchungsergebnis zurückgeben.

Tritt vor bestätigtem Commit ein Fehler auf, versucht der Service einen vollständigen Rollback. Danach wird der Fehler weitergegeben. Ein zusätzlicher Rollback-Fehler darf die ursprüngliche Ursache nicht verdrängen; er wird als unterdrückter Fehler angehängt.

Die Artikelsperre schützt die Bestandsprüfung auch bei parallelen Ausgaben und bleibt bis zum Transaktionsende bestehen. [PostgreSQL-Dokumentation](https://www.postgresql.org/docs/17/explicit-locking.html)

| Aufgabe                             | Verantwortlich      |
| ----------------------------------- | ------------------- |
| Verbindung erzeugen                 | ConnectionFactory   |
| Verbindung schließen                | Aufrufender Service |
| Transaktion, Commit und Rollback    | Service             |
| Statements und ResultSets schließen | DAO                 |
| SQL ausführen und BOs erzeugen      | DAO                 |
| Eingaben und Rückmeldungen          | UI                  |

DAOs öffnen keine eigene Verbindung, führen keinen Commit oder Rollback aus und schließen die übergebene Verbindung nicht. Statements, ResultSets und Verbindungen werden mit `try`-with-resources zuverlässig geschlossen.

Bei Verbindungsabbruch während des Commits kann der Buchungsausgang unklar sein. Die UI darf dann weder Erfolg noch eine sichere Nichtbuchung behaupten. Sie fordert zur Prüfung von Bestand und Historie auf; es erfolgt keine automatische Wiederholung.

**8. Fehlerkonzept**

Gemeinsames Package: `materialverwaltung.exception`.

- `FachlicheException extends RuntimeException`: Eine erwartbare Fachregel wurde verletzt.
- `DatenzugriffsException extends RuntimeException`: Ein technischer Datenbankfehler ist aufgetreten.
- `UnzureichenderBestandException extends FachlicheException`: Wird entsprechend T14 für Überausgaben ergänzt.

Fachfehlercodes:

| Code                         | Bedeutung                                                    |
| ---------------------------- | ------------------------------------------------------------ |
| UNGUELTIGE\_EINGABE          | Pflichttext, Länge, ID oder Menge ungültig                   |
| ARTIKEL\_NICHT\_GEFUNDEN     | Angegebener Artikel existiert nicht                          |
| MITARBEITER\_NICHT\_GEFUNDEN | Angegebener Mitarbeiter existiert nicht                      |
| ARTIKELBEZEICHNUNG\_VERGEBEN | Bezeichnung gehört bereits einem anderen Artikel             |
| ARTIKEL\_HAT\_BEWEGUNGEN     | Artikel darf wegen seiner Historie nicht gelöscht werden     |
| MITARBEITER\_HAT\_BEWEGUNGEN | Mitarbeiter darf wegen seiner Historie nicht gelöscht werden |
| UNZUREICHENDER\_BESTAND      | Angeforderte Ausgabe übersteigt den Bestand                  |
| BESTANDSUEBERLAUF            | Eingang würde den erlaubten Zahlenbereich überschreiten      |

Der DAO erhält die ursprüngliche `SQLException` als Ursache der `DatenzugriffsException`.

Der Service kann bekannte Datenbankkonflikte fachlich übersetzen:

- SQLState `23505`: Verletzung einer Eindeutigkeitsbedingung.
- SQLState `23503`: Verletzung einer Fremdschlüsselbedingung.

Diese Kennungen sind unabhängig vom Wortlaut der Datenbankmeldung. [PostgreSQL-Fehlercodes](https://www.postgresql.org/docs/17/errcodes-appendix.html)

Die Übersetzung muss zur Operation und betroffenen Bedingung passen. Eine doppelte Artikelbezeichnung wird beispielsweise zu `ARTIKELBEZEICHNUNG_VERGEBEN`. Nicht jede beliebige Eindeutigkeitsverletzung bedeutet automatisch eine doppelte Bezeichnung.

Dafür werden folgende Constraint-Namen vorgeschlagen:

- `uq_artikel_bezeichnung`
- `fk_lagerbewegung_artikel`
- `fk_lagerbewegung_mitarbeiter`

Bei Bedarf wird der strukturierte Constraint-Name der PostgreSQL-Ursache ausgewertet. Unbekannte Konflikte bleiben technische Fehler. Bei fachlicher Übersetzung bleibt die `DatenzugriffsException` als Ursache erhalten.

Die UI zeigt verständliche Fachmeldungen. Bei technischen Fehlern zeigt sie eine neutrale Rückmeldung, keine ungefilterten SQL-Meldungen, Stacktraces oder Zugangsdaten. Zahlformatfehler werden bereits bei der Eingabe abgefangen.

**9. Beispiele für die Abnahme**

| Ausgangslage und Aktion                            | Erwartetes Ergebnis                               |
| -------------------------------------------------- | ------------------------------------------------- |
| „ Maus “ neu anlegen                               | Bezeichnung „Maus“, generierte ID, Bestand 0      |
| „Maus“ erneut anlegen                              | Fachliche Ablehnung, kein weiterer Artikel        |
| Artikel mit Bestand 5 umbenennen                   | Neue Bezeichnung, weiterhin Bestand 5             |
| Eingang 5, anschließend Ausgang 2                  | Bestand 3, genau zwei Bewegungen                  |
| Bestand 5, Ausgang 5                               | Erfolg, Bestand 0                                 |
| Bestand 5, Ausgang 6                               | UnzureichenderBestandException, keine Änderung    |
| Menge 0 oder negativ                               | Fachliche Ablehnung, keine Bewegung               |
| Eingang bei maximalem Bestand                      | Bestandsüberlauf, keine Änderung                  |
| Unbekannter Artikel oder Mitarbeiter               | Fachliche Ablehnung, keine Teilbuchung            |
| Referenzierten Artikel mit Bestand 0 löschen       | Ablehnung wegen vorhandener Historie              |
| Suche ohne Treffer                                 | Leere Liste                                       |
| Zwei Ausgaben von je 4 bei Bestand 5               | Genau eine erfolgreich, Endbestand 1              |
| Fehler beim Bewegungseintrag nach Bestandsänderung | Rollback stellt ursprünglichen Bestand wieder her |

Die späteren Datenbanktests prüfen den tatsächlichen Datenbestand. Für den Rollback-Nachweis muss der zweite Schreibschritt wirklich scheitern; eine Eingabeablehnung vor der Transaktion genügt nicht.
