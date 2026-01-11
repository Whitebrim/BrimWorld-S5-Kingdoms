package gg.brim.kingdoms.commands;

import gg.brim.kingdoms.KingdomsAddon;
import gg.brim.kingdoms.config.MessagesConfig;
import gg.brim.kingdoms.manager.KingdomManager;
import gg.brim.kingdoms.manager.SpawnManager;
import gg.brim.kingdoms.util.FoliaUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main command handler for KingdomsAddon.
 */
public class KingdomsCommand implements CommandExecutor, TabCompleter {
    
    private final KingdomsAddon plugin;
    
    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "setspawn", "reload", "info", "assign", "list", "help", "altar", "resurrect", "debug"
    );
    
    public KingdomsCommand(KingdomsAddon plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                             @NotNull String label, @NotNull String[] args) {
        
        if (args.length == 0) {
            return handleHelp(sender);
        }
        
        String subcommand = args[0].toLowerCase();
        
        return switch (subcommand) {
            case "setspawn" -> handleSetSpawn(sender, args);
            case "reload" -> handleReload(sender);
            case "info" -> handleInfo(sender, args);
            case "assign" -> handleAssign(sender, args);
            case "list" -> handleList(sender);
            case "help" -> handleHelp(sender);
            case "altar" -> handleAltar(sender, args);
            case "resurrect" -> handleResurrect(sender);
            case "debug" -> handleDebug(sender);
            default -> {
                sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("commands.unknown"));
                yield true;
            }
        };
    }
    
    /**
     * Handles /kingdoms setspawn <kingdom>
     */
    private boolean handleSetSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kingdoms.setspawn")) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("plugin.no-permission"));
            return true;
        }
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("plugin.player-only"));
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "commands.usage",
                    MessagesConfig.placeholder("usage", "/kingdoms setspawn <kingdom>")
            ));
            return true;
        }
        
        String kingdomId = args[1].toLowerCase();
        
        if (!KingdomManager.ALL_KINGDOMS.contains(kingdomId)) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "commands.invalid-kingdom",
                    MessagesConfig.placeholder("kingdom", args[1])
            ));
            return true;
        }
        
        Location loc = player.getLocation();
        plugin.getSpawnManager().setSpawn(kingdomId, loc);
        
        String displayName = plugin.getConfigManager().getKingdomDisplayName(kingdomId);
        sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                "commands.spawn-set",
                MessagesConfig.placeholders()
                        .add("kingdom", displayName)
                        .add("location", SpawnManager.formatLocation(loc))
                        .build()
        ));
        
        return true;
    }
    
    /**
     * Handles /kingdoms reload
     */
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("kingdoms.reload")) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("plugin.no-permission"));
            return true;
        }
        
        plugin.reload();
        sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("commands.reloaded"));
        
        return true;
    }
    
    /**
     * Handles /kingdoms info [kingdom]
     */
    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kingdoms.info")) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("plugin.no-permission"));
            return true;
        }
        
        String kingdomId;
        
        if (args.length < 2) {
            // If player, show their kingdom info
            if (sender instanceof Player player) {
                String playerKingdom = plugin.getKingdomManager().getPlayerKingdomId(player.getUniqueId());
                if (playerKingdom == null) {
                    sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                            "commands.usage",
                            MessagesConfig.placeholder("usage", "/kingdoms info <kingdom>")
                    ));
                    return true;
                }
                kingdomId = playerKingdom;
            } else {
                sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                        "commands.usage",
                        MessagesConfig.placeholder("usage", "/kingdoms info <kingdom>")
                ));
                return true;
            }
        } else {
            kingdomId = args[1].toLowerCase();
        }
        
        if (!KingdomManager.ALL_KINGDOMS.contains(kingdomId)) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "commands.invalid-kingdom",
                    MessagesConfig.placeholder("kingdom", args.length > 1 ? args[1] : kingdomId)
            ));
            return true;
        }
        
        showKingdomInfo(sender, kingdomId);
        return true;
    }
    
    /**
     * Shows detailed info about a kingdom.
     */
    private void showKingdomInfo(CommandSender sender, String kingdomId) {
        String displayName = plugin.getConfigManager().getKingdomDisplayName(kingdomId);
        
        // Header
        sender.sendMessage(plugin.getMessagesConfig().getComponent(
                "kingdom.info-header",
                MessagesConfig.placeholder("kingdom", displayName)
        ));
        
        // Members count
        int whitelistCount = plugin.getKingdomManager().getWhitelistCount(kingdomId);
        int onlineCount = plugin.getKingdomManager().getOnlineMemberCount(kingdomId);
        int totalMembers = plugin.getKingdomManager().getTotalMemberCount(kingdomId);
        
        sender.sendMessage(plugin.getMessagesConfig().getComponent(
                "kingdom.info-members",
                MessagesConfig.placeholders()
                        .add("count", totalMembers + " (онлайн: " + onlineCount + ", в списке: " + whitelistCount + ")")
                        .build()
        ));
        
        // Spawn
        Location spawn = plugin.getSpawnManager().getSpawn(kingdomId);
        if (spawn != null) {
            sender.sendMessage(plugin.getMessagesConfig().getComponent(
                    "kingdom.info-spawn",
                    MessagesConfig.placeholder("location", SpawnManager.formatLocation(spawn))
            ));
        } else {
            sender.sendMessage(plugin.getMessagesConfig().getComponent("kingdom.info-spawn-not-set"));
        }
    }
    
    /**
     * Handles /kingdoms assign <player> <kingdom>
     */
    private boolean handleAssign(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kingdoms.assign")) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("plugin.no-permission"));
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "commands.usage",
                    MessagesConfig.placeholder("usage", "/kingdoms assign <player> <kingdom>")
            ));
            return true;
        }
        
        String playerName = args[1];
        String kingdomId = args[2].toLowerCase();
        
        if (!KingdomManager.ALL_KINGDOMS.contains(kingdomId)) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "commands.invalid-kingdom",
                    MessagesConfig.placeholder("kingdom", args[2])
            ));
            return true;
        }
        
        // Add to whitelist
        boolean added = plugin.getKingdomManager().addPlayerToWhitelist(playerName, kingdomId);
        
        if (added) {
            String displayName = plugin.getConfigManager().getKingdomDisplayName(kingdomId);
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "commands.player-assigned",
                    MessagesConfig.placeholders()
                            .add("player", playerName)
                            .add("kingdom", displayName)
                            .build()
            ));
            
            // If player is online, assign them immediately
            Player target = Bukkit.getPlayerExact(playerName);
            if (target != null) {
                plugin.getKingdomManager().assignPlayerToKingdom(target, kingdomId);
                target.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                        "kingdom.joined",
                        MessagesConfig.placeholder("kingdom", displayName)
                ));
            }
        } else {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "commands.player-already-assigned",
                    MessagesConfig.placeholder("player", playerName)
            ));
        }
        
        return true;
    }
    
    /**
     * Handles /kingdoms list
     */
    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("kingdoms.info")) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("plugin.no-permission"));
            return true;
        }
        
        sender.sendMessage(plugin.getMessagesConfig().getComponent("kingdom.list-header"));
        
        for (String kingdomId : KingdomManager.ALL_KINGDOMS) {
            String displayName = plugin.getConfigManager().getKingdomDisplayName(kingdomId);
            int online = plugin.getKingdomManager().getOnlineMemberCount(kingdomId);
            int total = plugin.getKingdomManager().getTotalMemberCount(kingdomId);
            
            sender.sendMessage(plugin.getMessagesConfig().getComponent(
                    "kingdom.list-entry",
                    MessagesConfig.placeholders()
                            .add("kingdom", displayName)
                            .add("id", kingdomId)
                            .add("online", online)
                            .add("total", total)
                            .build()
            ));
        }
        
        return true;
    }
    
    /**
     * Handles /kingdoms help
     */
    private boolean handleHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessagesConfig().getComponent("commands.help.header"));
        sender.sendMessage(plugin.getMessagesConfig().getComponent("commands.help.setspawn"));
        sender.sendMessage(plugin.getMessagesConfig().getComponent("commands.help.reload"));
        sender.sendMessage(plugin.getMessagesConfig().getComponent("commands.help.info"));
        sender.sendMessage(plugin.getMessagesConfig().getComponent("commands.help.assign"));
        sender.sendMessage(plugin.getMessagesConfig().getComponent("commands.help.list"));
        
        // Add ghost system commands if enabled
        if (plugin.getConfig().getBoolean("ghost-system.enabled", false)) {
            sender.sendMessage(plugin.getMessagesConfig().getComponent("commands.help.altar"));
            sender.sendMessage(plugin.getMessagesConfig().getComponent("commands.help.resurrect"));
        }
        
        return true;
    }
    
    /**
     * Handles /kingdoms altar <create|remove|list|tp> [kingdom]
     */
    private boolean handleAltar(CommandSender sender, String[] args) {
        if (!plugin.getConfig().getBoolean("ghost-system.enabled", false)) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("commands.ghost-system-disabled"));
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "commands.usage",
                    MessagesConfig.placeholder("usage", "/kingdoms altar <create|remove|list|tp> [kingdom]")
            ));
            return true;
        }
        
        String action = args[1].toLowerCase();
        
        return switch (action) {
            case "create" -> handleAltarCreate(sender, args);
            case "remove" -> handleAltarRemove(sender, args);
            case "list" -> handleAltarList(sender, args);
            case "tp" -> handleAltarTp(sender, args);
            default -> {
                sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                        "commands.usage",
                        MessagesConfig.placeholder("usage", "/kingdoms altar <create|remove|list|tp> [kingdom]")
                ));
                yield true;
            }
        };
    }
    
    /**
     * Handles /kingdoms altar create <kingdom>
     */
    private boolean handleAltarCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kingdoms.admin")) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("plugin.no-permission"));
            return true;
        }
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("plugin.player-only"));
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "commands.usage",
                    MessagesConfig.placeholder("usage", "/kingdoms altar create <kingdom>")
            ));
            return true;
        }
        
        String kingdomId = args[2].toLowerCase();
        
        if (!KingdomManager.ALL_KINGDOMS.contains(kingdomId)) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "commands.invalid-kingdom",
                    MessagesConfig.placeholder("kingdom", args[2])
            ));
            return true;
        }
        
        Location loc = player.getLocation();
        plugin.getAltarManager().createAltar(kingdomId, loc);
        
        String displayName = plugin.getConfigManager().getKingdomDisplayName(kingdomId);
        sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                "ghost.altar.created",
                MessagesConfig.placeholder("kingdom", displayName)
        ));
        
        return true;
    }
    
    /**
     * Handles /kingdoms altar remove <kingdom> [index]
     */
    private boolean handleAltarRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kingdoms.admin")) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("plugin.no-permission"));
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "commands.usage",
                    MessagesConfig.placeholder("usage", "/kingdoms altar remove <kingdom> [index]")
            ));
            return true;
        }
        
        String kingdomId = args[2].toLowerCase();
        
        if (!KingdomManager.ALL_KINGDOMS.contains(kingdomId)) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "commands.invalid-kingdom",
                    MessagesConfig.placeholder("kingdom", args[2])
            ));
            return true;
        }
        
        var altars = plugin.getAltarManager().getAltarsForKingdom(kingdomId);
        if (altars.isEmpty()) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.altar.not-found"));
            return true;
        }
        
        int index = 0;
        if (args.length >= 4) {
            try {
                index = Integer.parseInt(args[3]) - 1;
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                        "commands.usage",
                        MessagesConfig.placeholder("usage", "/kingdoms altar remove <kingdom> [index]")
                ));
                return true;
            }
        }
        
        if (index < 0 || index >= altars.size()) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.altar.not-found"));
            return true;
        }
        
        plugin.getAltarManager().removeAltar(altars.get(index).getAltarId());
        sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.altar.removed"));
        
        return true;
    }
    
    /**
     * Handles /kingdoms altar list [kingdom]
     */
    private boolean handleAltarList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kingdoms.info")) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("plugin.no-permission"));
            return true;
        }
        
        String kingdomId = null;
        if (args.length >= 3) {
            kingdomId = args[2].toLowerCase();
            if (!KingdomManager.ALL_KINGDOMS.contains(kingdomId)) {
                sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                        "commands.invalid-kingdom",
                        MessagesConfig.placeholder("kingdom", args[2])
                ));
                return true;
            }
        }
        
        // If no kingdom specified, try to get player's kingdom
        if (kingdomId == null && sender instanceof Player player) {
            kingdomId = plugin.getKingdomManager().getPlayerKingdomId(player.getUniqueId());
        }
        
        if (kingdomId == null) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "commands.usage",
                    MessagesConfig.placeholder("usage", "/kingdoms altar list <kingdom>")
            ));
            return true;
        }
        
        String displayName = plugin.getConfigManager().getKingdomDisplayName(kingdomId);
        var altars = plugin.getAltarManager().getAltarsForKingdom(kingdomId);
        
        sender.sendMessage(plugin.getMessagesConfig().getComponent(
                "ghost.altar.list-header",
                MessagesConfig.placeholder("kingdom", displayName)
        ));
        
        if (altars.isEmpty()) {
            sender.sendMessage(plugin.getMessagesConfig().getComponent("ghost.altar.list-empty"));
        } else {
            int index = 1;
            for (var altar : altars) {
                Location loc = altar.getLocation();
                String locStr = String.format("%s: %.1f, %.1f, %.1f",
                        loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
                sender.sendMessage(plugin.getMessagesConfig().getComponent(
                        "ghost.altar.list-entry",
                        MessagesConfig.placeholders()
                                .add("index", index)
                                .add("location", locStr)
                                .build()
                ));
                index++;
            }
        }
        
        return true;
    }
    
    /**
     * Handles /kingdoms altar tp <kingdom> [index]
     */
    private boolean handleAltarTp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kingdoms.admin")) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("plugin.no-permission"));
            return true;
        }
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("plugin.player-only"));
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "commands.usage",
                    MessagesConfig.placeholder("usage", "/kingdoms altar tp <kingdom> [index]")
            ));
            return true;
        }
        
        String kingdomId = args[2].toLowerCase();
        
        if (!KingdomManager.ALL_KINGDOMS.contains(kingdomId)) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix(
                    "commands.invalid-kingdom",
                    MessagesConfig.placeholder("kingdom", args[2])
            ));
            return true;
        }
        
        var altars = plugin.getAltarManager().getAltarsForKingdom(kingdomId);
        if (altars.isEmpty()) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.altar.not-found"));
            return true;
        }
        
        int index = 0;
        if (args.length >= 4) {
            try {
                index = Integer.parseInt(args[3]) - 1;
            } catch (NumberFormatException e) {
                index = 0;
            }
        }
        
        if (index < 0 || index >= altars.size()) {
            index = 0;
        }
        
        Location loc = altars.get(index).getLocation().clone().add(0.5, 1, 0.5);
        FoliaUtil.teleportAsync(player, loc);
        
        return true;
    }
    
    /**
     * Handles /kingdoms resurrect (self-resurrect for ghosts)
     */
    private boolean handleResurrect(CommandSender sender) {
        if (!plugin.getConfig().getBoolean("ghost-system.enabled", false)) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("commands.ghost-system-disabled"));
            return true;
        }
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("plugin.player-only"));
            return true;
        }
        
        if (!plugin.getGhostManager().isGhost(player.getUniqueId())) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("ghost.not-a-ghost"));
            return true;
        }
        
        plugin.getGhostManager().performSelfResurrect(player);
        return true;
    }
    
    /**
     * Handles /kingdoms debug - shows diagnostic info
     */
    private boolean handleDebug(CommandSender sender) {
        if (!sender.hasPermission("kingdoms.admin")) {
            sender.sendMessage(plugin.getMessagesConfig().getComponentWithPrefix("plugin.no-permission"));
            return true;
        }
        
        sender.sendMessage("§6=== KingdomsAddon Debug Info ===");
        
        // Config status
        sender.sendMessage("§eConfig:");
        sender.sendMessage("  §7teleport.on-first-join: §f" + plugin.getConfigManager().isTeleportOnFirstJoin());
        sender.sendMessage("  §7teleport.on-death-no-respawn: §f" + plugin.getConfigManager().isTeleportOnDeathNoRespawn());
        sender.sendMessage("  §7teleport.delay-ticks: §f" + plugin.getConfigManager().getTeleportDelayTicks());
        sender.sendMessage("  §7debug: §f" + plugin.getConfigManager().isDebug());
        
        // Spawns status
        sender.sendMessage("§eSpawns:");
        for (String kingdomId : KingdomManager.ALL_KINGDOMS) {
            Location spawn = plugin.getSpawnManager().getSpawn(kingdomId);
            if (spawn != null) {
                sender.sendMessage("  §a" + kingdomId + ": §f" + spawn.getWorld().getName() + 
                        " " + String.format("%.1f, %.1f, %.1f", spawn.getX(), spawn.getY(), spawn.getZ()));
            } else {
                sender.sendMessage("  §c" + kingdomId + ": §7NOT SET");
            }
        }
        
        // Player info if sender is player
        if (sender instanceof Player player) {
            sender.sendMessage("§eYour Info:");
            String kingdom = plugin.getKingdomManager().getPlayerKingdomId(player.getUniqueId());
            sender.sendMessage("  §7Kingdom: §f" + (kingdom != null ? kingdom : "NONE"));
            sender.sendMessage("  §7Has joined before: §f" + plugin.getPlayerDataManager().hasJoinedBefore(player.getUniqueId()));
            sender.sendMessage("  §7Current location: §f" + player.getWorld().getName() + 
                    " " + String.format("%.1f, %.1f, %.1f", player.getLocation().getX(), 
                            player.getLocation().getY(), player.getLocation().getZ()));
            
            // Bed spawn
            Location bedSpawn = player.getBedSpawnLocation();
            sender.sendMessage("  §7Bed spawn: §f" + (bedSpawn != null ? 
                    bedSpawn.getWorld().getName() + " " + String.format("%.1f, %.1f, %.1f", 
                            bedSpawn.getX(), bedSpawn.getY(), bedSpawn.getZ()) : "NONE"));
        }
        
        return true;
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // First argument - subcommands
            String partial = args[0].toLowerCase();
            completions.addAll(SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList()));
        } else if (args.length == 2) {
            String subcommand = args[0].toLowerCase();
            String partial = args[1].toLowerCase();
            
            switch (subcommand) {
                case "setspawn", "info" -> {
                    completions.addAll(KingdomManager.ALL_KINGDOMS.stream()
                            .filter(k -> k.startsWith(partial))
                            .collect(Collectors.toList()));
                }
                case "assign" -> {
                    // Online player names
                    completions.addAll(Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(partial))
                            .collect(Collectors.toList()));
                }
                case "altar" -> {
                    // Altar subcommands
                    completions.addAll(Arrays.asList("create", "remove", "list", "tp").stream()
                            .filter(s -> s.startsWith(partial))
                            .collect(Collectors.toList()));
                }
            }
        } else if (args.length == 3) {
            String subcommand = args[0].toLowerCase();
            String partial = args[2].toLowerCase();
            
            if (subcommand.equals("assign") || (subcommand.equals("altar") && 
                    Arrays.asList("create", "remove", "list", "tp").contains(args[1].toLowerCase()))) {
                completions.addAll(KingdomManager.ALL_KINGDOMS.stream()
                        .filter(k -> k.startsWith(partial))
                        .collect(Collectors.toList()));
            }
        }
        
        return completions;
    }
}
