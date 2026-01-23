package su.brim.kingdoms.ghost;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Represents the state of a ghost (dead player waiting for resurrection).
 */
public class GhostState {
    
    private final UUID playerUuid;
    private final String playerName;
    private final String kingdomId;
    private final long deathTime;
    private final long durationMs;
    private final List<ItemStack> resurrectionCost;
    private final Location deathLocation;
    private final Location bedSpawnLocation; // Player's bed/anchor spawn at time of death
    
    // Resurrection data (set when resurrected while offline)
    private boolean pendingResurrection = false;
    private Location resurrectionLocation = null;
    private UUID resurrectedBy = null;
    
    // Flag to track if self-resurrect notification was sent
    private boolean selfResurrectNotified = false;
    
    public GhostState(UUID playerUuid, String playerName, String kingdomId, 
                      long deathTime, long durationMs, List<ItemStack> resurrectionCost, 
                      Location deathLocation, Location bedSpawnLocation) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.kingdomId = kingdomId;
        this.deathTime = deathTime;
        this.durationMs = durationMs;
        this.resurrectionCost = resurrectionCost;
        this.deathLocation = deathLocation;
        this.bedSpawnLocation = bedSpawnLocation;
    }
    
    /**
     * Legacy constructor for backward compatibility.
     */
    public GhostState(UUID playerUuid, String playerName, String kingdomId, 
                      long deathTime, long durationMs, List<ItemStack> resurrectionCost, Location deathLocation) {
        this(playerUuid, playerName, kingdomId, deathTime, durationMs, resurrectionCost, deathLocation, null);
    }
    
    /**
     * Gets the remaining time in milliseconds until self-resurrection is available.
     */
    public long getRemainingTimeMs() {
        return Math.max(0, (deathTime + durationMs) - System.currentTimeMillis());
    }
    
    /**
     * Checks if self-resurrection timer has expired.
     */
    public boolean canSelfResurrect() {
        return getRemainingTimeMs() <= 0;
    }
    
    /**
     * Formats remaining time as a human-readable string.
     */
    public String getFormattedRemainingTime() {
        long remainingMs = getRemainingTimeMs();
        if (remainingMs <= 0) {
            return "Готов к воскрешению";
        }
        
        long hours = remainingMs / (1000 * 60 * 60);
        long minutes = (remainingMs % (1000 * 60 * 60)) / (1000 * 60);
        long seconds = (remainingMs % (1000 * 60)) / 1000;
        
        if (hours > 0) {
            return String.format("%dч %dм", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dм %dс", minutes, seconds);
        } else {
            return String.format("%dс", seconds);
        }
    }
    
    // === Getters and Setters ===
    
    public UUID getPlayerUuid() {
        return playerUuid;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public String getKingdomId() {
        return kingdomId;
    }
    
    public long getDeathTime() {
        return deathTime;
    }
    
    public long getDurationMs() {
        return durationMs;
    }
    
    public List<ItemStack> getResurrectionCost() {
        return resurrectionCost;
    }
    
    public Location getDeathLocation() {
        return deathLocation;
    }
    
    public Location getBedSpawnLocation() {
        return bedSpawnLocation;
    }
    
    public boolean isPendingResurrection() {
        return pendingResurrection;
    }
    
    public void setPendingResurrection(boolean pendingResurrection) {
        this.pendingResurrection = pendingResurrection;
    }
    
    public Location getResurrectionLocation() {
        return resurrectionLocation;
    }
    
    public void setResurrectionLocation(Location resurrectionLocation) {
        this.resurrectionLocation = resurrectionLocation;
    }
    
    public UUID getResurrectedBy() {
        return resurrectedBy;
    }
    
    public void setResurrectedBy(UUID resurrectedBy) {
        this.resurrectedBy = resurrectedBy;
    }
    
    public boolean isSelfResurrectNotified() {
        return selfResurrectNotified;
    }
    
    public void setSelfResurrectNotified(boolean selfResurrectNotified) {
        this.selfResurrectNotified = selfResurrectNotified;
    }
}
