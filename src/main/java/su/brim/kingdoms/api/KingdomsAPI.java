package su.brim.kingdoms.api;

import su.brim.kingdoms.KingdomsAddon;
import su.brim.kingdoms.ghost.GhostState;
import su.brim.kingdoms.manager.KingdomManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Public API for KingdomsAddon plugin.
 * Other plugins can use this to get information about kingdoms and players.
 * 
 * Usage example:
 * <pre>
 * KingdomsAPI api = KingdomsAPI.getInstance();
 * if (api != null) {
 *     String kingdom = api.getPlayerKingdom(player.getUniqueId());
 *     boolean isGhost = api.isGhost(player.getUniqueId());
 * }
 * </pre>
 */
public class KingdomsAPI {
    
    private static KingdomsAPI instance;
    private final KingdomsAddon plugin;
    
    /**
     * Admin bypass permission node.
     */
    public static final String ADMIN_PERMISSION = "kingdoms.admin";
    
    /**
     * Creates the API instance.
     * Called internally by the plugin.
     */
    public KingdomsAPI(KingdomsAddon plugin) {
        this.plugin = plugin;
        instance = this;
    }
    
    /**
     * Gets the API instance.
     * @return The API instance, or null if plugin is not loaded
     */
    @Nullable
    public static KingdomsAPI getInstance() {
        return instance;
    }
    
    /**
     * Clears the API instance.
     * Called internally when plugin is disabled.
     */
    public static void clearInstance() {
        instance = null;
    }
    
    // ==================== Kingdom Information ====================
    
    /**
     * Gets the kingdom ID of a player.
     * @param playerUuid The player's UUID
     * @return Kingdom ID (e.g., "snow_kingdom"), or null if not in a kingdom
     */
    @Nullable
    public String getPlayerKingdom(@NotNull UUID playerUuid) {
        return plugin.getKingdomManager().getPlayerKingdomId(playerUuid);
    }
    
    /**
     * Gets the display name of a kingdom.
     * @param kingdomId The kingdom ID (e.g., "snow_kingdom")
     * @return Display name (e.g., "Снежное Королевство"), or the ID if not found
     */
    @NotNull
    public String getKingdomDisplayName(@NotNull String kingdomId) {
        return plugin.getConfigManager().getKingdomDisplayName(kingdomId);
    }
    
    /**
     * Gets the color of a kingdom as a hex string.
     * @param kingdomId The kingdom ID
     * @return Hex color (e.g., "#87CEEB"), or "#FFFFFF" if not found
     */
    @NotNull
    public String getKingdomColor(@NotNull String kingdomId) {
        return plugin.getConfigManager().getKingdomColor(kingdomId);
    }
    
    /**
     * Checks if a player has a kingdom assigned.
     * @param playerUuid The player's UUID
     * @return true if player has a kingdom
     */
    public boolean hasKingdom(@NotNull UUID playerUuid) {
        return plugin.getKingdomManager().hasKingdom(playerUuid);
    }
    
    /**
     * Checks if two players are in the same kingdom (allies).
     * @param player1 First player's UUID
     * @param player2 Second player's UUID
     * @return true if both players are in the same kingdom
     */
    public boolean areAllies(@NotNull UUID player1, @NotNull UUID player2) {
        return plugin.getKingdomManager().areAllies(player1, player2);
    }
    
    /**
     * Gets a list of all available kingdom IDs.
     * @return Unmodifiable list of kingdom IDs
     */
    @NotNull
    public List<String> getAllKingdoms() {
        return KingdomManager.ALL_KINGDOMS;
    }
    
    /**
     * Gets the count of online players in a specific kingdom.
     * @param kingdomId The kingdom ID
     * @return Number of online players
     */
    public int getOnlineMemberCount(@NotNull String kingdomId) {
        return plugin.getKingdomManager().getOnlineMemberCount(kingdomId);
    }
    
    /**
     * Gets all online players in a specific kingdom.
     * @param kingdomId The kingdom ID
     * @return Set of online players in this kingdom
     */
    @NotNull
    public Set<Player> getOnlineKingdomMembers(@NotNull String kingdomId) {
        Set<Player> members = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerKingdom = getPlayerKingdom(player.getUniqueId());
            if (kingdomId.equals(playerKingdom)) {
                members.add(player);
            }
        }
        return members;
    }
    
    // ==================== Ghost System ====================
    
    /**
     * Checks if a player is currently a ghost.
     * @param playerUuid The player's UUID
     * @return true if player is a ghost
     */
    public boolean isGhost(@NotNull UUID playerUuid) {
        if (plugin.getGhostManager() == null) {
            return false;
        }
        return plugin.getGhostManager().isGhost(playerUuid);
    }
    
    /**
     * Gets the remaining ghost time for a player in milliseconds.
     * @param playerUuid The player's UUID
     * @return Remaining time in ms, or -1 if not a ghost
     */
    public long getGhostRemainingTimeMs(@NotNull UUID playerUuid) {
        if (plugin.getGhostManager() == null) {
            return -1;
        }
        GhostState state = plugin.getGhostManager().getGhostState(playerUuid);
        if (state == null) {
            return -1;
        }
        return state.getRemainingTimeMs();
    }
    
    /**
     * Checks if a ghost can self-resurrect (timer expired).
     * @param playerUuid The player's UUID
     * @return true if can self-resurrect, false if not a ghost or timer not expired
     */
    public boolean canGhostSelfResurrect(@NotNull UUID playerUuid) {
        if (plugin.getGhostManager() == null) {
            return false;
        }
        GhostState state = plugin.getGhostManager().getGhostState(playerUuid);
        if (state == null) {
            return false;
        }
        return state.canSelfResurrect();
    }
    
    /**
     * Checks if the ghost system is enabled.
     * @return true if ghost system is enabled
     */
    public boolean isGhostSystemEnabled() {
        return plugin.getConfig().getBoolean("ghost-system.enabled", false);
    }
    
    /**
     * Gets all current ghost UUIDs.
     * @return Set of ghost player UUIDs
     */
    @NotNull
    public Set<UUID> getAllGhostUUIDs() {
        if (plugin.getGhostManager() == null) {
            return Collections.emptySet();
        }
        return plugin.getGhostManager().getAllGhosts().keySet();
    }
    
    // ==================== Admin Bypass ====================
    
    /**
     * Checks if a player has admin bypass permissions.
     * Admin players:
     * - Are not assigned to any kingdom
     * - Can interact with all portals/altars
     * - Do not become ghosts on death
     * - Are not affected by team damage modifiers
     * 
     * @param player The player to check
     * @return true if player has admin bypass
     */
    public boolean isAdmin(@NotNull Player player) {
        return player.hasPermission(ADMIN_PERMISSION);
    }
    
    /**
     * Checks if a player has admin bypass permissions by UUID.
     * Note: This only works for online players.
     * 
     * @param playerUuid The player's UUID
     * @return true if player is online and has admin bypass
     */
    public boolean isAdmin(@NotNull UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) {
            return false;
        }
        return isAdmin(player);
    }
    
    // ==================== Team Colors ====================
    
    /**
     * Gets the team color manager for manipulating player nametag display.
     * @return TeamColorManager instance, or null if not initialized
     */
    @Nullable
    public Object getTeamColorManager() {
        return plugin.getTeamColorManager();
    }
    
    /**
     * Forces an update of a player's nametag display above their head.
     * Note: Chat and tab colors are now handled via PlaceholderAPI.
     * @param player The player to update
     */
    public void updatePlayerDisplay(@NotNull Player player) {
        if (plugin.getTeamColorManager() != null) {
            plugin.getTeamColorManager().updatePlayer(player);
        }
    }
}
