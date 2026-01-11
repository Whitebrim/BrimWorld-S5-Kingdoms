package gg.brim.kingdoms.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Utility class for Folia-compatible operations.
 * Handles scheduling and teleportation in a region-safe manner.
 */
public final class FoliaUtil {
    
    private FoliaUtil() {
        // Utility class
    }
    
    /**
     * Teleports an entity asynchronously using Folia's teleportAsync.
     * 
     * @param entity The entity to teleport
     * @param location The target location
     * @return CompletableFuture that completes with true if successful
     */
    public static CompletableFuture<Boolean> teleportAsync(Entity entity, Location location) {
        return entity.teleportAsync(location);
    }
    
    /**
     * Runs a task on the entity's owning region.
     * In Folia, this ensures the task runs on the correct thread.
     * 
     * @param plugin The plugin
     * @param entity The entity whose region to run on
     * @param task The task to run
     */
    public static void runOnEntity(Plugin plugin, Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }
    
    /**
     * Runs a task on the entity's owning region with a delay.
     * 
     * @param plugin The plugin
     * @param entity The entity whose region to run on
     * @param task The task to run
     * @param delayTicks Delay in ticks
     */
    public static void runDelayed(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
    }
    
    /**
     * Runs a task on the region owning the specified location.
     * 
     * @param plugin The plugin
     * @param location The location
     * @param task The task to run
     */
    public static void runAtLocation(Plugin plugin, Location location, Runnable task) {
        Bukkit.getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
    }
    
    /**
     * Runs a task on the region owning the specified location with a delay.
     * 
     * @param plugin The plugin
     * @param location The location
     * @param task The task to run
     * @param delayTicks Delay in ticks
     */
    public static void runAtLocationDelayed(Plugin plugin, Location location, Runnable task, long delayTicks) {
        Bukkit.getRegionScheduler().runDelayed(plugin, location, scheduledTask -> task.run(), delayTicks);
    }
    
    /**
     * Runs a task asynchronously.
     * 
     * @param plugin The plugin
     * @param task The task to run
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }
    
    /**
     * Runs a task asynchronously with a delay.
     * 
     * @param plugin The plugin
     * @param task The task to run
     * @param delayTicks Delay in ticks (converted to milliseconds)
     */
    public static void runAsyncDelayed(Plugin plugin, Runnable task, long delayTicks) {
        // Convert ticks to milliseconds (1 tick = 50ms)
        long delayMs = delayTicks * 50;
        Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), 
                delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    
    /**
     * Runs a task on the global region (for global operations like world time).
     * 
     * @param plugin The plugin
     * @param task The task to run
     */
    public static void runGlobal(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
    }
    
    /**
     * Runs a task on the global region with a delay.
     * 
     * @param plugin The plugin
     * @param task The task to run
     * @param delayTicks Delay in ticks
     */
    public static void runGlobalDelayed(Plugin plugin, Runnable task, long delayTicks) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks);
    }
    
    /**
     * Checks if the current thread owns the entity.
     * 
     * @param entity The entity to check
     * @return true if the current region owns the entity
     */
    public static boolean isOwnedByCurrentRegion(Entity entity) {
        return Bukkit.isOwnedByCurrentRegion(entity);
    }
    
    /**
     * Checks if the current thread owns the location.
     * 
     * @param location The location to check
     * @return true if the current region owns the location
     */
    public static boolean isOwnedByCurrentRegion(Location location) {
        return Bukkit.isOwnedByCurrentRegion(location);
    }
    
    /**
     * Schedules a repeating task on the entity's region.
     * 
     * @param plugin The plugin
     * @param entity The entity
     * @param task The task to run
     * @param initialDelayTicks Initial delay in ticks
     * @param periodTicks Period in ticks
     */
    public static void runRepeatingOnEntity(Plugin plugin, Entity entity, Consumer<Runnable> task, 
                                            long initialDelayTicks, long periodTicks) {
        entity.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
            task.accept(() -> scheduledTask.cancel());
        }, null, initialDelayTicks, periodTicks);
    }
    
    /**
     * Runs a task on the global region repeatedly.
     * 
     * @param plugin The plugin
     * @param task The task to run
     * @param initialDelayTicks Initial delay in ticks
     * @param periodTicks Period in ticks
     */
    public static void runGlobalRepeating(Plugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), initialDelayTicks, periodTicks);
    }
}
