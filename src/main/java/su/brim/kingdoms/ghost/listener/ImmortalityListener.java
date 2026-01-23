package su.brim.kingdoms.ghost.listener;

import su.brim.kingdoms.KingdomsAddon;
import su.brim.kingdoms.api.KingdomsAPI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;

/**
 * Handles the immortality effect triggering when a player would die.
 * This listener intercepts fatal damage and triggers the immortality effect
 * similar to how a Totem of Undying works.
 */
public class ImmortalityListener implements Listener {
    
    private final KingdomsAddon plugin;
    
    public ImmortalityListener(KingdomsAddon plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Intercepts damage events to check for fatal damage on players with immortality.
     * Runs at HIGHEST priority to run after other damage modifications.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        
        // Check if immortality system is enabled
        if (plugin.getImmortalityManager() == null || !plugin.getImmortalityManager().isEnabled()) {
            return;
        }
        
        // Admins don't use immortality (they don't become ghosts either)
        if (player.hasPermission(KingdomsAPI.ADMIN_PERMISSION)) {
            return;
        }
        
        // Check if this damage would be fatal
        double finalDamage = event.getFinalDamage();
        double currentHealth = player.getHealth();
        
        if (currentHealth - finalDamage > 0) {
            // Not fatal damage
            return;
        }
        
        // Player would die - check for immortality
        if (!plugin.getImmortalityManager().hasImmortality(player.getUniqueId())) {
            return;
        }
        
        // Trigger immortality effect
        boolean triggered = plugin.getImmortalityManager().triggerImmortality(player);
        
        if (triggered) {
            // Cancel the fatal damage
            event.setCancelled(true);
            plugin.debug("Immortality saved " + player.getName() + " from " + event.getCause());
        }
    }
    
    /**
     * Handles vanilla totem resurrection event.
     * If player has our immortality effect and no totem, we can let our system handle it.
     * If player has a real totem, we let the vanilla behavior work.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        
        // Log for debugging
        if (plugin.getImmortalityManager() != null && 
            plugin.getImmortalityManager().hasImmortality(player.getUniqueId())) {
            plugin.debug("EntityResurrectEvent for " + player.getName() + 
                        " - cancelled: " + event.isCancelled() + 
                        " (has our immortality)");
        }
    }
}
