package su.brim.kingdoms.ghost.listener;

import dev.geco.gsit.api.event.PrePlayerPlayerSitEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import su.brim.kingdoms.KingdomsAddon;

/**
 * Prevents players from sitting on ghosts using GSit plugin.
 * This listener is only registered when GSit is available.
 */
public class GSitListener implements Listener {
    
    private final KingdomsAddon plugin;
    
    public GSitListener(KingdomsAddon plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Prevents players from sitting on ghost players.
     * The PrePlayerPlayerSitEvent is fired before a player sits on another player.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerSitOnPlayer(PrePlayerPlayerSitEvent event) {
        Player sitter = event.getPlayer();
        Player target = event.getTarget();
        
        // Check if the target player is a ghost
        if (plugin.getGhostManager() != null && plugin.getGhostManager().isGhost(target.getUniqueId())) {
            event.setCancelled(true);
            sitter.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.cannot-sit-on-ghost"));
            plugin.debug("Prevented " + sitter.getName() + " from sitting on ghost " + target.getName());
        }
    }
}
