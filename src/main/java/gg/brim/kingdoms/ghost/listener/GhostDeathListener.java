package gg.brim.kingdoms.ghost.listener;

import gg.brim.kingdoms.KingdomsAddon;
import gg.brim.kingdoms.util.FoliaUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Handles player death events for the ghost system.
 */
public class GhostDeathListener implements Listener {
    
    private final KingdomsAddon plugin;
    
    public GhostDeathListener(KingdomsAddon plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Check if ghost system is enabled
        if (!plugin.getConfig().getBoolean("ghost-system.enabled", false)) {
            return;
        }
        
        Player player = event.getEntity();
        
        // Check if player is already a ghost (shouldn't happen, but just in case)
        if (plugin.getGhostManager().isGhost(player.getUniqueId())) {
            plugin.debug("Ghost " + player.getName() + " died again - ignoring");
            return;
        }
        
        // Check if player has a team/kingdom (use fallback method)
        String kingdomId = plugin.getKingdomManager().getPlayerKingdomId(player.getUniqueId());
        if (kingdomId == null) {
            plugin.debug("Player " + player.getName() + " has no kingdom - not creating ghost");
            return;
        }
        
        plugin.debug("Player " + player.getName() + " died in kingdom " + kingdomId);
        
        // The ghost state will be applied after respawn
        // Store that this player should become a ghost
        player.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "pending_ghost"),
                org.bukkit.persistence.PersistentDataType.STRING,
                kingdomId
        );
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // Check if ghost system is enabled
        if (!plugin.getConfig().getBoolean("ghost-system.enabled", false)) {
            return;
        }
        
        Player player = event.getPlayer();
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "pending_ghost");
        
        // Check if player should become a ghost
        String kingdomId = player.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING);
        if (kingdomId == null) {
            return;
        }
        
        // Remove the pending marker
        player.getPersistentDataContainer().remove(key);
        
        // Apply ghost state after a short delay (to ensure respawn is complete)
        FoliaUtil.runDelayed(plugin, player, () -> {
            if (player.isOnline()) {
                plugin.getGhostManager().makeGhost(player, kingdomId);
            }
        }, 5L);
    }
}
