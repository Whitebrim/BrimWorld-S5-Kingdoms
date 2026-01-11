package gg.brim.kingdoms.listeners;

import gg.brim.kingdoms.KingdomsAddon;
import gg.brim.kingdoms.config.MessagesConfig;
import gg.brim.kingdoms.util.FoliaUtil;
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
 * This listener serves as a fallback when nLogin is not used,
 * or as a timeout handler when waiting for nLogin authentication.
 */
public class PlayerJoinListener implements Listener {
    
    private final KingdomsAddon plugin;
    
    // Tracks players waiting for nLogin authentication
    private final Map<UUID, Long> pendingPlayers = new ConcurrentHashMap<>();
    
    public PlayerJoinListener(KingdomsAddon plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        plugin.debug("PlayerJoinEvent: " + player.getName());
        
        // If nLogin is enabled, we wait for nLogin to process the player
        if (plugin.isNLoginEnabled()) {
            // Mark as pending
            pendingPlayers.put(uuid, System.currentTimeMillis());
            
            // Schedule a timeout check
            int timeoutSeconds = plugin.getConfigManager().getNLoginTimeout();
            FoliaUtil.runDelayed(plugin, player, () -> {
                if (pendingPlayers.containsKey(uuid) && player.isOnline()) {
                    plugin.debug("nLogin timeout for " + player.getName() + ", processing manually");
                    pendingPlayers.remove(uuid);
                    processPlayer(player);
                }
            }, timeoutSeconds * 20L);
            
            return;
        }
        
        // No nLogin, process immediately with a small delay
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
        pendingPlayers.remove(uuid);
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
        
        boolean success = plugin.getKingdomManager().processPlayerJoin(player);
        
        if (!success) {
            // Player not whitelisted - kick them
            kickPlayerNotWhitelisted(player);
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
    
    /**
     * Called by NLoginListener when player authenticates.
     */
    public void onPlayerAuthenticated(Player player) {
        UUID uuid = player.getUniqueId();
        pendingPlayers.remove(uuid);
        
        plugin.debug("Player " + player.getName() + " authenticated via nLogin");
        
        // Process on the entity's region
        FoliaUtil.runOnEntity(plugin, player, () -> {
            if (player.isOnline()) {
                processPlayer(player);
            }
        });
    }
    
    /**
     * Checks if a player is pending nLogin authentication.
     */
    public boolean isPending(UUID uuid) {
        return pendingPlayers.containsKey(uuid);
    }
}
