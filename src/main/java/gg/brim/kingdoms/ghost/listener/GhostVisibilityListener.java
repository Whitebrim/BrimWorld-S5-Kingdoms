package gg.brim.kingdoms.ghost.listener;

import gg.brim.kingdoms.KingdomsAddon;
import gg.brim.kingdoms.util.FoliaUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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
            
            if (plugin.getGhostManager().isGhost(player.getUniqueId())) {
                // This player is a ghost - handle their rejoin
                plugin.getGhostManager().handleGhostJoin(player);
            } else {
                // This player is alive - hide all ghosts from them
                plugin.getGhostManager().updateVisibilityForLivingPlayer(player);
            }
        }, 20L); // 1 second delay
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
