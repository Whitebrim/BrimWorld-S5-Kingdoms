package gg.brim.kingdoms.manager;

import gg.brim.kingdoms.KingdomsAddon;
import gg.brim.kingdoms.config.MessagesConfig;
import gg.brim.kingdoms.util.FoliaUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages kingdoms and player assignments.
 * Standalone implementation without external dependencies.
 */
public class KingdomManager {
    
    private final KingdomsAddon plugin;
    
    // Kingdom IDs
    public static final String SNOW_KINGDOM = "snow_kingdom";
    public static final String FOREST_KINGDOM = "forest_kingdom";
    public static final String TROPICAL_KINGDOM = "tropical_kingdom";
    
    public static final List<String> ALL_KINGDOMS = Arrays.asList(
            SNOW_KINGDOM, FOREST_KINGDOM, TROPICAL_KINGDOM
    );
    
    // Player name -> Kingdom ID mapping (loaded from whitelist files)
    private final Map<String, String> playerWhitelist = new ConcurrentHashMap<>();
    
    // UUID -> Kingdom ID mapping (active assignments, persisted)
    private final Map<UUID, String> playerKingdoms = new ConcurrentHashMap<>();
    
    // Tracks players that have been processed this session
    private final Set<UUID> processedPlayers = ConcurrentHashMap.newKeySet();
    
    // File for persisting player-kingdom assignments
    private final File playerDataFile;
    private FileConfiguration playerDataConfig;
    
    public KingdomManager(KingdomsAddon plugin) {
        this.plugin = plugin;
        this.playerDataFile = new File(plugin.getDataFolder(), "player-kingdoms.yml");
        loadWhitelists();
        loadPlayerKingdoms();
    }
    
    /**
     * Loads player whitelists from kingdom files.
     */
    public void loadWhitelists() {
        playerWhitelist.clear();
        
        File teamsDir = new File(plugin.getDataFolder(), "teams");
        if (!teamsDir.exists()) {
            teamsDir.mkdirs();
            // Save default files
            for (String kingdom : ALL_KINGDOMS) {
                plugin.saveResource("teams/" + kingdom + ".yml", false);
            }
        }
        
        for (String kingdom : ALL_KINGDOMS) {
            File file = new File(teamsDir, kingdom + ".yml");
            if (!file.exists()) {
                plugin.saveResource("teams/" + kingdom + ".yml", false);
            }
            
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            List<String> players = config.getStringList("players");
            
            for (String playerName : players) {
                if (playerName != null && !playerName.isEmpty()) {
                    playerWhitelist.put(playerName.toLowerCase(), kingdom);
                }
            }
            
            plugin.debug("Loaded " + players.size() + " players for " + kingdom);
        }
        
        plugin.getLogger().info("Loaded " + playerWhitelist.size() + " player whitelist entries.");
    }
    
    /**
     * Loads persisted player-kingdom assignments.
     */
    private void loadPlayerKingdoms() {
        if (!playerDataFile.exists()) {
            return;
        }
        
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
        
        for (String uuidStr : playerDataConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String kingdom = playerDataConfig.getString(uuidStr);
                if (kingdom != null && ALL_KINGDOMS.contains(kingdom)) {
                    playerKingdoms.put(uuid, kingdom);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in player-kingdoms.yml: " + uuidStr);
            }
        }
        
        plugin.getLogger().info("Loaded " + playerKingdoms.size() + " player kingdom assignments.");
    }
    
    /**
     * Saves player-kingdom assignments to file.
     */
    public void savePlayerKingdoms() {
        playerDataConfig = new YamlConfiguration();
        
        for (Map.Entry<UUID, String> entry : playerKingdoms.entrySet()) {
            playerDataConfig.set(entry.getKey().toString(), entry.getValue());
        }
        
        try {
            playerDataConfig.save(playerDataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player kingdoms: " + e.getMessage());
        }
    }
    
    /**
     * Reloads kingdom data.
     */
    public void reload() {
        loadWhitelists();
    }
    
    /**
     * Finds which kingdom a player should belong to based on their name (whitelist).
     */
    @Nullable
    public String findKingdomInWhitelist(String playerName) {
        return playerWhitelist.get(playerName.toLowerCase());
    }
    
    /**
     * Gets a player's current kingdom ID.
     */
    @Nullable
    public String getPlayerKingdomId(UUID playerUuid) {
        // Check active assignments first
        String kingdom = playerKingdoms.get(playerUuid);
        if (kingdom != null) {
            return kingdom;
        }
        
        // Fallback: check whitelist by name
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            return findKingdomInWhitelist(player.getName());
        }
        
        // Check offline player name
        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
        String name = offlinePlayer.getName();
        if (name != null) {
            return findKingdomInWhitelist(name);
        }
        
        return null;
    }
    
    /**
     * Checks if two players are in the same kingdom.
     */
    public boolean areAllies(UUID player1, UUID player2) {
        String kingdom1 = getPlayerKingdomId(player1);
        String kingdom2 = getPlayerKingdomId(player2);
        
        if (kingdom1 == null || kingdom2 == null) {
            return false;
        }
        
        return kingdom1.equals(kingdom2);
    }
    
    /**
     * Checks if a player has a kingdom.
     */
    public boolean hasKingdom(UUID playerUuid) {
        return getPlayerKingdomId(playerUuid) != null;
    }
    
    /**
     * Assigns a player to a kingdom.
     */
    public boolean assignPlayerToKingdom(Player player, String kingdomId) {
        if (!ALL_KINGDOMS.contains(kingdomId)) {
            plugin.getLogger().warning("Invalid kingdom ID: " + kingdomId);
            return false;
        }
        
        UUID uuid = player.getUniqueId();
        String currentKingdom = playerKingdoms.get(uuid);
        
        if (kingdomId.equals(currentKingdom)) {
            plugin.debug("Player " + player.getName() + " already in " + kingdomId);
            return true;
        }
        
        playerKingdoms.put(uuid, kingdomId);
        savePlayerKingdoms();
        
        plugin.debug("Assigned " + player.getName() + " to " + kingdomId);
        return true;
    }
    
    /**
     * Processes a player joining the server.
     * Checks their whitelist and assigns them to the correct kingdom.
     * 
     * @param player The player
     * @return true if player was processed (has kingdom), false if not in whitelist
     */
    public boolean processPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        
        plugin.debug("=== Processing player join: " + name + " (" + uuid + ") ===");
        
        // Check if already has a kingdom assignment
        String currentKingdom = playerKingdoms.get(uuid);
        plugin.debug("Current kingdom in playerKingdoms: " + currentKingdom);
        
        if (currentKingdom != null) {
            plugin.debug("Player already assigned to " + currentKingdom);
            processedPlayers.add(uuid);
            
            // Check if first join and should teleport
            handleFirstJoinTeleport(player, currentKingdom);
            return true;
        }
        
        // Find their assigned kingdom from whitelist
        String kingdomId = findKingdomInWhitelist(name);
        plugin.debug("Kingdom from whitelist: " + kingdomId);
        
        if (kingdomId == null) {
            plugin.debug("Player " + name + " NOT found in any kingdom whitelist.");
            return false;
        }
        
        plugin.debug("Player " + name + " found in whitelist for " + kingdomId);
        
        // Assign to kingdom
        boolean success = assignPlayerToKingdom(player, kingdomId);
        if (success) {
            processedPlayers.add(uuid);
            
            // Send welcome message
            String displayName = plugin.getConfigManager().getKingdomDisplayName(kingdomId);
            player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "kingdom.joined",
                    MessagesConfig.placeholder("kingdom", displayName)
            ));
            
            // Handle first join teleport
            handleFirstJoinTeleport(player, kingdomId);
        }
        
        return success;
    }
    
    /**
     * Handles teleportation for first-time joiners.
     */
    private void handleFirstJoinTeleport(Player player, String kingdomId) {
        plugin.debug("=== handleFirstJoinTeleport for " + player.getName() + " ===");
        
        boolean teleportOnFirstJoin = plugin.getConfigManager().isTeleportOnFirstJoin();
        plugin.debug("teleport.on-first-join config: " + teleportOnFirstJoin);
        
        if (!teleportOnFirstJoin) {
            plugin.debug("First join teleport DISABLED in config");
            return;
        }
        
        UUID uuid = player.getUniqueId();
        boolean hasJoinedBefore = plugin.getPlayerDataManager().hasJoinedBefore(uuid);
        plugin.debug("Has joined before: " + hasJoinedBefore);
        
        if (hasJoinedBefore) {
            plugin.debug("Player has joined before, skipping first join teleport");
            return;
        }
        
        plugin.debug("FIRST JOIN detected for " + player.getName() + ", will teleport to " + kingdomId);
        
        // Mark as joined BEFORE teleport
        plugin.getPlayerDataManager().markAsJoined(uuid);
        plugin.debug("Marked player as joined");
        
        // Teleport to kingdom spawn
        teleportToKingdomSpawn(player, kingdomId);
    }
    
    /**
     * Teleports a player to their kingdom's spawn point.
     */
    public void teleportToKingdomSpawn(Player player, String kingdomId) {
        plugin.debug("=== teleportToKingdomSpawn for " + player.getName() + " to " + kingdomId + " ===");
        
        Location spawn = plugin.getSpawnManager().getSpawn(kingdomId);
        plugin.debug("Spawn location: " + (spawn != null ? 
                spawn.getWorld().getName() + " " + spawn.getX() + "," + spawn.getY() + "," + spawn.getZ() : "NULL"));
        
        if (spawn == null) {
            plugin.debug("No spawn set for " + kingdomId);
            plugin.getLogger().warning("Cannot teleport " + player.getName() + " - no spawn set for " + kingdomId + "!");
            player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("kingdom.no-spawn"));
            return;
        }
        
        // Schedule teleportation with delay (for nLogin compatibility)
        int delay = plugin.getConfigManager().getTeleportDelayTicks();
        plugin.debug("Scheduling teleport with delay: " + delay + " ticks");
        
        FoliaUtil.runDelayed(plugin, player, () -> {
            if (!player.isOnline()) {
                plugin.debug("Player went offline before teleport");
                return;
            }
            
            plugin.debug("Executing teleportAsync for " + player.getName());
            FoliaUtil.teleportAsync(player, spawn).thenAccept(success -> {
                plugin.debug("Teleport result: " + success);
                if (success) {
                    player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("kingdom.teleported"));
                } else {
                    plugin.getLogger().warning("Teleport failed for " + player.getName());
                }
            });
        }, delay);
    }
    
    /**
     * Checks if a player has been processed this session.
     */
    public boolean isProcessed(UUID uuid) {
        return processedPlayers.contains(uuid);
    }
    
    /**
     * Marks a player as processed.
     */
    public void markProcessed(UUID uuid) {
        processedPlayers.add(uuid);
    }
    
    /**
     * Removes a player from processed set (on quit).
     */
    public void unmarkProcessed(UUID uuid) {
        processedPlayers.remove(uuid);
    }
    
    /**
     * Adds a player to a kingdom's whitelist file.
     * Removes the player from any other kingdom first.
     */
    public boolean addPlayerToWhitelist(String playerName, String kingdomId) {
        if (!ALL_KINGDOMS.contains(kingdomId)) {
            return false;
        }
        
        // First, remove player from all other kingdoms
        removePlayerFromAllWhitelists(playerName);
        
        File file = new File(plugin.getDataFolder(), "teams/" + kingdomId + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        List<String> players = new ArrayList<>(config.getStringList("players"));
        
        // Check if already exists in target kingdom
        if (players.stream().anyMatch(p -> p.equalsIgnoreCase(playerName))) {
            // Already in target kingdom, but might have been removed from others
            return true;
        }
        
        players.add(playerName);
        config.set("players", players);
        
        try {
            config.save(file);
            playerWhitelist.put(playerName.toLowerCase(), kingdomId);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save " + kingdomId + ".yml: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Removes a player from all kingdom whitelist files.
     */
    private void removePlayerFromAllWhitelists(String playerName) {
        String lowerName = playerName.toLowerCase();
        
        for (String kingdom : ALL_KINGDOMS) {
            File file = new File(plugin.getDataFolder(), "teams/" + kingdom + ".yml");
            if (!file.exists()) continue;
            
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            List<String> players = new ArrayList<>(config.getStringList("players"));
            
            // Remove player (case-insensitive)
            boolean removed = players.removeIf(p -> p.equalsIgnoreCase(playerName));
            
            if (removed) {
                config.set("players", players);
                try {
                    config.save(file);
                    plugin.debug("Removed " + playerName + " from " + kingdom + " whitelist");
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to save " + kingdom + ".yml: " + e.getMessage());
                }
            }
        }
        
        // Remove from cache
        playerWhitelist.remove(lowerName);
    }
    
    /**
     * Gets the count of players in a kingdom's whitelist.
     */
    public int getWhitelistCount(String kingdomId) {
        return (int) playerWhitelist.values().stream()
                .filter(k -> k.equals(kingdomId))
                .count();
    }
    
    /**
     * Gets the count of online members in a kingdom.
     */
    public int getOnlineMemberCount(String kingdomId) {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            String kingdom = getPlayerKingdomId(player.getUniqueId());
            if (kingdomId.equals(kingdom)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Gets total member count for a kingdom.
     */
    public int getTotalMemberCount(String kingdomId) {
        return (int) playerKingdoms.values().stream()
                .filter(k -> k.equals(kingdomId))
                .count();
    }
}
