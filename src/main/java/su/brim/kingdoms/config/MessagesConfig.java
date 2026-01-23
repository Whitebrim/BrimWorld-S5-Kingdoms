package su.brim.kingdoms.config;

import su.brim.kingdoms.KingdomsAddon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages plugin messages with Russian localization support.
 */
public class MessagesConfig {
    
    private final KingdomsAddon plugin;
    private final File messagesFile;
    private FileConfiguration messagesConfig;
    
    private String prefix;
    
    // Cache for formatted messages
    private final Map<String, String> messageCache = new HashMap<>();
    
    public MessagesConfig(KingdomsAddon plugin) {
        this.plugin = plugin;
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        load();
    }
    
    /**
     * Loads messages from file.
     */
    public void load() {
        // Save default if not exists
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        
        // Load defaults from jar for missing keys
        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            messagesConfig.setDefaults(defaultConfig);
        }
        
        // Load prefix
        prefix = messagesConfig.getString("prefix", "&8[&6Королевства&8] ");
        
        // Clear cache
        messageCache.clear();
    }
    
    /**
     * Reloads messages.
     */
    public void reload() {
        load();
    }
    
    /**
     * Gets a raw message from config.
     */
    public String getRawMessage(String path) {
        return messagesConfig.getString(path, "Missing message: " + path);
    }
    
    /**
     * Gets a formatted message (with color codes translated, no prefix).
     */
    public String getMessage(String path) {
        if (messageCache.containsKey(path)) {
            return messageCache.get(path);
        }
        
        String raw = getRawMessage(path);
        String formatted = translateColors(raw);
        messageCache.put(path, formatted);
        return formatted;
    }
    
    /**
     * Gets a formatted message with prefix.
     */
    public String getMessageWithPrefix(String path) {
        return translateColors(prefix) + getMessage(path);
    }
    
    /**
     * Gets a message with placeholders replaced.
     */
    public String getMessage(String path, Map<String, String> placeholders) {
        String message = getMessage(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }
    
    /**
     * Gets a message with prefix and placeholders replaced.
     */
    public String getMessageWithPrefix(String path, Map<String, String> placeholders) {
        return translateColors(prefix) + getMessage(path, placeholders);
    }
    
    /**
     * Gets a Component from a message path.
     */
    public Component getComponent(String path) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(getMessage(path));
    }
    
    /**
     * Gets a Component with prefix.
     */
    public Component getComponentWithPrefix(String path) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(getMessageWithPrefix(path));
    }
    
    /**
     * Gets a Component with placeholders.
     */
    public Component getComponent(String path, Map<String, String> placeholders) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(getMessage(path, placeholders));
    }
    
    /**
     * Gets a Component with prefix and placeholders.
     */
    public Component getComponentWithPrefix(String path, Map<String, String> placeholders) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(getMessageWithPrefix(path, placeholders));
    }
    
    /**
     * Translates legacy color codes (&) to section symbols.
     */
    private String translateColors(String message) {
        if (message == null) return "";
        
        char[] chars = message.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(chars[i + 1]) > -1) {
                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }
    
    /**
     * Creates a placeholder map with a single key-value pair.
     */
    public static Map<String, String> placeholder(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }
    
    /**
     * Creates a placeholder map builder.
     */
    public static PlaceholderBuilder placeholders() {
        return new PlaceholderBuilder();
    }
    
    /**
     * Helper class for building placeholder maps.
     */
    public static class PlaceholderBuilder {
        private final Map<String, String> map = new HashMap<>();
        
        public PlaceholderBuilder add(String key, String value) {
            map.put(key, value);
            return this;
        }
        
        public PlaceholderBuilder add(String key, int value) {
            map.put(key, String.valueOf(value));
            return this;
        }
        
        public PlaceholderBuilder add(String key, double value) {
            map.put(key, String.format("%.2f", value));
            return this;
        }
        
        public Map<String, String> build() {
            return map;
        }
    }
}
