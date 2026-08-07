package de.streuland.clan;

import de.streuland.path.PathGenerator;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClanWarTest {

    private PlotManager plotManager;
    private PlotStorage plotStorage;
    private ClanManager clanManager;
    private ClanDiplomacyManager diplomacyManager;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        FileConfiguration config = Mockito.mock(FileConfiguration.class);
        when(config.getLong(anyString(), anyLong())).thenReturn(24L);
        when(config.getDouble(anyString(), anyDouble())).thenReturn(500D);
        when(plugin.getConfig()).thenReturn(config);

        plotStorage = Mockito.mock(PlotStorage.class);
        when(plotStorage.getPlot(anyString())).thenReturn(null);
        plotManager = Mockito.mock(PlotManager.class);
        when(plotManager.getStorage()).thenReturn(plotStorage);
        when(plotManager.getPlotsByOwner(any(UUID.class))).thenReturn(Collections.emptyList());

        clanManager = new ClanManager(plugin, plotManager, Mockito.mock(PathGenerator.class), null);
        diplomacyManager = clanManager.getDiplomacyManager();
    }

    private Clan createClanWithMembers(String name, int memberCount) {
        Clan clan = clanManager.createClan(name, UUID.randomUUID());
        for (int i = 1; i < memberCount; i++) {
            clanManager.joinClan(UUID.randomUUID(), clan.getClanId());
        }
        return clan;
    }

    private Plot mockPlot(String plotId, UUID owner) {
        Plot plot = Mockito.mock(Plot.class);
        when(plot.getPlotId()).thenReturn(plotId);
        when(plot.getOwner()).thenReturn(owner);
        when(plotStorage.getPlot(plotId)).thenReturn(plot);
        return plot;
    }

    private ClanDiplomacyManager.ActiveWar declareWarAndAccept(Clan attacker, Clan defender, String plotId) {
        assertTrue(clanManager.declareWar(attacker.getClanId(), defender.getClanId(), plotId));
        ClanDiplomacyManager.DiplomaticProposal proposal = diplomacyManager.getPendingProposals(defender.getClanId()).stream()
                .filter(p -> p.getType() == ClanDiplomacyManager.ProposalType.WAR_DECLARATION)
                .findFirst()
                .orElseThrow();
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            assertTrue(diplomacyManager.acceptProposal(proposal.getProposalId(), defender.getClanId()));
        }
        return diplomacyManager.getActiveWar(attacker.getClanId());
    }

    @Test
    void declareWarRejectsPlotNotOwnedByTargetClan() {
        Clan attacker = createClanWithMembers("Angreifer", 3);
        Clan defender = createClanWithMembers("Verteidiger", 1);
        mockPlot("P_FREMDE", UUID.randomUUID());

        assertFalse(clanManager.declareWar(attacker.getClanId(), defender.getClanId(), "P_FREMDE"));
    }

    @Test
    void declareWarRejectsUnknownOrEmptyPlot() {
        Clan attacker = createClanWithMembers("Angreifer", 3);
        Clan defender = createClanWithMembers("Verteidiger", 1);

        assertFalse(clanManager.declareWar(attacker.getClanId(), defender.getClanId(), "P_GIBTS_NICHT"));
        assertFalse(clanManager.declareWar(attacker.getClanId(), defender.getClanId(), ""));
        assertFalse(clanManager.declareWar(attacker.getClanId(), defender.getClanId(), null));
    }

    @Test
    void declareWarRequiresThreeMembers() {
        Clan attacker = createClanWithMembers("ZuKlein", 2);
        Clan defender = createClanWithMembers("Verteidiger", 1);
        mockPlot("P1", defender.getLeader());

        assertFalse(clanManager.declareWar(attacker.getClanId(), defender.getClanId(), "P1"));
    }

    @Test
    void declareWarSucceedsWithValidTargetAndPlot() {
        Clan attacker = createClanWithMembers("Angreifer", 3);
        Clan defender = createClanWithMembers("Verteidiger", 1);
        mockPlot("P1", defender.getLeader());

        assertTrue(clanManager.declareWar(attacker.getClanId(), defender.getClanId(), "P1"));
        ClanDiplomacyManager.DiplomaticProposal proposal = diplomacyManager.getPendingProposals(defender.getClanId()).stream()
                .filter(p -> p.getType() == ClanDiplomacyManager.ProposalType.WAR_DECLARATION)
                .findFirst()
                .orElseThrow();
        assertEquals("P1", proposal.getTargetPlotId());
    }

    @Test
    void endWarTransfersTargetPlotToWinningAttacker() {
        Clan attacker = createClanWithMembers("Angreifer", 3);
        Clan defender = createClanWithMembers("Verteidiger", 1);
        Plot target = mockPlot("P1", defender.getLeader());
        defender.addPlot("P1");
        ClanDiplomacyManager.ActiveWar war = declareWarAndAccept(attacker, defender, "P1");

        war.addKill(UUID.randomUUID(), true);
        war.addKill(UUID.randomUUID(), true);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            diplomacyManager.endWar(war);
        }

        verify(plotManager).transferPlotOwnership("P1", defender.getLeader(), attacker.getLeader());
        assertFalse(defender.getPlotIds().contains("P1"));
        assertTrue(attacker.getPlotIds().contains("P1"));
        assertEquals(DiplomacyStatus.NEUTRAL, attacker.getRelationship(defender.getClanId()));
        assertEquals(DiplomacyStatus.NEUTRAL, defender.getRelationship(attacker.getClanId()));
        assertNull(diplomacyManager.getActiveWar(attacker.getClanId()));
    }

    @Test
    void endWarWithTieKeepsPlotWithDefender() {
        Clan attacker = createClanWithMembers("Angreifer", 3);
        Clan defender = createClanWithMembers("Verteidiger", 1);
        mockPlot("P1", defender.getLeader());
        defender.addPlot("P1");
        ClanDiplomacyManager.ActiveWar war = declareWarAndAccept(attacker, defender, "P1");

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            diplomacyManager.endWar(war);
        }

        verify(plotManager).transferPlotOwnership("P1", defender.getLeader(), defender.getLeader());
        assertTrue(defender.getPlotIds().contains("P1"));
        assertFalse(attacker.getPlotIds().contains("P1"));
    }

    @Test
    void endWarWithDefenderAdvantageKeepsPlot() {
        Clan attacker = createClanWithMembers("Angreifer", 3);
        Clan defender = createClanWithMembers("Verteidiger", 1);
        mockPlot("P1", defender.getLeader());
        defender.addPlot("P1");
        ClanDiplomacyManager.ActiveWar war = declareWarAndAccept(attacker, defender, "P1");

        war.addKill(UUID.randomUUID(), false);
        war.addKill(UUID.randomUUID(), false);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            diplomacyManager.endWar(war);
        }

        verify(plotManager).transferPlotOwnership("P1", defender.getLeader(), defender.getLeader());
        assertTrue(defender.getPlotIds().contains("P1"));
        assertFalse(attacker.getPlotIds().contains("P1"));
    }

    @Test
    void activeWarExpiryCheckUsesConfiguredDuration() {
        assertFalse(new ClanDiplomacyManager.ActiveWar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                System.currentTimeMillis() - 100_000L, 1_000L).isExpired(System.currentTimeMillis() - 99_000L));
        assertTrue(new ClanDiplomacyManager.ActiveWar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                System.currentTimeMillis() - 100_000L, 1_000L).isExpired(System.currentTimeMillis()));
    }

    @Test
    void killInActiveWarCountsForAttackerSide() {
        Clan attacker = createClanWithMembers("Angreifer", 3);
        Clan defender = createClanWithMembers("Verteidiger", 1);
        mockPlot("P1", defender.getLeader());
        declareWarAndAccept(attacker, defender, "P1");

        UUID killerId = attacker.getMembers().iterator().next();
        UUID victimId = defender.getMembers().iterator().next();
        Player killer = Mockito.mock(Player.class);
        Player victim = Mockito.mock(Player.class);
        when(killer.getUniqueId()).thenReturn(killerId);
        when(victim.getUniqueId()).thenReturn(victimId);
        when(victim.getType()).thenReturn(EntityType.PLAYER);
        when(victim.getKiller()).thenReturn(killer);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            new ClanWarListener(clanManager).onEntityDeath(new EntityDeathEvent(victim, Collections.emptyList()));
        }

        ClanDiplomacyManager.ActiveWar war = diplomacyManager.getActiveWar(attacker.getClanId());
        assertEquals(1, war.getTotalAttackerKills());
        assertEquals(0, war.getTotalDefenderKills());
        assertEquals(1, attacker.getKills());
    }

    @Test
    void killInActiveWarCountsForDefenderSide() {
        Clan attacker = createClanWithMembers("Angreifer", 3);
        Clan defender = createClanWithMembers("Verteidiger", 1);
        mockPlot("P1", defender.getLeader());
        declareWarAndAccept(attacker, defender, "P1");

        UUID killerId = defender.getMembers().iterator().next();
        UUID victimId = attacker.getMembers().iterator().next();
        Player killer = Mockito.mock(Player.class);
        Player victim = Mockito.mock(Player.class);
        when(killer.getUniqueId()).thenReturn(killerId);
        when(victim.getUniqueId()).thenReturn(victimId);
        when(victim.getType()).thenReturn(EntityType.PLAYER);
        when(victim.getKiller()).thenReturn(killer);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            new ClanWarListener(clanManager).onEntityDeath(new EntityDeathEvent(victim, Collections.emptyList()));
        }

        ClanDiplomacyManager.ActiveWar war = diplomacyManager.getActiveWar(attacker.getClanId());
        assertEquals(1, war.getTotalDefenderKills());
        assertEquals(0, war.getTotalAttackerKills());
        assertEquals(1, defender.getKills());
    }

    @Test
    void killWithoutActiveWarIsNotCounted() {
        Clan attacker = createClanWithMembers("Angreifer", 3);
        Clan defender = createClanWithMembers("Verteidiger", 1);

        UUID killerId = attacker.getMembers().iterator().next();
        UUID victimId = defender.getMembers().iterator().next();
        Player killer = Mockito.mock(Player.class);
        Player victim = Mockito.mock(Player.class);
        when(killer.getUniqueId()).thenReturn(killerId);
        when(victim.getUniqueId()).thenReturn(victimId);
        when(victim.getType()).thenReturn(EntityType.PLAYER);
        when(victim.getKiller()).thenReturn(killer);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            new ClanWarListener(clanManager).onEntityDeath(new EntityDeathEvent(victim, Collections.emptyList()));
        }

        assertEquals(0, attacker.getKills());
        assertEquals(0, defender.getKills());
    }

    @Test
    void sameClanKillIsNotCountedInWar() {
        Clan attacker = createClanWithMembers("Angreifer", 3);
        Clan defender = createClanWithMembers("Verteidiger", 1);
        mockPlot("P1", defender.getLeader());
        declareWarAndAccept(attacker, defender, "P1");

        UUID killerId = attacker.getMembers().iterator().next();
        Player killer = Mockito.mock(Player.class);
        Player victim = Mockito.mock(Player.class);
        when(killer.getUniqueId()).thenReturn(killerId);
        when(victim.getUniqueId()).thenReturn(killerId);
        when(victim.getType()).thenReturn(EntityType.PLAYER);
        when(victim.getKiller()).thenReturn(killer);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            new ClanWarListener(clanManager).onEntityDeath(new EntityDeathEvent(victim, Collections.emptyList()));
        }

        assertEquals(0, diplomacyManager.getActiveWar(attacker.getClanId()).getTotalAttackerKills());
        assertEquals(0, attacker.getKills());
    }

    @Test
    void clanlessKillerKillIsNotCounted() {
        Clan defender = createClanWithMembers("Verteidiger", 1);
        mockPlot("P1", defender.getLeader());
        Clan attacker = createClanWithMembers("Angreifer", 3);
        declareWarAndAccept(attacker, defender, "P1");

        Player killer = Mockito.mock(Player.class);
        Player victim = Mockito.mock(Player.class);
        when(killer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(victim.getUniqueId()).thenReturn(defender.getMembers().iterator().next());
        when(victim.getType()).thenReturn(EntityType.PLAYER);
        when(victim.getKiller()).thenReturn(killer);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            new ClanWarListener(clanManager).onEntityDeath(new EntityDeathEvent(victim, Collections.emptyList()));
        }

        assertEquals(0, diplomacyManager.getActiveWar(attacker.getClanId()).getTotalDefenderKills());
    }

    private net.milkbowl.vault.economy.Economy installEconomy(double balance) {
        net.milkbowl.vault.economy.Economy economy = Mockito.mock(net.milkbowl.vault.economy.Economy.class);
        when(economy.getBalance(Mockito.nullable(org.bukkit.OfflinePlayer.class))).thenReturn(balance);
        when(economy.withdrawPlayer(Mockito.nullable(org.bukkit.OfflinePlayer.class), Mockito.anyDouble()))
                .thenReturn(new net.milkbowl.vault.economy.EconomyResponse(500, 0,
                        net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS, null));
        when(economy.depositPlayer(Mockito.nullable(org.bukkit.OfflinePlayer.class), Mockito.anyDouble()))
                .thenReturn(new net.milkbowl.vault.economy.EconomyResponse(500, 0,
                        net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS, null));
        clanManager.setEconomyHook(new de.streuland.economy.PlotEconomyHook(economy));
        return economy;
    }

    @Test
    void warRewardTransfersFeesToWinnerLeader() {
        Clan attacker = createClanWithMembers("Angreifer", 3);
        Clan defender = createClanWithMembers("Verteidiger", 2);
        mockPlot("P1", defender.getLeader());
        ClanDiplomacyManager.ActiveWar war = declareWarAndAccept(attacker, defender, "P1");
        war.addKill(UUID.randomUUID(), true);
        net.milkbowl.vault.economy.Economy economy = installEconomy(1000D);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            diplomacyManager.endWar(war);
        }

        Mockito.verify(economy, Mockito.times(2)).withdrawPlayer(Mockito.nullable(org.bukkit.OfflinePlayer.class), Mockito.eq(500D));
        Mockito.verify(economy).depositPlayer(Mockito.nullable(org.bukkit.OfflinePlayer.class), Mockito.eq(1000D));
    }

    @Test
    void warRewardPaysOnlyAvailableBalance() {
        Clan attacker = createClanWithMembers("Angreifer", 3);
        Clan defender = createClanWithMembers("Verteidiger", 1);
        mockPlot("P1", defender.getLeader());
        ClanDiplomacyManager.ActiveWar war = declareWarAndAccept(attacker, defender, "P1");
        war.addKill(UUID.randomUUID(), true);
        net.milkbowl.vault.economy.Economy economy = installEconomy(200D);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            diplomacyManager.endWar(war);
        }

        Mockito.verify(economy).withdrawPlayer(Mockito.nullable(org.bukkit.OfflinePlayer.class), Mockito.eq(200D));
        Mockito.verify(economy).depositPlayer(Mockito.nullable(org.bukkit.OfflinePlayer.class), Mockito.eq(200D));
    }

    @Test
    void warEndsWithoutRewardWhenNoEconomy() {
        Clan attacker = createClanWithMembers("Angreifer", 3);
        Clan defender = createClanWithMembers("Verteidiger", 1);
        mockPlot("P1", defender.getLeader());
        ClanDiplomacyManager.ActiveWar war = declareWarAndAccept(attacker, defender, "P1");

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            diplomacyManager.endWar(war);
        }

        assertNull(diplomacyManager.getActiveWar(attacker.getClanId()));
    }
}

