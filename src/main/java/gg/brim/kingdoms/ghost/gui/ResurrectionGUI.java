package gg.brim.kingdoms.ghost.gui;

import gg.brim.kingdoms.KingdomsAddon;
import gg.brim.kingdoms.ghost.GhostState;
import gg.brim.kingdoms.ghost.altar.Altar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the resurrection merchant GUI for altars.
 */
public class ResurrectionGUI {
    
    private final KingdomsAddon plugin;
    
    // Track open merchants (player UUID -> merchant)
    private final Map<UUID, Merchant> openMerchants = new ConcurrentHashMap<>();
    
    // Track which ghost each trade is for (player UUID -> (trade index -> ghost UUID))
    private final Map<UUID, Map<Integer, UUID>> tradeGhostMapping = new ConcurrentHashMap<>();
    
    // Track which altar the GUI is for
    private final Map<UUID, Altar> playerAltarMapping = new ConcurrentHashMap<>();
    
    public ResurrectionGUI(KingdomsAddon plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Opens the resurrection GUI for a player at an altar.
     */
    public void openGUI(Player player, Altar altar) {
        String kingdomId = altar.getKingdomId();
        List<GhostState> ghosts = plugin.getGhostManager().getGhostsForKingdom(kingdomId);
        
        if (ghosts.isEmpty()) {
            player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.altar.no-ghosts"));
            return;
        }
        
        // Create merchant title
        String kingdomName = plugin.getConfigManager().getKingdomDisplayName(kingdomId);
        Component title = Component.text("☠ Алтарь Воскрешения ☠")
                .color(NamedTextColor.DARK_PURPLE)
                .decorate(TextDecoration.BOLD);
        
        Merchant merchant = Bukkit.createMerchant(title);
        
        // Create trades for each ghost
        List<MerchantRecipe> recipes = new ArrayList<>();
        Map<Integer, UUID> ghostMapping = new HashMap<>();
        
        int index = 0;
        for (GhostState ghost : ghosts) {
            MerchantRecipe recipe = createResurrectionRecipe(ghost);
            recipes.add(recipe);
            ghostMapping.put(index, ghost.getPlayerUuid());
            index++;
        }
        
        merchant.setRecipes(recipes);
        
        // Store mappings
        openMerchants.put(player.getUniqueId(), merchant);
        tradeGhostMapping.put(player.getUniqueId(), ghostMapping);
        playerAltarMapping.put(player.getUniqueId(), altar);
        
        // Open merchant GUI
        player.openMerchant(merchant, true);
        
        plugin.debug("Opened resurrection GUI for " + player.getName() + " with " + ghosts.size() + " ghosts");
    }
    
    /**
     * Creates a merchant recipe for resurrecting a ghost.
     */
    private MerchantRecipe createResurrectionRecipe(GhostState ghost) {
        // Create result item (book with ghost info)
        ItemStack result = createResurrectionResultItem(ghost);
        
        // Create recipe
        // Parameters: result, uses, maxUses, experienceReward, villagerXP, priceMultiplier, demand, specialPrice, ignoreDiscounts
        MerchantRecipe recipe = new MerchantRecipe(result, 0, 1, false, 0, 0f, 0, 0, true);
        
        // Add ingredients (resurrection cost)
        List<ItemStack> cost = ghost.getResurrectionCost();
        if (cost.size() >= 1) {
            recipe.addIngredient(cost.get(0));
        }
        if (cost.size() >= 2) {
            recipe.addIngredient(cost.get(1));
        }
        
        return recipe;
    }
    
    /**
     * Creates the result item showing ghost information.
     */
    private ItemStack createResurrectionResultItem(GhostState ghost) {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        
        // Title with ghost name
        meta.displayName(Component.text("✦ Воскресить: " + ghost.getPlayerName() + " ✦")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        
        // Lore with information
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Игрок: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(ghost.getPlayerName()).color(NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Оставшееся время: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(ghost.getFormattedRemainingTime()).color(NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        
        // Add cost display
        lore.add(Component.text("Стоимость воскрешения:")
                .color(NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        
        for (ItemStack costItem : ghost.getResurrectionCost()) {
            String itemName = formatItemName(costItem.getType());
            lore.add(Component.text("  • " + costItem.getAmount() + "x " + itemName)
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
        }
        
        lore.add(Component.empty());
        lore.add(Component.text("Нажмите для воскрешения!")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Handles a trade completion event.
     * 
     * @param player The player who made the trade
     * @param tradeIndex The index of the trade
     * @param merchantInv The merchant inventory to consume items from
     * @return true if resurrection was processed
     */
    public boolean handleTrade(Player player, int tradeIndex, org.bukkit.inventory.MerchantInventory merchantInv) {
        UUID playerUuid = player.getUniqueId();
        
        Map<Integer, UUID> ghostMapping = tradeGhostMapping.get(playerUuid);
        if (ghostMapping == null) return false;
        
        UUID ghostUuid = ghostMapping.get(tradeIndex);
        if (ghostUuid == null) return false;
        
        Altar altar = playerAltarMapping.get(playerUuid);
        if (altar == null) return false;
        
        GhostState ghost = plugin.getGhostManager().getGhostState(ghostUuid);
        if (ghost == null) {
            player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.altar.ghost-not-found"));
            return false;
        }
        
        // Check if player has required items
        List<ItemStack> cost = ghost.getResurrectionCost();
        if (!hasRequiredItems(player, cost)) {
            player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.altar.not-enough-items"));
            return false;
        }
        
        // Consume items from player inventory
        consumeItems(player, cost);
        
        // Clear the merchant input slots (so items don't stay there)
        merchantInv.setItem(0, null);
        merchantInv.setItem(1, null);
        
        // Determine resurrection location
        String locationType = plugin.getConfig().getString("ghost-system.buyback-location", "altar");
        org.bukkit.Location resLoc;
        
        if (locationType.equalsIgnoreCase("altar")) {
            resLoc = altar.getLocation().clone().add(0.5, 1, 0.5);
        } else {
            Player ghostPlayer = Bukkit.getPlayer(ghostUuid);
            if (ghostPlayer != null) {
                resLoc = plugin.getGhostManager().getResurrectionLocation(ghostPlayer, ghost, locationType);
            } else {
                resLoc = altar.getLocation().clone().add(0.5, 1, 0.5);
            }
        }
        
        // Perform resurrection
        boolean success = plugin.getGhostManager().resurrect(ghostUuid, resLoc, playerUuid);
        
        if (success) {
            player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "ghost.altar.resurrected",
                    gg.brim.kingdoms.config.MessagesConfig.placeholder("player", ghost.getPlayerName())
            ));
            
            // Close inventory
            player.closeInventory();
            
            // Notify other clan members
            broadcastResurrection(ghost, player);
        }
        
        return success;
    }
    
    /**
     * Checks if player has all required items.
     */
    private boolean hasRequiredItems(Player player, List<ItemStack> cost) {
        for (ItemStack required : cost) {
            if (!player.getInventory().containsAtLeast(required, required.getAmount())) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Consumes required items from player inventory.
     */
    private void consumeItems(Player player, List<ItemStack> cost) {
        for (ItemStack required : cost) {
            int remaining = required.getAmount();
            ItemStack[] contents = player.getInventory().getContents();
            
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack item = contents[i];
                if (item != null && item.getType() == required.getType()) {
                    int take = Math.min(remaining, item.getAmount());
                    item.setAmount(item.getAmount() - take);
                    remaining -= take;
                    
                    if (item.getAmount() <= 0) {
                        player.getInventory().setItem(i, null);
                    }
                }
            }
        }
        player.updateInventory();
    }
    
    /**
     * Broadcasts a resurrection message to clan members.
     */
    private void broadcastResurrection(GhostState ghost, Player resurrector) {
        String kingdomId = ghost.getKingdomId();
        Component message = plugin.getMessagesConfig().getComponentWithPrefix(
                "ghost.altar.broadcast-resurrected",
                gg.brim.kingdoms.config.MessagesConfig.placeholders()
                        .add("ghost", ghost.getPlayerName())
                        .add("resurrector", resurrector.getName())
                        .build()
        );
        
        // Send to all online clan members
        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerKingdom = plugin.getKingdomManager().getPlayerKingdomId(player.getUniqueId());
            if (playerKingdom != null && playerKingdom.equals(kingdomId)) {
                player.sendMessage(message);
            }
        }
    }
    
    /**
     * Cleans up when a player closes the merchant GUI.
     */
    public void handleClose(Player player) {
        UUID uuid = player.getUniqueId();
        openMerchants.remove(uuid);
        tradeGhostMapping.remove(uuid);
        playerAltarMapping.remove(uuid);
    }
    
    /**
     * Checks if a player has an open resurrection GUI.
     */
    public boolean hasOpenGUI(UUID playerUuid) {
        return openMerchants.containsKey(playerUuid);
    }
    
    /**
     * Gets the altar associated with a player's open GUI.
     */
    public Altar getOpenAltar(UUID playerUuid) {
        return playerAltarMapping.get(playerUuid);
    }
    
    /**
     * Formats a material name for display.
     */
    private String formatItemName(Material material) {
        // Simple formatting - replace underscores and capitalize
        String name = material.name().toLowerCase().replace('_', ' ');
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        
        for (char c : name.toCharArray()) {
            if (c == ' ') {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
}
