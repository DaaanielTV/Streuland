package de.streuland.marketstand;

import de.streuland.economy.PlotEconomyHook;
import de.streuland.marketstand.MarketStandService.MarketStand;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketStandServiceTest {

    @TempDir
    File tempDir;

    private JavaPlugin plugin;
    private Economy vault;
    private World world;
    private MarketStandService service;
    private UUID playerId;

    @BeforeEach
    void setUp() throws Exception {
        Server server = mock(Server.class);
        when(server.getOfflinePlayer(Mockito.any(UUID.class))).thenReturn(mock(OfflinePlayer.class));
        when(server.getItemFactory()).thenReturn(mock(org.bukkit.inventory.ItemFactory.class));
        world = mock(World.class);
        when(server.getWorlds()).thenReturn(Collections.singletonList(world));
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);

        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir);
        FileConfiguration config = mock(FileConfiguration.class);
        when(config.getDouble("market.stand-price", 500.0)).thenReturn(500.0);
        when(config.getInt("market.radius", 30)).thenReturn(30);
        when(config.getInt("market.spawn.x", 0)).thenReturn(0);
        when(config.getInt("market.spawn.z", 0)).thenReturn(0);
        when(plugin.getConfig()).thenReturn(config);

        vault = mock(Economy.class);
        when(vault.getBalance(any(OfflinePlayer.class))).thenReturn(5000.0);
        when(vault.withdrawPlayer(any(OfflinePlayer.class), anyDouble()))
                .thenReturn(new EconomyResponse(100.0, 4900.0, EconomyResponse.ResponseType.SUCCESS, null));
        when(vault.depositPlayer(any(OfflinePlayer.class), anyDouble()))
                .thenReturn(new EconomyResponse(100.0, 5100.0, EconomyResponse.ResponseType.SUCCESS, null));

        playerId = UUID.randomUUID();
        service = new MarketStandService(plugin, new PlotEconomyHook(vault));
    }

    @AfterEach
    void tearDown() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, null);
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getWorld()).thenReturn(world);
        return player;
    }

    private void stubChestAt(int x, int y, int z, Chest chest) {
        Block block = mock(Block.class);
        BlockState state = chest == null ? mock(BlockState.class) : chest;
        when(block.getState()).thenReturn(state);
        when(world.getBlockAt(eq(x), eq(y), eq(z))).thenReturn(block);
    }

    @Test
    void marketAreaCoversSpawnRadius() {
        assertTrue(service.isInsideMarket(world, 0, 0));
        assertTrue(service.isInsideMarket(world, 29, 0));
        assertFalse(service.isInsideMarket(world, 31, 0));
        assertFalse(service.isInsideMarket(world, 0, 40));
    }

    @Test
    void rentCreatesStandAtNextFreeSlot() {
        Player player = player();
        stubChestAt(3, 63, 0, mock(Chest.class));

        MarketStand stand = service.rent(player);

        assertNotNull(stand);
        assertEquals(playerId, stand.owner);
        assertEquals(3, stand.x);
        assertEquals(0, stand.z);
        assertEquals(1, service.getAllStands().size());
        verify(vault).withdrawPlayer(any(OfflinePlayer.class), eq(500.0));
    }

    @Test
    void rentFailsWhenPlayerAlreadyOwnsStand() {
        Player player = player();
        stubChestAt(3, 63, 0, mock(Chest.class));
        service.rent(player);

        assertNull(service.rent(player));
    }

    @Test
    void setPriceAndGetPriceRoundTrip() {
        Player player = player();
        stubChestAt(3, 63, 0, mock(Chest.class));
        MarketStand stand = service.rent(player);

        assertTrue(service.setPrice(stand, "DIAMOND", 150.5));
        assertEquals(150.5, service.getPrice(stand, Material.DIAMOND));
        assertEquals(0, service.getPrice(stand, Material.STONE));
    }

    @Test
    void setPriceRejectsUnknownMaterial() {
        Player player = player();
        stubChestAt(3, 63, 0, mock(Chest.class));
        MarketStand stand = service.rent(player);

        assertFalse(service.setPrice(stand, "NOT_A_MATERIAL", 5.0));
    }

    @Test
    void purchaseTransfersItemAndMoney() {
        Player player = player();
        stubChestAt(3, 63, 0, mock(Chest.class));
        MarketStand stand = service.rent(player);
        service.setPrice(stand, "DIAMOND", 100.0);

        Inventory storage = mock(Inventory.class);
        ItemStack diamond = new ItemStack(Material.DIAMOND, 10);
        when(storage.getContents()).thenReturn(new ItemStack[]{diamond});
        when(storage.removeItem(Mockito.any(ItemStack.class))).thenReturn(new HashMap<>());
        Chest chest = mock(Chest.class);
        when(chest.getBlockInventory()).thenReturn(storage);
        stubChestAt(3, 63, 0, chest);

        Player buyer = player();
        org.bukkit.inventory.PlayerInventory buyerInv = mock(org.bukkit.inventory.PlayerInventory.class);
        when(buyerInv.addItem(Mockito.any(ItemStack.class))).thenReturn(new HashMap<>());
        when(buyer.getInventory()).thenReturn(buyerInv);

        assertTrue(service.purchase(stand, buyer, Material.DIAMOND, 2));
        verify(storage).removeItem(Mockito.any(ItemStack.class));
        verify(vault).withdrawPlayer(any(OfflinePlayer.class), eq(200.0));
        verify(vault).depositPlayer(any(OfflinePlayer.class), eq(200.0));
    }

    @Test
    void purchaseFailsWhenNotEnoughStock() {
        Player player = player();
        stubChestAt(3, 63, 0, mock(Chest.class));
        MarketStand stand = service.rent(player);
        service.setPrice(stand, "DIAMOND", 100.0);

        Inventory storage = mock(Inventory.class);
        when(storage.getContents()).thenReturn(new ItemStack[]{new ItemStack(Material.DIAMOND, 1)});
        Chest chest = mock(Chest.class);
        when(chest.getBlockInventory()).thenReturn(storage);
        stubChestAt(3, 63, 0, chest);

        assertFalse(service.purchase(stand, player(), Material.DIAMOND, 5));
    }

    @Test
    void purchaseFailsWithoutPrice() {
        Player player = player();
        stubChestAt(3, 63, 0, mock(Chest.class));
        MarketStand stand = service.rent(player);

        Inventory storage = mock(Inventory.class);
        when(storage.getContents()).thenReturn(new ItemStack[]{new ItemStack(Material.STONE, 10)});
        Chest chest = mock(Chest.class);
        when(chest.getBlockInventory()).thenReturn(storage);
        stubChestAt(3, 63, 0, chest);

        assertFalse(service.purchase(stand, player(), Material.STONE, 1));
    }

    @Test
    void standsArePersistedAcrossServiceInstances() {
        Player player = player();
        stubChestAt(3, 63, 0, mock(Chest.class));
        service.rent(player);

        MarketStandService reloaded = new MarketStandService(plugin, new PlotEconomyHook(vault));

        List<MarketStand> stands = reloaded.getAllStands();
        assertEquals(1, stands.size());
        assertEquals(playerId, stands.get(0).owner);
    }
}
