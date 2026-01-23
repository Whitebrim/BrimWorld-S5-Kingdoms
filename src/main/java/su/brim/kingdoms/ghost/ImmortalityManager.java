package su.brim.kingdoms.ghost;

import su.brim.kingdoms.KingdomsAddon;
import su.brim.kingdoms.config.MessagesConfig;
import su.brim.kingdoms.util.FoliaUtil;
import org.bukkit.*;
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

/**
 * Manages the immortality effect system.
 * Players can purchase an immortality effect at their kingdom's altar
 * that works like a totem of undying.
 */
public class ImmortalityManager {
    
    private final KingdomsAddon plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    
    // Active immortality effects (player UUID -> expiration time in millis)
    private final Map<UUID, Long> activeEffects = new ConcurrentHashMap<>();
    
    // Config values
    private boolean enabled;
    private long durationMs;
    private List<ItemStack> cost;
    private boolean showParticles;
    private boolean playSound;
    private int healAmount;
    private boolean giveTotemEffects;
    
    public ImmortalityManager(KingdomsAddon plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "immortality.yml");
        
        loadConfig();
        loadData();
        startExpirationChecker();
        startActionbarTask();
    }
    
    /**
     * Loads immortality config values.
     */
    private void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("ghost-system.immortality.enabled", true);
        
        int minutes = plugin.getConfig().getInt("ghost-system.immortality.duration-minutes", 30);
        this.durationMs = minutes * 60L * 1000L;
        
        this.showParticles = plugin.getConfig().getBoolean("ghost-system.immortality.effects.particles", true);
        this.playSound = plugin.getConfig().getBoolean("ghost-system.immortality.effects.sound", true);
        this.healAmount = plugin.getConfig().getInt("ghost-system.immortality.heal-amount", 10);
        this.giveTotemEffects = plugin.getConfig().getBoolean("ghost-system.immortality.give-totem-effects", true);
        
        // Load cost
        this.cost = new ArrayList<>();
        List<Map<?, ?>> costList = plugin.getConfig().getMapList("ghost-system.immortality.cost");
        for (Map<?, ?> itemData : costList) {
            String materialName = (String) itemData.get("material");
            int amount = itemData.get("amount") instanceof Number ?
                    ((Number) itemData.get("amount")).intValue() : 1;
            Material material = Material.matchMaterial(materialName);
            if (material != null) {
                cost.add(new ItemStack(material, amount));
            }
        }
        
        // Default cost if empty
        if (cost.isEmpty()) {
            cost.add(new ItemStack(Material.GOLD_INGOT, 8));
        }
    }
    
    /**
     * Starts periodic task to check for expired effects.
     */
    private void startExpirationChecker() {
        FoliaUtil.runGlobalRepeating(plugin, () -> {
            long now = System.currentTimeMillis();
            
            Iterator<Map.Entry<UUID, Long>> iterator = activeEffects.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Long> entry = iterator.next();
                if (now >= entry.getValue()) {
                    UUID uuid = entry.getKey();
                    iterator.remove();
                    
                    // Notify player if online
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        FoliaUtil.runOnEntity(plugin, player, () -> {
                            player.sendMessage(plugin.getMessagesConfig()
                                    .getComponentWithPrefix("ghost.immortality.expired"));
                        });
                    }
                    
                    saveData();
                }
            }
        }, 20 * 30, 20 * 30); // Check every 30 seconds
    }
    
    /**
     * Starts actionbar display task for players with immortality.
     */
    private void startActionbarTask() {
        FoliaUtil.runGlobalRepeating(plugin, () -> {
            for (Map.Entry<UUID, Long> entry : activeEffects.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    FoliaUtil.runOnEntity(plugin, player, () -> {
                        if (!player.isOnline()) return;
                        
                        long remaining = entry.getValue() - System.currentTimeMillis();
                        if (remaining > 0) {
                            String timeText = formatTime(remaining);
                            String message = plugin.getMessagesConfig()
                                    .getMessage("ghost.immortality.actionbar",
                                            MessagesConfig.placeholder("time", timeText));
                            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                    .legacyAmpersand().deserialize(message));
                        }
                    });
                }
            }
        }, 20L, 20L); // Every second
    }
    
    /**
     * Checks if the immortality system is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Checks if a player has an active immortality effect.
     */
    public boolean hasImmortality(UUID playerUuid) {
        Long expiration = activeEffects.get(playerUuid);
        if (expiration == null) return false;
        
        // Check if expired
        if (System.currentTimeMillis() >= expiration) {
            activeEffects.remove(playerUuid);
            saveData();
            return false;
        }
        
        return true;
    }
    
    /**
     * Gets the remaining time of a player's immortality effect.
     * @return remaining time in milliseconds, or 0 if no effect
     */
    public long getRemainingTime(UUID playerUuid) {
        Long expiration = activeEffects.get(playerUuid);
        if (expiration == null) return 0;
        
        long remaining = expiration - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
    
    /**
     * Gets the cost of purchasing immortality.
     */
    public List<ItemStack> getCost() {
        return new ArrayList<>(cost);
    }
    
    /**
     * Gets the duration of immortality in milliseconds.
     */
    public long getDurationMs() {
        return durationMs;
    }
    
    /**
     * Grants immortality effect to a player.
     * @param player The player to grant immortality to
     * @return true if successful
     */
    public boolean grantImmortality(Player player) {
        if (!enabled) return false;
        
        UUID uuid = player.getUniqueId();
        
        // Check if already has effect
        if (hasImmortality(uuid)) {
            return false;
        }
        
        // Set expiration time
        long expiration = System.currentTimeMillis() + durationMs;
        activeEffects.put(uuid, expiration);
        
        // Visual feedback
        if (showParticles) {
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, 
                    player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        }
        if (playSound) {
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
        }
        
        // Send message
        String durationText = formatTime(durationMs);
        player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                "ghost.immortality.purchased",
                MessagesConfig.placeholder("duration", durationText)
        ));
        
        saveData();
        plugin.debug("Granted immortality to " + player.getName() + " for " + durationText);
        
        return true;
    }
    
    /**
     * Called when a player would die but has immortality.
     * Consumes the immortality effect and saves them.
     * 
     * @param player The player who would die
     * @return true if immortality was triggered
     */
    public boolean triggerImmortality(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (!hasImmortality(uuid)) {
            return false;
        }
        
        // Remove the effect
        activeEffects.remove(uuid);
        saveData();
        
        // Apply totem-like effects
        
        // Heal player
        double newHealth = Math.min(player.getMaxHealth(), healAmount);
        player.setHealth(newHealth);
        
        // Clear negative effects
        player.getActivePotionEffects().stream()
                .filter(effect -> isNegativeEffect(effect.getType()))
                .forEach(effect -> player.removePotionEffect(effect.getType()));
        
        // Give totem effects if enabled
        if (giveTotemEffects) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 45 * 20, 1)); // 45 seconds
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 5 * 20, 1)); // 5 seconds
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40 * 20, 0)); // 40 seconds
        }
        
        // Visual effects
        if (showParticles) {
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, 
                    player.getLocation().add(0, 1, 0), 100, 0.5, 1, 0.5, 0.3);
        }
        if (playSound) {
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        }
        
        // Send message
        player.sendMessage(plugin.getMessagesConfig()
                .getComponentWithPrefix("ghost.immortality.triggered"));
        
        plugin.debug("Immortality triggered for " + player.getName());
        
        return true;
    }
    
    /**
     * Checks if a potion effect type is negative.
     */
    private boolean isNegativeEffect(PotionEffectType type) {
        return type.equals(PotionEffectType.POISON) ||
               type.equals(PotionEffectType.WITHER) ||
               type.equals(PotionEffectType.SLOWNESS) ||
               type.equals(PotionEffectType.MINING_FATIGUE) ||
               type.equals(PotionEffectType.INSTANT_DAMAGE) ||
               type.equals(PotionEffectType.NAUSEA) ||
               type.equals(PotionEffectType.BLINDNESS) ||
               type.equals(PotionEffectType.HUNGER) ||
               type.equals(PotionEffectType.WEAKNESS) ||
               type.equals(PotionEffectType.LEVITATION) ||
               type.equals(PotionEffectType.UNLUCK) ||
               type.equals(PotionEffectType.DARKNESS);
    }
    
    /**
     * Handles player join - restores actionbar display if they have immortality.
     */
    public void handlePlayerJoin(Player player) {
        // Effect persists, actionbar will be shown by the periodic task
    }
    
    /**
     * Formats milliseconds to human-readable time string.
     */
    private String formatTime(long ms) {
        long hours = ms / (1000 * 60 * 60);
        long minutes = (ms % (1000 * 60 * 60)) / (1000 * 60);
        long seconds = (ms % (1000 * 60)) / 1000;
        
        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append(" ч ");
        }
        if (minutes > 0 || hours > 0) {
            sb.append(minutes).append(" мин ");
        }
        sb.append(seconds).append(" сек");
        
        return sb.toString();
    }
    
    /**
     * Gets formatted remaining time for a player.
     */
    public String getFormattedRemainingTime(UUID playerUuid) {
        return formatTime(getRemainingTime(playerUuid));
    }
    
    /**
     * Saves immortality data to file.
     */
    public void saveData() {
        dataConfig = new YamlConfiguration();
        
        for (Map.Entry<UUID, Long> entry : activeEffects.entrySet()) {
            dataConfig.set("immortality." + entry.getKey().toString(), entry.getValue());
        }
        
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save immortality data: " + e.getMessage());
        }
    }
    
    /**
     * Loads immortality data from file.
     */
    private void loadData() {
        if (!dataFile.exists()) {
            return;
        }
        
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        
        if (dataConfig.contains("immortality")) {
            for (String uuidStr : dataConfig.getConfigurationSection("immortality").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    long expiration = dataConfig.getLong("immortality." + uuidStr);
                    
                    // Only load if not expired
                    if (System.currentTimeMillis() < expiration) {
                        activeEffects.put(uuid, expiration);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in immortality data: " + uuidStr);
                }
            }
        }
        
        plugin.getLogger().info("Loaded " + activeEffects.size() + " active immortality effects.");
    }
    
    /**
     * Reloads the immortality manager configuration.
     */
    public void reload() {
        loadConfig();
    }
    
    /**
     * Gets the count of active immortality effects.
     */
    public int getActiveCount() {
        return activeEffects.size();
    }
}
