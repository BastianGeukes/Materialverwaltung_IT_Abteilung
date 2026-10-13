# Projektsteckbrief

> Problem:
> - Materialien wie Netzwerkkabel, Mäuse und Tastaturen in einer IT-Abteilung verwalten

> Lösung: <br/>
> Wir können prüfen:
> - Was wir auf Lager haben
> - Was dazu kommt
> - Wer Material entnommen hat

---

> Daten die dauerhaft gespeichert werden:
> - Artikel
> - Mitarbeiter
> - Lagerbewegung

---

> Anwendungsfälle
> - Artikel verwalten: Artikel anlegen, bearbeiten, suchen und löschen (falls keine Lagerbewegung existiert)
> - Material einlagern: Wareneingang erfassen und Bestand erhöhen
> - Material ausgeben: Entnahme für einen Mitarbeiter erfassen und den Bestand reduzieren

---

| Bereich                | Fachregel                                                                                  | Fehlerfall und Reaktion                                                                                 |
|------------------------|--------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| Artikel Anlegen        | Artikelnummer eindeutig und Bezeichnung darf nicht leer sein                               | bereits verwendete Artikelnummer oder leere Bezeichnung führt zur Ablehnung. Kein Artikel wird angelegt |
| Material einlagern     | Menge muss ganze Positive Zahl sein                                                        | Bei 0, negativ oder ungültige Zahl wird abgelehnt. -> Bestand unverändert                               |
| Material ausgeben      | höchstens darf die vorhandene Menge ausgegeben werden                                      | Wenn Bestand nicht ausreicht wird Ausgabe mit Rückmeldung angelehnt. -> Bestand unverändert             |
| Artikel löschen        | Artikel mit Lagerbewegung darf nicht gelöscht werden                                       | Löschvorgang wird abgelehnt mit Rückmeldung falls Lagerbewegung vorhanden                               |
| Lagerbewegung zuordnen | jede Lagerbewegung muss eindeutig zu einem vorhanden Artikel und Mitarbeiter zuordbar sein | Wenn Mitarbeiter oder Artikel ID nicht existiert wird keine Buchung durchgeführt                        |

## Konkrete Anforderungen an die IT-Materialverwaltung

Die Konsolenanwendung verwaltet Materialien wie Netzwerkkabel, Mäuse und Tastaturen. Sie zeigt vorhandene Bestände,
erfasst Materialeingänge und dokumentiert Ausgaben an Mitarbeiter.

Dauerhaft gespeichert werden Artikel, Mitarbeiter und Lagerbewegungen. Artikel besitzen eine automatisch erzeugte ID,
eine eindeutige Bezeichnung und einen gespeicherten Bestand. Eine zusätzliche Artikelnummer ist nicht vorgesehen.

Unterstützt werden Artikel- und Mitarbeiterpflege, Suche, Bestandsanzeige, Einlagerung, Ausgabe und Bewegungshistorie.

Neue Artikel beginnen mit Bestand 0. Bestandsänderungen erfolgen ausschließlich durch Buchungen mit positiver
ganzzahliger Menge. Beim Eingang bezeichnet der Mitarbeiter den Erfasser, beim Ausgang den Empfänger. Eine Ausgabe darf
den vorhandenen Bestand nicht überschreiten.

Bestandsänderung und Lagerbewegung werden gemeinsam in einer Transaktion gespeichert. Artikel und Mitarbeiter mit
vorhandenen Bewegungen dürfen nicht gelöscht werden. Bewegungen bleiben unverändert erhalten.

Die Anwendung verwendet Java 25, JDBC, Flyway und PostgreSQL 17. Services verantworten Fachlogik und Transaktionen; DAOs
übernehmen Datenzugriffe. Die genauen Regeln, Schnittstellen und Fehlerfälle werden im [gemeinsamen Vertrag aus T01
festgelegt](T01%20-%20Gemeinsamer%20Vertrag%20für%20die%20Materialverwaltung.md).
