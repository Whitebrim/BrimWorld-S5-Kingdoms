package gg.brim.kingdoms.ghost.listener;

import gg.brim.kingdoms.KingdomsAddon;
import gg.brim.kingdoms.util.FoliaUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Manages ghost visibility when players join and leave.
 */
public class GhostVisibilityListener implements Listener {
    
    private final KingdomsAddon plugin;
    
    public GhostVisibilityListener(KingdomsAddon plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Check if ghost system is enabled
        if (!plugin.getConfig().getBoolean("ghost-system.enabled", false)) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Delay to ensure player is fully loaded
        FoliaUtil.runDelayed(plugin, player, () -> {
            if (!player.isOnline()) return;
            
            // First check if player has pending_ghost marker (quit on death screen)
            if (checkPendingGhost(player)) {
                return; // Player will be made ghost, visibility will be handled there
            }
            
            if (plugin.getGhostManager().isGhost(player.getUniqueId())) {
                // This player is a ghost - handle their rejoin
                plugin.getGhostManager().handleGhostJoin(player);
            } else {
                // This player is alive - hide all ghosts from them
                plugin.getGhostManager().updateVisibilityForLivingPlayer(player);
            }
        }, 20L); // 1 second delay
    }
    
    /**
     * Checks if player quit on death screen and should become ghost.
     * @return true if player will be made ghost
     */
    private boolean checkPendingGhost(Player player) {
        var respawnHook = plugin.getRespawnHook();
        if (respawnHook == null) return false;
        
        String pendingKingdom = player.getPersistentDataContainer().get(
                respawnHook.getPendingGhostKey(),
                PersistentDataType.STRING
        );
        
        if (pendingKingdom == null) return false;
        
        plugin.debug("Player " + player.getName() + " has pending_ghost marker, making ghost");
        
        // Get death location from PDC
        String deathLocStr = player.getPersistentDataContainer().get(
                respawnHook.getDeathLocationKey(),
                PersistentDataType.STRING
        );
        Location deathLocation = respawnHook.deserializeLocation(deathLocStr);
        
        // Get bed spawn from PDC
        String bedSpawnStr = player.getPersistentDataContainer().get(
                respawnHook.getBedSpawnKey(),
                PersistentDataType.STRING
        );
        Location bedSpawnLocation = respawnHook.deserializeLocation(bedSpawnStr);
        
        // Remove PDC markers
        player.getPersistentDataContainer().remove(respawnHook.getPendingGhostKey());
        player.getPersistentDataContainer().remove(respawnHook.getDeathLocationKey());
        player.getPersistentDataContainer().remove(respawnHook.getBedSpawnKey());
        
        // Make ghost and teleport to death location
        FoliaUtil.runDelayed(plugin, player, () -> {
            if (player.isOnline()) {
                plugin.getGhostManager().makeGhost(player, pendingKingdom, deathLocation, bedSpawnLocation);
                
                // Teleport ghost to death location
                if (deathLocation != null && deathLocation.getWorld() != null) {
                    FoliaUtil.teleportAsync(player, deathLocation);
                }
            }
        }, 5L);
        
        return true;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Check if ghost system is enabled
        if (!plugin.getConfig().getBoolean("ghost-system.enabled", false)) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Clean up any GUI states
        if (plugin.getResurrectionGUI().hasOpenGUI(player.getUniqueId())) {
            plugin.getResurrectionGUI().handleClose(player);
        }
        
        // Ghost data is automatically persisted, no need to do anything special here
    }
}
