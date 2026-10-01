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
| ---------------------- | ------------------------------------------------------------------------------------------ |---------------------------------------------------------------------------------------------------------|
| Artikel Anlegen        | Artikelnummer eindeutig und Bezeichnung darf nicht leer sein                               | bereits verwendete Artikelnummer oder leere Bezeichnung führt zur Ablehnung. Kein Artikel wird angelegt |
| Material einlagern     | Menge muss ganze Positive Zahl sein                                                        | Bei 0, negativ oder ungültige Zahl wird abgelehnt. -> Bestand unverändert                               |
| Material ausgeben      | höchstens darf die vorhandene Menge ausgegeben werden                                      | Wenn Bestand nicht ausreicht wird Ausgabe mit Rückmeldung angelehnt. -> Bestand unverändert             |
| Artikel löschen        | Artikel mit Lagerbewegung darf nicht gelöscht werden                                       | Löschvorgang wird abgelehnt mit Rückmeldung falls Lagerbewegung vorhanden                               |
| Lagerbewegung zuordnen | jede Lagerbewegung muss eindeutig zu einem vorhanden Artikel und Mitarbeiter zuordbar sein | Wenn Mitarbeiter oder Artikel ID nicht existiert wird keine Buchung durchgeführt                        |
