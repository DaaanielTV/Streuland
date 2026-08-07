package de.streuland.clan;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dropschächte (Phase 3): Jeder Clan erhält am Hauptquartier (ältester Plot
 * des Leaders) einen Schacht mit Lagerkiste. Wirft ein Clan-Mitglied Items
 * in den Schacht, landen sie direkt im Clan-Lager.
 */
public class ClanShaftService {

    public static final int SURFACE_Y = 63;
    public static final int SHAFT_DEPTH = 6;
    public static final int DROP_OVERHEAD = 4;

    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final boolean enabled;
    private final Map<Long, UUID> storageChestOwners;

    public ClanShaftService(JavaPlugin plugin, PlotManager plotManager) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.enabled = plugin.getConfig().getBoolean("plot.clan.shaft-enabled", true);
        this.storageChestOwners = new HashMap<>();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Hauptquartier = ältester Plot des Clan-Leaders.
     */
    public Plot findHeadquarters(Clan clan) {
        if (clan == null || clan.getLeader() == null) {
            return null;
        }
        List<Plot> plots = plotManager.getPlotsByOwner(clan.getLeader());
        if (plots == null || plots.isEmpty()) {
            return null;
        }
        Plot oldest = plots.get(0);
        for (Plot plot : plots) {
            if (plot.getCreatedAt() < oldest.getCreatedAt()) {
                oldest = plot;
            }
        }
        return oldest;
    }

    /**
     * Baut Schacht (1x1-Loch) und Lagerkiste neben dem Hauptquartier.
     */
    public void buildShaft(Clan clan, Plot hqPlot) {
        if (!enabled || hqPlot == null) {
            return;
        }
        World world = plotManager.getWorld();
        int cx = hqPlot.getCenterX();
        int cz = hqPlot.getCenterZ();
        for (int y = SURFACE_Y; y > SURFACE_Y - SHAFT_DEPTH; y--) {
            world.getBlockAt(cx, y, cz).setType(Material.AIR);
        }
        world.getBlockAt(cx + 1, SURFACE_Y, cz).setType(Material.CHEST);
        storageChestOwners.put(key(world.getName(), cx + 1, SURFACE_Y, cz), clan.getClanId());
    }

    /**
     * Leert die Lagerkisten-Registry (vor einem kompletten Neuaufbau).
     */
    public void clearStorageChestRegistry() {
        storageChestOwners.clear();
    }

    /**
     * Gehört der Block zur Lagerkiste eines Clans? Liefert die Clan-ID oder null.
     */
    public UUID getClanIdForStorageChest(Block block) {
        if (block == null || block.getType() != Material.CHEST) {
            return null;
        }
        return storageChestOwners.get(key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
    }

    private static long key(String world, int x, int y, int z) {
        long h = world.hashCode();
        return (h << 32) ^ ((long) x << 16) ^ ((long) y << 8) ^ (z & 0xffffffffL);
    }

    /**
     * Liegt die Position (z. B. ein Item-Drop) im Schacht-Bereich des Clans?
     */
    public boolean isInsideShaft(Clan clan, int x, int y, int z) {
        Plot hq = findHeadquarters(clan);
        if (hq == null) {
            return false;
        }
        int cx = hq.getCenterX();
        int cz = hq.getCenterZ();
        return Math.abs(x - cx) <= 1
                && Math.abs(z - cz) <= 1
                && y > SURFACE_Y - SHAFT_DEPTH
                && y <= SURFACE_Y + DROP_OVERHEAD;
    }

    /**
     * Legt Items ins Clan-Lager. Gibt nicht platzierte Reste zurück.
     */
    public List<ItemStack> deposit(Clan clan, ItemStack item) {
        if (item == null) {
            return Collections.emptyList();
        }
        Chest chest = getStorageChest(clan);
        if (chest == null) {
            return Collections.singletonList(item);
        }
        HashMap<Integer, ItemStack> leftovers = chest.getBlockInventory().addItem(item.clone());
        return new ArrayList<>(leftovers.values());
    }

    /**
     * Lagerkiste des Clans (neben dem Schacht). null, wenn sie fehlt.
     */
    public Chest getStorageChest(Clan clan) {
        Plot hq = findHeadquarters(clan);
        if (hq == null) {
            return null;
        }
        Block block = plotManager.getWorld().getBlockAt(hq.getCenterX() + 1, SURFACE_Y, hq.getCenterZ());
        return block.getState() instanceof Chest ? (Chest) block.getState() : null;
    }
}
