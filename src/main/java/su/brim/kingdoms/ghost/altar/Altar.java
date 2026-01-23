package su.brim.kingdoms.ghost.altar;

import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;

import java.util.UUID;

/**
 * Represents an altar for resurrection.
 * Consists of a BlockDisplay (visual) and Interaction (hitbox) entity.
 */
public class Altar {
    
    private final UUID altarId;
    private final String kingdomId;
    private final Location location;
    
    // Entity UUIDs (for persistence and lookup)
    private UUID displayEntityUuid;
    private UUID interactionEntityUuid;
    
    // Transient entity references (not persisted)
    private transient BlockDisplay displayEntity;
    private transient Interaction interactionEntity;
    
    public Altar(String kingdomId, Location location) {
        this.altarId = UUID.randomUUID();
        this.kingdomId = kingdomId;
        this.location = location.clone();
    }
    
    /**
     * Constructor for loading from persistence.
     */
    public Altar(UUID altarId, String kingdomId, Location location, 
                 UUID displayEntityUuid, UUID interactionEntityUuid) {
        this.altarId = altarId;
        this.kingdomId = kingdomId;
        this.location = location;
        this.displayEntityUuid = displayEntityUuid;
        this.interactionEntityUuid = interactionEntityUuid;
    }
    
    /**
     * Checks if the altar entities are valid and loaded.
     */
    public boolean isValid() {
        return displayEntity != null && displayEntity.isValid() &&
               interactionEntity != null && interactionEntity.isValid();
    }
    
    /**
     * Removes the altar entities from the world.
     */
    public void remove() {
        if (displayEntity != null && displayEntity.isValid()) {
            displayEntity.remove();
        }
        if (interactionEntity != null && interactionEntity.isValid()) {
            interactionEntity.remove();
        }
        displayEntity = null;
        interactionEntity = null;
    }
    
    // === Getters and Setters ===
    
    public UUID getAltarId() {
        return altarId;
    }
    
    public String getKingdomId() {
        return kingdomId;
    }
    
    public Location getLocation() {
        return location.clone();
    }
    
    public UUID getDisplayEntityUuid() {
        return displayEntityUuid;
    }
    
    public void setDisplayEntityUuid(UUID displayEntityUuid) {
        this.displayEntityUuid = displayEntityUuid;
    }
    
    public UUID getInteractionEntityUuid() {
        return interactionEntityUuid;
    }
    
    public void setInteractionEntityUuid(UUID interactionEntityUuid) {
        this.interactionEntityUuid = interactionEntityUuid;
    }
    
    public BlockDisplay getDisplayEntity() {
        return displayEntity;
    }
    
    public void setDisplayEntity(BlockDisplay displayEntity) {
        this.displayEntity = displayEntity;
        if (displayEntity != null) {
            this.displayEntityUuid = displayEntity.getUniqueId();
        }
    }
    
    public Interaction getInteractionEntity() {
        return interactionEntity;
    }
    
    public void setInteractionEntity(Interaction interactionEntity) {
        this.interactionEntity = interactionEntity;
        if (interactionEntity != null) {
            this.interactionEntityUuid = interactionEntity.getUniqueId();
        }
    }
}
