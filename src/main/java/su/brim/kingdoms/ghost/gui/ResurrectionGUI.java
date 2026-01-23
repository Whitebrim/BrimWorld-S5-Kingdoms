package su.brim.kingdoms.ghost.gui;

import su.brim.kingdoms.KingdomsAddon;
import su.brim.kingdoms.ghost.GhostState;
import su.brim.kingdoms.ghost.altar.Altar;
import su.brim.kingdoms.config.MessagesConfig;
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
 * Also handles immortality purchase for living players.
 */
public class ResurrectionGUI {
    
    private final KingdomsAddon plugin;
    
    // Track open merchants (player UUID -> merchant)
    private final Map<UUID, Merchant> openMerchants = new ConcurrentHashMap<>();
    
    // Track which ghost each trade is for (player UUID -> (trade index -> ghost UUID))
    // Index -1 is reserved for immortality purchase
    private final Map<UUID, Map<Integer, UUID>> tradeGhostMapping = new ConcurrentHashMap<>();
    
    // Track which altar the GUI is for
    private final Map<UUID, Altar> playerAltarMapping = new ConcurrentHashMap<>();
    
    // Track which trades are immortality purchases (player UUID -> trade index)
    private final Map<UUID, Integer> immortalityTradeIndex = new ConcurrentHashMap<>();
    
    // Special marker UUID for immortality trade
    private static final UUID IMMORTALITY_MARKER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    
    public ResurrectionGUI(KingdomsAddon plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Opens the resurrection GUI for a player at an altar.
     * Shows ghosts to resurrect AND immortality purchase option if enabled.
     */
    public void openGUI(Player player, Altar altar) {
        String kingdomId = altar.getKingdomId();
        List<GhostState> ghosts = plugin.getGhostManager().getGhostsForKingdom(kingdomId);
        
        // Check if immortality system is enabled
        boolean immortalityEnabled = plugin.getImmortalityManager() != null && 
                                     plugin.getImmortalityManager().isEnabled();
        
        // If no ghosts and no immortality option, show message
        if (ghosts.isEmpty() && !immortalityEnabled) {
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
        
        // First add immortality purchase option if enabled
        if (immortalityEnabled) {
            MerchantRecipe immortalityRecipe = createImmortalityRecipe(player);
            if (immortalityRecipe != null) {
                recipes.add(immortalityRecipe);
                ghostMapping.put(index, IMMORTALITY_MARKER);
                immortalityTradeIndex.put(player.getUniqueId(), index);
                index++;
            }
        }
        
        // Then add ghost resurrection options
        for (GhostState ghost : ghosts) {
            MerchantRecipe recipe = createResurrectionRecipe(ghost);
            recipes.add(recipe);
            ghostMapping.put(index, ghost.getPlayerUuid());
            index++;
        }
        
        // If no trades available at all, show message
        if (recipes.isEmpty()) {
            player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.altar.no-ghosts"));
            return;
        }
        
        merchant.setRecipes(recipes);
        
        // Store mappings
        openMerchants.put(player.getUniqueId(), merchant);
        tradeGhostMapping.put(player.getUniqueId(), ghostMapping);
        playerAltarMapping.put(player.getUniqueId(), altar);
        
        // Open merchant GUI
        player.openMerchant(merchant, true);
        
        plugin.debug("Opened resurrection GUI for " + player.getName() + 
                    " with " + ghosts.size() + " ghosts" +
                    (immortalityEnabled ? " + immortality option" : ""));
    }
    
    /**
     * Creates a merchant recipe for purchasing immortality.
     * Returns null if player already has immortality.
     */
    private MerchantRecipe createImmortalityRecipe(Player player) {
        // Check if player already has immortality
        if (plugin.getImmortalityManager().hasImmortality(player.getUniqueId())) {
            // Show info item instead with remaining time
            return createImmortalityInfoRecipe(player);
        }
        
        // Create result item (golden apple representing immortality)
        ItemStack result = createImmortalityResultItem();
        
        // Create recipe
        MerchantRecipe recipe = new MerchantRecipe(result, 0, 1, false, 0, 0f, 0, 0, true);
        
        // Add ingredients (immortality cost)
        List<ItemStack> cost = plugin.getImmortalityManager().getCost();
        if (cost.size() >= 1) {
            recipe.addIngredient(cost.get(0));
        }
        if (cost.size() >= 2) {
            recipe.addIngredient(cost.get(1));
        }
        
        return recipe;
    }
    
    /**
     * Creates an info recipe showing current immortality status.
     */
    private MerchantRecipe createImmortalityInfoRecipe(Player player) {
        ItemStack result = new ItemStack(Material.BARRIER);
        ItemMeta meta = result.getItemMeta();
        
        meta.displayName(Component.text("⚔ Бессмертие уже активно ⚔")
                .color(NamedTextColor.YELLOW)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        
        String remainingTime = plugin.getImmortalityManager().getFormattedRemainingTime(player.getUniqueId());
        lore.add(Component.text("Осталось времени: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(remainingTime).color(NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));
        
        lore.add(Component.empty());
        lore.add(Component.text("Вы уже защищены бессмертием!")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        
        meta.lore(lore);
        result.setItemMeta(meta);
        
        // Create non-purchasable recipe (using unobtainable item as cost)
        MerchantRecipe recipe = new MerchantRecipe(result, 0, 0, false, 0, 0f, 0, 0, true);
        recipe.addIngredient(new ItemStack(Material.BARRIER, 64));
        
        return recipe;
    }
    
    /**
     * Creates the result item for immortality purchase.
     */
    private ItemStack createImmortalityResultItem() {
        ItemStack item = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE);
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(Component.text("⚔ Купить Бессмертие ⚔")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Эффект бессмертия защитит вас от смерти!")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("При смертельном уроне вы будете воскрешены.")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        
        // Duration info
        long durationMs = plugin.getImmortalityManager().getDurationMs();
        String durationText = formatDuration(durationMs);
        lore.add(Component.text("Длительность: ")
                .color(NamedTextColor.DARK_PURPLE)
                .append(Component.text(durationText).color(NamedTextColor.LIGHT_PURPLE))
                .decoration(TextDecoration.ITALIC, false));
        
        lore.add(Component.empty());
        
        // Cost display
        lore.add(Component.text("Стоимость:")
                .color(NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        
        for (ItemStack costItem : plugin.getImmortalityManager().getCost()) {
            String itemName = formatItemName(costItem.getType());
            lore.add(Component.text("  • " + costItem.getAmount() + "x " + itemName)
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
        }
        
        lore.add(Component.empty());
        lore.add(Component.text("Нажмите для покупки!")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Formats duration in milliseconds to human-readable string.
     */
    private String formatDuration(long ms) {
        long hours = ms / (1000 * 60 * 60);
        long minutes = (ms % (1000 * 60 * 60)) / (1000 * 60);
        
        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append(" ч ");
        }
        if (minutes > 0) {
            sb.append(minutes).append(" мин");
        }
        
        return sb.toString().trim();
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
        ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING);
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
     * @return true if transaction was processed successfully
     */
    public boolean handleTrade(Player player, int tradeIndex, org.bukkit.inventory.MerchantInventory merchantInv) {
        UUID playerUuid = player.getUniqueId();
        
        Map<Integer, UUID> ghostMapping = tradeGhostMapping.get(playerUuid);
        if (ghostMapping == null) return false;
        
        UUID targetUuid = ghostMapping.get(tradeIndex);
        if (targetUuid == null) return false;
        
        // Check if this is an immortality purchase
        if (targetUuid.equals(IMMORTALITY_MARKER)) {
            return handleImmortalityPurchase(player, merchantInv);
        }
        
        // Otherwise, it's a ghost resurrection
        return handleGhostResurrection(player, targetUuid, merchantInv);
    }
    
    /**
     * Handles immortality purchase.
     */
    private boolean handleImmortalityPurchase(Player player, org.bukkit.inventory.MerchantInventory merchantInv) {
        // Check if immortality system is enabled
        if (plugin.getImmortalityManager() == null || !plugin.getImmortalityManager().isEnabled()) {
            player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.immortality.disabled"));
            return false;
        }
        
        // Check if player already has immortality
        if (plugin.getImmortalityManager().hasImmortality(player.getUniqueId())) {
            String remainingTime = plugin.getImmortalityManager().getFormattedRemainingTime(player.getUniqueId());
            player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "ghost.immortality.already-have",
                    MessagesConfig.placeholder("time", remainingTime)
            ));
            return false;
        }
        
        // Check if merchant trade slots have required items
        List<ItemStack> cost = plugin.getImmortalityManager().getCost();
        if (!hasRequiredItemsInMerchant(merchantInv, cost)) {
            player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.immortality.not-enough-items"));
            return false;
        }
        
        // Consume items from merchant trade slots
        consumeItemsFromMerchant(merchantInv, cost);
        
        // Grant immortality
        boolean success = plugin.getImmortalityManager().grantImmortality(player);
        
        if (!success) {
            // Something went wrong - refund items? For now just log
            plugin.getLogger().warning("Failed to grant immortality to " + player.getName());
            return false;
        }
        
        plugin.debug("Player " + player.getName() + " purchased immortality");
        return true;
    }
    
    /**
     * Handles ghost resurrection.
     */
    private boolean handleGhostResurrection(Player player, UUID ghostUuid, org.bukkit.inventory.MerchantInventory merchantInv) {
        Altar altar = playerAltarMapping.get(player.getUniqueId());
        if (altar == null) return false;
        
        GhostState ghost = plugin.getGhostManager().getGhostState(ghostUuid);
        if (ghost == null) {
            player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.altar.ghost-not-found"));
            return false;
        }
        
        // Check if merchant trade slots have required items (slots 0 and 1)
        List<ItemStack> cost = ghost.getResurrectionCost();
        if (!hasRequiredItemsInMerchant(merchantInv, cost)) {
            player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.altar.not-enough-items"));
            return false;
        }
        
        // Consume items from merchant trade slots only (not from player inventory!)
        consumeItemsFromMerchant(merchantInv, cost);
        
        // Determine resurrection location
        String locationType = plugin.getConfig().getString("ghost-system.buyback-location", "altar");
        org.bukkit.Location resLoc;
        
        if (locationType.equalsIgnoreCase("altar")) {
            resLoc = altar.getLocation().clone().add(0, 1, 0);
        } else {
            // Use safe location getter that doesn't rely on getRespawnLocation()
            // which can fail in Folia when ghost is in different region
            resLoc = plugin.getGhostManager().getResurrectionLocationSafe(ghost);
            if (resLoc == null) {
                // Fallback to altar location
                resLoc = altar.getLocation().clone().add(0, 1, 0);
            }
        }
        
        // Perform resurrection
        boolean success = plugin.getGhostManager().resurrect(ghostUuid, resLoc, player.getUniqueId());
        
        if (success) {
            player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "ghost.altar.resurrected",
                    MessagesConfig.placeholder("player", ghost.getPlayerName())
            ));
            
            // Notify other clan members
            broadcastResurrection(ghost, player);
        }
        
        return success;
    }
    
    /**
     * Checks if merchant trade slots have all required items.
     */
    private boolean hasRequiredItemsInMerchant(org.bukkit.inventory.MerchantInventory merchantInv, List<ItemStack> cost) {
        // Merchant trade slots: 0 = first input, 1 = second input
        for (int i = 0; i < cost.size() && i < 2; i++) {
            ItemStack required = cost.get(i);
            ItemStack inSlot = merchantInv.getItem(i);
            
            if (inSlot == null || inSlot.getType() != required.getType() || inSlot.getAmount() < required.getAmount()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Consumes required items from merchant trade slots.
     */
    private void consumeItemsFromMerchant(org.bukkit.inventory.MerchantInventory merchantInv, List<ItemStack> cost) {
        // Merchant trade slots: 0 = first input, 1 = second input
        for (int i = 0; i < cost.size() && i < 2; i++) {
            ItemStack required = cost.get(i);
            ItemStack inSlot = merchantInv.getItem(i);
            
            if (inSlot != null) {
                int newAmount = inSlot.getAmount() - required.getAmount();
                if (newAmount <= 0) {
                    merchantInv.setItem(i, null);
                } else {
                    inSlot.setAmount(newAmount);
                }
            }
        }
    }
    
    /**
     * Checks if player has all required items (kept for backward compatibility).
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
                su.brim.kingdoms.config.MessagesConfig.placeholders()
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
        immortalityTradeIndex.remove(uuid);
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
