package de.streuland.movement;

import de.streuland.clan.Clan;
import de.streuland.clan.ClanManager;
import de.streuland.marketstand.MarketStandService;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MovementGuardTest {

    private PlotManager plotManager;
    private ClanManager clanManager;
    private MovementPassService passService;
    private MovementGuard guard;
    private MarketStandService marketStandService;
    private Player player;
    private World world;
    private Block block;
    private Location from;
    private Location to;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        plotManager = mock(PlotManager.class);
        clanManager = mock(ClanManager.class);
        passService = mock(MovementPassService.class);
        marketStandService = mock(MarketStandService.class);
        guard = new MovementGuard(plotManager, clanManager, passService, marketStandService);

        playerId = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        world = mock(World.class);
        block = mock(Block.class);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(block.getType()).thenReturn(Material.GRASS_BLOCK);
        when(player.getWorld()).thenReturn(world);
        when(player.hasPermission("streuland.bypass.movement")).thenReturn(false);
        when(clanManager.getClanByPlayer(playerId)).thenReturn(null);

        from = new Location(world, 10, 63, 10);
        to = new Location(world, 11, 63, 10);
    }

    private PlayerMoveEvent move() {
        return new PlayerMoveEvent(player, from, to);
    }

    private void stubPathBlock() {
        when(block.getType()).thenReturn(Material.STONE);
    }

    private void stubClan(Set<UUID> members) {
        Clan clan = mock(Clan.class);
        when(clan.getClanId()).thenReturn(UUID.randomUUID());
        Set<UUID> all = new HashSet<>(members);
        all.add(playerId);
        when(clan.getMembers()).thenReturn(all);
        when(clanManager.getClanByPlayer(playerId)).thenReturn(clan);
    }

    @Test
    void clanlessPlayerIsNeverBlocked() {
        PlayerMoveEvent event = move();
        guard.onPlayerMove(event);

        assertEquals(to, event.getTo());
        verify(player, never()).sendMessage(any(String.class));
    }

    @Test
    void bypassPermissionIgnoresEverything() {
        stubClan(Collections.emptySet());
        when(player.hasPermission("streuland.bypass.movement")).thenReturn(true);

        Plot foreign = mock(Plot.class);
        when(plotManager.getPlotAt(any(World.class), anyInt(), anyInt())).thenReturn(foreign);

        PlayerMoveEvent event = move();
        guard.onPlayerMove(event);

        assertEquals(to, event.getTo());
    }

    @Test
    void ownClanPlotIsFreeToEnter() {
        stubClan(Collections.emptySet());

        Plot own = mock(Plot.class);
        when(own.getOwner()).thenReturn(playerId);
        when(plotManager.getPlotAt(any(World.class), anyInt(), anyInt())).thenReturn(own);

        PlayerMoveEvent event = move();
        guard.onPlayerMove(event);

        assertEquals(to, event.getTo());
    }

    @Test
    void foreignClanPlotIsBlocked() {
        stubClan(Collections.emptySet());

        Plot foreign = mock(Plot.class);
        when(foreign.getOwner()).thenReturn(UUID.randomUUID());
        when(plotManager.getPlotAt(any(World.class), anyInt(), anyInt())).thenReturn(foreign);

        PlayerMoveEvent event = move();
        guard.onPlayerMove(event);

        assertEquals(from, event.getTo());
        verify(player).sendMessage(any(String.class));
    }

    @Test
    void ownPathIsFreeToWalk() {
        stubClan(Collections.emptySet());
        stubPathBlock();
        when(plotManager.getPlotAt(any(World.class), anyInt(), anyInt())).thenReturn(null);
        when(clanManager.isOwnPath(any(UUID.class), anyInt(), anyInt())).thenReturn(true);

        PlayerMoveEvent event = move();
        guard.onPlayerMove(event);

        assertEquals(to, event.getTo());
    }

    @Test
    void foreignPathRequiresActivePass() {
        stubClan(Collections.emptySet());
        stubPathBlock();
        when(plotManager.getPlotAt(any(World.class), anyInt(), anyInt())).thenReturn(null);
        when(clanManager.isOwnPath(any(UUID.class), anyInt(), anyInt())).thenReturn(false);

        PlayerMoveEvent event = move();
        guard.onPlayerMove(event);

        assertEquals(from, event.getTo());
        verify(player).sendMessage(any(String.class));
    }

    @Test
    void foreignPathWithActivePassIsFree() {
        stubClan(Collections.emptySet());
        stubPathBlock();
        when(plotManager.getPlotAt(any(World.class), anyInt(), anyInt())).thenReturn(null);
        when(clanManager.isOwnPath(any(UUID.class), anyInt(), anyInt())).thenReturn(false);
        when(passService.isActive(playerId)).thenReturn(true);

        PlayerMoveEvent event = move();
        guard.onPlayerMove(event);

        assertEquals(to, event.getTo());
    }

    @Test
    void marketAreaRequiresActivePass() {
        stubClan(Collections.emptySet());
        when(marketStandService.isInsideMarket(any(World.class), anyInt(), anyInt())).thenReturn(true);
        when(passService.isActive(playerId)).thenReturn(false);

        PlayerMoveEvent event = move();
        guard.onPlayerMove(event);

        assertEquals(from, event.getTo());
        verify(player).sendMessage(any(String.class));
    }

    @Test
    void marketAreaWithActivePassIsFree() {
        stubClan(Collections.emptySet());
        when(marketStandService.isInsideMarket(any(World.class), anyInt(), anyInt())).thenReturn(true);
        when(passService.isActive(playerId)).thenReturn(true);

        PlayerMoveEvent event = move();
        guard.onPlayerMove(event);

        assertEquals(to, event.getTo());
    }

    @Test
    void unclaimedLandIsFreeToWalk() {
        stubClan(Collections.emptySet());
        when(plotManager.getPlotAt(any(World.class), anyInt(), anyInt())).thenReturn(null);
        when(clanManager.isOwnPath(any(UUID.class), anyInt(), anyInt())).thenReturn(false);

        PlayerMoveEvent event = move();
        guard.onPlayerMove(event);

        assertEquals(to, event.getTo());
    }
}
