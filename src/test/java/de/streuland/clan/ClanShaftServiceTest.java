package de.streuland.clan;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClanShaftServiceTest {

    private PlotManager plotManager;
    private World world;
    private ClanShaftService service;
    private Clan clan;
    private Plot hq;

    @BeforeEach
    void setUp() {
        FileConfiguration config = mock(FileConfiguration.class);
        when(config.getBoolean("plot.clan.shaft-enabled", true)).thenReturn(true);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(config);

        plotManager = mock(PlotManager.class);
        world = mock(World.class);
        when(plotManager.getWorld()).thenReturn(world);

        service = new ClanShaftService(plugin, plotManager);

        clan = mock(Clan.class);
        UUID leader = UUID.randomUUID();
        when(clan.getLeader()).thenReturn(leader);

        hq = mock(Plot.class);
        when(hq.getCenterX()).thenReturn(100);
        when(hq.getCenterZ()).thenReturn(200);
        when(hq.getCreatedAt()).thenReturn(1000L);
        when(plotManager.getPlotsByOwner(leader)).thenReturn(Collections.singletonList(hq));
    }

    @Test
    void headquartersIsOldestPlotOfLeader() {
        Plot newer = mock(Plot.class);
        when(newer.getCreatedAt()).thenReturn(5000L);
        when(plotManager.getPlotsByOwner(Mockito.any(UUID.class))).thenReturn(Arrays.asList(newer, hq));

        assertEquals(hq, service.findHeadquarters(clan));
    }

    @Test
    void headquartersIsNullWithoutLeaderPlots() {
        when(plotManager.getPlotsByOwner(Mockito.any(UUID.class))).thenReturn(Collections.emptyList());

        assertNull(service.findHeadquarters(clan));
    }

    @Test
    void buildShaftDigsHoleAndPlacesChest() {
        Block air = mock(Block.class);
        Block chest = mock(Block.class);
        when(world.getName()).thenReturn("world");
        when(world.getBlockAt(eq(100), anyInt(), eq(200))).thenReturn(air);
        when(world.getBlockAt(eq(101), eq(63), eq(200))).thenReturn(chest);

        service.buildShaft(clan, hq);

        verify(world, Mockito.times(ClanShaftService.SHAFT_DEPTH)).getBlockAt(eq(100), anyInt(), eq(200));
        verify(air, Mockito.times(ClanShaftService.SHAFT_DEPTH)).setType(Material.AIR);
        verify(chest).setType(Material.CHEST);
    }

    @Test
    void dropInsideShaftAreaIsRecognized() {
        assertTrue(service.isInsideShaft(clan, 100, 60, 200));
        assertTrue(service.isInsideShaft(clan, 101, 64, 201));
    }

    @Test
    void dropOutsideShaftAreaIsRejected() {
        assertFalse(service.isInsideShaft(clan, 110, 60, 200));
        assertFalse(service.isInsideShaft(clan, 100, 40, 200));
        assertFalse(service.isInsideShaft(clan, 100, 80, 200));
    }

    @Test
    void depositStoresItemsInStorageChest() {
        Chest chest = mock(Chest.class);
        Inventory inventory = mock(Inventory.class);
        when(chest.getBlockInventory()).thenReturn(inventory);
        when(inventory.addItem(Mockito.any(ItemStack.class))).thenReturn(new java.util.HashMap<>());
        BlockState chestState = chest;
        Block block = mock(Block.class);
        when(block.getState()).thenReturn(chestState);
        when(world.getBlockAt(101, 63, 200)).thenReturn(block);

        ItemStack item = mock(ItemStack.class);
        when(item.clone()).thenReturn(item);

        List<ItemStack> leftovers = service.deposit(clan, item);

        assertTrue(leftovers.isEmpty());
        verify(inventory).addItem(item);
    }

    @Test
    void depositReturnsItemWhenChestMissing() {
        Block block = mock(Block.class);
        when(block.getState()).thenReturn(mock(BlockState.class));
        when(world.getBlockAt(101, 63, 200)).thenReturn(block);

        ItemStack item = new ItemStack(Material.STONE, 3);
        List<ItemStack> leftovers = service.deposit(clan, item);

        assertEquals(1, leftovers.size());
        assertEquals(3, leftovers.get(0).getAmount());
    }
}
