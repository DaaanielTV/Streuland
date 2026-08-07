package de.streuland.clan;

import de.streuland.path.PathGenerator;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ClanPlotQuotaTest {

    private PlotManager plotManager;
    private ClanManager clanManager;

    @BeforeEach
    void setUp() {
        plotManager = Mockito.mock(PlotManager.class);
        when(plotManager.getPlotsByOwner(any(UUID.class))).thenReturn(Collections.emptyList());
        PlotStorage plotStorage = Mockito.mock(PlotStorage.class);
        when(plotStorage.getPlot(anyString())).thenReturn(null);
        when(plotManager.getStorage()).thenReturn(plotStorage);
        clanManager = new ClanManager(Mockito.mock(JavaPlugin.class), plotManager, Mockito.mock(PathGenerator.class), null);
    }

    @Test
    void clanlessPlayerHasNoClanQuota() {
        UUID player = UUID.randomUUID();

        assertEquals(0, clanManager.getClanPlotQuota(player));
        assertEquals(0, clanManager.getClanPlotCount(player));
        assertFalse(clanManager.hasReachedClanPlotQuota(player));
    }

    @Test
    void singleMemberClanGetsQuotaOfThree() {
        UUID leader = UUID.randomUUID();
        clanManager.createClan("Solo", leader);

        assertEquals(3, clanManager.getClanPlotQuota(leader));
    }

    @Test
    void threeMemberClanGetsQuotaOfFive() {
        UUID leader = UUID.randomUUID();
        Clan clan = clanManager.createClan("Trio", leader);
        clanManager.joinClan(UUID.randomUUID(), clan.getClanId());
        clanManager.joinClan(UUID.randomUUID(), clan.getClanId());

        assertEquals(5, clanManager.getClanPlotQuota(leader));
    }

    @Test
    void quotaIsReachedWhenClanOwnsQuotaPlots() {
        UUID leader = UUID.randomUUID();
        Clan clan = clanManager.createClan("Solo", leader);

        for (int i = 1; i <= 3; i++) {
            clanManager.registerPlot(leader, "plot_" + i);
        }

        assertTrue(clanManager.hasReachedClanPlotQuota(leader));
        assertEquals(3, clanManager.getClanPlotCount(leader));
        assertEquals(3, clan.getPlotIds().size());
    }

    @Test
    void registerPlotIgnoresClanlessPlayer() {
        UUID player = UUID.randomUUID();

        clanManager.registerPlot(player, "plot_1");

        assertEquals(0, clanManager.getClanPlotCount(player));
        assertNull(clanManager.getClanByPlayer(player));
    }

    @Test
    void leaderPlotsAreCountedTowardsClanQuotaOnCreation() {
        UUID leader = UUID.randomUUID();
        Plot existing = Mockito.mock(Plot.class);
        when(existing.getPlotId()).thenReturn("plot_a");
        when(plotManager.getPlotsByOwner(leader)).thenReturn(Collections.singletonList(existing));

        Clan clan = clanManager.createClan("Erbe", leader);

        assertEquals(1, clan.getPlotCount());
        assertEquals(3, clanManager.getClanPlotQuota(leader));
        assertFalse(clanManager.hasReachedClanPlotQuota(leader));
    }
}
