# Streuland Plot Plugin

> Open-Source Paper Plugin für ein Vanilla-nahes Grundstückssystem – kein hässliches Grid, sondern natürliche Plots in einer normalen Minecraft Welt.

Entstanden am 01.02.2025 – first commit `bfbadcb` mit 28 Dateien (siehe Screenshot). Der Ursprung: Ich mochte "CB Nature" auf GrieferGames, aber der Citybuild war irgendwann tot und wurde nur noch für AFK-Farmen genutzt. Ich wusste, man kann die normale Vanilla World-Gen nutzen und damit viel bessere, natürlichere Plots bauen als das Standard-Grid.

### Was ist Streuland?

Streuland ist ein Paper 1.16.5 Plugin, das ein zufälliges Plot-System innerhalb von normalen Vanilla-Welten erzeugt. Plots werden zufällig verteilt (nicht Grid-basiert), automatisch über Pfade verbunden und sind vollständig für den Besitzer geschützt.

Das volle Java-Projekt mit Maven, gebaut für Paper `1.16.5`.

## Features / Zweck

- **Plot Verwaltung** – erstellen, besitzen, trust/untrust, löschen
- **Schutzmodell** – `PATH`, `PLOT_UNCLAIMED`, `PLOT_CLAIMED`
- **Automatische Weggenerierung** – kein Grid, Wege verbinden Plots natürlich
- **District & Nachbarschaften** – Progression statt einfach nur Claims
- **Plot Upgrades, Markt & Freigaben** – Wirtschaft + Approval Workflows
- **Speicherung** – YAML + SQLite
- **Optionales Dashboard** – REST/WebSocket Assets
- **Clan System** – Quota, Diplomatie, Chunk Wars & Belohnungen
- **Marktstände** – `/markt`
- **Bewegungseinschränkung** – Wegpass zum Erkunden fremder Wege
- **Eigene Plot-Rollen**

## Installation

### Voraussetzungen
- Java 17+
- Maven 3.8+
- Paper Server kompatibel mit `1.16.5`

### Aus Source bauen
```bash
mvn clean package
```
Die Jar liegt danach in `target/`.

### Auf Paper deployen
1. Generierte Jar in `plugins/` kopieren
2. Server starten / restarten
3. Configs unter `plugins/Streuland/` anpassen

## Nutzung

Wichtigste Befehle:

- `/plot create` – neues Grundstück erstellen
- `/plot info` – Infos zum aktuellen Plot
- `/plot trust <Spieler>` / `/plot untrust <Spieler>` – Mitarbeiter verwalten
- `/plot list` – eigene Plots
- `/plot home` – zum Plot teleportieren
- `/plot wegpass` – Bewegungspass kaufen für fremde Pfade
- `/district ...` – District Verwaltung
- `/plotapprove ...` – Anträge prüfen
- `/streuland ...` – Diagnose & Wartung
- `/clan ...` – Clan System (erstellen, beitreten, Diplomatie, Wars)
- `/markt ...` – Marktstände (mieten, Preis, Info)

Mehr in `docs/examples/command-flows.md` und `docs/features/one-chunk-civilization.md`.

## Development

```bash
git clone <dein-fork>
cd streuland
mvn clean verify
```

IDE mit Maven Import nutzen. `target/` ist ignoriert – keine Binaries committen.

## Konfiguration

Wird beim ersten Start aus `src/main/resources/` nach `plugins/Streuland/` generiert:

- `config.yml` – Plot, Schutz, Rollen, Clan War, Markt, Wege
- `world_main.yml`, `world_nether.yml`, `world_end.yml`
- `plot-upgrades.yml`
- `quests.yml`
- `messages_en.yml` / `messages_de.yml`

Siehe `docs/features/one-chunk-civilization.md` für Clan, War, Wegpass und Markt Konfig.

## Build / Run

- Jar bauen: `mvn clean package`
- Tests: `mvn test`
- Full Check: `mvn clean verify`

## Fehlersuche

- **Dependency Fehler**: Internetzugang zu Maven Repos in `pom.xml` prüfen
- **Plugin lädt nicht**: Paper Version prüfen, Logs nach fehlenden Soft-Dependencies checken (`Vault`, `WorldGuard`)
- **Config kaputt**: Ungültige Configs in `plugins/Streuland/` löschen und neu generieren lassen
- **Economy geht nicht (Wegpass, Markt, War Rewards)**: Vault-kompatibles Economy Plugin installieren, ohne geht es nicht

## Doku

- `docs/README.md` – Index
- `docs/features/one-chunk-civilization.md` – Clan, Chunk Wars, Wegpass, Markt
- `docs/architecture/system-overview.md` – Architektur
- `docs/architecture/code-walkthrough.md` – Code Übersicht
- `docs/api/core-components.md` – Module

## Mitwirken

Siehe [CONTRIBUTING.md](CONTRIBUTING.md).

## Lizenz

GNU GPLv3 – siehe [LICENSE](LICENSE).
Das ist jetzt viel nahbarer – du erklärst nicht nur WAS es tut, sondern WARUM es existiert. Das zieht auf Spigot / GitHub viel mehr als eine trockene Feature-Liste.

Willst du dass ich dir das noch auf `drosemann/streuland` als PR-fertige README formatiere und die `pom.xml` Beschreibung auch auf Deutsch angleiche?
