package de.streuland.clan;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * Zugriffsschutz für das Clan-Lager: Die Lagerkiste am Hauptquartier
 * (aus den Dropschächten) darf nur von Clan-Mitgliedern geöffnet werden.
 */
public class ClanStorageChestListener implements Listener {
    private final ClanShaftService shaftService;
    private final ClanManager clanManager;

    public ClanStorageChestListener(ClanShaftService shaftService, ClanManager clanManager) {
        this.shaftService = shaftService;
        this.clanManager = clanManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) {
            return;
        }

        UUID clanId = shaftService.getClanIdForStorageChest(block);
        if (clanId == null) {
            return;
        }

        Clan clan = clanManager.getClanById(clanId);
        if (clan == null) {
            return;
        }

        Player player = event.getPlayer();
        if (clan.getMembers().contains(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage("§cDas ist das Clan-Lager von §f" + clan.getName() + "§c. Nur Mitglieder haben Zugriff!");
    }
}
