# Fehlerbehandlungskonzept des Projekts Materialverwaltung IT-Abteilung

## Logging von Fehlern
- Fehler werden generell über eine static serialVersionUID geloggt
- Jede Exception Klasse hat dabei eine eigene

## SQL Fehler
- `Der Fehler entsteht in der Schicht der Data Access Objects (DAOs)`
- Die Fehler werden dort geworfen, wenn der Try-With-Resources Block mit dem PreparedStatement oder ResultSet einen SQL Fehler auslöst.
- Es wird dabei eine eigen erstellte DatenzugriffsException geworfen die eine Unterklasse von der RuntimeException ist
- Der Fehler wird dann in der Service-Schicht behandelt durch einen Rollback und eine für den Nutzer verständliche Fehlermeldung wird an den User ausgegeben

## Fachliche Fehler
- `Der Fehler entsteht in der Service-Schicht`
- Der Fehler wird in der gleichen Schicht behandelt und nicht geworfen
- Die Fehler werden mittels einer eigen erstellten FachlicheException behandelt, die eine Unterklasse der RuntimeException ist
- Die verschiedenen fachlichen Fehler sind in dem Enum FachlicherFehler dokumentiert damit eine eindeutige Bestimmung des Fehlers stattfindet
- Dem nutzer wird dann eine Verständliche Meldung zurückgegeben