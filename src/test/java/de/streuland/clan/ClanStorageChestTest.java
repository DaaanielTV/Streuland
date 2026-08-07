package de.streuland.clan;

import de.streuland.path.PathGenerator;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClanStorageChestTest {

    private ClanManager clanManager;
    private ClanShaftService shaftService;
    private ClanStorageChestListener listener;
    private World world;
    private Block chestBlock;
    private Clan clan;
    private UUID leaderId;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(config.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        when(plugin.getConfig()).thenReturn(config);

        PlotManager plotManager = mock(PlotManager.class);
        when(plotManager.getPlotsByOwner(Mockito.any(UUID.class))).thenReturn(Collections.emptyList());
        world = mock(World.class);
        when(world.getName()).thenReturn("world_main");
        when(plotManager.getWorld()).thenReturn(world);
        Block dummyBlock = mock(Block.class);
        when(world.getBlockAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(dummyBlock);

        shaftService = new ClanShaftService(plugin, plotManager);
        clanManager = new ClanManager(plugin, plotManager, mock(PathGenerator.class), shaftService);
        listener = new ClanStorageChestListener(shaftService, clanManager);

        leaderId = UUID.randomUUID();
        clan = clanManager.createClan("TestClan", leaderId);

        Plot hqPlot = mock(Plot.class);
        when(hqPlot.getCenterX()).thenReturn(100);
        when(hqPlot.getCenterZ()).thenReturn(200);
        shaftService.buildShaft(clan, hqPlot);

        chestBlock = mock(Block.class);
        when(chestBlock.getType()).thenReturn(Material.CHEST);
        when(chestBlock.getWorld()).thenReturn(world);
        when(chestBlock.getX()).thenReturn(101);
        when(chestBlock.getY()).thenReturn(ClanShaftService.SURFACE_Y);
        when(chestBlock.getZ()).thenReturn(200);
    }

    private PlayerInteractEvent click(Player player) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, chestBlock, BlockFace.UP);
    }

    @Test
    void memberCanOpenOwnClanStorage() {
        Player member = mock(Player.class);
        when(member.getUniqueId()).thenReturn(leaderId);

        PlayerInteractEvent event = click(member);
        listener.onInteract(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void foreignPlayerIsBlockedFromStorage() {
        Player foreigner = mock(Player.class);
        when(foreigner.getUniqueId()).thenReturn(UUID.randomUUID());

        PlayerInteractEvent event = click(foreigner);
        listener.onInteract(event);

        assertTrue(event.isCancelled());
        verify(foreigner).sendMessage(org.mockito.ArgumentMatchers.contains("Clan-Lager"));
    }

    @Test
    void clanlessPlayerIsBlockedFromStorage() {
        Player clanless = mock(Player.class);
        when(clanless.getUniqueId()).thenReturn(UUID.randomUUID());

        PlayerInteractEvent event = click(clanless);
        listener.onInteract(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void nonChestBlockIsIgnored() {
        Player foreigner = mock(Player.class);
        when(foreigner.getUniqueId()).thenReturn(UUID.randomUUID());
        Block grass = mock(Block.class);
        when(grass.getType()).thenReturn(Material.GRASS_BLOCK);
        when(grass.getWorld()).thenReturn(world);
        when(grass.getX()).thenReturn(101);
        when(grass.getY()).thenReturn(ClanShaftService.SURFACE_Y);
        when(grass.getZ()).thenReturn(200);

        PlayerInteractEvent event = new PlayerInteractEvent(foreigner, Action.RIGHT_CLICK_BLOCK, null, grass, BlockFace.UP);
        listener.onInteract(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void unregisteredChestIsIgnored() {
        Player foreigner = mock(Player.class);
        when(foreigner.getUniqueId()).thenReturn(UUID.randomUUID());
        Block other = mock(Block.class);
        when(other.getType()).thenReturn(Material.CHEST);
        when(other.getWorld()).thenReturn(world);
        when(other.getX()).thenReturn(999);
        when(other.getY()).thenReturn(ClanShaftService.SURFACE_Y);
        when(other.getZ()).thenReturn(999);

        PlayerInteractEvent event = new PlayerInteractEvent(foreigner, Action.RIGHT_CLICK_BLOCK, null, other, BlockFace.UP);
        listener.onInteract(event);

        assertFalse(event.isCancelled());
    }
}
