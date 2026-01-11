package gg.brim.kingdoms.ghost.listener;

import gg.brim.kingdoms.KingdomsAddon;
import gg.brim.kingdoms.ghost.altar.Altar;
import io.papermc.paper.event.player.PlayerPurchaseEvent;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Handles interactions with resurrection altars.
 */
public class AltarInteractionListener implements Listener {
    
    private final KingdomsAddon plugin;
    
    public AltarInteractionListener(KingdomsAddon plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Handles right-click on altar interaction entity.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAltarInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;
        
        // Check if this is an altar interaction entity
        if (!plugin.getAltarManager().isAltarInteraction(interaction)) return;
        
        event.setCancelled(true);
        
        Player player = event.getPlayer();
        Altar altar = plugin.getAltarManager().getAltarByInteraction(interaction.getUniqueId());
        
        if (altar == null) {
            plugin.debug("Altar not found for interaction entity " + interaction.getUniqueId());
            return;
        }
        
        // Check if player is in the same kingdom as the altar (use fallback method)
        String playerKingdom = plugin.getKingdomManager().getPlayerKingdomId(player.getUniqueId());
        if (playerKingdom == null || !playerKingdom.equals(altar.getKingdomId())) {
            player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.altar.wrong-kingdom"));
            return;
        }
        
        // Check if player is a ghost
        if (plugin.getGhostManager().isGhost(player.getUniqueId())) {
            // Ghosts can't resurrect others, but can view the list
            player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.altar.cannot-use-as-ghost"));
            return;
        }
        
        // Open resurrection GUI
        plugin.getResurrectionGUI().openGUI(player, altar);
    }
    
    /**
     * Handles purchase event in merchant GUI (resurrection).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPurchase(PlayerPurchaseEvent event) {
        Player player = event.getPlayer();
        
        // Check if this is our resurrection GUI
        if (!plugin.getResurrectionGUI().hasOpenGUI(player.getUniqueId())) {
            return;
        }
        
        // Get the trade index
        int tradeIndex = event.getTrade().getUses(); // This is a workaround - we track by index
        
        // For Paper's virtual merchants, we need to get the index differently
        // We'll use a small trick - iterate through merchant recipes to find the index
        var merchant = player.getOpenInventory().getTopInventory();
        if (merchant.getType() != InventoryType.MERCHANT) {
            return;
        }
        
        // Get the actual index from the merchant view
        org.bukkit.inventory.MerchantInventory merchantInv = (org.bukkit.inventory.MerchantInventory) merchant;
        int selectedIndex = merchantInv.getSelectedRecipeIndex();
        
        // Cancel the default trade behavior
        event.setRewardExp(false);
        event.setIncreaseTradeUses(false);
        
        // Handle the resurrection
        boolean success = plugin.getResurrectionGUI().handleTrade(player, selectedIndex);
        
        if (!success) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Handles closing of resurrection GUI.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        
        if (plugin.getResurrectionGUI().hasOpenGUI(player.getUniqueId())) {
            plugin.getResurrectionGUI().handleClose(player);
        }
    }
}
