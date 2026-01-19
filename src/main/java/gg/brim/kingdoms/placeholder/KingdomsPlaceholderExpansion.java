package gg.brim.kingdoms.placeholder;

import gg.brim.kingdoms.KingdomsAddon;
import gg.brim.kingdoms.api.KingdomsAPI;
import gg.brim.kingdoms.ghost.GhostState;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * PlaceholderAPI expansion for KingdomsAddon.
 * 
 * Available placeholders:
 * - %kingdoms_color% - Kingdom color in hex format (#RRGGBB) or ghost prefix
 * - %kingdoms_color_legacy% - Kingdom color in legacy format (§x§R§R§G§G§B§B) or ghost prefix
 * - %kingdoms_kingdom% - Kingdom ID (e.g., "snow_kingdom") or empty
 * - %kingdoms_kingdom_name% - Kingdom display name (e.g., "Снежное Королевство") or empty
 * - %kingdoms_is_ghost% - "true" or "false"
 * - %kingdoms_ghost_time% - Remaining ghost time formatted (e.g., "12:34") or empty
 * - %kingdoms_ghost_prefix% - "☠ " if ghost, empty otherwise
 * - %kingdoms_is_admin% - "true" or "false"
 */
public class KingdomsPlaceholderExpansion extends PlaceholderExpansion {
    
    private final KingdomsAddon plugin;
    
    public KingdomsPlaceholderExpansion(KingdomsAddon plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public @NotNull String getIdentifier() {
        return "kingdoms";
    }
    
    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }
    
    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }
    
    @Override
    public boolean persist() {
        // Keep the expansion loaded through reloads
        return true;
    }
    
    @Override
    public boolean canRegister() {
        return true;
    }
    
    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) {
            return "";
        }
        
        UUID uuid = offlinePlayer.getUniqueId();
        Player player = offlinePlayer.getPlayer();
        
        // Handle different placeholders
        switch (params.toLowerCase()) {
            case "color":
                return getColor(player, uuid);
                
            case "color_legacy":
                return getLegacyColor(player, uuid);
                
            case "kingdom":
                return getKingdomId(uuid);
                
            case "kingdom_name":
                return getKingdomName(uuid);
                
            case "is_ghost":
                return isGhost(uuid);
                
            case "ghost_time":
                return getGhostTime(uuid);
                
            case "ghost_prefix":
                return getGhostPrefix(uuid);
                
            case "is_admin":
                return isAdmin(player);
                
            default:
                return null;
        }
    }
    
    /**
     * Gets the color for the player in hex format.
     * For ghosts, returns the ghost prefix.
     * For kingdom members, returns the hex color.
     */
    private String getColor(Player player, UUID uuid) {
        // Need online player for permission check
        if (player != null && player.hasPermission(KingdomsAPI.ADMIN_PERMISSION)) {
            return "#FFFFFF";
        }
        
        // Ghost check
//        if (plugin.getGhostManager() != null && plugin.getGhostManager().isGhost(uuid)) {
//            return plugin.getConfig().getString("team-colors.ghost-prefix", "§7§o☠ ");
//        }
        
        // Kingdom color
        String kingdomId = plugin.getKingdomManager().getPlayerKingdomId(uuid);
        if (kingdomId != null) {
            return plugin.getConfigManager().getKingdomColor(kingdomId);
        }
        
        return "";
    }
    
    /**
     * Gets the color for the player in legacy format.
     * For ghosts, returns the ghost prefix.
     * For kingdom members, returns §x§R§R§G§G§B§B format.
     */
    private String getLegacyColor(Player player, UUID uuid) {
        // Need online player for permission check
        if (player != null && player.hasPermission(KingdomsAPI.ADMIN_PERMISSION)) {
            return "§f";
        }
        
        // Ghost check
//        if (plugin.getGhostManager() != null && plugin.getGhostManager().isGhost(uuid)) {
//            return plugin.getConfig().getString("team-colors.ghost-prefix", "§7§o☠ ");
//        }
        
        // Kingdom color
        String kingdomId = plugin.getKingdomManager().getPlayerKingdomId(uuid);
        if (kingdomId != null) {
            if (plugin.getTeamColorManager() != null) {
                TextColor color = plugin.getTeamColorManager().getKingdomColor(kingdomId);
                if (color != null) {
                    return convertToLegacyColor(color);
                }
            }
            // Fallback to hex if TeamColorManager is not available
            String hex = plugin.getConfigManager().getKingdomColor(kingdomId);
            if (hex != null && hex.startsWith("#")) {
                return convertHexToLegacy(hex);
            }
        }
        
        return "";
    }
    
    /**
     * Gets the kingdom ID for the player.
     */
    private String getKingdomId(UUID uuid) {
        String kingdomId = plugin.getKingdomManager().getPlayerKingdomId(uuid);
        return kingdomId != null ? kingdomId : "";
    }
    
    /**
     * Gets the kingdom display name for the player.
     */
    private String getKingdomName(UUID uuid) {
        String kingdomId = plugin.getKingdomManager().getPlayerKingdomId(uuid);
        if (kingdomId != null) {
            return plugin.getConfigManager().getKingdomDisplayName(kingdomId);
        }
        return "";
    }
    
    /**
     * Checks if the player is a ghost.
     */
    private String isGhost(UUID uuid) {
        if (plugin.getGhostManager() != null && plugin.getGhostManager().isGhost(uuid)) {
            return "true";
        }
        return "false";
    }
    
    /**
     * Gets the ghost prefix symbol if player is a ghost.
     */
    private String getGhostPrefix(UUID uuid) {
        if (plugin.getGhostManager() != null && plugin.getGhostManager().isGhost(uuid)) {
            return "☠ ";
        }
        return "";
    }
    
    /**
     * Gets the remaining ghost time formatted as MM:SS.
     */
    private String getGhostTime(UUID uuid) {
        if (plugin.getGhostManager() == null) {
            return "";
        }
        
        GhostState state = plugin.getGhostManager().getGhostState(uuid);
        if (state == null) {
            return "";
        }
        
        long remainingMs = state.getRemainingTimeMs();
        if (remainingMs <= 0) {
            return "0:00";
        }
        
        long totalSeconds = remainingMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        
        return String.format("%d:%02d", minutes, seconds);
    }
    
    /**
     * Checks if the player is an admin.
     */
    private String isAdmin(Player player) {
        if (player != null && player.hasPermission(KingdomsAPI.ADMIN_PERMISSION)) {
            return "true";
        }
        return "false";
    }
    
    /**
     * Converts a TextColor to legacy format.
     */
    private String convertToLegacyColor(TextColor color) {
        if (color == null) {
            return "";
        }
        
        String hex = color.asHexString().substring(1); // Remove #
        StringBuilder sb = new StringBuilder("§x");
        for (char c : hex.toCharArray()) {
            sb.append("§").append(c);
        }
        return sb.toString();
    }
    
    /**
     * Converts a hex string to legacy format.
     */
    private String convertHexToLegacy(String hex) {
        if (hex == null || !hex.startsWith("#") || hex.length() != 7) {
            return "";
        }
        
        String hexValue = hex.substring(1); // Remove #
        StringBuilder sb = new StringBuilder("§x");
        for (char c : hexValue.toCharArray()) {
            sb.append("§").append(c);
        }
        return sb.toString();
    }
}
