package su.brim.kingdoms.config;

import su.brim.kingdoms.KingdomsAddon;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Manages plugin configuration.
 */
public class ConfigManager {
    
    private final KingdomsAddon plugin;
    
    // Damage settings
    private double allyDamageMultiplier;
    private double enemyDamageMultiplier;
    private boolean blockTeamlessDamage;
    
    // Teleport settings
    private int teleportDelayTicks;
    private boolean teleportOnFirstJoin;
    private boolean teleportOnDeathNoRespawn;
    
    // Debug
    private boolean debug;
    
    public ConfigManager(KingdomsAddon plugin) {
        this.plugin = plugin;
        load();
    }
    
    /**
     * Loads configuration values.
     */
    public void load() {
        FileConfiguration config = plugin.getConfig();
        
        // Damage settings
        allyDamageMultiplier = config.getDouble("damage.ally-multiplier", 0.5);
        enemyDamageMultiplier = config.getDouble("damage.enemy-multiplier", 1.0);
        blockTeamlessDamage = config.getBoolean("damage.block-teamless-damage", false);
        
        // Teleport settings
        teleportDelayTicks = config.getInt("teleport.delay-ticks", 20);
        teleportOnFirstJoin = config.getBoolean("teleport.on-first-join", true);
        teleportOnDeathNoRespawn = config.getBoolean("teleport.on-death-no-respawn", true);
        
        // Debug
        debug = config.getBoolean("debug", false);
        
        plugin.debug("Config loaded: allyMultiplier=" + allyDamageMultiplier + 
                     ", enemyMultiplier=" + enemyDamageMultiplier);
    }
    
    /**
     * Reloads the configuration.
     */
    public void reload() {
        plugin.reloadConfig();
        load();
    }
    
    // === Getters ===
    
    public double getAllyDamageMultiplier() {
        return allyDamageMultiplier;
    }
    
    public double getEnemyDamageMultiplier() {
        return enemyDamageMultiplier;
    }
    
    public boolean isBlockTeamlessDamage() {
        return blockTeamlessDamage;
    }
    
    public int getTeleportDelayTicks() {
        return teleportDelayTicks;
    }
    
    public boolean isTeleportOnFirstJoin() {
        return teleportOnFirstJoin;
    }
    
    public boolean isTeleportOnDeathNoRespawn() {
        return teleportOnDeathNoRespawn;
    }
    
    public boolean isDebug() {
        return debug;
    }
    
    /**
     * Gets the display name for a kingdom from config.
     */
    public String getKingdomDisplayName(String kingdomId) {
        return plugin.getConfig().getString("kingdoms." + kingdomId + ".display-name", kingdomId);
    }
    
    /**
     * Gets the color for a kingdom from config.
     */
    public String getKingdomColor(String kingdomId) {
        return plugin.getConfig().getString("kingdoms." + kingdomId + ".color", "#FFFFFF");
    }
}
