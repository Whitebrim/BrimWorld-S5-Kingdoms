package gg.brim.kingdoms.listeners;

import gg.brim.kingdoms.KingdomsAddon;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RespawnHook implements Listener {

    private final KingdomsAddon plugin;
    private final Map<UUID, ScheduledTask> waiting = new ConcurrentHashMap<>();

    public RespawnHook(KingdomsAddon plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        if (waiting.containsKey(uuid)) {
            return;
        }

        ScheduledTask task = player.getScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> {

                    if (!player.isOnline()) {
                        cleanup(uuid, scheduledTask);
                        return;
                    }

                    if (player.isDead()) {
                        return; // ждём реального респавна
                    }

                    Location vanillaRespawn = player.getRespawnLocation();
                    if (vanillaRespawn != null) {
                        cleanup(uuid, scheduledTask);
                        return;
                    }

                    if (!plugin.getConfigManager().isTeleportOnDeathNoRespawn()) {
                        cleanup(uuid, scheduledTask);
                        return;
                    }

                    String kingdomId =
                            plugin.getKingdomManager().getPlayerKingdomId(uuid);

                    if (kingdomId == null) {
                        cleanup(uuid, scheduledTask);
                        return;
                    }

                    Location spawn =
                            plugin.getSpawnManager().getSpawn(kingdomId);

                    if (spawn == null || spawn.getWorld() == null) {
                        cleanup(uuid, scheduledTask);
                        return;
                    }

                    player.teleportAsync(spawn);
                    cleanup(uuid, scheduledTask);
                },
                () -> waiting.remove(uuid),
                1L,
                4L
        );

        waiting.put(uuid, task);
    }

    private void cleanup(UUID uuid, ScheduledTask task) {
        task.cancel();
        waiting.remove(uuid);
    }
}
