package gg.brim.kingdoms.listeners;

import gg.brim.kingdoms.KingdomsAddon;
import gg.brim.kingdoms.util.FoliaUtil;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RespawnHook implements Listener {

    private final KingdomsAddon plugin;
    private final Map<UUID, ScheduledTask> waiting = new ConcurrentHashMap<>();
    private final Map<UUID, Location> deathLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Location> bedSpawnLocations = new ConcurrentHashMap<>();
    private final NamespacedKey pendingGhostKey;
    private final NamespacedKey deathLocationKey;
    private final NamespacedKey bedSpawnKey;

    public RespawnHook(KingdomsAddon plugin) {
        this.plugin = plugin;
        this.pendingGhostKey = new NamespacedKey(plugin, "pending_ghost");
        this.deathLocationKey = new NamespacedKey(plugin, "death_location");
        this.bedSpawnKey = new NamespacedKey(plugin, "bed_spawn");
    }
    
    /**
     * Gets the pending ghost key for external access.
     */
    public NamespacedKey getPendingGhostKey() {
        return pendingGhostKey;
    }
    
    /**
     * Gets the death location key for external access.
     */
    public NamespacedKey getDeathLocationKey() {
        return deathLocationKey;
    }
    
    /**
     * Gets the bed spawn key for external access.
     */
    public NamespacedKey getBedSpawnKey() {
        return bedSpawnKey;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        if (waiting.containsKey(uuid)) {
            return;
        }

        // Save death location BEFORE respawn
        Location deathLocation = player.getLocation().clone();
        
        // Try to get bed spawn - this needs to be done on player's region
        // so we do it here before potential region change
        Location bedSpawn = null;
        try {
            bedSpawn = player.getRespawnLocation();
        } catch (Exception e) {
            // May fail in Folia if chunk not loaded, ignore
        }
        
        String kingdomId = plugin.getKingdomManager().getPlayerKingdomId(uuid);
        
        // Check if ghost system is enabled and player has a kingdom
        boolean ghostSystemEnabled = plugin.getConfig().getBoolean("ghost-system.enabled", false);
        boolean shouldBecomeGhost = ghostSystemEnabled 
                && plugin.getGhostManager() != null
                && kingdomId != null 
                && !plugin.getGhostManager().isGhost(uuid);
        
        if (shouldBecomeGhost) {
            // Store locations in memory
            deathLocations.put(uuid, deathLocation);
            if (bedSpawn != null) {
                bedSpawnLocations.put(uuid, bedSpawn);
            }
            
            // Mark player to become ghost after respawn (save kingdom ID)
            player.getPersistentDataContainer().set(
                    pendingGhostKey,
                    PersistentDataType.STRING,
                    kingdomId
            );
            
            // Save death location to PDC (for case when player quits on death screen)
            String locString = serializeLocation(deathLocation);
            player.getPersistentDataContainer().set(
                    deathLocationKey,
                    PersistentDataType.STRING,
                    locString
            );
            
            // Save bed spawn to PDC
            if (bedSpawn != null) {
                String bedSpawnString = serializeLocation(bedSpawn);
                player.getPersistentDataContainer().set(
                        bedSpawnKey,
                        PersistentDataType.STRING,
                        bedSpawnString
                );
            }
            
            plugin.debug("Player " + player.getName() + " marked to become ghost at " + 
                    deathLocation.getWorld().getName() + " " + 
                    String.format("%.1f, %.1f, %.1f", deathLocation.getX(), deathLocation.getY(), deathLocation.getZ()));
        }

        ScheduledTask task = player.getScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> {

                    if (!player.isOnline()) {
                        cleanup(uuid, scheduledTask);
                        return;
                    }

                    if (player.isDead()) {
                        return; // ждём реального респавна
                    }

                    // === GHOST SYSTEM HANDLING ===
                    String pendingKingdom = player.getPersistentDataContainer().get(
                            pendingGhostKey, 
                            PersistentDataType.STRING
                    );
                    
                    if (pendingKingdom != null) {
                        // Remove pending markers from PDC
                        player.getPersistentDataContainer().remove(pendingGhostKey);
                        player.getPersistentDataContainer().remove(deathLocationKey);
                        player.getPersistentDataContainer().remove(bedSpawnKey);
                        
                        // Get saved locations from memory
                        Location savedDeathLoc = deathLocations.remove(uuid);
                        Location savedBedSpawn = bedSpawnLocations.remove(uuid);
                        
                        // Make player a ghost and teleport to death location
                        player.getScheduler().runDelayed(plugin, task2 -> {
                            if (player.isOnline()) {
                                plugin.getGhostManager().makeGhost(player, pendingKingdom, savedDeathLoc, savedBedSpawn);
                                
                                // Teleport ghost to death location
                                if (savedDeathLoc != null && savedDeathLoc.getWorld() != null) {
                                    FoliaUtil.teleportAsync(player, savedDeathLoc);
                                }
                            }
                        }, null, 5L);
                        
                        cleanup(uuid, scheduledTask);
                        return;
                    }

                    // === NORMAL RESPAWN TELEPORT LOGIC ===
                    if (!plugin.getConfigManager().isTeleportOnDeathNoRespawn()) {
                        cleanup(uuid, scheduledTask);
                        return;
                    }

                    String playerKingdom = plugin.getKingdomManager().getPlayerKingdomId(uuid);

                    if (playerKingdom == null) {
                        cleanup(uuid, scheduledTask);
                        return;
                    }

                    Location spawn = plugin.getSpawnManager().getSpawn(playerKingdom);

                    if (spawn == null || spawn.getWorld() == null) {
                        cleanup(uuid, scheduledTask);
                        return;
                    }

                    player.teleportAsync(spawn);
                    cleanup(uuid, scheduledTask);
                },
                () -> waiting.remove(uuid),
                1L,
                4L
        );

        waiting.put(uuid, task);
    }

    private void cleanup(UUID uuid, ScheduledTask task) {
        task.cancel();
        waiting.remove(uuid);
        deathLocations.remove(uuid);
        bedSpawnLocations.remove(uuid);
    }
    
    /**
     * Serializes a Location to a string for PDC storage.
     */
    private String serializeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().getName() + ";" + 
               loc.getX() + ";" + 
               loc.getY() + ";" + 
               loc.getZ() + ";" +
               loc.getYaw() + ";" +
               loc.getPitch();
    }
    
    /**
     * Deserializes a Location from a string.
     */
    public Location deserializeLocation(String str) {
        if (str == null || str.isEmpty()) return null;
        try {
            String[] parts = str.split(";");
            if (parts.length < 4) return null;
            
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
            
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception e) {
            return null;
        }
    }
}
