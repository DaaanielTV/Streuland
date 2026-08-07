package de.streuland.commands;

import de.streuland.admin.AdminPlotService;
import de.streuland.analytics.PlotAnalyticsService;
import de.streuland.command.PlotCommandExecutor;
import de.streuland.commands.PlotUpgradeCommand;
import de.streuland.district.TraderNpcService;
import de.streuland.flags.PlotFlagManager;
import de.streuland.neighborhood.NeighborhoodService;
import de.streuland.path.PathGenerator;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
import de.streuland.plot.biome.BiomeBonusService;
import de.streuland.plot.market.PlotMarketService;
import de.streuland.plot.skin.PlotSkinService;
import de.streuland.plot.snapshot.SnapshotManager;
import de.streuland.quest.QuestService;
import de.streuland.quest.QuestTracker;
import de.streuland.rules.RuleEngine;
import de.streuland.weather.SeasonalWeatherService;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PlotFirstTeleportTest {

    private static class Fixture {
        JavaPlugin plugin;
        Server server;
        BukkitScheduler scheduler;
        PlotManager plotManager;
        PlotStorage storage;
        PathGenerator pathGenerator;
        World world;
        Player player;
        Plot plot;
        Block block;
        Location location;
        UUID playerId = UUID.randomUUID();
        Command command = mock(Command.class);
        PlotCommandExecutor executor;

        Fixture(int existingPlots) {
            plugin = mock(JavaPlugin.class);
            FileConfiguration config = mock(FileConfiguration.class);
            when(config.getLong(anyString(), anyLong())).thenReturn(30L);
            when(config.getInt(anyString(), anyInt())).thenReturn(128);
            when(config.getString(anyString(), anyString())).thenReturn("");
            when(config.getString(anyString())).thenReturn("");
            when(plugin.getConfig()).thenReturn(config);
            when(plugin.getDataFolder()).thenReturn(new java.io.File(System.getProperty("java.io.tmpdir")));
            server = mock(Server.class);
            when(plugin.getServer()).thenReturn(server);
            org.bukkit.plugin.PluginManager pluginManager = mock(org.bukkit.plugin.PluginManager.class);
            when(server.getPluginManager()).thenReturn(pluginManager);
            when(pluginManager.getPlugin(anyString())).thenReturn(null);
            scheduler = mock(BukkitScheduler.class);
            when(server.getScheduler()).thenReturn(scheduler);
            when(scheduler.scheduleSyncDelayedTask(any(JavaPlugin.class), any(Runnable.class))).thenAnswer(inv -> {
                inv.getArgument(1, Runnable.class).run();
                return 1;
            });

            world = mock(World.class);
            player = mock(Player.class);
            when(player.getWorld()).thenReturn(world);
            when(player.getUniqueId()).thenReturn(playerId);
            when(player.getName()).thenReturn("Neuling");

            storage = mock(PlotStorage.class);
            when(storage.getPlayerPlots(playerId)).thenReturn(Collections.emptyList());
            plotManager = mock(PlotManager.class);
            when(plotManager.getStorage(world)).thenReturn(storage);
            when(plotManager.getMaxPlotsPerPlayer(world)).thenReturn(4);
            java.util.List<Plot> owned = existingPlots == 0
                    ? Collections.emptyList()
                    : Collections.singletonList(mock(Plot.class));
            when(plotManager.getPlotsByOwner(playerId)).thenReturn(owned);

            plot = new Plot("world_main_plot_1", 10, 20, 64, playerId, System.currentTimeMillis(), 70, Plot.PlotState.CLAIMED);
            when(plotManager.createPlotAsync(playerId, world)).thenReturn(CompletableFuture.completedFuture(plot));
            when(plotManager.getWorldForPlot("world_main_plot_1")).thenReturn(world);

            block = mock(Block.class);
            location = new Location(world, 10, 70, 20);
            when(world.getBlockAt(10, 70, 20)).thenReturn(block);
            when(block.getLocation()).thenReturn(location);
            when(player.spigot()).thenReturn(mock(Player.Spigot.class));

            pathGenerator = mock(PathGenerator.class);
            when(pathGenerator.generatePath(plot)).thenReturn(Collections.emptyList());

            executor = new PlotCommandExecutor(
                    plugin, plotManager, pathGenerator,
                    mock(SnapshotManager.class), mock(RuleEngine.class), mock(PlotSkinService.class),
                    mock(BiomeBonusService.class), mock(NeighborhoodService.class), mock(QuestService.class),
                    mock(QuestTracker.class), mock(PlotMarketService.class), mock(AdminPlotService.class),
                    mock(PlotAnalyticsService.class), mock(TraderNpcService.class), mock(SeasonalWeatherService.class),
                    mock(PlotFlagManager.class), mock(PlotUpgradeCommand.class));
        }
    }

    @Test
    void firstPlotIsAutoTeleportedAfterCreate() {
        Fixture f = new Fixture(0);

        f.executor.onCommand(f.player, f.command, "plot", new String[]{"create"});

        verify(f.player).teleport(f.location);
        verify(f.player).sendMessage(contains("erstes Grundstück"));
    }

    @Test
    void subsequentPlotIsNotAutoTeleported() {
        Fixture f = new Fixture(1);

        f.executor.onCommand(f.player, f.command, "plot", new String[]{"create"});

        verify(f.player, never()).teleport(any(Location.class));
        verify(f.player).sendMessage(contains("/plot home"));
    }

    @Test
    void noTeleportWhenCreateFails() {
        Fixture f = new Fixture(0);
        when(f.plotManager.createPlotAsync(f.playerId, f.world)).thenReturn(CompletableFuture.completedFuture(null));

        f.executor.onCommand(f.player, f.command, "plot", new String[]{"create"});

        verify(f.player, never()).teleport(any(Location.class));
    }
}
