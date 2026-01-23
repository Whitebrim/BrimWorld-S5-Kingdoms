package su.brim.kingdoms;

import su.brim.kingdoms.api.KingdomsAPI;
import su.brim.kingdoms.commands.KingdomsCommand;
import su.brim.kingdoms.config.ConfigManager;
import su.brim.kingdoms.config.ConfigUpdater;
import su.brim.kingdoms.config.MessagesConfig;
import su.brim.kingdoms.ghost.GhostManager;
import su.brim.kingdoms.ghost.ImmortalityManager;
import su.brim.kingdoms.ghost.altar.AltarManager;
import su.brim.kingdoms.ghost.gui.ResurrectionGUI;
import su.brim.kingdoms.ghost.listener.AltarInteractionListener;
import su.brim.kingdoms.ghost.listener.GhostInteractionListener;
import su.brim.kingdoms.ghost.listener.GhostVisibilityListener;
import su.brim.kingdoms.ghost.listener.ImmortalityListener;
import su.brim.kingdoms.listeners.*;
import su.brim.kingdoms.manager.KingdomManager;
import su.brim.kingdoms.manager.SpawnManager;
import su.brim.kingdoms.manager.PlayerDataManager;
import su.brim.kingdoms.team.TeamColorManager;
import su.brim.kingdoms.placeholder.KingdomsPlaceholderExpansion;
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
    private ImmortalityManager immortalityManager;
    
    // Team colors
    private TeamColorManager teamColorManager;
    
    // API
    private KingdomsAPI api;
    
    // Config updater
    private ConfigUpdater configUpdater;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Update configs with new values (before loading)
        this.configUpdater = new ConfigUpdater(this);
        configUpdater.updateAll();
        
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
        
        // Initialize team colors if enabled
        if (getConfig().getBoolean("team-colors.enabled", true)) {
            initializeTeamColors();
        }
        
        // Initialize API
        this.api = new KingdomsAPI(this);
        
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
        if (immortalityManager != null) {
            immortalityManager.saveData();
        }
        
        // Cleanup team colors
        if (teamColorManager != null) {
            teamColorManager.cleanup();
        }
        
        // Clear API instance
        KingdomsAPI.clearInstance();
        
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
        
        // Initialize immortality system if enabled
        if (getConfig().getBoolean("ghost-system.immortality.enabled", true)) {
            this.immortalityManager = new ImmortalityManager(this);
            Bukkit.getPluginManager().registerEvents(new ImmortalityListener(this), this);
            getLogger().info("Immortality system initialized!");
        }
        
        // Register ghost system listeners (death handling is in RespawnHook)
        Bukkit.getPluginManager().registerEvents(new GhostInteractionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AltarInteractionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GhostVisibilityListener(this), this);
        
        getLogger().info("Ghost system initialized!");
    }
    
    /**
     * Initializes the team color system.
     */
    private void initializeTeamColors() {
        getLogger().info("Initializing team colors...");
        
        this.teamColorManager = new TeamColorManager(this);
        
        // Register PlaceholderAPI expansion if available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new KingdomsPlaceholderExpansion(this).register();
            getLogger().info("PlaceholderAPI detected! Registered kingdoms placeholders.");
            getLogger().info("Available: %kingdoms_color%, %kingdoms_color_legacy%, %kingdoms_kingdom%, etc.");
        } else {
            getLogger().info("PlaceholderAPI not found. Placeholders will not be available.");
            getLogger().info("Install PlaceholderAPI for chat/tab color integration.");
        }
        
        getLogger().info("Team colors initialized!");
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
        // Update configs with any new values
        if (configUpdater != null) {
            configUpdater.updateAll();
        }
        
        reloadConfig();
        configManager.reload();
        messagesConfig.reload();
        playerDataManager.reload();
        kingdomManager.reload();
        
        if (ghostManager != null) {
            ghostManager.reload();
        }
        
        if (immortalityManager != null) {
            immortalityManager.reload();
        }
        
        if (teamColorManager != null) {
            teamColorManager.reload();
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
    
    public ImmortalityManager getImmortalityManager() {
        return immortalityManager;
    }
    
    public TeamColorManager getTeamColorManager() {
        return teamColorManager;
    }
    
    public KingdomsAPI getApi() {
        return api;
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
