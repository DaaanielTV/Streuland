package de.streuland.marketstand;

import de.streuland.marketstand.MarketStandService.MarketStand;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Käufer-GUI für Marktstände: Rechtsklick auf die Kiste öffnet eine
 * gespiegelte Shop-Ansicht. Klick auf ein Item kauft (Shift = ganzer Stack).
 * Der Stand-Owner öffnet die Kiste normal.
 */
public class MarketStandListener implements Listener {

    private static final int GUI_SIZE = 27;

    private final MarketStandService standService;
    private final Map<UUID, MarketStand> openGuis = new HashMap<>();

    public MarketStandListener(MarketStandService standService) {
        this.standService = standService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!(event.getClickedBlock().getState() instanceof Chest)) {
            return;
        }
        Player player = event.getPlayer();
        MarketStand stand = standService.getStandAt(
                event.getClickedBlock().getX(), event.getClickedBlock().getY(), event.getClickedBlock().getZ());
        if (stand == null) {
            return;
        }
        if (stand.owner.equals(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        openBuyGui(player, stand);
    }

    private void openBuyGui(Player player, MarketStand stand) {
        Chest chest = standService.getStandChest(stand);
        if (chest == null) {
            player.sendMessage("§cDer Stand ist beschädigt.");
            return;
        }
        String ownerName = Bukkit.getOfflinePlayer(stand.owner).getName();
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, "§6Marktstand von " + (ownerName == null ? "?" : ownerName));
        ItemStack[] contents = chest.getBlockInventory().getContents().clone();
        for (int i = 0; i < contents.length && i < GUI_SIZE; i++) {
            if (contents[i] != null) {
                gui.setItem(i, contents[i].clone());
            }
        }
        openGuis.put(player.getUniqueId(), stand);
        player.openInventory(gui);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        MarketStand stand = openGuis.get(event.getWhoClicked().getUniqueId());
        if (stand == null) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        Player buyer = (Player) event.getWhoClicked();
        double price = standService.getPrice(stand, item.getType());
        if (price <= 0) {
            buyer.sendMessage("§cDieses Item ist hier nicht käuflich.");
            return;
        }
        int amount = event.isShiftClick() ? item.getAmount() : 1;
        if (standService.purchase(stand, buyer, item.getType(), amount)) {
            buyer.sendMessage("§aGekauft: §f" + amount + "x " + item.getType().name()
                    + " §afür §f" + (int) (price * amount) + "§a Guthaben.");
            refreshGui(buyer, stand);
        } else {
            buyer.sendMessage("§cKauf fehlgeschlagen (genug Guthaben/Vorrat?).");
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        openGuis.remove(event.getPlayer().getUniqueId());
    }

    private void refreshGui(Player buyer, MarketStand stand) {
        Chest chest = standService.getStandChest(stand);
        if (chest == null) {
            return;
        }
        Inventory gui = buyer.getOpenInventory().getTopInventory();
        ItemStack[] contents = chest.getBlockInventory().getContents().clone();
        for (int i = 0; i < contents.length && i < GUI_SIZE; i++) {
            gui.setItem(i, contents[i] == null ? null : contents[i].clone());
        }
    }
}
