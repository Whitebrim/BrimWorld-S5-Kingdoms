package gg.brim.kingdoms.team;

import gg.brim.kingdoms.KingdomsAddon;
import gg.brim.kingdoms.api.KingdomsAPI;
import gg.brim.kingdoms.manager.KingdomManager;
import gg.brim.kingdoms.util.FoliaUtil;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages team colors for players - nametags above head only.
 * Uses TAB plugin API for nametag colors (Folia doesn't support Scoreboard Teams).
 * Chat and tab colors should be handled via PlaceholderAPI placeholders.
 */
public class TeamColorManager {
    
    private final KingdomsAddon plugin;
    
    // Cache for kingdom colors parsed to TextColor
    private final Map<String, TextColor> kingdomColors = new HashMap<>();
    
    // TAB plugin integration
    private boolean tabPluginPresent = false;
    
    public TeamColorManager(KingdomsAddon plugin) {
        this.plugin = plugin;
        
        // Check for TAB plugin
        tabPluginPresent = Bukkit.getPluginManager().getPlugin("TAB") != null;
        if (tabPluginPresent) {
            plugin.getLogger().info("TAB plugin detected! Using TAB API for nametag colors above head.");
        } else {
            plugin.getLogger().info("TAB plugin not found. Nametag colors will not be applied.");
            plugin.getLogger().info("Install TAB plugin for colored nametags above player heads.");
        }
        
        // Load kingdom colors
        loadKingdomColors();
        
        // Update all online players after a delay
        FoliaUtil.runGlobalDelayed(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updatePlayer(player);
            }
        }, 20L);
    }
    
    /**
     * Loads kingdom colors from config.
     */
    private void loadKingdomColors() {
        kingdomColors.clear();
        
        for (String kingdomId : KingdomManager.ALL_KINGDOMS) {
            String colorHex = plugin.getConfigManager().getKingdomColor(kingdomId);
            TextColor color = TextColor.fromHexString(colorHex);
            if (color == null) {
                color = NamedTextColor.WHITE;
            }
            kingdomColors.put(kingdomId, color);
        }
    }
    
    /**
     * Updates a player's nametag above head.
     * @param player The player to update
     */
    public void updatePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Check if admin - admins don't get team colors
        if (player.hasPermission(KingdomsAPI.ADMIN_PERMISSION)) {
            plugin.debug("Player " + player.getName() + " is admin, no nametag color applied");
            clearNametagFormatting(player);
            return;
        }
        
        // Check if ghost
        if (plugin.getGhostManager() != null && plugin.getGhostManager().isGhost(uuid)) {
            updateNametagForGhost(player);
            return;
        }
        
        // Get kingdom
        String kingdomId = plugin.getKingdomManager().getPlayerKingdomId(uuid);
        if (kingdomId == null) {
            plugin.debug("Player " + player.getName() + " has no kingdom, no nametag color applied");
            clearNametagFormatting(player);
            return;
        }
        
        // Update nametag for kingdom
        updateNametagForKingdom(player, kingdomId);
    }
    
    /**
     * Clears nametag formatting for a player.
     */
    private void clearNametagFormatting(Player player) {
        if (!tabPluginPresent) return;
        
        try {
            me.neznamy.tab.api.TabAPI api = me.neznamy.tab.api.TabAPI.getInstance();
            if (api != null) {
                me.neznamy.tab.api.TabPlayer tabPlayer = api.getPlayer(player.getUniqueId());
                if (tabPlayer != null) {
                    var nameTagManager = api.getNameTagManager();
                    
                    if (nameTagManager != null) {
                        nameTagManager.setPrefix(tabPlayer, null);
                        nameTagManager.setSuffix(tabPlayer, null);
                    }
                }
            }
        } catch (NoClassDefFoundError | Exception e) {
            // TAB API not available or error - ignore
            plugin.debug("TAB API error: " + e.getMessage());
        }
    }
    
    /**
     * Updates nametag for a ghost player.
     */
    private void updateNametagForGhost(Player player) {
        if (!tabPluginPresent) return;
        
        try {
            me.neznamy.tab.api.TabAPI api = me.neznamy.tab.api.TabAPI.getInstance();
            if (api != null) {
                me.neznamy.tab.api.TabPlayer tabPlayer = api.getPlayer(player.getUniqueId());
                if (tabPlayer != null) {
                    // Ghost formatting - gray italic with skull symbol
                    String ghostPrefix = plugin.getConfig().getString("team-colors.ghost-prefix", "§7§o☠ ");
                    
                    var nameTagManager = api.getNameTagManager();
                    
                    if (nameTagManager != null) {
                        nameTagManager.setPrefix(tabPlayer, ghostPrefix);
                        nameTagManager.setSuffix(tabPlayer, "");
                    }
                    
                    plugin.debug("Set ghost nametag for " + player.getName());
                }
            }
        } catch (NoClassDefFoundError | Exception e) {
            plugin.debug("TAB API error: " + e.getMessage());
        }
    }
    
    /**
     * Updates nametag for a kingdom player.
     */
    private void updateNametagForKingdom(Player player, String kingdomId) {
        if (!tabPluginPresent) return;
        
        try {
            me.neznamy.tab.api.TabAPI api = me.neznamy.tab.api.TabAPI.getInstance();
            if (api != null) {
                me.neznamy.tab.api.TabPlayer tabPlayer = api.getPlayer(player.getUniqueId());
                if (tabPlayer != null) {
                    // Get kingdom color as hex
                    String colorHex = plugin.getConfigManager().getKingdomColor(kingdomId);
                    
                    var nameTagManager = api.getNameTagManager();
                    
                    if (nameTagManager != null) {
                        nameTagManager.setPrefix(tabPlayer, colorHex);
                        nameTagManager.setSuffix(tabPlayer, "");
                    }
                    
                    plugin.debug("Set kingdom nametag for " + player.getName() + " (" + kingdomId + ")");
                }
            }
        } catch (NoClassDefFoundError | Exception e) {
            plugin.debug("TAB API error: " + e.getMessage());
        }
    }
    
    /**
     * Gets the color prefix for a player (for PlaceholderAPI).
     * Returns hex color format or ghost prefix.
     * 
     * @param player The player
     * @return Color prefix string (hex like "#87CEEB" or ghost prefix)
     */
    public String getColorPrefix(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Admin - no color
        if (player.hasPermission(KingdomsAPI.ADMIN_PERMISSION)) {
            return "";
        }
        
        // Ghost
        if (plugin.getGhostManager() != null && plugin.getGhostManager().isGhost(uuid)) {
            return plugin.getConfig().getString("team-colors.ghost-prefix", "§7§o☠ ");
        }
        
        // Kingdom
        String kingdomId = plugin.getKingdomManager().getPlayerKingdomId(uuid);
        if (kingdomId != null) {
            return plugin.getConfigManager().getKingdomColor(kingdomId);
        }
        
        return "";
    }
    
    /**
     * Gets the legacy color code string for a player.
     * @param player The player
     * @return Legacy color code string (e.g., "§a" or "§x§8§7§C§E§E§B")
     */
    public String getLegacyColorPrefix(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Admin - no color
        if (player.hasPermission(KingdomsAPI.ADMIN_PERMISSION)) {
            return "";
        }
        
        // Ghost
        if (plugin.getGhostManager() != null && plugin.getGhostManager().isGhost(uuid)) {
            return plugin.getConfig().getString("team-colors.ghost-prefix", "§7§o☠ ");
        }
        
        // Kingdom
        String kingdomId = plugin.getKingdomManager().getPlayerKingdomId(uuid);
        if (kingdomId != null) {
            TextColor color = kingdomColors.get(kingdomId);
            if (color != null) {
                return convertToLegacyColor(color);
            }
        }
        
        return "";
    }
    
    /**
     * Converts a TextColor to legacy Minecraft color format.
     */
    private String convertToLegacyColor(TextColor color) {
        if (color == null) {
            return "";
        }
        
        // Convert hex color to §x§R§R§G§G§B§B format
        String hex = color.asHexString().substring(1); // Remove #
        StringBuilder sb = new StringBuilder("§x");
        for (char c : hex.toCharArray()) {
            sb.append("§").append(c);
        }
        return sb.toString();
    }
    
    /**
     * Reloads configuration and updates all players.
     */
    public void reload() {
        loadKingdomColors();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }
    
    /**
     * Cleans up when plugin is disabled.
     */
    public void cleanup() {
        // Clear nametag formatting for all players
        if (tabPluginPresent) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                clearNametagFormatting(player);
            }
        }
    }
    
    /**
     * Gets the TextColor for a kingdom.
     * @param kingdomId The kingdom ID
     * @return TextColor, or null if kingdom not found
     */
    public TextColor getKingdomColor(String kingdomId) {
        return kingdomColors.get(kingdomId);
    }
    
    /**
     * Checks if TAB plugin is present and can be used.
     * @return true if TAB is available
     */
    public boolean isTabPluginPresent() {
        return tabPluginPresent;
    }
}
