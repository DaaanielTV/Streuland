package de.streuland.marketstand;

import de.streuland.marketstand.MarketStandService.MarketStand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /markt mieten | /markt preis <item> <preis> | /markt info
 */
public class MarketStandCommand implements CommandExecutor {

    private final MarketStandService standService;

    public MarketStandCommand(MarketStandService standService) {
        this.standService = standService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cNur Spieler können Marktstände verwalten.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "mieten":
            case "rent":
                return handleRent(player);
            case "preis":
            case "price":
                return handlePrice(player, args);
            case "info":
                return handleInfo(player);
            default:
                sendHelp(player);
                return true;
        }
    }

    private boolean handleRent(Player player) {
        MarketStand stand = standService.rent(player);
        if (stand == null) {
            MarketStand existing = standService.getStandOf(player.getUniqueId());
            if (existing != null) {
                player.sendMessage("§eDu besitzt bereits einen Stand bei §f(" + existing.x + ", " + existing.z + ")§e.");
                return true;
            }
            player.sendMessage("§cMiete fehlgeschlagen! Der Stand kostet §f" + (int) standService.getRentPrice()
                    + " §cGuthaben und du darfst nur einen Stand besitzen.");
            return true;
        }
        player.sendMessage("§aStand gemietet! Lage: §f(" + stand.x + ", " + stand.z + ")§a.");
        player.sendMessage("§7Lege Items in die Kiste und setze Preise mit §f/markt preis <item> <preis>");
        return true;
    }

    private boolean handlePrice(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: §f/markt preis <item> <preis>§c (Preis 0 = nicht käuflich)");
            return true;
        }
        MarketStand stand = standService.getStandOf(player.getUniqueId());
        if (stand == null) {
            player.sendMessage("§cDu besitzt keinen Marktstand. Miete einen mit §f/markt mieten");
            return true;
        }
        double price;
        try {
            price = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cUngültiger Preis.");
            return true;
        }
        if (!standService.setPrice(stand, args[1], price)) {
            player.sendMessage("§cUngültiges Item oder Preis (0 – 1.000.000).");
            return true;
        }
        player.sendMessage("§aPreis gesetzt: §f" + args[1].toUpperCase() + " §afür §f" + (int) price + " §aGuthaben/Stück.");
        return true;
    }

    private boolean handleInfo(Player player) {
        MarketStand stand = standService.getStandOf(player.getUniqueId());
        if (stand == null) {
            player.sendMessage("§7Du besitzt keinen Marktstand. §e/markt mieten§7 zum Mieten (§f"
                    + (int) standService.getRentPrice() + " §7Guthaben).");
            return true;
        }
        player.sendMessage("§6=== Dein Marktstand ===");
        player.sendMessage("§eLage: §f(" + stand.x + ", " + stand.z + ")");
        if (stand.prices.isEmpty()) {
            player.sendMessage("§7Keine Preise gesetzt. §e/markt preis <item> <preis>");
        } else {
            for (java.util.Map.Entry<String, Double> entry : stand.prices.entrySet()) {
                player.sendMessage("§e" + entry.getKey() + ": §f" + (int) (double) entry.getValue() + " §7Guthaben/Stück");
            }
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== Marktstand ===");
        player.sendMessage("§e/markt mieten §7- miete einen Stand am Markt");
        player.sendMessage("§e/markt preis <item> <preis> §7- Preis für ein Item setzen");
        player.sendMessage("§e/markt info §7- deinen Stand anzeigen");
        player.sendMessage("§7Käufer: Rechtsklick auf die Kiste eines Standes.");
    }
}
