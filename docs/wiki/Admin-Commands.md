# Admin Commands

This page documents all administrative commands available to server operators and staff members.

## Administrative Command Overview

Admin commands provide powerful tools for managing the Chunklock plugin, monitoring player progress, and maintaining server balance.

**Permission Required**: Most admin commands require `chunklock.admin` permission.

## Server Management Commands

### `/chunklock setup <diameter>`

**Permission**: `chunklock.admin`  
**Description**: Creates and initializes the Chunklock world. This must be run before players can use the plugin.

```
/chunklock setup 30000
/cl setup 50000
```

**Parameters**:

- `diameter` - World diameter in blocks (e.g., 30000 = 30,000 block diameter)
  - Minimum: 1,000 blocks
  - Maximum: 100,000 blocks
  - Recommended: 20,000-30,000 blocks

**What it does**:

- **Creates** a new world named `chunklock_world` (if it doesn't exist)
- Sets world border to the specified diameter
- Configures world settings (difficulty, PVP, spawn flags)
- Pre-generates chunks for better performance
- Updates `worlds.yml` configuration automatically
- Enables chunk locking in that world

**Example**:

```
/chunklock setup 30000
> Starting Chunklock world setup...
> Diameter: 30000 blocks
> This may take several minutes depending on the size.
> ✅ Chunklock world setup completed successfully!
> World name: chunklock_world
> Players can now use '/chunklock start' to begin!
```

**Important Notes**:

- The world is **automatically created** - you don't need to create it manually
- World name is automatically set to `chunklock_world` (configurable in `worlds.yml`)
- Setup runs asynchronously and may take several minutes
- If world already exists, you'll see a message with current settings
- This command must be run before players can use `/chunklock start`

---

### `/chunklock reload`

**Permission**: `chunklock.reload`  
**Description**: Reloads the plugin configuration without restarting the server.

```
/chunklock reload
/cl reload
```

**What it reloads**:

- All modular configuration files (config.yml, economy.yml, openai.yml, etc.)
- Economy settings and multipliers
- Visual effect configurations (borders, holograms)
- Language files
- Permission changes

**Notes**:

- Player data is preserved
- Active chunk operations continue normally
- OpenAI settings reload (API key changes take effect)
- Some changes may require a full restart

---

### `/chunklock resetall`

**Permission**: `chunklock.admin`  
**Description**: Resets ALL player data and chunk progressions. **USE WITH EXTREME CAUTION**.

```
/chunklock resetall
```

**Confirmation Required**: Type `CONFIRM RESET ALL` to proceed.

**What it resets**:

- All player progression data
- All unlocked chunks
- All team data
- All statistics

**Backup Warning**: This action is irreversible. Always backup your data first.

---

## Player Management Commands

### `/chunklock reset <player>`

**Permission**: `chunklock.reset`  
**Description**: Resets a specific player's progression and chunk data.

```
/chunklock reset PlayerName
/cl reset PlayerName
```

**What it resets**:

- Player's unlocked chunks
- Player's progression statistics
- Player's team membership (removes from team)
- Player's starting chunk location

**Example**:

```
/chunklock reset Steve
> Player 'Steve' has been reset. All chunks unlocked and progression cleared.
```

---

### `/chunklock bypass [player]`

**Permission**: `chunklock.bypass`  
**Description**: Toggles bypass mode for yourself or another player.

```
/chunklock bypass              # Toggle your own bypass
/chunklock bypass PlayerName   # Toggle bypass for specific player
/cl bypass PlayerName
```

**Bypass Mode Effects**:

- Can access any chunk regardless of lock status
- Can break/place blocks in locked chunks
- Chunk unlocking is free (no resource cost)
- Bypassed players are highlighted in admin tools

**Status Check**:

```
/chunklock bypass
> Bypass mode: ENABLED for YourUsername
```

---

### `/chunklock unlock <player> [x] [z] [world]`

**Permission**: `chunklock.unlock`  
**Description**: Force unlocks a chunk for a player. Can unlock the player's current chunk or a specific chunk.

```
/chunklock unlock PlayerName                    # Unlock player's current chunk
/chunklock unlock PlayerName 10 -5 world        # Unlock specific chunk
/chunklock unlock here                           # Unlock your current chunk (if you're a player)
/cl unlock PlayerName
```

**Parameters**:

- `player` - Player name or "here" to unlock your current chunk
- `x` (optional) - Chunk X coordinate
- `z` (optional) - Chunk Z coordinate  
- `world` (optional) - World name

**Notes**:

- No resource cost for admin unlocks
- Bypasses adjacency requirements
- Useful for fixing stuck players or resolving issues
- If coordinates not specified, unlocks the player's current chunk

---

## Monitoring and Statistics

### `/chunklock status [player]`

**Permission**: `chunklock.admin`  
**Description**: Shows detailed status information for any player.

```
/chunklock status PlayerName
/cl status PlayerName
```

**Admin Status Output**:

```
=== Admin Status: PlayerName ===
Player UUID: 12345678-1234-1234-1234-123456789012
Chunks Unlocked: 15
Starting Chunk: (5, -3) in world_chunklock
Team: TeamName (Leader/Member/Moderator)
Bypass Mode: Disabled
Last Login: 2025-10-10 14:30:25
Total Playtime: 45h 30m
Resources Spent:
  - Stone: 1,234
  - Iron: 567
  - Coal: 2,890
Current Location: Chunk (7, 2) - Unlocked
```

---

## OpenAI Integration Commands

### `/chunklock apikey <key>`

**Permission**: `chunklock.admin.apikey`  
**Description**: Sets or updates the OpenAI API key for AI-powered cost calculation.

```
/chunklock apikey sk-your-api-key-here
/cl apikey <your-api-key>
```

**What it does**:

- Sets the OpenAI API key in `openai.yml`
- Enables OpenAI integration if `enabled: true` in `openai.yml`
- Key is stored securely in the configuration file

**Notes**:

- You can also set the API key directly in `openai.yml`
- Changes take effect after reload or restart
- Use `/chunklock reload` after setting the key to activate it immediately
- The key is required for OpenAI cost calculation features

**Example**:

```
/chunklock apikey sk-1234567890abcdef
> OpenAI API key updated successfully
> Use /chunklock reload to activate
```

---

### `/chunklock aidebug`

**Permission**: `chunklock.admin.test`  
**Description**: Tests the OpenAI integration and shows debug information.

```
/chunklock aidebug
/cl aidebug
```

**What it shows**:

- OpenAI API connection status
- Current API key status (masked)
- Test cost calculation results
- Error messages if integration fails

**Use cases**:

- Verify OpenAI API key is working
- Test cost calculation with AI
- Debug OpenAI integration issues

---

## Team Management Commands

### `/chunklock team list`

**Permission**: `chunklock.admin`  
**Description**: Lists all teams on the server with detailed information.

```
/chunklock team list
/cl team list
```

**Team List Output**:

```
=== Server Teams (23 total) ===
1. Explorers [8/8] - Leader: Alice
   - Members: Alice, Bob, Charlie, Dave, Eve, Frank, Grace, Henry
   - Chunks: 67 | Progress: 78%

2. Builders [4/8] - Leader: Steve
   - Members: Steve, Alex, Sam, Taylor
   - Chunks: 34 | Progress: 45%

3. Miners [2/8] - Leader: Rocky
   - Members: Rocky, Stone
   - Chunks: 12 | Progress: 23%
```

---

### `/chunklock team disband <teamname>`

**Permission**: `chunklock.admin`  
**Description**: Forcibly disbands a team.

```
/chunklock team disband TeamName
/cl team disband "Team With Spaces"
```

**Effects**:

- All team members become teamless
- Team progression is preserved for individual members
- Team-specific data is removed

---

### `/chunklock team transfer <teamname> <newleader>`

**Permission**: `chunklock.admin`  
**Description**: Transfers team leadership to a different member.

```
/chunklock team transfer TeamName NewLeaderName
/cl team transfer "Cool Team" Alice
```

**Requirements**:

- New leader must be a member of the team
- Original leader loses leadership status

---

## Debug and Maintenance Commands

### `/chunklock debug`

**Permission**: `chunklock.admin`  
**Description**: Shows debug information and system health status.

```
/chunklock debug
/cl debug
```

**Debug Output**:

```
=== Chunklock Debug Information ===
Plugin Version: 2.1.0
Server: Paper 1.21.10-123
Java: OpenJDK 17.0.8

Service Layer Status:
  ✅ ChunkService: Healthy
  ✅ TeamService: Healthy
  ✅ EconomyService: Healthy (Vault)
  ✅ HologramService: Healthy (FancyHolograms)
  ✅ Service Container: 8 services registered

Database Status:
  ✅ Connection: Active (MapDB)
  ✅ Response Time: 12ms
  ✅ Queue Size: 0

OpenAI Integration:
  ✅ Status: Enabled
  ✅ API Key: Configured
  ✅ Cache: Active (5 min TTL)

Performance Metrics:
  - Chunk Loading Time: 45ms avg
  - Border Update Time: 23ms avg
  - Hologram Refresh Time: 8ms avg
  - Memory Usage: 156MB / 512MB allocated

Active Operations:
  - Chunk Unlocks in Progress: 3
  - Border Updates Queued: 0
  - Hologram Updates Pending: 1
```

---

## Command Examples

### Daily Admin Tasks

```bash
# Check server health
/chunklock debug

# Review player status
/chunklock status PlayerName

# Test OpenAI integration
/chunklock aidebug
```

### Player Support

```bash
# Help stuck player
/chunklock bypass PlayerName
/chunklock unlock PlayerName

# Reset problematic player
/chunklock reset PlayerName

# Check player status
/chunklock status PlayerName
```

### Maintenance Operations

```bash
# Reload configuration
/chunklock reload

# Update OpenAI API key
/chunklock apikey <new-key>

# Test OpenAI integration
/chunklock aidebug
```

## Best Practices

1. **Regular Backups**: Create backups before major changes
2. **Monitor Performance**: Use debug commands to track system health
3. **Gradual Changes**: Test configuration changes on small groups first
4. **Documentation**: Keep records of admin actions for accountability
5. **Player Communication**: Inform players before maintenance operations

---

_For player commands, see the [Player Commands](Player-Commands) guide._
