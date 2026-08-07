package de.streuland.clan;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.UUID;

/**
 * Zählt Kills zwischen verfeindeten Clans im aktiven Chunk-Krieg.
 * Jeder Kill des Angreifers oder Verteidigers an der Gegenseite
 * bringt dem eigenen Clan einen Kill-Punkt.
 */
public class ClanWarListener implements Listener {
    private final ClanManager clanManager;

    public ClanWarListener(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }

        Clan killerClan = clanManager.getClanByPlayer(killer.getUniqueId());
        Clan victimClan = clanManager.getClanByPlayer(victim.getUniqueId());
        if (killerClan == null || victimClan == null || killerClan.getClanId().equals(victimClan.getClanId())) {
            return;
        }

        ClanDiplomacyManager diplomacyManager = clanManager.getDiplomacyManager();
        ClanDiplomacyManager.ActiveWar war = diplomacyManager.getActiveWar(killerClan.getClanId());
        if (war == null) {
            return;
        }

        boolean isAttacker = war.getAttackerClanId().equals(killerClan.getClanId());
        UUID enemySide = isAttacker ? war.getDefenderClanId() : war.getAttackerClanId();
        if (!enemySide.equals(victimClan.getClanId())) {
            return;
        }

        war.addKill(killer.getUniqueId(), isAttacker);
        killerClan.addKill();

        int attackerKills = war.getTotalAttackerKills();
        int defenderKills = war.getTotalDefenderKills();
        Bukkit.broadcastMessage("§4[Clan-Krieg] §c" + killer.getName() + " §4erzielt einen Kill für §c" + killerClan.getName()
                + " §4(§c" + attackerKills + " §4: §c" + defenderKills + "§4)");
    }
}
