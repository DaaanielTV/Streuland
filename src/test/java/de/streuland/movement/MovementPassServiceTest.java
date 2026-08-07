package de.streuland.movement;

import de.streuland.economy.PlotEconomyHook;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MovementPassServiceTest {

    @TempDir
    File tempDir;

    private JavaPlugin plugin;
    private FileConfiguration config;
    private Economy vault;
    private Player player;
    private UUID playerId;

    @BeforeEach
    void setUp() throws Exception {
        Server server = mock(Server.class);
        when(server.getOfflinePlayer(Mockito.any(UUID.class))).thenReturn(mock(OfflinePlayer.class));
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);

        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir);
        config = mock(FileConfiguration.class);
        when(config.getDouble("plot.wegpass.price", 1000.0)).thenReturn(1000.0);
        when(config.getLong("plot.wegpass.duration-hours", 12L)).thenReturn(12L);
        when(plugin.getConfig()).thenReturn(config);

        vault = mock(Economy.class);
        playerId = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
    }

    @AfterEach
    void tearDown() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, null);
    }

    private PlotEconomyHook economyHook(double balance) {
        when(vault.getBalance(Mockito.any(org.bukkit.OfflinePlayer.class))).thenReturn(balance);
        when(vault.withdrawPlayer(Mockito.any(org.bukkit.OfflinePlayer.class), Mockito.anyDouble()))
                .thenReturn(new EconomyResponse(1000.0, balance - 1000.0, EconomyResponse.ResponseType.SUCCESS, null));
        return new PlotEconomyHook(vault);
    }

    @Test
    void passIsInactiveWithoutPurchase() {
        MovementPassService service = new MovementPassService(plugin, economyHook(5000.0));

        assertFalse(service.isActive(playerId));
    }

    @Test
    void purchaseDeductsMoneyAndActivatesPass() {
        MovementPassService service = new MovementPassService(plugin, economyHook(5000.0));

        assertTrue(service.purchase(player));
        assertTrue(service.isActive(playerId));
        Mockito.verify(vault).withdrawPlayer(Mockito.any(org.bukkit.OfflinePlayer.class), Mockito.eq(1000.0));
    }

    @Test
    void purchaseFailsWithoutEnoughBalance() {
        MovementPassService service = new MovementPassService(plugin, economyHook(100.0));

        assertFalse(service.purchase(player));
        assertFalse(service.isActive(playerId));
    }

    @Test
    void purchaseFailsIfPassAlreadyActive() {
        MovementPassService service = new MovementPassService(plugin, economyHook(5000.0));
        assertTrue(service.purchase(player));

        assertFalse(service.purchase(player));
    }

    @Test
    void passIsPersistedAcrossServiceInstances() {
        MovementPassService service = new MovementPassService(plugin, economyHook(5000.0));
        assertTrue(service.purchase(player));

        MovementPassService reloaded = new MovementPassService(plugin, economyHook(5000.0));

        assertTrue(reloaded.isActive(playerId));
    }

    @Test
    void passPriceAndDurationComeFromConfig() {
        MovementPassService service = new MovementPassService(plugin, economyHook(5000.0));

        assertEquals(1000.0, service.getPrice());
    }
}
