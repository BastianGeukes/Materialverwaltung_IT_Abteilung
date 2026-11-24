# Datenmodell der Materialverwaltung

Die PostgreSQL-Datenbank speichert den aktuellen Bestand je Artikel und hält jeden Materialeingang und jede
Materialausgabe als Lagerbewegung fest.

| Tabelle         | Primärschlüssel | Weitere Spalten und Regeln                                                                                                                                                                  |
|-----------------|-----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Artikel`       | `ArtikelID`     | `Bezeichnung` ist eindeutig und darf nicht leer sein. `Bestand` beginnt bei `0` und darf nicht negativ werden.                                                                              |
| `Mitarbeiter`   | `MitarbeiterID` | `Name` ist ein Pflichtfeld. `Abteilung` ist optional.                                                                                                                                       |
| `Lagerbewegung` | `BewegungsID`   | `ArtikelID` und `MitarbeiterID` sind Fremdschlüssel. `Menge` muss positiv sein. `Bewegungsart` ist `Eingang` oder `Ausgang`. `Bewegungszeitpunkt` wird standardmäßig beim Einfügen gesetzt. |

Jede Lagerbewegung gehört zu genau einem Artikel und einem Mitarbeiter. Ein Artikel und ein Mitarbeiter können jeweils
in beliebig vielen Lagerbewegungen vorkommen. Beim Eingang ist der Mitarbeiter der Erfasser, beim Ausgang der Empfänger.
Solange eine Bewegung auf einen Artikel oder Mitarbeiter verweist, verhindert die Datenbank dessen Löschung.

[SQL-Schema mit Datentypen, Schlüsseln und Prüfbedingungen](../src/main/resources/db/migration/V1__schema_anlegen.sql)