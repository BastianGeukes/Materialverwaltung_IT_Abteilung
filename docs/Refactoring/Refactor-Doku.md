# Refactoring Dokumentation

- Diese Doku zeigt die Reafactorings die gemacht wurden
  für [T-24](https://github.com/BastianGeukes/Materialverwaltung_IT_Abteilung/issues/28)

## 1. Artikel-Auswahl in der UI auslagern

In [ArtikelUI.java](../../src/main/java/materialverwaltung/ui/ArtikelUI.java) haben sich diese Schritte in anzeigen(),
umbenennen() und loeschen() wiederholt.

Als Refactoring wurde deshalb diese wiederholenden Stellen in eine Private-Hilfsmethode `waehleArtikel()` ausgelagert.

Vorher:

````java
void anzeigen() {
    int id = eingabe.lesePositiveGanzzahl("Artikel-ID");
    ArtikelBO artikel = service.holeNachId(id);
    // ...
}

void umbenennen() {
    int id = eingabe.lesePositiveGanzzahl("Artikel-ID");
    ArtikelBO bisher = service.holeNachId(id);
    // ...
}

void loeschen() {
    int id = eingabe.lesePositiveGanzzahl("Artikel-ID");
    ArtikelBO artikel = service.holeNachId(id);
    // ...
}
````

Nachher:

````java
void anzeigen() {
    ArtikelBO artikel = waehleArtikel();
    // ...
}

void umbenennen() {
    ArtikelBO bisher = waehleArtikel();
    // ...
}

void loeschen() {
    ArtikelBO artikel = waehleArtikel();
    // ...
}

// ...
// Private-Hilfsmethoden

private ArtikelBO waehleArtikel() {
    int id = eingabe.lesePositiveGanzzahl("Artikel-ID");
    return service.holeNachId(id);
}
````

## 2. Suchmuster für beide DAOs gemeinsam erstellen

In [JdbcArtikelDAO.java (line 101)](../../src/main/java/materialverwaltung/dao/jdbc/JdbcArtikelDAO.java)
und [JdbcMitarbeiterDAO.java (line 116)](../../src/main/java/materialverwaltung/dao/jdbc/JdbcMitarbeiterDAO.java) stand
dieselbe Behandlung von Sonderzeichen in der Teilbegriffsuche.

Als Refactoring wurde eine finale Hilfsklasse erstellt mir einer static Methode die diese Behandlung zentral durchführt.

Vorher:

````java
// Klasse JdbcArtikelDAO
@Override
public List<ArtikelBO> sucheNachBezeichnung(Connection connection, String suchbegriff) {
    //...
    String suchmuster = suchbegriff
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
    //...
    sucheMitTeilbegriff.setString(
            1, "%" + suchmuster + "%"
    );
    //...
}

//Klasse JdbcMitarbeiterDAO
@Override
public List<MitarbeiterBO> sucheNachName(Connection connection, String suchbegriff) {
    //...
    String suchmuster = suchbegriff
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
    //...
    sucheMitTeilbegriff.setString(1, "%" + suchmuster + "%");
    //...
}
````

Nachher:

````java
// Klasse JdbcArtikelDAO
@Override
public List<ArtikelBO> sucheNachBezeichnung(Connection connection, String suchbegriff) {
    //...
    sucheMitTeilbegriff.setString(
            1, SqlSuchmuster.teilbegriff(suchbegriff)
    );
    //...
}

//Klasse JdbcMitarbeiterDAO
@Override
public List<MitarbeiterBO> sucheNachName(Connection connection, String suchbegriff) {
    //...
    sucheMitTeilbegriff.setString(1, SqlSuchmuster.teilbegriff(suchbegriff));
    //...
}

//finale Hilfsklasse
public final class SqlSuchmuster {
    public static String teilbegriff(String suchbegriff) {
        return "%" + suchbegriff
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_")
                + "%";

    }
}
````

### Weitere Refactor Möglichkeiten die nicht umgesetzt wurden

1. Jetzt gerade wird für jede Integrationstestklasse ein neuer Postgres-Container gestartet, bei der jedes Mal zu begin
   die Flyway migration durchgeführt wird
    1. Problem: Maven Build dauert lange durch das neue erstellen von Testcontainern und Migrieren mit Flyway
    2. Lösung: Ein einziger Testcontainer für alle Integrationstests, der nach jedem Testlauf wieder aufgeräumt wird und
       dem nächsten Test zur Verfügung gestellt wird
    3. Warum nicht umgesetzt?
        1. Wir haben uns gegen die Umsetzung entschieden, da wir viele Integrationstests haben und
           jeden hätten anpassen müssen, heißt es hätte viel Zeit beansprucht.
        2. Außerdem kann man nicht direkt sagen, ob es
           wirklich eine große Zeiteinsparung für den Build wäre. Dies kann man erst prüfen, wenn man die neue
           Version hat und gegen die alte Testet.