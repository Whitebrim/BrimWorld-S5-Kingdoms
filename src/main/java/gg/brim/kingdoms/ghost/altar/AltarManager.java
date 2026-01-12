package gg.brim.kingdoms.ghost.altar;

import gg.brim.kingdoms.KingdomsAddon;
import gg.brim.kingdoms.util.FoliaUtil;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages resurrection altars for kingdoms.
 */
public class AltarManager {
    
    private final KingdomsAddon plugin;
    private final File altarsFile;
    private FileConfiguration altarsConfig;
    
    // All altars (altar UUID -> Altar)
    private final Map<UUID, Altar> altars = new ConcurrentHashMap<>();
    
    // Altars by kingdom (kingdom ID -> List of Altar UUIDs)
    private final Map<String, List<UUID>> altarsByKingdom = new ConcurrentHashMap<>();
    
    // Interaction entity UUID -> Altar UUID mapping for quick lookup
    private final Map<UUID, UUID> interactionToAltar = new ConcurrentHashMap<>();
    
    // PDC keys
    private final NamespacedKey altarKey;
    private final NamespacedKey altarIdKey;
    private final NamespacedKey kingdomKey;
    
    public AltarManager(KingdomsAddon plugin) {
        this.plugin = plugin;
        this.altarsFile = new File(plugin.getDataFolder(), "altars.yml");
        
        this.altarKey = new NamespacedKey(plugin, "altar");
        this.altarIdKey = new NamespacedKey(plugin, "altar_id");
        this.kingdomKey = new NamespacedKey(plugin, "kingdom");
        
        loadAltars();
    }
    
    /**
     * Creates a new altar at the specified location.
     */
    public Altar createAltar(String kingdomId, Location location) {
        // Normalize location - remove pitch to prevent tilted altar
        Location normalizedLoc = location.clone();
        normalizedLoc.setPitch(0);
        
        Altar altar = new Altar(kingdomId, normalizedLoc);
        
        // Spawn entities using RegionScheduler for Folia safety
        FoliaUtil.runAtLocation(plugin, normalizedLoc, () -> {
            spawnAltarEntities(altar);
            
            // Register altar
            altars.put(altar.getAltarId(), altar);
            altarsByKingdom.computeIfAbsent(kingdomId, k -> new ArrayList<>()).add(altar.getAltarId());
            interactionToAltar.put(altar.getInteractionEntityUuid(), altar.getAltarId());
            
            // Start altar particles
            startAltarParticles(altar);
            
            saveAltars();
        });
        
        plugin.debug("Created altar for " + kingdomId + " at " + formatLocation(normalizedLoc));
        return altar;
    }
    
    /**
     * Starts repeating particle effect around an altar.
     */
    private void startAltarParticles(Altar altar) {
        Location loc = altar.getLocation().clone().add(0, 1.2, 0);
        
        FoliaUtil.runAtLocationRepeating(plugin, loc, () -> {
            // Check if altar still exists
            if (!altars.containsKey(altar.getAltarId())) {
                return;
            }
            
            World world = loc.getWorld();
            if (world == null) return;
            
            // Enchant particles
            world.spawnParticle(Particle.ENCHANT, loc.clone().add(0, 0.3, 0), 5, 0.4, 0.3, 0.4, 0.5);
            
            // Occasional portal particle
            if (Math.random() < 0.3) {
                world.spawnParticle(Particle.PORTAL, loc, 3, 0.2, 0.1, 0.2, 0.1);
            }
        }, 10L, 10L); // Every half second
    }
    
    /**
     * Spawns the BlockDisplay and Interaction entities for an altar.
     */
    private void spawnAltarEntities(Altar altar) {
        Location loc = altar.getLocation();
        World world = loc.getWorld();
        
        // Create BlockDisplay (lectern with book visual)
        BlockData lecternData = Bukkit.createBlockData("minecraft:lectern[has_book=true]");
        
        BlockDisplay display = world.spawn(loc, BlockDisplay.class, entity -> {
            entity.setBlock(lecternData);
            entity.setPersistent(true);
            entity.setViewRange(64.0f);
            
            // Center the display
            entity.setTransformation(new Transformation(
                    new Vector3f(-0.5f, 0, -0.5f),  // Translation to center
                    new AxisAngle4f(),              // No left rotation
                    new Vector3f(1.0f, 1.0f, 1.0f), // Normal scale
                    new AxisAngle4f()               // No right rotation
            ));
            
            // Set PDC data
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            pdc.set(altarKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(altarIdKey, PersistentDataType.STRING, altar.getAltarId().toString());
            pdc.set(kingdomKey, PersistentDataType.STRING, altar.getKingdomId());
        });
        
        altar.setDisplayEntity(display);
        
        // Create Interaction entity (hitbox) slightly offset for easier clicking
        Location hitboxLoc = loc.clone().add(0, 0.5, 0);
        
        Interaction interaction = world.spawn(hitboxLoc, Interaction.class, entity -> {
            entity.setInteractionWidth(1.0f);
            entity.setInteractionHeight(1.5f);
            entity.setResponsive(true);
            entity.setPersistent(true);
            
            // Set PDC data
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            pdc.set(altarKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(altarIdKey, PersistentDataType.STRING, altar.getAltarId().toString());
            pdc.set(kingdomKey, PersistentDataType.STRING, altar.getKingdomId());
        });
        
        altar.setInteractionEntity(interaction);
        
        // Spawn particles to indicate creation
        world.spawnParticle(Particle.ENCHANT, loc.clone().add(0.5, 1.5, 0.5), 50, 0.5, 0.5, 0.5, 0.1);
        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
    }
    
    /**
     * Removes an altar.
     */
    public boolean removeAltar(UUID altarId) {
        Altar altar = altars.remove(altarId);
        if (altar == null) return false;
        
        // Remove from kingdom list
        List<UUID> kingdomAltars = altarsByKingdom.get(altar.getKingdomId());
        if (kingdomAltars != null) {
            kingdomAltars.remove(altarId);
        }
        
        // Remove interaction mapping
        if (altar.getInteractionEntityUuid() != null) {
            interactionToAltar.remove(altar.getInteractionEntityUuid());
        }
        
        // Remove entities
        FoliaUtil.runAtLocation(plugin, altar.getLocation(), () -> {
            altar.remove();
        });
        
        saveAltars();
        plugin.debug("Removed altar " + altarId);
        return true;
    }
    
    /**
     * Relocates an altar to a new location.
     */
    public boolean relocateAltar(UUID altarId, Location newLocation) {
        Altar altar = altars.get(altarId);
        if (altar == null) return false;
        
        // Normalize location - remove pitch to prevent tilted altar
        Location normalizedLoc = newLocation.clone();
        normalizedLoc.setPitch(0);
        
        // Remove old entities
        FoliaUtil.runAtLocation(plugin, altar.getLocation(), () -> {
            altar.remove();
        });
        
        // Create new altar at new location with same ID
        Altar newAltar = new Altar(
                altar.getAltarId(),
                altar.getKingdomId(),
                normalizedLoc,
                null, null
        );
        
        // Spawn new entities
        FoliaUtil.runAtLocation(plugin, normalizedLoc, () -> {
            spawnAltarEntities(newAltar);
            
            // Update maps
            altars.put(altarId, newAltar);
            interactionToAltar.put(newAltar.getInteractionEntityUuid(), altarId);
            
            saveAltars();
        });
        
        plugin.debug("Relocated altar " + altarId + " to " + formatLocation(normalizedLoc));
        return true;
    }
    
    /**
     * Gets an altar by its ID.
     */
    @Nullable
    public Altar getAltar(UUID altarId) {
        return altars.get(altarId);
    }
    
    /**
     * Gets the altar associated with an Interaction entity.
     */
    @Nullable
    public Altar getAltarByInteraction(UUID interactionUuid) {
        UUID altarId = interactionToAltar.get(interactionUuid);
        if (altarId == null) return null;
        return altars.get(altarId);
    }
    
    /**
     * Checks if an entity is an altar interaction entity.
     */
    public boolean isAltarInteraction(Entity entity) {
        if (!(entity instanceof Interaction)) return false;
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return pdc.has(altarKey, PersistentDataType.BYTE);
    }
    
    /**
     * Gets all altars for a kingdom.
     */
    public List<Altar> getAltarsForKingdom(String kingdomId) {
        List<UUID> ids = altarsByKingdom.get(kingdomId);
        if (ids == null) return Collections.emptyList();
        
        List<Altar> result = new ArrayList<>();
        for (UUID id : ids) {
            Altar altar = altars.get(id);
            if (altar != null) {
                result.add(altar);
            }
        }
        return result;
    }
    
    /**
     * Gets the nearest altar for a kingdom from a location.
     */
    @Nullable
    public Altar getNearestAltar(String kingdomId, Location from) {
        List<Altar> kingdomAltars = getAltarsForKingdom(kingdomId);
        if (kingdomAltars.isEmpty()) return null;
        
        Altar nearest = null;
        double nearestDist = Double.MAX_VALUE;
        
        for (Altar altar : kingdomAltars) {
            if (!altar.getLocation().getWorld().equals(from.getWorld())) continue;
            
            double dist = altar.getLocation().distanceSquared(from);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = altar;
            }
        }
        
        return nearest;
    }
    
    /**
     * Gets all altars.
     */
    public Collection<Altar> getAllAltars() {
        return altars.values();
    }
    
    /**
     * Loads altars from the world by searching for entities with PDC markers.
     * Called on server startup to re-link entities.
     */
    public void reloadAltarEntities() {
        plugin.debug("Reloading altar entities from worlds...");
        
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Interaction interaction) {
                    PersistentDataContainer pdc = interaction.getPersistentDataContainer();
                    if (pdc.has(altarIdKey, PersistentDataType.STRING)) {
                        String altarIdStr = pdc.get(altarIdKey, PersistentDataType.STRING);
                        try {
                            UUID altarId = UUID.fromString(altarIdStr);
                            Altar altar = altars.get(altarId);
                            if (altar != null) {
                                altar.setInteractionEntity(interaction);
                                interactionToAltar.put(interaction.getUniqueId(), altarId);
                            }
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid altar ID in entity PDC: " + altarIdStr);
                        }
                    }
                } else if (entity instanceof BlockDisplay display) {
                    PersistentDataContainer pdc = display.getPersistentDataContainer();
                    if (pdc.has(altarIdKey, PersistentDataType.STRING)) {
                        String altarIdStr = pdc.get(altarIdKey, PersistentDataType.STRING);
                        try {
                            UUID altarId = UUID.fromString(altarIdStr);
                            Altar altar = altars.get(altarId);
                            if (altar != null) {
                                altar.setDisplayEntity(display);
                            }
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid altar ID in entity PDC: " + altarIdStr);
                        }
                    }
                }
            }
        }
        
        // Start particles for all loaded altars
        for (Altar altar : altars.values()) {
            startAltarParticles(altar);
        }
        
        plugin.debug("Started particles for " + altars.size() + " altars");
    }
    
    /**
     * Saves altars to file.
     */
    public void saveAltars() {
        altarsConfig = new YamlConfiguration();
        
        for (Map.Entry<UUID, Altar> entry : altars.entrySet()) {
            Altar altar = entry.getValue();
            String path = "altars." + entry.getKey().toString();
            
            altarsConfig.set(path + ".kingdom", altar.getKingdomId());
            
            Location loc = altar.getLocation();
            altarsConfig.set(path + ".location.world", loc.getWorld().getName());
            altarsConfig.set(path + ".location.x", loc.getX());
            altarsConfig.set(path + ".location.y", loc.getY());
            altarsConfig.set(path + ".location.z", loc.getZ());
            altarsConfig.set(path + ".location.yaw", loc.getYaw());
            altarsConfig.set(path + ".location.pitch", loc.getPitch());
            
            if (altar.getDisplayEntityUuid() != null) {
                altarsConfig.set(path + ".display-uuid", altar.getDisplayEntityUuid().toString());
            }
            if (altar.getInteractionEntityUuid() != null) {
                altarsConfig.set(path + ".interaction-uuid", altar.getInteractionEntityUuid().toString());
            }
        }
        
        try {
            altarsConfig.save(altarsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save altars: " + e.getMessage());
        }
    }
    
    /**
     * Loads altars from file.
     */
    private void loadAltars() {
        if (!altarsFile.exists()) {
            return;
        }
        
        altarsConfig = YamlConfiguration.loadConfiguration(altarsFile);
        ConfigurationSection altarsSection = altarsConfig.getConfigurationSection("altars");
        
        if (altarsSection == null) return;
        
        for (String uuidStr : altarsSection.getKeys(false)) {
            try {
                UUID altarId = UUID.fromString(uuidStr);
                String path = "altars." + uuidStr;
                
                String kingdomId = altarsConfig.getString(path + ".kingdom");
                String worldName = altarsConfig.getString(path + ".location.world");
                World world = Bukkit.getWorld(worldName);
                
                if (world == null) {
                    plugin.getLogger().warning("World " + worldName + " not found for altar " + uuidStr);
                    continue;
                }
                
                Location loc = new Location(
                        world,
                        altarsConfig.getDouble(path + ".location.x"),
                        altarsConfig.getDouble(path + ".location.y"),
                        altarsConfig.getDouble(path + ".location.z"),
                        (float) altarsConfig.getDouble(path + ".location.yaw"),
                        (float) altarsConfig.getDouble(path + ".location.pitch")
                );
                
                UUID displayUuid = null;
                UUID interactionUuid = null;
                
                if (altarsConfig.contains(path + ".display-uuid")) {
                    displayUuid = UUID.fromString(altarsConfig.getString(path + ".display-uuid"));
                }
                if (altarsConfig.contains(path + ".interaction-uuid")) {
                    interactionUuid = UUID.fromString(altarsConfig.getString(path + ".interaction-uuid"));
                }
                
                Altar altar = new Altar(altarId, kingdomId, loc, displayUuid, interactionUuid);
                altars.put(altarId, altar);
                altarsByKingdom.computeIfAbsent(kingdomId, k -> new ArrayList<>()).add(altarId);
                
                if (interactionUuid != null) {
                    interactionToAltar.put(interactionUuid, altarId);
                }
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load altar " + uuidStr + ": " + e.getMessage());
            }
        }
        
        plugin.getLogger().info("Loaded " + altars.size() + " altars.");
        
        // Schedule entity reload after worlds are fully loaded
        FoliaUtil.runGlobalDelayed(plugin, this::reloadAltarEntities, 100L);
    }
    
    private String formatLocation(Location loc) {
        return String.format("%s: %.1f, %.1f, %.1f", 
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }
}
