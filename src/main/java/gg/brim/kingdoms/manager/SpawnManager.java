package gg.brim.kingdoms.manager;

import gg.brim.kingdoms.KingdomsAddon;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages spawn points for each kingdom.
 */
public class SpawnManager {
    
    private final KingdomsAddon plugin;
    private final File spawnsFile;
    private FileConfiguration spawnsConfig;
    
    // Kingdom ID -> Spawn Location
    private final Map<String, Location> spawns = new HashMap<>();
    
    public SpawnManager(KingdomsAddon plugin) {
        this.plugin = plugin;
        this.spawnsFile = new File(plugin.getDataFolder(), "spawns.yml");
        load();
    }
    
    /**
     * Loads spawn points from file.
     */
    public void load() {
        if (!spawnsFile.exists()) {
            try {
                spawnsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create spawns.yml: " + e.getMessage());
            }
        }
        
        spawnsConfig = YamlConfiguration.loadConfiguration(spawnsFile);
        spawns.clear();
        
        for (String kingdomId : KingdomManager.ALL_KINGDOMS) {
            if (spawnsConfig.contains(kingdomId)) {
                Location loc = loadLocation(kingdomId);
                if (loc != null) {
                    spawns.put(kingdomId, loc);
                    plugin.debug("Loaded spawn for " + kingdomId + ": " + formatLocation(loc));
                }
            }
        }
        
        plugin.getLogger().info("Loaded " + spawns.size() + " kingdom spawn points.");
    }
    
    /**
     * Saves spawn points to file.
     */
    public void saveSpawns() {
        for (Map.Entry<String, Location> entry : spawns.entrySet()) {
            saveLocation(entry.getKey(), entry.getValue());
        }
        
        try {
            spawnsConfig.save(spawnsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save spawns.yml: " + e.getMessage());
        }
    }
    
    /**
     * Gets the spawn location for a kingdom.
     */
    @Nullable
    public Location getSpawn(String kingdomId) {
        return spawns.get(kingdomId);
    }
    
    /**
     * Sets the spawn location for a kingdom.
     */
    public void setSpawn(String kingdomId, Location location) {
        spawns.put(kingdomId, location.clone());
        saveLocation(kingdomId, location);
        
        try {
            spawnsConfig.save(spawnsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save spawn for " + kingdomId + ": " + e.getMessage());
        }
        
        plugin.debug("Set spawn for " + kingdomId + ": " + formatLocation(location));
    }
    
    /**
     * Checks if a kingdom has a spawn set.
     */
    public boolean hasSpawn(String kingdomId) {
        return spawns.containsKey(kingdomId);
    }
    
    /**
     * Removes the spawn point for a kingdom.
     */
    public void removeSpawn(String kingdomId) {
        spawns.remove(kingdomId);
        spawnsConfig.set(kingdomId, null);
        
        try {
            spawnsConfig.save(spawnsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to remove spawn for " + kingdomId + ": " + e.getMessage());
        }
    }
    
    /**
     * Loads a location from config.
     */
    @Nullable
    private Location loadLocation(String path) {
        String worldName = spawnsConfig.getString(path + ".world");
        if (worldName == null) return null;
        
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("World '" + worldName + "' not found for spawn " + path);
            return null;
        }
        
        double x = spawnsConfig.getDouble(path + ".x");
        double y = spawnsConfig.getDouble(path + ".y");
        double z = spawnsConfig.getDouble(path + ".z");
        float yaw = (float) spawnsConfig.getDouble(path + ".yaw", 0);
        float pitch = (float) spawnsConfig.getDouble(path + ".pitch", 0);
        
        return new Location(world, x, y, z, yaw, pitch);
    }
    
    /**
     * Saves a location to config.
     */
    private void saveLocation(String path, Location location) {
        spawnsConfig.set(path + ".world", location.getWorld().getName());
        spawnsConfig.set(path + ".x", location.getX());
        spawnsConfig.set(path + ".y", location.getY());
        spawnsConfig.set(path + ".z", location.getZ());
        spawnsConfig.set(path + ".yaw", location.getYaw());
        spawnsConfig.set(path + ".pitch", location.getPitch());
    }
    
    /**
     * Formats a location for display.
     */
    public static String formatLocation(Location loc) {
        if (loc == null) return "Not set";
        return String.format("%s: %.1f, %.1f, %.1f (%.1f, %.1f)",
                loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch());
    }
}
