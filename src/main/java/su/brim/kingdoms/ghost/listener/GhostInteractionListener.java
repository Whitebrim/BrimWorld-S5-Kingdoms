package su.brim.kingdoms.ghost.listener;

import su.brim.kingdoms.KingdomsAddon;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.util.Vector;

import java.util.Set;

/**
 * Restricts what ghost players can do in the world.
 */
public class GhostInteractionListener implements Listener {
    
    private final KingdomsAddon plugin;
    
    // Blocks that ghosts can interact with (doors, trapdoors, etc.)
    private static final Set<Material> ALLOWED_INTERACTIONS = Set.of(
            // Doors
            Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR,
            Material.JUNGLE_DOOR, Material.ACACIA_DOOR, Material.DARK_OAK_DOOR,
            Material.MANGROVE_DOOR, Material.CHERRY_DOOR, Material.BAMBOO_DOOR,
            Material.CRIMSON_DOOR, Material.WARPED_DOOR, Material.IRON_DOOR,
            Material.COPPER_DOOR, Material.EXPOSED_COPPER_DOOR,
            Material.WEATHERED_COPPER_DOOR, Material.OXIDIZED_COPPER_DOOR,
            Material.WAXED_COPPER_DOOR, Material.WAXED_EXPOSED_COPPER_DOOR,
            Material.WAXED_WEATHERED_COPPER_DOOR, Material.WAXED_OXIDIZED_COPPER_DOOR,
            // Trapdoors
            Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.BIRCH_TRAPDOOR,
            Material.JUNGLE_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR,
            Material.MANGROVE_TRAPDOOR, Material.CHERRY_TRAPDOOR, Material.BAMBOO_TRAPDOOR,
            Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR, Material.IRON_TRAPDOOR,
            Material.COPPER_TRAPDOOR, Material.EXPOSED_COPPER_TRAPDOOR,
            Material.WEATHERED_COPPER_TRAPDOOR, Material.OXIDIZED_COPPER_TRAPDOOR,
            Material.WAXED_COPPER_TRAPDOOR, Material.WAXED_EXPOSED_COPPER_TRAPDOOR,
            Material.WAXED_WEATHERED_COPPER_TRAPDOOR, Material.WAXED_OXIDIZED_COPPER_TRAPDOOR,
            // Fence gates
            Material.OAK_FENCE_GATE, Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE,
            Material.JUNGLE_FENCE_GATE, Material.ACACIA_FENCE_GATE, Material.DARK_OAK_FENCE_GATE,
            Material.MANGROVE_FENCE_GATE, Material.CHERRY_FENCE_GATE, Material.BAMBOO_FENCE_GATE,
            Material.CRIMSON_FENCE_GATE, Material.WARPED_FENCE_GATE
    );
    
    public GhostInteractionListener(KingdomsAddon plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Prevents ghosts from opening containers (except allowed ones).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!plugin.getGhostManager().isGhost(player.getUniqueId())) return;
        
        InventoryType type = event.getInventory().getType();
        
        // Allow merchant GUI (for resurrection) and player inventory
        if (type == InventoryType.MERCHANT || type == InventoryType.PLAYER || type == InventoryType.CRAFTING) {
            return;
        }
        
        // Block all other inventories
        event.setCancelled(true);
        player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.cannot-interact"));
    }
    
    /**
     * Restricts block interactions for ghosts.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGhostManager().isGhost(player.getUniqueId())) return;
        
        // Allow right-click on air (flying/looking around)
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_AIR) {
            return;
        }
        
        Block block = event.getClickedBlock();
        if (block == null) return;
        
        // Check if this block type is allowed
        if (isAllowedInteraction(block.getType())) {
            return;
        }
        
        // Block all other interactions
        event.setCancelled(true);
    }
    
    /**
     * Prevents ghosts from picking up items.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getGhostManager().isGhost(player.getUniqueId())) return;
        
        event.setCancelled(true);
    }
    
    /**
     * Prevents ghosts from dropping items.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGhostManager().isGhost(player.getUniqueId())) return;
        
        event.setCancelled(true);
        player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.cannot-interact"));
    }
    
    /**
     * Prevents ghosts from dealing damage.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGhostDealDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!plugin.getGhostManager().isGhost(player.getUniqueId())) return;
        
        event.setCancelled(true);
    }
    
    /**
     * Prevents ghosts from taking damage (they're already dead!).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGhostTakeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getGhostManager().isGhost(player.getUniqueId())) return;
        
        event.setCancelled(true);
    }
    
    /**
     * Prevents mobs from targeting ghosts.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player)) return;
        if (!plugin.getGhostManager().isGhost(player.getUniqueId())) return;
        
        event.setCancelled(true);
    }
    
    /**
     * Prevents ghosts from interacting with entities (except altars).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGhostManager().isGhost(player.getUniqueId())) return;
        
        // Allow interaction with altar entities
        if (plugin.getAltarManager().isAltarInteraction(event.getRightClicked())) {
            return;
        }
        
        event.setCancelled(true);
    }
    
    /**
     * Prevents ghosts from interacting at entities (armor stands, etc.).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteractAt(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGhostManager().isGhost(player.getUniqueId())) return;
        
        // Allow interaction with altar entities
        if (plugin.getAltarManager().isAltarInteraction(event.getRightClicked())) {
            return;
        }
        
        event.setCancelled(true);
    }
    
    /**
     * Prevents ghosts from using beds.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGhostManager().isGhost(player.getUniqueId())) return;
        
        event.setCancelled(true);
        player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.cannot-interact"));
    }
    
    /**
     * Prevents ghosts from eating/consuming items.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGhostManager().isGhost(player.getUniqueId())) return;
        
        event.setCancelled(true);
    }
    
    /**
     * Prevents ghosts from breaking blocks in adventure mode
     * (this shouldn't be needed, but just in case).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGhostManager().isGhost(player.getUniqueId())) return;
        
        event.setCancelled(true);
    }
    
    /**
     * Prevents ghosts from placing blocks.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGhostManager().isGhost(player.getUniqueId())) return;
        
        event.setCancelled(true);
    }
    
    /**
     * Prevents sculk sensors from detecting ghost players.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGameEvent(org.bukkit.event.world.GenericGameEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getGhostManager().isGhost(player.getUniqueId())) return;
        
        // Cancel all game events from ghosts (footsteps, etc.)
        event.setCancelled(true);
    }
    
    /**
     * Restricts maximum flight height for ghosts.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGhostManager().isGhost(player.getUniqueId())) return;
        
        int maxHeight = plugin.getGhostManager().getMaxFlightHeight();
        if (maxHeight < 0) return; // No limit configured
        
        if (event.getTo() == null) return;
        
        double toY = event.getTo().getY();
        double fromY = event.getFrom().getY();
        
        // Only restrict if trying to fly higher than max
        if (toY > maxHeight && toY > fromY) {
            // Push player back down
            event.getTo().setY(maxHeight);
            
            // Stop upward velocity
            Vector velocity = player.getVelocity();
            if (velocity.getY() > 0) {
                velocity.setY(0);
                player.setVelocity(velocity);
            }
            
            // Notify player (only once per second to avoid spam)
            Long lastNotify = lastHeightNotification.get(player.getUniqueId());
            long now = System.currentTimeMillis();
            if (lastNotify == null || now - lastNotify > 1000) {
                player.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.max-height-reached"));
                lastHeightNotification.put(player.getUniqueId(), now);
            }
        }
    }
    
    // Track last height notification to avoid spam
    private final java.util.Map<java.util.UUID, Long> lastHeightNotification = new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * Checks if a material is allowed for ghost interaction.
     */
    private boolean isAllowedInteraction(Material material) {
        // Check config settings
        boolean allowDoors = plugin.getConfig().getBoolean("ghost-system.ghost-permissions.use-doors", true);
        boolean allowTrapdoors = plugin.getConfig().getBoolean("ghost-system.ghost-permissions.use-trapdoors", true);
        
        if (!allowDoors && material.name().contains("DOOR") && !material.name().contains("TRAPDOOR")) {
            return false;
        }
        
        if (!allowTrapdoors && material.name().contains("TRAPDOOR")) {
            return false;
        }
        
        return ALLOWED_INTERACTIONS.contains(material);
    }
}
