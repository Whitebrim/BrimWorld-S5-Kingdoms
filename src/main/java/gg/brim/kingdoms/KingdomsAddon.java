package gg.brim.kingdoms;

import gg.brim.kingdoms.commands.KingdomsCommand;
import gg.brim.kingdoms.config.ConfigManager;
import gg.brim.kingdoms.config.MessagesConfig;
import gg.brim.kingdoms.ghost.GhostManager;
import gg.brim.kingdoms.ghost.altar.AltarManager;
import gg.brim.kingdoms.ghost.gui.ResurrectionGUI;
import gg.brim.kingdoms.ghost.listener.AltarInteractionListener;
import gg.brim.kingdoms.ghost.listener.GhostInteractionListener;
import gg.brim.kingdoms.ghost.listener.GhostVisibilityListener;
import gg.brim.kingdoms.listeners.*;
import gg.brim.kingdoms.manager.KingdomManager;
import gg.brim.kingdoms.manager.SpawnManager;
import gg.brim.kingdoms.manager.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for KingdomsAddon.
 * Standalone kingdoms management with Folia support.
 */
public class KingdomsAddon extends JavaPlugin {
    
    private static KingdomsAddon instance;
    
    private ConfigManager configManager;
    private MessagesConfig messagesConfig;
    private KingdomManager kingdomManager;
    private SpawnManager spawnManager;
    private PlayerDataManager playerDataManager;
    
    // Ghost system
    private GhostManager ghostManager;
    private AltarManager altarManager;
    private ResurrectionGUI resurrectionGUI;
    private RespawnHook respawnHook;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize configuration
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.messagesConfig = new MessagesConfig(this);
        
        // Initialize managers
        this.spawnManager = new SpawnManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.kingdomManager = new KingdomManager(this);
        
        // Initialize ghost system if enabled
        if (getConfig().getBoolean("ghost-system.enabled", false)) {
            initializeGhostSystem();
        }
        
        // Register listeners
        registerListeners();
        
        // Register commands
        registerCommands();
        
        getLogger().info(messagesConfig.getMessage("plugin.enabled"));
    }
    
    @Override
    public void onDisable() {
        // Save all data
        if (spawnManager != null) {
            spawnManager.saveSpawns();
        }
        if (playerDataManager != null) {
            playerDataManager.saveData();
        }
        if (kingdomManager != null) {
            kingdomManager.savePlayerKingdoms();
        }
        if (ghostManager != null) {
            ghostManager.saveGhostData();
        }
        if (altarManager != null) {
            altarManager.saveAltars();
        }
        
        if (messagesConfig != null) {
            getLogger().info(messagesConfig.getMessage("plugin.disabled"));
        }
        instance = null;
    }
    
    /**
     * Initializes the ghost system components.
     */
    private void initializeGhostSystem() {
        getLogger().info("Initializing ghost system...");
        
        this.altarManager = new AltarManager(this);
        this.ghostManager = new GhostManager(this);
        this.resurrectionGUI = new ResurrectionGUI(this);
        
        // Register ghost system listeners (death handling is in RespawnHook)
        Bukkit.getPluginManager().registerEvents(new GhostInteractionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AltarInteractionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GhostVisibilityListener(this), this);
        
        getLogger().info("Ghost system initialized!");
    }
    
    /**
     * Registers all event listeners.
     */
    private void registerListeners() {
        // Always register damage listener
        Bukkit.getPluginManager().registerEvents(new DamageListener(this), this);
        getLogger().info("Registered DamageListener");
        
        // Always register respawn listener
        PlayerRespawnListener respawnListener = new PlayerRespawnListener(this);
        Bukkit.getPluginManager().registerEvents(respawnListener, this);
        getLogger().info("Registered PlayerRespawnListener");

        this.respawnHook = new RespawnHook(this);
        Bukkit.getPluginManager().registerEvents(respawnHook, this);

        // Register join listener
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getLogger().info("Registered PlayerJoinListener");
    }
    
    /**
     * Registers all commands.
     */
    private void registerCommands() {
        KingdomsCommand command = new KingdomsCommand(this);
        getCommand("kingdoms").setExecutor(command);
        getCommand("kingdoms").setTabCompleter(command);
    }
    
    /**
     * Reloads all plugin configuration.
     */
    public void reload() {
        reloadConfig();
        configManager.reload();
        messagesConfig.reload();
        playerDataManager.reload();
        kingdomManager.reload();
        
        if (ghostManager != null) {
            ghostManager.reload();
        }
    }
    
    // === Getters ===
    
    public static KingdomsAddon getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public MessagesConfig getMessagesConfig() {
        return messagesConfig;
    }
    
    public KingdomManager getKingdomManager() {
        return kingdomManager;
    }
    
    public SpawnManager getSpawnManager() {
        return spawnManager;
    }
    
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }
    
    public GhostManager getGhostManager() {
        return ghostManager;
    }
    
    public AltarManager getAltarManager() {
        return altarManager;
    }
    
    public ResurrectionGUI getResurrectionGUI() {
        return resurrectionGUI;
    }
    
    public RespawnHook getRespawnHook() {
        return respawnHook;
    }
    
    /**
     * Logs a debug message if debug mode is enabled.
     */
    public void debug(String message) {
        if (configManager != null && configManager.isDebug()) {
            getLogger().info("[DEBUG] " + message);
        }
    }
}
