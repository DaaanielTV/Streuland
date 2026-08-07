package de.streuland.movement;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.streuland.economy.PlotEconomyHook;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wegpass: erlaubt 12h lang die Erkundung fremder Wege. Wird aus dem
 * Konto-Guthaben gekauft und in movement-passes.json persistiert.
 */
public class MovementPassService {

    private final JavaPlugin plugin;
    private final PlotEconomyHook economy;
    private final double price;
    private final long durationMs;
    private final Map<UUID, Long> passes = new HashMap<>();

    public MovementPassService(JavaPlugin plugin, PlotEconomyHook economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.price = Math.max(0, plugin.getConfig().getDouble("plot.wegpass.price", 1000.0));
        long hours = Math.max(1, plugin.getConfig().getLong("plot.wegpass.duration-hours", 12L));
        this.durationMs = hours * 3600_000L;
        load();
    }

    public double getPrice() {
        return price;
    }

    public boolean isActive(UUID playerId) {
        Long expiresAt = passes.get(playerId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= System.currentTimeMillis()) {
            passes.remove(playerId);
            return false;
        }
        return true;
    }

    public long getRemainingMillis(UUID playerId) {
        Long expiresAt = passes.get(playerId);
        if (expiresAt == null) {
            return 0L;
        }
        long remaining = expiresAt - System.currentTimeMillis();
        return remaining > 0 ? remaining : 0L;
    }

    public boolean purchase(Player player) {
        if (economy == null || !economy.hasEconomy()) {
            return false;
        }
        if (isActive(player.getUniqueId())) {
            return false;
        }
        if (economy.getBalance(player.getUniqueId()) < price) {
            return false;
        }
        if (!economy.withdraw(player.getUniqueId(), price)) {
            return false;
        }
        passes.put(player.getUniqueId(), System.currentTimeMillis() + durationMs);
        save();
        return true;
    }

    private void load() {
        File file = file();
        if (!file.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            Map<String, Long> raw = new Gson().fromJson(reader, new TypeToken<Map<String, Long>>() { }.getType());
            if (raw == null) {
                return;
            }
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> entry : raw.entrySet()) {
                try {
                    UUID id = UUID.fromString(entry.getKey());
                    if (entry.getValue() > now) {
                        passes.put(id, entry.getValue());
                    }
                } catch (IllegalArgumentException ignored) {
                    // skip corrupt entries
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not load movement passes: " + e.getMessage());
        }
    }

    private void save() {
        Map<String, Long> raw = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : passes.entrySet()) {
            if (entry.getValue() > now) {
                raw.put(entry.getKey().toString(), entry.getValue());
            }
        }
        File file = file();
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        try (FileWriter writer = new FileWriter(file)) {
            new Gson().toJson(raw, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save movement passes: " + e.getMessage());
        }
    }

    private File file() {
        return new File(plugin.getDataFolder(), "movement-passes.json");
    }
}
