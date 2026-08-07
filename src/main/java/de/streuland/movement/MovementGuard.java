package de.streuland.movement;

import de.streuland.clan.Clan;
import de.streuland.clan.ClanManager;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bewegungssperre (Phase 2):
 * - Clanlose und Spieler mit Permission streuland.bypass.movement sind frei.
 * - Clan-Mitglieder bewegen sich frei auf eigenen Clan-Plots und eigenen Wegen.
 * - Fremde Clan-Plots sind gesperrt (Knockback + Meldung).
 * - Fremde Wege sind nur mit aktivem Wegpass begehbar.
 * - Unbeanspruchtes Land ist frei begehbar.
 */
public class MovementGuard implements Listener {

    private static final List<Material> PATH_MATERIALS = Arrays.asList(
            Material.STONE,
            Material.GRANITE,
            Material.DIORITE,
            Material.ANDESITE,
            Material.MOSSY_COBBLESTONE,
            Material.COBBLESTONE,
            Material.GOLD_BLOCK,
            Material.YELLOW_CONCRETE,
            Material.YELLOW_WOOL,
            Material.ORANGE_CONCRETE
    );

    private static final long MESSAGE_COOLDOWN_MS = 3000L;

    private final PlotManager plotManager;
    private final ClanManager clanManager;
    private final MovementPassService passService;
    private final de.streuland.marketstand.MarketStandService marketStandService;
    private final Map<UUID, Long> lastBlockMessages = new HashMap<>();

    public MovementGuard(PlotManager plotManager, ClanManager clanManager, MovementPassService passService,
                         de.streuland.marketstand.MarketStandService marketStandService) {
        this.plotManager = plotManager;
        this.clanManager = clanManager;
        this.passService = passService;
        this.marketStandService = marketStandService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission("streuland.bypass.movement")) {
            return;
        }
        Clan clan = clanManager.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            return;
        }

        int x = event.getTo().getBlockX();
        int z = event.getTo().getBlockZ();

        if (marketStandService != null && marketStandService.isInsideMarket(event.getTo().getWorld(), x, z)) {
            if (passService != null && passService.isActive(player.getUniqueId())) {
                return;
            }
            block(event, "§cDer Marktbereich ist nur mit aktivem Wegpass zugänglich! (§e/plot wegpass§c)");
            return;
        }

        Plot plot = plotManager.getPlotAt(event.getTo().getWorld(), x, z);
        if (plot != null) {
            if (clan.getMembers().contains(plot.getOwner())) {
                return;
            }
            block(event, "§cDas ist fremdes Clan-Territorium!");
            return;
        }

        Block block = event.getTo().getBlock();
        if (block != null && PATH_MATERIALS.contains(block.getType())) {
            if (clanManager.isOwnPath(clan.getClanId(), x, z)) {
                return;
            }
            if (passService != null && passService.isActive(player.getUniqueId())) {
                return;
            }
            block(event, "§cDu benötigst einen Wegpass, um fremde Wege zu erkunden! (§e/plot wegpass§c)");
        }
    }

    private void block(PlayerMoveEvent event, String message) {
        event.setTo(event.getFrom());
        long now = System.currentTimeMillis();
        Long last = lastBlockMessages.get(event.getPlayer().getUniqueId());
        if (last == null || now - last >= MESSAGE_COOLDOWN_MS) {
            lastBlockMessages.put(event.getPlayer().getUniqueId(), now);
            event.getPlayer().sendMessage(message);
        }
    }
}
