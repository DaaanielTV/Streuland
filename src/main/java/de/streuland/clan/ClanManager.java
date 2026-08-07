package de.streuland.clan;

import de.streuland.path.PathGenerator;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClanManager implements ClanDiplomacyManager.WarResolutionHandler {
    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final PathGenerator pathGenerator;
    private final Map<UUID, Clan> clans;
    private final Map<UUID, UUID> playerClanMap;
    private final ClanDiplomacyManager diplomacyManager;
    private final Map<Long, UUID> pathOwners;
    private final ClanShaftService clanShaftService;
    private de.streuland.economy.PlotEconomyHook economyHook;

    public ClanManager(JavaPlugin plugin, PlotManager plotManager, PathGenerator pathGenerator, ClanShaftService clanShaftService) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.pathGenerator = pathGenerator;
        this.clans = new ConcurrentHashMap<>();
        this.playerClanMap = new ConcurrentHashMap<>();
        this.diplomacyManager = new ClanDiplomacyManager(plugin, clans, this);
        this.pathOwners = new ConcurrentHashMap<>();
        this.clanShaftService = clanShaftService;
    }

    /**
     * Restores persisted clans into the manager (e.g. from {@link ClanStorage#loadAll()} on startup).
     */
    public void loadAll(Collection<Clan> loadedClans) {
        if (loadedClans == null) {
            return;
        }
        for (Clan clan : loadedClans) {
            clans.put(clan.getClanId(), clan);
            for (UUID member : clan.getMembers()) {
                playerClanMap.put(member, clan.getClanId());
            }
        }
    }

    public ClanDiplomacyManager getDiplomacyManager() {
        return diplomacyManager;
    }

    public Clan createClan(String name, UUID leader) {
        if (playerClanMap.containsKey(leader)) {
            return null;
        }
        Clan clan = new Clan(UUID.randomUUID(), name, leader);
        clans.put(clan.getClanId(), clan);
        playerClanMap.put(leader, clan.getClanId());
        
        // Add leader's plots to clan
        for (Plot plot : plotManager.getPlotsByOwner(leader)) {
            clan.addPlot(plot.getPlotId());
        }

        updateClanPaths(clan);
        if (clanShaftService != null) {
            Plot hq = clanShaftService.findHeadquarters(clan);
            if (hq != null) {
                clanShaftService.buildShaft(clan, hq);
            }
        }
        return clan;
    }

    public boolean joinClan(UUID player, UUID clanId) {
        Clan clan = clans.get(clanId);
        if (clan == null || playerClanMap.containsKey(player)) {
            return false;
        }
        clan.addMember(player);
        playerClanMap.put(player, clanId);
        
        // Add player's plots to clan
        for (Plot plot : plotManager.getPlotsByOwner(player)) {
            clan.addPlot(plot.getPlotId());
        }
        
        updateClanPaths(clan);
        return true;
    }

    public void updateClanPaths(Clan clan) {
        List<String> plotIds = new ArrayList<>(clan.getPlotIds());
        if (plotIds.size() < 2) return;

        List<PathGenerator.BlockPosition> allPathBlocks = new ArrayList<>();
        for (int i = 0; i < plotIds.size() - 1; i++) {
            Plot p1 = plotManager.getStorage().getPlot(plotIds.get(i));
            Plot p2 = plotManager.getStorage().getPlot(plotIds.get(i + 1));
            if (p1 != null && p2 != null) {
                allPathBlocks.addAll(pathGenerator.generatePathBetween(
                    p1.getCenterX(), p1.getCenterZ(),
                    p2.getCenterX(), p2.getCenterZ()
                ));
            }
        }
        registerOwnPath(clan.getClanId(), allPathBlocks);
        pathGenerator.buildPathBlocks(allPathBlocks, true);
    }

    public void registerOwnPath(UUID clanId, List<PathGenerator.BlockPosition> blocks) {
        if (clanId == null || blocks == null) {
            return;
        }
        for (PathGenerator.BlockPosition pos : blocks) {
            pathOwners.put(key(pos.x, pos.z), clanId);
        }
    }

    public UUID getPathOwner(int x, int z) {
        return pathOwners.get(key(x, z));
    }

    public boolean isOwnPath(UUID clanId, int x, int z) {
        return clanId != null && clanId.equals(pathOwners.get(key(x, z)));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public void rebuildAllPaths() {
        for (Clan clan : clans.values()) {
            updateClanPaths(clan);
        }
    }

    public void rebuildClanStructures() {
        if (clanShaftService != null) {
            clanShaftService.clearStorageChestRegistry();
        }
        for (Clan clan : clans.values()) {
            updateClanPaths(clan);
            if (clanShaftService != null) {
                Plot hq = clanShaftService.findHeadquarters(clan);
                if (hq != null) {
                    clanShaftService.buildShaft(clan, hq);
                }
            }
        }
    }

    /**
     * Erklärt einem Clan den Krieg und benennt den Ziel-Plot, der bei Sieg erobert wird.
     * Der Plot muss einem Mitglied des Ziel-Clans gehören.
     */
    public boolean declareWar(UUID attackerClanId, UUID targetClanId, String targetPlotId) {
        Clan target = clans.get(targetClanId);
        if (target == null || targetPlotId == null || targetPlotId.isEmpty()) {
            return false;
        }

        Plot plot = plotManager.getStorage().getPlot(targetPlotId);
        if (plot == null || plot.getOwner() == null || !target.getMembers().contains(plot.getOwner())) {
            return false;
        }

        return diplomacyManager.declareWar(attackerClanId, targetClanId, targetPlotId);
    }

    public void setEconomyHook(de.streuland.economy.PlotEconomyHook economyHook) {
        this.economyHook = economyHook;
    }

    /**
     * Kriegsende: Der Sieger erhält den Ziel-Plot, der Verlierer verliert ihn.
     */
    @Override
    public void onWarEnded(ClanDiplomacyManager.ActiveWar war, Clan winner, Clan loser) {
        String targetPlotId = war.getTargetPlotId();
        if (targetPlotId != null) {
            Plot plot = plotManager.getStorage().getPlot(targetPlotId);
            if (plot != null && plot.getOwner() != null) {
                plotManager.transferPlotOwnership(targetPlotId, plot.getOwner(), winner.getLeader());
                loser.removePlot(targetPlotId);
                winner.addPlot(targetPlotId);
                updateClanPaths(loser);
                updateClanPaths(winner);

                Bukkit.broadcastMessage("§6[Clan-Krieg] §c" + winner.getName() + " §6erobert den Plot §c" + targetPlotId
                        + " §6von §c" + loser.getName() + "§6!");
            }
        }

        applyWarReward(winner, loser);
    }

    /**
     * Kriegs-Belohnung: Jedes Mitglied des Verlierer-Clans zahlt eine Gebühr
     * (config: clan.war.loser-fee), die der Sieger-Leader erhält.
     */
    public void applyWarReward(Clan winner, Clan loser) {
        if (economyHook == null || !economyHook.hasEconomy() || winner == null || loser == null) {
            return;
        }
        double fee = Math.max(0, plugin.getConfig().getDouble("clan.war.loser-fee", 500D));
        if (fee <= 0) {
            return;
        }

        double collected = 0;
        for (UUID member : new ArrayList<>(loser.getMembers())) {
            double pay = Math.min(fee, economyHook.getBalance(member));
            if (pay > 0 && economyHook.withdraw(member, pay)) {
                collected += pay;
            }
        }

        if (collected > 0) {
            economyHook.deposit(winner.getLeader(), collected);
            Bukkit.broadcastMessage("§6[Clan-Krieg] §c" + loser.getName() + " §6zahlt §c" + winner.getName()
                    + " §6eine Kriegsentschädigung von §c" + collected + " §6Guthaben!");
        }
    }

    public void leaveClan(UUID player) {
        UUID clanId = playerClanMap.remove(player);
        if (clanId != null) {
            Clan clan = clans.get(clanId);
            if (clan != null) {
                clan.removeMember(player);
                // If leader leaves, assign new leader or disband
                if (clan.getLeader().equals(player)) {
                    if (clan.getMembers().isEmpty()) {
                        clans.remove(clanId);
                    } else {
                        clan.setLeader(clan.getMembers().iterator().next());
                    }
                }
                
                // Remove player's plots from clan
                for (Plot plot : plotManager.getPlotsByOwner(player)) {
                    clan.removePlot(plot.getPlotId());
                }
            }
        }
    }

    public Clan getClanByPlayer(UUID player) {
        UUID clanId = playerClanMap.get(player);
        return clanId != null ? clans.get(clanId) : null;
    }

    /**
     * Clan-Gesamtkontingent eines Spielers: 2 + Clan-Memberzahl.
     * Liefert 0, wenn der Spieler in keinem Clan ist (dann gilt die Welt-Quota).
     */
    public int getClanPlotQuota(UUID playerId) {
        Clan clan = getClanByPlayer(playerId);
        return clan == null ? 0 : 2 + clan.getMemberCount();
    }

    /**
     * Aktuell vom Clan des Spielers belegte Plots. 0 wenn clanlos.
     */
    public int getClanPlotCount(UUID playerId) {
        Clan clan = getClanByPlayer(playerId);
        return clan == null ? 0 : clan.getPlotCount();
    }

    public boolean hasReachedClanPlotQuota(UUID playerId) {
        Clan clan = getClanByPlayer(playerId);
        return clan != null && clan.getPlotCount() >= 2 + clan.getMemberCount();
    }

    /**
     * Registriert einen neu beanspruchten Plot beim Clan des Spielers (falls vorhanden).
     */
    public void registerPlot(UUID playerId, String plotId) {
        Clan clan = getClanByPlayer(playerId);
        if (clan == null || plotId == null) {
            return;
        }
        clan.addPlot(plotId);
        updateClanPaths(clan);
    }

    public Clan getClanById(UUID clanId) {
        return clans.get(clanId);
    }

    public Collection<Clan> getAllClans() {
        return clans.values();
    }

    public boolean proposeAlly(UUID requesterId, UUID targetId) {
        return diplomacyManager.proposeAlly(requesterId, targetId);
    }

    public boolean proposePeace(UUID requesterId, UUID targetId) {
        return diplomacyManager.proposePeace(requesterId, targetId);
    }

    public boolean acceptDiplomacyProposal(UUID proposalId, UUID acceptingClanId) {
        return diplomacyManager.acceptProposal(proposalId, acceptingClanId);
    }

    public boolean rejectDiplomacyProposal(UUID proposalId, UUID rejectingClanId) {
        return diplomacyManager.rejectProposal(proposalId, rejectingClanId);
    }

    public boolean isAtWar(UUID clanIdA, UUID clanIdB) {
        return diplomacyManager.isAtWar(clanIdA, clanIdB);
    }

    public boolean isAlly(UUID clanIdA, UUID clanIdB) {
        return diplomacyManager.isAlly(clanIdA, clanIdB);
    }

    public List<Clan> getAllies(Clan clan) {
        return diplomacyManager.getAllies(clan);
    }

    public List<Clan> getEnemies(Clan clan) {
        return diplomacyManager.getEnemies(clan);
    }

    public Collection<ClanDiplomacyManager.DiplomaticProposal> getPendingProposals(UUID clanId) {
        return diplomacyManager.getPendingProposals(clanId);
    }

    public void update() {
        diplomacyManager.checkWarExpiry();
        diplomacyManager.cleanupExpiredProposals();
    }

    public int getTotalClanCount() {
        return clans.size();
    }

    public int getActiveWarCount() {
        long count = clans.keySet().stream()
                .filter(clanId -> diplomacyManager.getActiveWar(clanId) != null)
                .count();
        return (int) count;
    }
}
