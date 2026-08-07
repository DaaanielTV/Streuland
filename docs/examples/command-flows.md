# Beispiele für Befehlsabläufe

## Ablauf: Plot erstellen

1. Spieler: `/plot create`
2. Plugin prüft Limit, Standort und Umgebungsregeln
3. Plot wird erstellt und gespeichert
4. Spieler wird informiert (ID, Position, nächste Schritte)

## Ablauf: Spieler vertrauen

1. Besitzer: `/plot trust <spieler>`
2. Plugin prüft Besitz und Zielspielernamen
3. Zielspieler erhält Build-Rechte
4. Aktion wird bestätigt und persistiert

## Ablauf: Plot löschen

1. Besitzer: `/plot delete [plotId]`
2. Plugin startet Bestätigungsfenster
3. Besitzer: `/plot confirm` oder `/plot cancel`
4. Bei Bestätigung wird der Plot gelöscht und freigegeben

## Fehlerfälle

- Kein Plot vorhanden: freundliche Fehlermeldung
- Keine Berechtigung: Aktion wird abgebrochen
- Persistenzfehler: Fehler protokollieren + Spieler informieren

## Ablauf: Clan gründen und beitreten

1. Spieler: `/clan create <Name>`
2. Plugin erstellt Clan und setzt den Ersteller als Leader
3. Clan-ID wird generiert und ist für `/clan join <ID>` nutzbar
4. Clan-Mitglieder teilen sich ein gemeinsames Plot-Kontingent

## Ablauf: Krieg erklären

1. Clan-Leader: `/clan war <ClanName> <PlotID>`
2. Plugin prüft (Leader, 3+ Mitglieder, Plot gehört Ziel-Clan)
3. Krieg wird erstellt, Tötungen beider Seiten werden gezählt
4. Nach Ablauf gewinnt die Seite mit mehr Kills (Gleichstand: Verteidiger behält Plot)
5. Verlierer-Mitglieder zahlen `clan.war.loser-fee` an den Sieger-Leader

## Ablauf: Wegpass kaufen

1. Spieler: `/plot wegpass`
2. Plugin prüft ob ein Vault-Economy-Plugin vorhanden ist
3. Guthaben wird um `plot.wegpass.price` reduziert, Pass wird für `plot.wegpass.duration-hours` aktiv
4. Pass erlaubt das Betreten fremder Wege und Plots
5. Status bleibt über Neustarts erhalten (`movement-passes.json`)

## Ablauf: Marktstand mieten

1. Spieler: `/markt mieten`
2. Plugin prüft Vault-Economie und ob der Spieler keinen Stand besitzt
3. Stand wird an der konfigurierten Markt-Spawn-Position erstellt
4. Spieler setzt Preise per `/markt preis <item> <preis>`
5. Käufer interagieren per Rechtsklick auf die Stand-Kiste

## Fehlerfälle (neue Features)

- Kein Vault-Economy-Plugin: Wegpass, Marktkauf und Kriegsbelohnung sind deaktiviert
- Kriegserklärung ohne Leader-Rechte: Aktion wird abgebrochen
- Plot gehört nicht dem Ziel-Clan: Kriegserklärung wird abgelehnt
- Passiven Pass kaufen nicht möglich: abgelehnt
- Stand-Kiste eines fremden Clans: Zugriff verweigert (nur Clan-Mitglieder)
