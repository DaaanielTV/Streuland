package de.streuland.marketstand;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.streuland.economy.PlotEconomyHook;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Zentrale Marktstände (Phase 4): Spieler mieten am Spawn einen Stand
 * (Kiste + Preisliste). Andere Spieler kaufen über eine Shop-GUI.
 * Der Marktbereich ist nur mit aktivem Wegpass betretbar (Clan-Mitglieder).
 */
public class MarketStandService {

    public static final int STAND_SURFACE_Y = 63;
    private static final int MAX_STANDS = 64;

    public static class MarketStand {
        public String id;
        public UUID owner;
        public int x;
        public int y;
        public int z;
        public Map<String, Double> prices = new LinkedHashMap<>();

        public MarketStand() {
        }

        public MarketStand(String id, UUID owner, int x, int y, int z) {
            this.id = id;
            this.owner = owner;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final JavaPlugin plugin;
    private final PlotEconomyHook economy;
    private final double rentPrice;
    private final int marketRadius;
    private final int spawnX;
    private final int spawnZ;
    private final List<MarketStand> stands = new ArrayList<>();

    public MarketStandService(JavaPlugin plugin, PlotEconomyHook economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.rentPrice = Math.max(0, plugin.getConfig().getDouble("market.stand-price", 500.0));
        this.marketRadius = Math.max(1, plugin.getConfig().getInt("market.radius", 30));
        this.spawnX = plugin.getConfig().getInt("market.spawn.x", 0);
        this.spawnZ = plugin.getConfig().getInt("market.spawn.z", 0);
        load();
    }

    public double getRentPrice() {
        return rentPrice;
    }

    /**
     * Liegt die Position im zentralen Marktbereich?
     */
    public boolean isInsideMarket(World world, int x, int z) {
        if (world == null) {
            return false;
        }
        World spawnWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (spawnWorld != null && !world.equals(spawnWorld)) {
            return false;
        }
        int dx = x - spawnX;
        int dz = z - spawnZ;
        return dx * dx + dz * dz <= marketRadius * marketRadius;
    }

    /**
     * Mietet dem Spieler den nächsten freien Stand (max. 1 pro Spieler).
     * null, wenn nicht möglich.
     */
    public MarketStand rent(Player player) {
        if (economy == null || !economy.hasEconomy()) {
            return null;
        }
        if (getStandOf(player.getUniqueId()) != null) {
            return null;
        }
        if (economy.getBalance(player.getUniqueId()) < rentPrice) {
            return null;
        }
        if (!economy.withdraw(player.getUniqueId(), rentPrice)) {
            return null;
        }
        int[] slot = nextFreeSlot();
        if (slot == null) {
            economy.deposit(player.getUniqueId(), rentPrice);
            return null;
        }
        MarketStand stand = new MarketStand("stand-" + UUID.randomUUID().toString().substring(0, 8),
                player.getUniqueId(), slot[0], STAND_SURFACE_Y, slot[1]);
        World world = player.getWorld();
        world.getBlockAt(stand.x, stand.y, stand.z).setType(Material.CHEST);
        stands.add(stand);
        save();
        return stand;
    }

    public MarketStand getStandAt(int x, int y, int z) {
        for (MarketStand stand : stands) {
            if (stand.x == x && stand.y == y && stand.z == z) {
                return stand;
            }
        }
        return null;
    }

    public MarketStand getStandOf(UUID owner) {
        for (MarketStand stand : stands) {
            if (stand.owner.equals(owner)) {
                return stand;
            }
        }
        return null;
    }

    public List<MarketStand> getAllStands() {
        return new ArrayList<>(stands);
    }

    /**
     * Setzt den Preis für ein Item an einem Stand. Preis 0 = nicht käuflich.
     */
    public boolean setPrice(MarketStand stand, String materialName, double price) {
        if (price < 0 || price > 1000000) {
            return false;
        }
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
        stand.prices.put(material.name(), price);
        save();
        return true;
    }

    public double getPrice(MarketStand stand, Material material) {
        Double price = stand.prices.get(material.name());
        return price == null ? 0 : price;
    }

    /**
     * Führt einen Kauf durch. false bei ungültigen Bedingungen.
     */
    public boolean purchase(MarketStand stand, Player buyer, Material material, int amount) {
        if (stand == null || buyer == null || material == null || amount <= 0) {
            return false;
        }
        if (economy == null || !economy.hasEconomy()) {
            return false;
        }
        double price = getPrice(stand, material);
        if (price <= 0) {
            return false;
        }
        Chest chest = getStandChest(stand);
        if (chest == null) {
            return false;
        }
        Inventory storage = chest.getBlockInventory();
        int available = availableAmount(storage, material);
        if (available < amount) {
            return false;
        }
        double total = price * amount;
        if (economy.getBalance(buyer.getUniqueId()) < total) {
            return false;
        }
        ItemStack sold = new ItemStack(material, amount);
        if (!storage.removeItem(sold).isEmpty()) {
            return false;
        }
        if (!economy.withdraw(buyer.getUniqueId(), total)) {
            storage.addItem(sold);
            return false;
        }
        ItemStack toGive = new ItemStack(material, amount);
        java.util.HashMap<Integer, ItemStack> overflow = buyer.getInventory().addItem(toGive);
        for (ItemStack rest : overflow.values()) {
            buyer.getWorld().dropItemNaturally(buyer.getLocation(), rest);
        }
        economy.deposit(stand.owner, total);
        return true;
    }

    private int availableAmount(Inventory storage, Material material) {
        int count = 0;
        for (ItemStack item : storage.getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    public Chest getStandChest(MarketStand stand) {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) {
            return null;
        }
        Block block = world.getBlockAt(stand.x, stand.y, stand.z);
        return block.getState() instanceof Chest ? (Chest) block.getState() : null;
    }

    private int[] nextFreeSlot() {
        for (int i = 0; i < MAX_STANDS; i++) {
            int ring = i / 8;
            int angleIndex = i % 8;
            int radius = (ring + 1) * 3;
            double angle = angleIndex * 45.0 * Math.PI / 180.0;
            int x = spawnX + (int) Math.round(radius * Math.cos(angle));
            int z = spawnZ + (int) Math.round(radius * Math.sin(angle));
            if (getStandAt(x, STAND_SURFACE_Y, z) == null) {
                return new int[]{x, z};
            }
        }
        return null;
    }

    private void load() {
        File file = file();
        if (!file.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            List<MarketStand> loaded = new Gson().fromJson(reader, new TypeToken<List<MarketStand>>() { }.getType());
            if (loaded != null) {
                stands.addAll(loaded);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not load market stands: " + e.getMessage());
        }
    }

    private void save() {
        File file = file();
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        try (FileWriter writer = new FileWriter(file)) {
            new Gson().toJson(stands, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save market stands: " + e.getMessage());
        }
    }

    private File file() {
        return new File(plugin.getDataFolder(), "market-stands.json");
    }
}
