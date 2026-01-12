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
    private final NamespacedKey pendingGhostKey;

    public RespawnHook(KingdomsAddon plugin) {
        this.plugin = plugin;
        this.pendingGhostKey = new NamespacedKey(plugin, "pending_ghost");
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
        
        String kingdomId = plugin.getKingdomManager().getPlayerKingdomId(uuid);
        
        // Check if ghost system is enabled and player has a kingdom
        boolean ghostSystemEnabled = plugin.getConfig().getBoolean("ghost-system.enabled", false);
        boolean shouldBecomeGhost = ghostSystemEnabled 
                && plugin.getGhostManager() != null
                && kingdomId != null 
                && !plugin.getGhostManager().isGhost(uuid);
        
        if (shouldBecomeGhost) {
            // Store death location
            deathLocations.put(uuid, deathLocation);
            
            // Mark player to become ghost after respawn
            player.getPersistentDataContainer().set(
                    pendingGhostKey,
                    PersistentDataType.STRING,
                    kingdomId
            );
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
                        // Remove pending marker
                        player.getPersistentDataContainer().remove(pendingGhostKey);
                        
                        // Get saved death location
                        Location savedDeathLoc = deathLocations.remove(uuid);
                        
                        // Make player a ghost and teleport to death location
                        player.getScheduler().runDelayed(plugin, task2 -> {
                            if (player.isOnline()) {
                                plugin.getGhostManager().makeGhost(player, pendingKingdom, savedDeathLoc);
                                
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
    }
}
