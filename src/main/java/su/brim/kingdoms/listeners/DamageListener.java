package su.brim.kingdoms.listeners;

import su.brim.kingdoms.KingdomsAddon;
import su.brim.kingdoms.api.KingdomsAPI;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Handles damage modification between kingdom members.
 */
public class DamageListener implements Listener {
    
    private final KingdomsAddon plugin;
    
    public DamageListener(KingdomsAddon plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Only care about player damage
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        
        Player attacker = getAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        
        // Don't modify self-damage
        if (attacker.equals(victim)) {
            return;
        }
        
        // Admins deal and receive full damage (not affected by kingdom modifiers)
        boolean attackerIsAdmin = attacker.hasPermission(KingdomsAPI.ADMIN_PERMISSION);
        boolean victimIsAdmin = victim.hasPermission(KingdomsAPI.ADMIN_PERMISSION);
        
        if (attackerIsAdmin || victimIsAdmin) {
            plugin.debug("Admin involved in damage: " + attacker.getName() + " -> " + victim.getName() + " (no modifier)");
            return;
        }
        
        // Get kingdom information
        boolean attackerHasKingdom = plugin.getKingdomManager().hasKingdom(attacker.getUniqueId());
        boolean victimHasKingdom = plugin.getKingdomManager().hasKingdom(victim.getUniqueId());
        
        // Handle teamless damage
        if (!attackerHasKingdom || !victimHasKingdom) {
            if (plugin.getConfigManager().isBlockTeamlessDamage()) {
                event.setCancelled(true);
                plugin.debug("Blocked teamless damage: " + attacker.getName() + " -> " + victim.getName());
            }
            return;
        }
        
        // Check if allies
        boolean allies = plugin.getKingdomManager().areAllies(attacker.getUniqueId(), victim.getUniqueId());
        
        // Apply damage multiplier
        double multiplier;
        if (allies) {
            multiplier = plugin.getConfigManager().getAllyDamageMultiplier();
            plugin.debug("Ally damage: " + attacker.getName() + " -> " + victim.getName() + 
                        " (x" + multiplier + ")");
        } else {
            multiplier = plugin.getConfigManager().getEnemyDamageMultiplier();
            plugin.debug("Enemy damage: " + attacker.getName() + " -> " + victim.getName() + 
                        " (x" + multiplier + ")");
        }
        
        // If multiplier is 0, cancel the event
        if (multiplier <= 0) {
            event.setCancelled(true);
            return;
        }
        
        // Apply multiplier to damage
        double originalDamage = event.getDamage();
        double newDamage = originalDamage * multiplier;
        event.setDamage(newDamage);
    }
    
    /**
     * Gets the attacking player from an entity (handles projectiles).
     */
    private Player getAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        
        return null;
    }
}
