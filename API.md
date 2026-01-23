# KingdomsAddon API Documentation

## Overview

KingdomsAddon provides a public API for other plugins to access kingdom and ghost system information.

## Getting the API Instance

```java
import su.brim.kingdoms.api.KingdomsAPI;

// Get the API instance
KingdomsAPI api = KingdomsAPI.getInstance();
if (api == null) {
    // Plugin not loaded
    return;
}
```

## Kingdom Methods

### Get Player's Kingdom
```java
String kingdomId = api.getPlayerKingdom(player.getUniqueId());
// Returns: "snow_kingdom", "forest_kingdom", "tropical_kingdom", or null
```

### Get Kingdom Display Name
```java
String displayName = api.getKingdomDisplayName("snow_kingdom");
// Returns: "Снежное Королевство"
```

### Get Kingdom Color
```java
String color = api.getKingdomColor("snow_kingdom");
// Returns: "#87CEEB"
```

### Check if Player Has Kingdom
```java
boolean hasKingdom = api.hasKingdom(player.getUniqueId());
```

### Check if Players are Allies
```java
boolean allies = api.areAllies(player1.getUniqueId(), player2.getUniqueId());
```

### Get All Kingdoms
```java
List<String> kingdoms = api.getAllKingdoms();
// Returns: ["snow_kingdom", "forest_kingdom", "tropical_kingdom"]
```

### Get Online Members in Kingdom
```java
int count = api.getOnlineMemberCount("snow_kingdom");
Set<Player> members = api.getOnlineKingdomMembers("snow_kingdom");
```

## Ghost System Methods

### Check if Player is Ghost
```java
boolean isGhost = api.isGhost(player.getUniqueId());
```

### Get Ghost Remaining Time
```java
long remainingMs = api.getGhostRemainingTimeMs(player.getUniqueId());
// Returns: remaining time in milliseconds, or -1 if not a ghost
```

### Check if Ghost Can Self-Resurrect
```java
boolean canResurrect = api.canGhostSelfResurrect(player.getUniqueId());
```

### Check if Ghost System is Enabled
```java
boolean enabled = api.isGhostSystemEnabled();
```

### Get All Ghost UUIDs
```java
Set<UUID> ghosts = api.getAllGhostUUIDs();
```

## Admin Bypass

### Check if Player is Admin
```java
boolean isAdmin = api.isAdmin(player);
// or
boolean isAdmin = api.isAdmin(player.getUniqueId()); // Only works for online players
```

Admin players have the permission `kingdoms.admin` and:
- Are not assigned to any kingdom
- Can interact with all portals/altars
- Do not become ghosts on death
- Are not affected by team damage modifiers
- Do not get team colors

## Team Colors

### Update Player Nametag Display
```java
api.updatePlayerDisplay(player);
```
Note: This only updates the nametag above the player's head. Chat and tab colors should be handled via PlaceholderAPI.

## PlaceholderAPI Integration

KingdomsAddon provides placeholders for use in chat plugins, tab plugins, and other plugins that support PlaceholderAPI.

### Available Placeholders

| Placeholder | Description | Example Output |
|-------------|-------------|----------------|
| `%kingdoms_color%` | Kingdom color in hex format or ghost prefix | `#87CEEB` or `§7§o☠ ` |
| `%kingdoms_color_legacy%` | Kingdom color in legacy format | `§x§8§7§C§E§E§B` or `§7§o☠ ` |
| `%kingdoms_kingdom%` | Kingdom ID | `snow_kingdom` |
| `%kingdoms_kingdom_name%` | Kingdom display name | `Снежное Королевство` |
| `%kingdoms_is_ghost%` | Whether player is a ghost | `true` / `false` |
| `%kingdoms_ghost_time%` | Remaining ghost time | `12:34` |
| `%kingdoms_ghost_prefix%` | Ghost symbol if player is ghost | `☠ ` or empty |
| `%kingdoms_is_admin%` | Whether player is admin | `true` / `false` |

### Usage Examples

**For chat plugins (EssentialsChat, ChatControl, etc.):**
```
format: '%kingdoms_color_legacy%%player_name%&r: %message%'
```

**For TAB plugin (tab list):**
```yaml
tablist-name-format: "%kingdoms_color%%player%"
```

**For scoreboard plugins:**
```
line: '&7Kingdom: %kingdoms_kingdom_name%'
```

## Maven Dependency

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.Whitebrim</groupId>
    <artifactId>BrimWorld-S5-Kingdoms</artifactId>
    <version>Tag</version>
    <scope>provided</scope>
</dependency>
```

## plugin.yml

Add KingdomsAddon as a dependency or soft-dependency:

```yaml
depend:
  - KingdomsAddon
# or
softdepend:
  - KingdomsAddon
```
