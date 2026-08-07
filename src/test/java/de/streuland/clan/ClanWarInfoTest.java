package de.streuland.clan;

import de.streuland.path.PathGenerator;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClanWarInfoTest {

    private ClanManager clanManager;
    private ClanDiplomacyManager diplomacyManager;
    private PlotStorage plotStorage;
    private Player player;
    private ClanCommand command;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(config.getLong(anyString(), anyLong())).thenReturn(24L);
        when(config.getDouble(anyString(), anyDouble())).thenReturn(500D);
        when(plugin.getConfig()).thenReturn(config);

        plotStorage = mock(PlotStorage.class);
        when(plotStorage.getPlot(anyString())).thenReturn(null);
        PlotManager plotManager = mock(PlotManager.class);
        when(plotManager.getStorage()).thenReturn(plotStorage);
        when(plotManager.getPlotsByOwner(any(UUID.class))).thenReturn(Collections.emptyList());

        clanManager = new ClanManager(plugin, plotManager, mock(PathGenerator.class), null);
        diplomacyManager = clanManager.getDiplomacyManager();
        command = new ClanCommand(clanManager);

        playerId = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
    }

    private Clan createClanWithMembers(String name, int memberCount) {
        Clan clan = clanManager.createClan(name, UUID.randomUUID());
        for (int i = 1; i < memberCount; i++) {
            clanManager.joinClan(UUID.randomUUID(), clan.getClanId());
        }
        return clan;
    }

    private void startWar(Clan attacker, Clan defender, String plotId) {
        Plot plot = mock(Plot.class);
        when(plot.getPlotId()).thenReturn(plotId);
        when(plot.getOwner()).thenReturn(defender.getLeader());
        when(plotStorage.getPlot(plotId)).thenReturn(plot);
        assertTrue(clanManager.declareWar(attacker.getClanId(), defender.getClanId(), plotId));
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            ClanDiplomacyManager.DiplomaticProposal proposal = diplomacyManager.getPendingProposals(defender.getClanId()).stream()
                    .filter(p -> p.getType() == ClanDiplomacyManager.ProposalType.WAR_DECLARATION)
                    .findFirst()
                    .orElseThrow();
            assertTrue(diplomacyManager.acceptProposal(proposal.getProposalId(), defender.getClanId()));
        }
    }

    @Test
    void warinfoShowsTargetPlotAndKillStand() {
        Clan attacker = createClanWithMembers("Angreifer", 3);
        Clan defender = createClanWithMembers("Verteidiger", 1);
        clanManager.joinClan(playerId, attacker.getClanId());
        startWar(attacker, defender, "P1");
        diplomacyManager.getActiveWar(attacker.getClanId()).addKill(playerId, true);

        command.onCommand(player, mock(org.bukkit.command.Command.class), "clan", new String[]{"warinfo"});

        verify(player).sendMessage(contains("Ziel-Plot:"));
        verify(player).sendMessage(contains("Kills:"));
        verify(player).sendMessage(contains("Angreifer"));
        verify(player).sendMessage(contains("1 "));
    }

    @Test
    void warinfoWithoutWarShowsInfo() {
        clanManager.createClan("Friedlich", playerId);

        command.onCommand(player, mock(org.bukkit.command.Command.class), "clan", new String[]{"warinfo"});

        verify(player).sendMessage(contains("keinem Krieg"));
    }

    @Test
    void warinfoWithoutClanShowsError() {
        command.onCommand(player, mock(org.bukkit.command.Command.class), "clan", new String[]{"warinfo"});

        verify(player).sendMessage(contains("keinem Clan"));
    }

    @Test
    void warinfoRemainingTimeIsFormatted() {
        Clan attacker = createClanWithMembers("Angreifer", 3);
        Clan defender = createClanWithMembers("Verteidiger", 1);
        clanManager.joinClan(playerId, attacker.getClanId());
        startWar(attacker, defender, "P1");

        command.onCommand(player, mock(org.bukkit.command.Command.class), "clan", new String[]{"warinfo"});

        verify(player).sendMessage(contains("Restzeit:"));
        assertNotNull(diplomacyManager.getActiveWar(attacker.getClanId()));
    }
}
