package su.brim.kingdoms.config;

import su.brim.kingdoms.KingdomsAddon;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

/**
 * Handles automatic configuration updates based on config version.
 * Merges new default values into existing configs without losing user settings.
 */
public class ConfigUpdater {
    
    private final KingdomsAddon plugin;
    
    // Current config version in the plugin
    private static final int CURRENT_CONFIG_VERSION = 2;
    
    public ConfigUpdater(KingdomsAddon plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Updates the main config.yml with any new values from default.
     * Uses version-based migration for breaking changes.
     */
    public void updateConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        
        // If file doesn't exist, just save the default
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
            plugin.getLogger().info("Created config.yml (version " + CURRENT_CONFIG_VERSION + ")");
            return;
        }
        
        try {
            FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
            int currentVersion = currentConfig.getInt("config-version", 1);
            
            if (currentVersion >= CURRENT_CONFIG_VERSION) {
                // Config is up to date, just check for missing keys
                updateConfigFile("config.yml");
                return;
            }
            
            plugin.getLogger().info("Updating config.yml from version " + currentVersion + " to " + CURRENT_CONFIG_VERSION);
            
            // Apply migrations sequentially
            for (int version = currentVersion; version < CURRENT_CONFIG_VERSION; version++) {
                applyMigration(currentConfig, version, version + 1);
            }
            
            // Set new version
            currentConfig.set("config-version", CURRENT_CONFIG_VERSION);
            
            // Save the migrated config
            currentConfig.save(configFile);
            
            // Now merge any new keys from default
            updateConfigFile("config.yml");
            
            plugin.getLogger().info("Config migration complete!");
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to update config.yml", e);
        }
    }
    
    /**
     * Applies migration from one version to the next.
     */
    private void applyMigration(FileConfiguration config, int fromVersion, int toVersion) {
        plugin.getLogger().info("Applying migration: v" + fromVersion + " -> v" + toVersion);
        
        switch (toVersion) {
            case 2:
                // Migration from v1 to v2:
                // - Removed team-colors.chat-enabled (now handled by PlaceholderAPI)
                // - Added config-version
                migrateV1toV2(config);
                break;
            // Add future migrations here:
            // case 3:
            //     migrateV2toV3(config);
            //     break;
        }
    }
    
    /**
     * Migration from version 1 to version 2.
     * Removes chat-enabled setting as chat colors are now handled via PlaceholderAPI.
     */
    private void migrateV1toV2(FileConfiguration config) {
        // Remove deprecated chat-enabled setting
        if (config.contains("team-colors.chat-enabled")) {
            config.set("team-colors.chat-enabled", null);
            plugin.getLogger().info("  - Removed team-colors.chat-enabled (use PlaceholderAPI for chat colors)");
        }
        
        // Ensure ghost-prefix exists
        if (!config.contains("team-colors.ghost-prefix")) {
            config.set("team-colors.ghost-prefix", "§7§o☠ ");
            plugin.getLogger().info("  - Added team-colors.ghost-prefix");
        }
    }
    
    /**
     * Updates messages.yml with any new values from default.
     */
    public void updateMessages() {
        updateConfigFile("messages.yml");
    }
    
    /**
     * Updates a specific config file by merging defaults.
     * Preserves existing user values and adds new keys from the default config.
     * @param filename The config file name (e.g., "config.yml")
     */
    public void updateConfigFile(String filename) {
        File configFile = new File(plugin.getDataFolder(), filename);
        
        // If file doesn't exist, just save the default
        if (!configFile.exists()) {
            plugin.saveResource(filename, false);
            plugin.getLogger().info("Created " + filename);
            return;
        }
        
        try {
            // Load the current config
            FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
            
            // Load the default config from jar
            InputStream defaultStream = plugin.getResource(filename);
            if (defaultStream == null) {
                plugin.getLogger().warning("Could not find default " + filename + " in jar!");
                return;
            }
            
            InputStreamReader reader = new InputStreamReader(defaultStream, StandardCharsets.UTF_8);
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(reader);
            reader.close();
            
            // Track if any changes were made
            List<String> addedKeys = new ArrayList<>();
            List<String> removedKeys = new ArrayList<>();
            
            // Find keys to add (in default but not in current)
            findMissingKeys(currentConfig, defaultConfig, "", addedKeys);
            
            // Add missing keys
            for (String key : addedKeys) {
                currentConfig.set(key, defaultConfig.get(key));
            }
            
            // If changes were made, save the config
            if (!addedKeys.isEmpty()) {
                saveConfigPreservingComments(configFile, filename, currentConfig, addedKeys);
                
                plugin.getLogger().info("Updated " + filename + " with " + addedKeys.size() + " new key(s):");
                for (String key : addedKeys) {
                    plugin.getLogger().info("  + " + key);
                }
            } else {
                plugin.debug(filename + " is up to date.");
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to update " + filename, e);
        }
    }
    
    /**
     * Recursively finds keys that exist in default but not in current config.
     */
    private void findMissingKeys(FileConfiguration current, FileConfiguration defaults, 
                                  String path, List<String> missingKeys) {
        Set<String> defaultKeys = path.isEmpty() 
                ? defaults.getKeys(false) 
                : defaults.getConfigurationSection(path) != null 
                    ? defaults.getConfigurationSection(path).getKeys(false) 
                    : Collections.emptySet();
        
        for (String key : defaultKeys) {
            String fullPath = path.isEmpty() ? key : path + "." + key;
            Object defaultValue = defaults.get(fullPath);
            
            if (defaultValue instanceof ConfigurationSection) {
                // It's a section, recurse
                if (!current.isConfigurationSection(fullPath)) {
                    // Section doesn't exist in current, add whole section
                    missingKeys.add(fullPath);
                } else {
                    // Section exists, check children
                    findMissingKeys(current, defaults, fullPath, missingKeys);
                }
            } else {
                // It's a value
                if (!current.contains(fullPath)) {
                    missingKeys.add(fullPath);
                }
            }
        }
    }
    
    /**
     * Saves the config while trying to preserve structure and add new keys at appropriate places.
     */
    private void saveConfigPreservingComments(File configFile, String resourceName, 
                                               FileConfiguration config, List<String> addedKeys) {
        try {
            // Read default file to get comments
            InputStream defaultStream = plugin.getResource(resourceName);
            if (defaultStream == null) {
                config.save(configFile);
                return;
            }
            
            // Read current file lines
            List<String> currentLines = new ArrayList<>();
            try (BufferedReader currentReader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = currentReader.readLine()) != null) {
                    currentLines.add(line);
                }
            }
            
            // Read default file to extract comments for new keys
            Map<String, List<String>> keyComments = extractComments(defaultStream);
            
            // Build result - existing content + new keys with their comments
            StringBuilder result = new StringBuilder();
            
            // Add existing content
            for (String line : currentLines) {
                result.append(line).append("\n");
            }
            
            // Group added keys by their parent section
            Map<String, List<String>> keysByParent = new LinkedHashMap<>();
            for (String key : addedKeys) {
                String parent = key.contains(".") ? key.substring(0, key.lastIndexOf('.')) : "";
                keysByParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(key);
            }
            
            // Add new keys at the end of their parent sections
            // For simplicity, append at the end (a smarter implementation would insert in place)
            for (String key : addedKeys) {
                // Add blank line and comments if available
                List<String> comments = keyComments.get(key);
                if (comments != null && !comments.isEmpty()) {
                    result.append("\n");
                    for (String comment : comments) {
                        result.append(comment).append("\n");
                    }
                }
                
                // Format and add the key-value pair
                String[] parts = key.split("\\.");
                int indent = parts.length - 1;
                String spaces = "  ".repeat(indent);
                String keyName = parts[parts.length - 1];
                Object value = config.get(key);
                
                String formattedValue = formatYamlValue(value, indent);
                result.append(spaces).append(keyName).append(": ").append(formattedValue).append("\n");
            }
            
            // Write result
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8))) {
                writer.write(result.toString());
            }
            
        } catch (Exception e) {
            // Fallback: just save normally
            try {
                config.save(configFile);
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to save config: " + ex.getMessage());
            }
        }
    }
    
    /**
     * Extracts comments from a YAML file.
     * Returns a map of key path -> list of comment lines before that key.
     */
    private Map<String, List<String>> extractComments(InputStream stream) throws IOException {
        Map<String, List<String>> comments = new HashMap<>();
        List<String> pendingComments = new ArrayList<>();
        Stack<String> pathStack = new Stack<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                
                // Collect comments and blank lines
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    pendingComments.add(line);
                    continue;
                }
                
                // Calculate path based on indentation
                int currentIndent = getIndent(line);
                
                // Adjust path stack based on indentation
                while (!pathStack.isEmpty() && pathStack.size() > currentIndent / 2) {
                    pathStack.pop();
                }
                
                // Extract key name (before the colon)
                int colonIndex = trimmed.indexOf(':');
                if (colonIndex > 0) {
                    String key = trimmed.substring(0, colonIndex).trim();
                    pathStack.push(key);
                    String fullPath = String.join(".", pathStack);
                    
                    // Save comments for this key
                    if (!pendingComments.isEmpty()) {
                        comments.put(fullPath, new ArrayList<>(pendingComments));
                        pendingComments.clear();
                    }
                }
            }
        }
        
        return comments;
    }
    
    /**
     * Gets the indentation level of a line (number of leading spaces).
     */
    private int getIndent(String line) {
        int indent = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') {
                indent++;
            } else {
                break;
            }
        }
        return indent;
    }
    
    /**
     * Formats a value for YAML output.
     */
    private String formatYamlValue(Object value, int baseIndent) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            String str = (String) value;
            // Check if needs quoting
            if (str.contains(":") || str.contains("#") || str.startsWith("&") || 
                str.contains("\"") || str.contains("\n") || str.isEmpty() ||
                str.startsWith(" ") || str.endsWith(" ")) {
                return "\"" + str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
            }
            return str;
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return "[]";
            }
            StringBuilder sb = new StringBuilder();
            String indent = "  ".repeat(baseIndent + 1);
            for (Object item : list) {
                sb.append("\n").append(indent).append("- ");
                if (item instanceof Map || item instanceof ConfigurationSection) {
                    // Complex item, format as nested
                    sb.append(formatMapValue(item, baseIndent + 2));
                } else {
                    sb.append(formatYamlValue(item, baseIndent + 1));
                }
            }
            return sb.toString();
        }
        if (value instanceof ConfigurationSection) {
            return formatMapValue(((ConfigurationSection) value).getValues(false), baseIndent);
        }
        if (value instanceof Map) {
            return formatMapValue(value, baseIndent);
        }
        return value.toString();
    }
    
    /**
     * Formats a map value for YAML output.
     */
    private String formatMapValue(Object value, int baseIndent) {
        Map<?, ?> map;
        if (value instanceof ConfigurationSection) {
            map = ((ConfigurationSection) value).getValues(false);
        } else if (value instanceof Map) {
            map = (Map<?, ?>) value;
        } else {
            return value.toString();
        }
        
        if (map.isEmpty()) {
            return "{}";
        }
        
        StringBuilder sb = new StringBuilder();
        String indent = "  ".repeat(baseIndent);
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append("\n").append(indent);
            }
            first = false;
            sb.append(entry.getKey()).append(": ").append(formatYamlValue(entry.getValue(), baseIndent));
        }
        return sb.toString();
    }
    
    /**
     * Updates all config files.
     */
    public void updateAll() {
        updateConfig();
        updateMessages();
    }
    
    /**
     * Gets the current config version.
     */
    public static int getCurrentConfigVersion() {
        return CURRENT_CONFIG_VERSION;
    }
}
