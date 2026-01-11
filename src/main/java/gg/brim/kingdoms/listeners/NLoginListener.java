package gg.brim.kingdoms.listeners;

import gg.brim.kingdoms.KingdomsAddon;
import gg.brim.kingdoms.util.FoliaUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

// nLogin API imports
import com.nickuc.login.api.event.bukkit.auth.LoginEvent;
import com.nickuc.login.api.event.bukkit.auth.PremiumLoginEvent;
import com.nickuc.login.api.event.bukkit.auth.BedrockLoginEvent;

/**
 * Handles nLogin authentication events.
 * This listener is only registered when nLogin is detected.
 */
public class NLoginListener implements Listener {
    
    private final KingdomsAddon plugin;
    
    public NLoginListener(KingdomsAddon plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Called when a player logs in via password.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(LoginEvent event) {
        handleAuthentication(event.getPlayer().getName());
    }
    
    /**
     * Called when a premium player auto-authenticates.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPremiumLogin(PremiumLoginEvent event) {
        handleAuthentication(event.getPlayer().getName());
    }
    
    /**
     * Called when a Bedrock player auto-authenticates.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBedrockLogin(BedrockLoginEvent event) {
        handleAuthentication(event.getPlayer().getName());
    }
    
    /**
     * Handles player authentication from any nLogin event.
     */
    private void handleAuthentication(String playerName) {
        plugin.debug("nLogin authentication: " + playerName);
        
        // nLogin events may be async, so we need to get the player and schedule properly
        FoliaUtil.runGlobal(plugin, () -> {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null && player.isOnline()) {
                // Find the PlayerJoinListener to notify it
                notifyPlayerAuthenticated(player);
            } else {
                plugin.debug("Player " + playerName + " not found after nLogin auth");
            }
        });
    }
    
    /**
     * Notifies the join listener that a player has authenticated.
     */
    private void notifyPlayerAuthenticated(Player player) {
        // Get the PlayerJoinListener from registered listeners
        for (org.bukkit.plugin.RegisteredListener registered : 
                org.bukkit.event.player.PlayerJoinEvent.getHandlerList().getRegisteredListeners()) {
            if (registered.getListener() instanceof PlayerJoinListener joinListener) {
                joinListener.onPlayerAuthenticated(player);
                return;
            }
        }
        
        // Fallback: process directly
        plugin.debug("Could not find PlayerJoinListener, processing directly");
        FoliaUtil.runOnEntity(plugin, player, () -> {
            if (player.isOnline() && !plugin.getKingdomManager().isProcessed(player.getUniqueId())) {
                boolean success = plugin.getKingdomManager().processPlayerJoin(player);
                if (!success) {
                    // Kick player
                    net.kyori.adventure.text.Component kickMsg = 
                            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                    .legacyAmpersand()
                                    .deserialize(plugin.getMessagesConfig().getMessage("kick.not-whitelisted"));
                    player.kick(kickMsg);
                }
            }
        });
    }
}
