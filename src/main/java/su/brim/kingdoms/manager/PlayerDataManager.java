package su.brim.kingdoms.manager;

import su.brim.kingdoms.KingdomsAddon;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages persistent player data such as first join status.
 */
public class PlayerDataManager {
    
    private final KingdomsAddon plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    
    // Players who have joined before (for first-join teleport)
    private final Set<UUID> joinedPlayers = new HashSet<>();
    
    public PlayerDataManager(KingdomsAddon plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        load();
    }
    
    /**
     * Loads player data from file.
     */
    public void load() {
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create playerdata.yml: " + e.getMessage());
            }
        }
        
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        joinedPlayers.clear();
        
        List<String> uuidStrings = dataConfig.getStringList("joined-players");
        for (String uuidStr : uuidStrings) {
            try {
                joinedPlayers.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in playerdata.yml: " + uuidStr);
            }
        }
        
        plugin.debug("Loaded " + joinedPlayers.size() + " joined players.");
    }
    
    /**
     * Saves player data to file.
     */
    public void saveData() {
        List<String> uuidStrings = joinedPlayers.stream()
                .map(UUID::toString)
                .collect(Collectors.toList());
        
        dataConfig.set("joined-players", uuidStrings);
        
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save playerdata.yml: " + e.getMessage());
        }
    }
    
    /**
     * Reloads player data.
     */
    public void reload() {
        load();
    }
    
    /**
     * Checks if a player has joined before.
     */
    public boolean hasJoinedBefore(UUID uuid) {
        return joinedPlayers.contains(uuid);
    }
    
    /**
     * Marks a player as having joined.
     */
    public void markAsJoined(UUID uuid) {
        joinedPlayers.add(uuid);
        // Save immediately to prevent data loss
        saveData();
    }
    
    /**
     * Resets a player's first join status (for testing/admin purposes).
     */
    public void resetFirstJoin(UUID uuid) {
        joinedPlayers.remove(uuid);
        saveData();
    }
}
