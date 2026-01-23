package su.brim.kingdoms.listeners;

import su.brim.kingdoms.KingdomsAddon;
import su.brim.kingdoms.util.FoliaUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Handles player respawn events.
 * Teleports players to their kingdom spawn if they don't have a bed/anchor.
 */
public class PlayerRespawnListener implements Listener {
    
    private final KingdomsAddon plugin;
    
    public PlayerRespawnListener(KingdomsAddon plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        try {
            Player player = event.getPlayer();
            
            plugin.getLogger().info("[RESPAWN] PlayerRespawnEvent fired for " + player.getName());
            plugin.debug("=== PlayerRespawnEvent for " + player.getName() + " ===");
            plugin.debug("isBedSpawn: " + event.isBedSpawn());
            plugin.debug("isAnchorSpawn: " + event.isAnchorSpawn());
            plugin.debug("Current respawn location: " + formatLoc(event.getRespawnLocation()));
            
            // Check if this feature is enabled
            if (!plugin.getConfigManager().isTeleportOnDeathNoRespawn()) {
                plugin.debug("Respawn teleport DISABLED in config");
                return;
            }
            
            // Check if player is respawning at anchor or bed
            if (event.isAnchorSpawn() || event.isBedSpawn()) {
                plugin.debug("Player has bed/anchor spawn, not overriding");
                return;
            }
            
            // Get player's kingdom
            String kingdomId = plugin.getKingdomManager().getPlayerKingdomId(player.getUniqueId());
            plugin.debug("Player kingdom: " + kingdomId);
            
            if (kingdomId == null) {
                plugin.debug("Player has no kingdom, not modifying respawn");
                return;
            }
            
            // Get kingdom spawn
            Location spawn = plugin.getSpawnManager().getSpawn(kingdomId);
            plugin.debug("Kingdom spawn for " + kingdomId + ": " + formatLoc(spawn));
            
            if (spawn == null) {
                plugin.debug("No spawn set for " + kingdomId + ", using default respawn");
                plugin.getLogger().warning("No spawn set for kingdom " + kingdomId + "! Use /kingdoms setspawn " + kingdomId);
                return;
            }
            
            // Validate spawn location
            if (spawn.getWorld() == null) {
                plugin.debug("Spawn world is null!");
                plugin.getLogger().warning("Spawn world is null for " + kingdomId);
                return;
            }
            
            // Set the respawn location
            plugin.getLogger().info("[RESPAWN] Setting respawn for " + player.getName() + " to " + formatLoc(spawn));
            event.setRespawnLocation(spawn);
            plugin.debug("Respawn location AFTER set: " + formatLoc(event.getRespawnLocation()));
            
            // Send message after respawn (delayed to ensure player has respawned)
            FoliaUtil.runDelayed(plugin, player, () -> {
                if (player.isOnline()) {
                    player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("respawn.at-kingdom-spawn"));
                }
            }, 10L);
            
        } catch (Exception e) {
            plugin.getLogger().severe("[RESPAWN] Exception in PlayerRespawnEvent handler: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Monitor listener to verify event is being fired at all.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawnMonitor(PlayerRespawnEvent event) {
        plugin.getLogger().info("[RESPAWN-MONITOR] Final respawn location for " + event.getPlayer().getName() + 
                ": " + formatLoc(event.getRespawnLocation()));
    }
    
    private String formatLoc(Location loc) {
        if (loc == null) return "null";
        if (loc.getWorld() == null) return "world=null";
        return loc.getWorld().getName() + " " + 
               String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ());
    }
}
