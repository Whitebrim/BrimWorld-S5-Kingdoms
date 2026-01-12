package gg.brim.kingdoms.ghost;

import gg.brim.kingdoms.KingdomsAddon;
import gg.brim.kingdoms.config.MessagesConfig;
import gg.brim.kingdoms.ghost.altar.Altar;
import gg.brim.kingdoms.util.FoliaUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages ghost states and resurrection mechanics.
 */
public class GhostManager {
    
    private final KingdomsAddon plugin;
    private final File ghostDataFile;
    private FileConfiguration ghostDataConfig;
    
    // Active ghosts (UUID -> GhostState)
    private final Map<UUID, GhostState> ghosts = new ConcurrentHashMap<>();
    
    // Duration in milliseconds
    private long ghostDurationMs;
    
    public GhostManager(KingdomsAddon plugin) {
        this.plugin = plugin;
        this.ghostDataFile = new File(plugin.getDataFolder(), "ghostdata.yml");
        
        loadConfig();
        loadGhostData();
        startSelfResurrectChecker();
    }
    
    /**
     * Loads ghost-related config values.
     */
    private void loadConfig() {
        int minutes = plugin.getConfig().getInt("ghost-system.duration-minutes", 30);
        this.ghostDurationMs = minutes * 60L * 1000L;
    }
    
    /**
     * Starts a periodic checker for automatic self-resurrection.
     */
    private void startSelfResurrectChecker() {
        // Check every 10 seconds for ghosts that can self-resurrect
        FoliaUtil.runGlobalRepeating(plugin, () -> {
            for (GhostState ghost : ghosts.values()) {
                if (ghost.canSelfResurrect()) {
                    Player player = Bukkit.getPlayer(ghost.getPlayerUuid());
                    if (player != null && player.isOnline()) {
                        // Auto-resurrect!
                        performAutoResurrect(player, ghost);
                    }
                }
            }
        }, 20 * 10, 20 * 10); // Every 10 seconds
    }
    
    /**
     * Performs automatic resurrection when time expires.
     */
    private void performAutoResurrect(Player player, GhostState state) {
        plugin.debug("Auto-resurrecting " + player.getName() + " (time expired)");
        
        // Determine resurrection location (bed -> kingdom spawn, never world spawn)
        Location location = getResurrectionLocation(player, state, "bed");
        
        performResurrection(player, location, null);
        player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.auto-resurrected"));
    }
    
    /**
     * Checks if a player should be auto-resurrected on join.
     * Called from GhostVisibilityListener on player join.
     */
    public void checkAutoResurrectOnJoin(Player player) {
        GhostState state = ghosts.get(player.getUniqueId());
        if (state == null) return;
        
        if (state.canSelfResurrect()) {
            // Schedule auto-resurrect after a short delay
            FoliaUtil.runDelayed(plugin, player, () -> {
                if (player.isOnline() && isGhost(player.getUniqueId())) {
                    performAutoResurrect(player, state);
                }
            }, 40L); // 2 seconds delay
        }
    }
    
    /**
     * Makes a player become a ghost.
     */
    public void makeGhost(Player player, String kingdomId) {
        makeGhost(player, kingdomId, player.getLocation());
    }
    
    /**
     * Makes a player become a ghost at specified death location.
     */
    public void makeGhost(Player player, String kingdomId, Location deathLocation) {
        UUID uuid = player.getUniqueId();
        
        // Generate resurrection cost
        List<ItemStack> cost = generateResurrectionCost();
        
        // Use provided death location or current location as fallback
        Location actualDeathLoc = deathLocation != null ? deathLocation : player.getLocation();
        
        // Create ghost state with configurable duration
        GhostState state = new GhostState(
                uuid,
                player.getName(),
                kingdomId,
                System.currentTimeMillis(),
                ghostDurationMs,
                cost,
                actualDeathLoc
        );
        
        ghosts.put(uuid, state);
        
        // Apply ghost effects
        applyGhostEffects(player);
        
        // Hide from living players, show to other ghosts
        updateVisibilityForGhost(player);
        
        // Save data
        saveGhostData();
        
        // Notify player
        player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.became-ghost"));
        
        plugin.debug("Player " + player.getName() + " became a ghost");
    }
    
    /**
     * Applies ghost mode effects to a player.
     */
    public void applyGhostEffects(Player player) {
        // Set adventure mode
        player.setGameMode(GameMode.ADVENTURE);
        
        // Enable flight
        player.setAllowFlight(true);
        player.setFlying(true);
        
        // Add invisibility effect (infinite duration)
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY,
                PotionEffect.INFINITE_DURATION,
                0,
                false,  // ambient
                false,  // particles
                false   // icon
        ));
        
        // Enable glowing (white glow since Folia doesn't support scoreboard teams)
        player.setGlowing(true);
        
        // Visual feedback - particles around player
        spawnGhostParticles(player);
    }
    
    /**
     * Removes ghost mode effects from a player.
     */
    public void removeGhostEffects(Player player) {
        // Remove effects
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.setGlowing(false);
        
        // Disable flight
        player.setAllowFlight(false);
        player.setFlying(false);
        
        // Set survival mode
        player.setGameMode(GameMode.SURVIVAL);
    }
    
    /**
     * Updates visibility for a ghost player.
     * Hides from living players, shows to other ghosts.
     */
    public void updateVisibilityForGhost(Player ghost) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(ghost)) continue;
            
            if (isGhost(other.getUniqueId())) {
                // Other is also a ghost - show each other
                ghost.showPlayer(plugin, other);
                other.showPlayer(plugin, ghost);
            } else {
                // Other is alive - hide ghost from them
                other.hidePlayer(plugin, ghost);
            }
        }
    }
    
    /**
     * Updates visibility when a living player joins.
     */
    public void updateVisibilityForLivingPlayer(Player living) {
        for (UUID ghostUuid : ghosts.keySet()) {
            Player ghost = Bukkit.getPlayer(ghostUuid);
            if (ghost != null && ghost.isOnline()) {
                living.hidePlayer(plugin, ghost);
            }
        }
    }
    
    /**
     * Resurrects a ghost player.
     * 
     * @param ghostUuid The ghost's UUID
     * @param location Where to resurrect (altar or spawn point)
     * @param resurrectedBy Who resurrected them (null for self-resurrect)
     * @return true if successful
     */
    public boolean resurrect(UUID ghostUuid, Location location, UUID resurrectedBy) {
        GhostState state = ghosts.get(ghostUuid);
        if (state == null) return false;
        
        Player player = Bukkit.getPlayer(ghostUuid);
        
        if (player != null && player.isOnline()) {
            // Player is online - resurrect immediately
            performResurrection(player, location, resurrectedBy);
        } else {
            // Player is offline - mark for resurrection on next login
            state.setPendingResurrection(true);
            state.setResurrectionLocation(location);
            state.setResurrectedBy(resurrectedBy);
            saveGhostData();
            
            plugin.debug("Marked offline ghost " + state.getPlayerName() + " for resurrection");
        }
        
        return true;
    }
    
    /**
     * Performs the actual resurrection of an online player.
     */
    private void performResurrection(Player player, Location location, UUID resurrectedBy) {
        UUID uuid = player.getUniqueId();
        GhostState state = ghosts.remove(uuid);
        
        if (state == null) return;
        
        // Remove ghost effects
        removeGhostEffects(player);
        
        // Restore visibility
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, player);
        }
        
        // Teleport to resurrection location
        FoliaUtil.teleportAsync(player, location).thenAccept(success -> {
            if (success) {
                // Send messages
                if (resurrectedBy != null) {
                    Player resurrector = Bukkit.getPlayer(resurrectedBy);
                    String resurrectorName = resurrector != null ? resurrector.getName() : "союзником";
                    player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                            "ghost.resurrected-by",
                            MessagesConfig.placeholder("player", resurrectorName)
                    ));
                } else {
                    player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.self-resurrected"));
                }
                
                // Heal player
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setSaturation(20f);
                
                // Play effects
                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 50);
            }
        });
        
        saveGhostData();
        plugin.debug("Resurrected " + player.getName());
    }
    
    /**
     * Handles a ghost player joining the server.
     * Reapplies ghost effects or processes pending resurrection.
     */
    public void handleGhostJoin(Player player) {
        GhostState state = ghosts.get(player.getUniqueId());
        if (state == null) return;
        
        if (state.isPendingResurrection()) {
            // Process pending resurrection
            Location loc = state.getResurrectionLocation();
            if (loc != null) {
                FoliaUtil.runDelayed(plugin, player, () -> {
                    performResurrection(player, loc, state.getResurrectedBy());
                }, 20L); // 1 second delay for safety
            }
        } else if (state.canSelfResurrect()) {
            // Auto-resurrect on join if time has expired
            FoliaUtil.runDelayed(plugin, player, () -> {
                if (player.isOnline() && isGhost(player.getUniqueId())) {
                    performAutoResurrect(player, state);
                }
            }, 40L); // 2 seconds delay
        } else {
            // Reapply ghost effects
            FoliaUtil.runDelayed(plugin, player, () -> {
                applyGhostEffects(player);
                updateVisibilityForGhost(player);
            }, 10L);
        }
    }
    
    /**
     * Checks if a player is a ghost.
     */
    public boolean isGhost(UUID uuid) {
        return ghosts.containsKey(uuid);
    }
    
    /**
     * Gets the ghost state for a player.
     */
    public GhostState getGhostState(UUID uuid) {
        return ghosts.get(uuid);
    }
    
    /**
     * Gets all ghosts for a kingdom.
     */
    public List<GhostState> getGhostsForKingdom(String kingdomId) {
        return ghosts.values().stream()
                .filter(g -> g.getKingdomId().equals(kingdomId))
                .collect(Collectors.toList());
    }
    
    /**
     * Generates a random resurrection cost from the config pool.
     */
    private List<ItemStack> generateResurrectionCost() {
        List<ItemStack> cost = new ArrayList<>();
        
        List<Map<?, ?>> costPool = plugin.getConfig().getMapList("ghost-system.resurrection-costs");
        if (costPool.isEmpty()) {
            // Default cost
            cost.add(new ItemStack(Material.DIAMOND, 5));
            return cost;
        }
        
        // Pick random cost from pool
        Random random = new Random();
        Map<?, ?> selectedCost = costPool.get(random.nextInt(costPool.size()));
        
        @SuppressWarnings("unchecked")
        List<Map<?, ?>> items = (List<Map<?, ?>>) selectedCost.get("items");
        if (items != null) {
            for (Map<?, ?> itemData : items) {
                String materialName = (String) itemData.get("material");
                int amount = itemData.get("amount") instanceof Number ? 
                        ((Number) itemData.get("amount")).intValue() : 1;
                
                Material material = Material.matchMaterial(materialName);
                if (material != null) {
                    cost.add(new ItemStack(material, amount));
                }
            }
        }
        
        if (cost.isEmpty()) {
            cost.add(new ItemStack(Material.DIAMOND, 5));
        }
        
        return cost;
    }
    
    /**
     * Notifies a ghost that self-resurrection is available.
     */
    private void notifySelfResurrectAvailable(Player player) {
        GhostState state = ghosts.get(player.getUniqueId());
        if (state == null || !state.canSelfResurrect()) return;
        
        // Only notify once by checking if we've already notified
        // (could add a flag to GhostState for this)
        player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.can-self-resurrect"));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
    }
    
    /**
     * Handles self-resurrection for a ghost.
     */
    public boolean performSelfResurrect(Player player) {
        GhostState state = ghosts.get(player.getUniqueId());
        if (state == null) return false;
        
        if (!state.canSelfResurrect()) {
            player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "ghost.cannot-self-resurrect-yet",
                    MessagesConfig.placeholder("time", state.getFormattedRemainingTime())
            ));
            return false;
        }
        
        // Determine resurrection location
        String locationType = plugin.getConfig().getString("ghost-system.self-resurrect-location", "bed");
        Location location = getResurrectionLocation(player, state, locationType);
        
        performResurrection(player, location, null);
        return true;
    }
    
    /**
     * Gets the appropriate resurrection location based on type.
     * Priority: specified type -> bed -> kingdom spawn (NEVER world spawn)
     */
    public Location getResurrectionLocation(Player player, GhostState state, String type) {
        Location result = null;
        
        switch (type.toLowerCase()) {
            case "altar":
                // Find nearest altar for the kingdom
                Altar altar = plugin.getAltarManager().getNearestAltar(state.getKingdomId(), player.getLocation());
                if (altar != null) {
                    result = altar.getLocation().clone().add(0.5, 1, 0.5);
                }
                break;
            case "bed":
            default:
                // Try bed/respawn anchor first
                result = player.getRespawnLocation();
                break;
        }
        
        // If no result yet, try bed as fallback
        if (result == null) {
            result = player.getRespawnLocation();
        }
        
        // Final fallback - kingdom spawn (NEVER world spawn!)
        if (result == null) {
            result = plugin.getSpawnManager().getSpawn(state.getKingdomId());
        }
        
        // Last resort - use death location if we still have nothing
        if (result == null && state.getDeathLocation() != null) {
            result = state.getDeathLocation();
        }
        
        return result;
    }
    
    /**
     * Spawns ghost particles around a player.
     */
    private void spawnGhostParticles(Player player) {
        FoliaUtil.runRepeatingOnEntity(plugin, player, (cancel) -> {
            if (!isGhost(player.getUniqueId()) || !player.isOnline()) {
                cancel.run();
                return;
            }
            
            Location loc = player.getLocation().add(0, 1, 0);
            player.getWorld().spawnParticle(
                    Particle.SOUL,
                    loc,
                    3,
                    0.3, 0.5, 0.3,
                    0.01
            );
        }, 10L, 20L); // Every second
    }
    
    /**
     * Saves ghost data to file.
     */
    public void saveGhostData() {
        ghostDataConfig = new YamlConfiguration();
        
        for (Map.Entry<UUID, GhostState> entry : ghosts.entrySet()) {
            GhostState state = entry.getValue();
            String path = "ghosts." + entry.getKey().toString();
            
            ghostDataConfig.set(path + ".name", state.getPlayerName());
            ghostDataConfig.set(path + ".kingdom", state.getKingdomId());
            ghostDataConfig.set(path + ".death-time", state.getDeathTime());
            ghostDataConfig.set(path + ".duration-ms", state.getDurationMs());
            ghostDataConfig.set(path + ".pending-resurrection", state.isPendingResurrection());
            
            if (state.getResurrectionLocation() != null) {
                Location loc = state.getResurrectionLocation();
                ghostDataConfig.set(path + ".resurrection-location.world", loc.getWorld().getName());
                ghostDataConfig.set(path + ".resurrection-location.x", loc.getX());
                ghostDataConfig.set(path + ".resurrection-location.y", loc.getY());
                ghostDataConfig.set(path + ".resurrection-location.z", loc.getZ());
            }
            
            if (state.getResurrectedBy() != null) {
                ghostDataConfig.set(path + ".resurrected-by", state.getResurrectedBy().toString());
            }
            
            // Save resurrection cost
            List<Map<String, Object>> costList = new ArrayList<>();
            for (ItemStack item : state.getResurrectionCost()) {
                Map<String, Object> itemData = new HashMap<>();
                itemData.put("material", item.getType().name());
                itemData.put("amount", item.getAmount());
                costList.add(itemData);
            }
            ghostDataConfig.set(path + ".resurrection-cost", costList);
            
            // Save death location
            if (state.getDeathLocation() != null) {
                Location loc = state.getDeathLocation();
                ghostDataConfig.set(path + ".death-location.world", loc.getWorld().getName());
                ghostDataConfig.set(path + ".death-location.x", loc.getX());
                ghostDataConfig.set(path + ".death-location.y", loc.getY());
                ghostDataConfig.set(path + ".death-location.z", loc.getZ());
            }
        }
        
        try {
            ghostDataConfig.save(ghostDataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save ghost data: " + e.getMessage());
        }
    }
    
    /**
     * Loads ghost data from file.
     */
    private void loadGhostData() {
        if (!ghostDataFile.exists()) {
            return;
        }
        
        ghostDataConfig = YamlConfiguration.loadConfiguration(ghostDataFile);
        ConfigurationSection ghostsSection = ghostDataConfig.getConfigurationSection("ghosts");
        
        if (ghostsSection == null) return;
        
        for (String uuidStr : ghostsSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String path = "ghosts." + uuidStr;
                
                String name = ghostDataConfig.getString(path + ".name");
                String kingdom = ghostDataConfig.getString(path + ".kingdom");
                long deathTime = ghostDataConfig.getLong(path + ".death-time");
                
                // Load resurrection cost
                List<ItemStack> cost = new ArrayList<>();
                List<Map<?, ?>> costList = ghostDataConfig.getMapList(path + ".resurrection-cost");
                for (Map<?, ?> itemData : costList) {
                    String materialName = (String) itemData.get("material");
                    int amount = itemData.get("amount") instanceof Number ?
                            ((Number) itemData.get("amount")).intValue() : 1;
                    Material material = Material.matchMaterial(materialName);
                    if (material != null) {
                        cost.add(new ItemStack(material, amount));
                    }
                }
                
                // Load death location
                Location deathLoc = null;
                if (ghostDataConfig.contains(path + ".death-location.world")) {
                    World world = Bukkit.getWorld(ghostDataConfig.getString(path + ".death-location.world"));
                    if (world != null) {
                        deathLoc = new Location(
                                world,
                                ghostDataConfig.getDouble(path + ".death-location.x"),
                                ghostDataConfig.getDouble(path + ".death-location.y"),
                                ghostDataConfig.getDouble(path + ".death-location.z")
                        );
                    }
                }
                
                // Load duration (use saved value or current config)
                long duration = ghostDataConfig.getLong(path + ".duration-ms", ghostDurationMs);
                
                GhostState state = new GhostState(uuid, name, kingdom, deathTime, duration, cost, deathLoc);
                
                // Load resurrection data
                state.setPendingResurrection(ghostDataConfig.getBoolean(path + ".pending-resurrection", false));
                
                if (ghostDataConfig.contains(path + ".resurrection-location.world")) {
                    World world = Bukkit.getWorld(ghostDataConfig.getString(path + ".resurrection-location.world"));
                    if (world != null) {
                        state.setResurrectionLocation(new Location(
                                world,
                                ghostDataConfig.getDouble(path + ".resurrection-location.x"),
                                ghostDataConfig.getDouble(path + ".resurrection-location.y"),
                                ghostDataConfig.getDouble(path + ".resurrection-location.z")
                        ));
                    }
                }
                
                if (ghostDataConfig.contains(path + ".resurrected-by")) {
                    state.setResurrectedBy(UUID.fromString(ghostDataConfig.getString(path + ".resurrected-by")));
                }
                
                ghosts.put(uuid, state);
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load ghost data for " + uuidStr + ": " + e.getMessage());
            }
        }
        
        plugin.getLogger().info("Loaded " + ghosts.size() + " ghost states.");
    }
    
    /**
     * Reloads the ghost manager configuration.
     */
    public void reload() {
        loadConfig();
    }
    
    /**
     * Gets the ghost duration in milliseconds.
     */
    public long getGhostDurationMs() {
        return ghostDurationMs;
    }
}
