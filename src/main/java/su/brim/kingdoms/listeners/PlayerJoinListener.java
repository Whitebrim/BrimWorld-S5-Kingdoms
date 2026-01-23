package su.brim.kingdoms.listeners;

import su.brim.kingdoms.KingdomsAddon;
import su.brim.kingdoms.api.KingdomsAPI;
import su.brim.kingdoms.config.MessagesConfig;
import su.brim.kingdoms.util.FoliaUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles player join events.
 */
public class PlayerJoinListener implements Listener {
    
    private final KingdomsAddon plugin;
    
    public PlayerJoinListener(KingdomsAddon plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        plugin.debug("PlayerJoinEvent: " + player.getName());

        // (allows other plugins to finish their join processing)
        FoliaUtil.runDelayed(plugin, player, () -> {
            if (player.isOnline()) {
                processPlayer(player);
            }
        }, 5L);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.getKingdomManager().unmarkProcessed(uuid);
    }
    
    /**
     * Processes a player - checks their kingdom assignment and handles accordingly.
     */
    public void processPlayer(Player player) {
        if (plugin.getKingdomManager().isProcessed(player.getUniqueId())) {
            plugin.debug("Player " + player.getName() + " already processed, skipping");
            return;
        }
        
        // Check if player is an admin - admins bypass kingdom assignment
        if (player.hasPermission(KingdomsAPI.ADMIN_PERMISSION)) {
            plugin.debug("Player " + player.getName() + " is admin, bypassing kingdom check");
            plugin.getKingdomManager().markProcessed(player.getUniqueId());
            
            // Update team colors for admin
            if (plugin.getTeamColorManager() != null) {
                plugin.getTeamColorManager().updatePlayer(player);
            }
            return;
        }
        
        boolean success = plugin.getKingdomManager().processPlayerJoin(player);
        
        if (!success) {
            // Player not whitelisted - kick them
            kickPlayerNotWhitelisted(player);
        } else {
            // Update team colors for kingdom member
            if (plugin.getTeamColorManager() != null) {
                plugin.getTeamColorManager().updatePlayer(player);
            }
        }
    }
    
    /**
     * Kicks a player with the "not whitelisted" message.
     */
    private void kickPlayerNotWhitelisted(Player player) {
        String kickMessage = plugin.getMessagesConfig().getMessage("kick.not-whitelisted");
        Component kickComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(kickMessage);
        
        // Kick must be done on the entity's region
        FoliaUtil.runOnEntity(plugin, player, () -> {
            player.kick(kickComponent);
        });
    }
}
