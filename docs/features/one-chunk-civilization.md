# One Chunk Civilization Features

This document covers the clan system, chunk wars, movement passes, market stands, shafts and storage protection introduced by the "One Chunk Civilization" feature set.

## Table of Contents

- [Clan System](#clan-system)
- [Plot Quota](#plot-quota)
- [Chunk Wars](#chunk-wars)
- [Clan Shafts and Storage Protection](#clan-shafts-and-storage-protection)
- [Movement Restriction and Wegpass](#movement-restriction-and-wegpass)
- [Market Stands](#market-stands)
- [First-Plot Teleport](#first-plot-teleport)
- [Configuration Reference](#configuration-reference)

## Clan System

Clans are player groups with shared plot ownership, diplomacy and war capabilities.

### Commands

| Command | Description |
|---|---|
| `/clan create <Name>` | Create a new clan (you must not be in a clan) |
| `/clan join <ClanID>` | Join a clan by its UUID |
| `/clan leave` | Leave your current clan |
| `/clan info [Name]` | Show clan details (members, plots, kills, allies, enemies) |
| `/clan list` | List all clans with member counts |
| `/clan members [Name]` | Show members of a clan |
| `/clan setmotto <Text>` | Set the clan motto (leader only, max 50 chars) |
| `/clan setcolor <Farbe>` | Set the clan color (leader only) |

The clan leader is the creator. When the leader leaves, the next member becomes leader. An empty clan is disbanded automatically.

### Diplomacy

Clans can form alliances or declare wars through a proposal system:

| Command | Description |
|---|---|
| `/clan ally <Name>` | Propose an alliance (leader only) |
| `/clan peace <Name>` | Propose peace (leader only) |
| `/clan proposals` | List open proposals directed at your clan |
| `/clan accept <ProposalID>` | Accept a proposal |
| `/clan reject <ProposalID>` | Reject a proposal |

Proposals are accepted or rejected by the opposing clan.

## Plot Quota

A clan's plot quota grows with its membership. Each clan member is allowed `2 + clan member count` plots collectively. The quota is checked on `/plot create` and `/plot claim`. Players without a clan fall back to the world-level `max-plots-per-player` limit.

Example: a 4-member clan has a quota of 6 shared plots.

## Chunk Wars

A chunk war is a time-limited conflict between two clans over a single target plot.

### Declaring War

```
/clan war <ClanName> <PlotID>
```

Requirements:
- Must be the clan leader
- Clan needs at least 3 members
- The target plot must belong to a member of the target clan

During the war, every kill of an enemy clan member by your clan member counts toward your side's kill total.

### Resolution

When the war duration expires (default 24 hours), the clan with more kills wins. On a tie, the defender keeps the plot.

- **Winner**: ownership of the target plot is transferred to the winning clan.
- **Loser**: each losing clan member pays a configurable fee (`clan.war.loser-fee`) to the winning clan leader, capped by their individual balance.

### War Info

```
/clan warinfo
```

Shows attacker vs. defender, current kill counts, target plot and remaining time.

## Clan Shafts and Storage Protection

Each clan has a headquarters shaft built at the leader's first plot. The shaft contains storage chests that are protected: only clan members may open them. Right-clicking a clan storage chest as a non-member is denied.

Chests are registered per-clan by world coordinates and restored on server startup when clan structures are rebuilt.

## Movement Restriction and Wegpass

By default, players are restricted from entering foreign plots and paths. A **Wegpass** (movement pass) allows temporary access to explore foreign areas.

### Buying a Pass

```
/plot wegpass
```

Purchases a time-limited pass using Vault economy. The price and duration are configurable (`plot.wegpass.price`, `plot.wegpass.duration-hours`). Passes persist across restarts in `movement-passes.json`.

Active pass status and remaining time are shown on purchase.

## Market Stands

Players can rent a market stand to sell items to others via chest interaction.

### Commands

| Command | Description |
|---|---|
| `/markt mieten` | Rent a stand at the market spawn (costs `market.stand-price`) |
| `/markt preis <item> <preis>` | Set a price for an item in your stand |
| `/markt info` | Show your stand location and listed prices |

Buyers interact by right-clicking the stand chest. Only one stand per player is allowed.

The market spawn location is configurable (`market.spawn.x`, `market.spawn.z`) with a radius (`market.radius`).

## First-Plot Teleport

When a player claims their very first plot, they are automatically teleported to it. Subsequent plots show the usual `/plot home` hint instead.

## Configuration Reference

All values are read from `config.yml` on startup.

### Clan Wars (`clan.war`)

| Key | Default | Description |
|---|---|---|
| `clan.war.duration-hours` | `24` | War duration in hours |
| `clan.war.loser-fee` | `500` | Fee per losing member paid to the winner leader |

### Movement Pass (`plot.wegpass`)

| Key | Default | Description |
|---|---|---|
| `plot.wegpass.price` | `1000.0` | Price of a Wegpass in economy currency |
| `plot.wegpass.duration-hours` | `12` | Duration of a pass in hours |

### Market Stands (`market`)

| Key | Default | Description |
|---|---|---|
| `market.stand-price` | `500.0` | Rent price for a market stand |
| `market.radius` | `30` | Market area radius |
| `market.spawn.x` | `0` | Market spawn X coordinate |
| `market.spawn.z` | `0` | Market spawn Z coordinate |

### Dependencies

The following features require a Vault-compatible economy provider:
- Wegpass purchases
- Market stand rentals
- War rewards

Without Vault, these features are disabled; all other clan and protection features remain functional.
