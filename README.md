# Materialverwaltung der IT-Abteilung

Mit der Materialverwaltung lassen sich Artikel und Mitarbeiter verwalten, Materialeingänge und -ausgaben buchen und
Bestände sowie Lagerbewegungen über die Konsole einsehen.

## Zusammenarbeit

Änderungen werden über eigene Branches und Pull Requests in `main` übernommen.

1. **Aktuellen Stand holen:** Auf `main` wechseln und die neuesten Änderungen von GitHub abrufen.
2. **Branch erstellen:** Für jede Aufgabe einen eigenen Branch von `main` anlegen, beispielsweise
   `feature/ArtikelServiceAnpassung`.
3. **Aufgabe bearbeiten:** Änderungen umsetzen, prüfen und mit Commit-Nachrichten speichern.
4. **Branch pushen:** Die Commits auf den eigenen Branch bei GitHub hochladen.
5. **Pull Request öffnen:** Als Zielbranch `main` auswählen und review anfordern.
6. **Review durchführen:** Mindestens ein anderes Teammitglied muss zustimmen. Falls Rückmeldungen kommen diese
   bearbeiten und offene Diskussionen abschließen. Nach neuen Änderungen ist eine erneute Freigabe erforderlich.
7. **Merge durchführen:** Den freigegebenen Pull Request über „Squash and merge“ übernehmen und den erledigten
   Feature-Branch löschen.
8. **Für die nächste Aufgabe:** Lokal wieder auf `main` wechseln, aktualisieren und einen neuen Feature Branch
   erstellen.

## Projekt in IntelliJ IDEA öffnen

Voraussetzungen für den lokalen Start sind **JDK 25**, **IntelliJ IDEA**, ein laufender **PostgreSQL-17-Server** und
**pgAdmin 4** zur Verwaltung der Datenbank.

1. In IntelliJ IDEA **Get from VCS** öffnen und das Repository klonen:
   `https://github.com/BastianGeukes/Materialverwaltung_IT_Abteilung.git`
2. Das Projekt als Maven-Projekt öffnen und die Abhängigkeiten laden lassen.
3. Unter **File → Project Structure → Project** das **JDK 25** als Project SDK auswählen.

## Lokale Datenbank in pgAdmin 4 anlegen

1. In pgAdmin 4 mit dem lokalen PostgreSQL-17-Server verbinden.
2. Unter dem Server mit der rechten Maustaste auf **Databases → Create → Database…** klicken.
3. Als Datenbanknamen beispielsweise `materialverwaltung` eintragen. Als **Owner** den PostgreSQL-Benutzer auswählen,
   mit dem die Anwendung später zugreift.
4. Mit **Save** bestätigen. Die Datenbank kann leer bleiben. Beim ersten Anwendungsstart legt Flyway die Tabellen
   `Artikel`, `Mitarbeiter` und `Lagerbewegung` automatisch an.

## Anwendung in IntelliJ starten

1. `src/main/java/materialverwaltung/Main.java` öffnen. Über das Startsymbol neben der `main`-Methode **Modify Run
   Configuration…** auswählen. Eine bereits vorhandene Startkonfiguration lässt sich unter
   **Run → Edit Configurations…** bearbeiten.
2. In der Startkonfiguration für `materialverwaltung.Main` das Feld **Environment variables** öffnen. Falls es
   ausgeblendet ist, lässt es sich über **Modify options** einblenden.
3. Die folgenden drei Variablen als einzelne Einträge hinterlegen:

   | Variable | Einzutragender Wert |
                        |---|---|
   | `DATABASE_URL` | JDBC-Adresse der angelegten Datenbank, z. B. `jdbc:postgresql://localhost:5432/materialverwaltung` |
   | `DATABASE_USERNAME` | PostgreSQL-Benutzer, den ihr in pgAdmin als Owner gewählt habt |
   | `DATABASE_PASSWORD` | Passwort dieses PostgreSQL-Benutzers |

4. Die Konfiguration speichern und `Main` mit **▶ Run** starten. Nach der Datenbankmigration erscheint das Hauptmenü in
   der IntelliJ-Konsole.

Host, Port und Datenbankname in der JDBC-Adresse müssen zur eigenen PostgreSQL-Installation passen. Das Passwort bleibt
in der persönlichen Startkonfiguration.

## Kurze Vorführung

Für die Demo werden die Beispieldaten direkt in der Anwendung angelegt. Bei den Buchungen die IDs verwenden, die das
Programm beim Anlegen ausgibt. Mit `0` gelangt man aus einem Untermenü zurück ins Hauptmenü.

| Schritt | Aktion                                                                                                                                               | Erwartetes Ergebnis                                                                                               |
|---------|------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| 1       | **Mitarbeiterverwaltung → Mitarbeiter anlegen**. `Anna` als Namen eingeben und die Abteilung `IT` auswählen.                                         | Die Mitarbeiter-ID wird angezeigt.                                                                                |
| 2       | **Artikelverwaltung → Artikel anlegen**. Den Artikel `Maus` anlegen.                                                                                 | Der Artikel erhält eine ID und startet mit Bestand `0`.                                                           |
| 3       | **Materialbuchung → Material einlagern (Eingang)**. Artikel-ID, Annas Mitarbeiter-ID als Erfasserin und Menge `5` eingeben.                          | Neuer Bestand: `5`.                                                                                               |
| 4       | **Materialbuchung → Material ausgeben (Ausgang)**. Artikel-ID, Annas Mitarbeiter-ID als Empfängerin und Menge `3` eingeben.                          | Neuer Bestand: `2`.                                                                                               |
| 5       | Unter **Artikelverwaltung → Bestand eines Artikels anzeigen** den Artikel aufrufen. Anschließend im Hauptmenü **Bewegungshistorie anzeigen** wählen. | Der Bestand beträgt `2`. Die Historie zeigt den Ausgang über `3` Stück und den vorherigen Eingang über `5` Stück. |

## Tests ausführen

Für den vollständigen Testlauf **Docker Desktop mit Linux-Containern** starten. Anschließend im IntelliJ-Terminal im
Projektverzeichnis unter Windows ausführen:

```powershell
.\mvnw.cmd clean verify
```

Der Maven Wrapper führt die Unit- und Integrationstests aus. Bei erfolgreichem Abschluss erscheint `BUILD SUCCESS`.

Die Integrationstests starten über **Testcontainers** eigene PostgreSQL-17-Container und legen die benötigten Testdaten
selbst an. Beispielsweise erzeugt `MaterialUIIT` vor jedem Test den Artikel „Maus“ und die Mitarbeiter „Anna“ und „Ben“.
Die lokale Anwendungsdatenbank und die Zugangsdaten aus der IntelliJ-Startkonfiguration werden dafür nicht benötigt.

## Optional: Anwendungsdatenbank als Container starten

Wer PostgreSQL für die Vorführung als Container betreiben möchte, kann die Datenbank mit folgendem Befehl anlegen. Der
Container verwendet auf dem Rechner Port `5433`, damit der übliche Port `5432` für eine lokale PostgreSQL-Installation
frei bleibt.

```powershell
docker run --name materialverwaltung-db -p 127.0.0.1:5433:5432 -e POSTGRES_DB=materialverwaltung -e POSTGRES_USER=material -e POSTGRES_PASSWORD=material_demo -d postgres:17-alpine
```

Für diese Variante in der IntelliJ-Startkonfiguration die JDBC-Adresse
`jdbc:postgresql://localhost:5433/materialverwaltung`, den Benutzer `material` und das lokale Demo-Passwort
`material_demo` eintragen. Anschließend `Main` wie oben beschrieben starten. Einen bereits angelegten und gestoppten
Container startet `docker start materialverwaltung-db` erneut.

## Projektdokumentation

- [T01 - Gemeinsamer Vertrag](docs/T01%20-%20Gemeinsamer%20Vertrag%20für%20die%20Materialverwaltung.md)
- [Projektsteckbreif](docs/projektsteckbrief.md)
- [Fehlerbehandlungskonzept](docs/Fehlerbehandlungskonzept.md)
- [Architektur](docs/architekturbeschreibung.md)
- [Datenmodell](docs/Datenmodel.md)
- [Anforderungen, Tests und Abgabenachweise](docs/T25-Abgabenachweise.md)