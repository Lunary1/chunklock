# Admin Commands

This page documents all administrative commands available to server operators and staff members.

## Administrative Command Overview

Admin commands provide powerful tools for managing the Chunklock plugin, monitoring player progress, and maintaining server balance.

**Permission Required**: Most admin commands require `chunklock.admin` permission.

## Server Management Commands

### `/chunklock reload`

**Permission**: `chunklock.reload`  
**Description**: Reloads the plugin configuration without restarting the server.

```
/chunklock reload
/cl reload
```

**What it reloads**:

- Configuration settings from `config.yml`
- Economy settings and multipliers
- Visual effect configurations
- Permission changes

**Notes**:

- Player data is preserved
- Active chunk operations continue normally
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

### `/chunklock unlock <player>`

**Permission**: `chunklock.unlock`  
**Description**: Force unlocks the chunk a player is standing in.

```
/chunklock unlock PlayerName
/cl unlock PlayerName
```

**Notes**:

- No resource cost for admin unlocks
- Bypasses adjacency requirements
- Useful for fixing stuck players or resolving issues

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

### `/chunklock stats`

**Permission**: `chunklock.admin`  
**Description**: Shows global server statistics and performance metrics.

```
/chunklock stats
/cl stats
```

**Global Statistics Output**:

```
=== Chunklock Server Statistics ===
Total Players: 147
Active Players (7 days): 89
Total Chunks Unlocked: 2,456
Average Chunks per Player: 16.7

Teams:
  - Total Teams: 23
  - Average Team Size: 3.2 players
  - Largest Team: 8 players

Biome Distribution:
  - Plains: 892 chunks (36.3%)
  - Forest: 634 chunks (25.8%)
  - Desert: 445 chunks (18.1%)
  - Ocean: 298 chunks (12.1%)
  - Other: 187 chunks (7.6%)

Performance:
  - Chunk Operations/sec: 45
  - Database Queries/sec: 12
  - Memory Usage: 156MB
  - Service Health: All systems operational
```

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
Plugin Version: 1.2.7
Server: Paper 1.20.4-496
Java: OpenJDK 17.0.8

Service Layer Status:
  ✅ ChunkService: Healthy
  ✅ TeamService: Healthy
  ✅ EconomyService: Healthy
  ✅ HologramService: Healthy (FancyHolograms)
  ✅ Service Container: 8 services registered

Database Status:
  ✅ Connection: Active
  ✅ Response Time: 12ms
  ✅ Queue Size: 0

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

### `/chunklock maintenance <enable|disable>`

**Permission**: `chunklock.admin`  
**Description**: Enables or disables maintenance mode.

```
/chunklock maintenance enable
/chunklock maintenance disable
/cl maintenance enable
```

**Maintenance Mode Effects**:

- Prevents new chunk unlocks
- Blocks team operations
- Shows maintenance message to players
- Allows admins to perform updates safely

---

## Economy Management Commands

### `/chunklock economy status`

**Permission**: `chunklock.admin`  
**Description**: Shows economy system status and statistics.

```
/chunklock economy status
/cl economy status
```

**Economy Status Output**:

```
=== Economy System Status ===
Economy Type: Vault
Economy Plugin: EssentialsX
Currency: Coins

Unlock Statistics:
  - Total Unlocks Today: 156
  - Total Revenue: $45,678
  - Average Cost per Unlock: $293

Cost Analysis:
  - Cheapest Unlock: $150 (Plains, Easy)
  - Most Expensive: $2,340 (Badlands, Impossible)
  - Most Popular: Forest chunks (34% of unlocks)

Player Balance Analysis:
  - Players with 0 balance: 12
  - Average player balance: $1,456
  - Richest player: $45,890
```

---

### `/chunklock economy reset <player>`

**Permission**: `chunklock.admin`  
**Description**: Resets economy statistics for a specific player.

```
/chunklock economy reset PlayerName
/cl economy reset PlayerName
```

**What it resets**:

- Player's total spending statistics
- Unlock cost calculations (resets to base values)
- Economy-related achievements

---

## Backup and Recovery Commands

### `/chunklock backup create [name]`

**Permission**: `chunklock.admin`  
**Description**: Creates a backup of all Chunklock data.

```
/chunklock backup create
/chunklock backup create "pre-update-backup"
/cl backup create daily-backup
```

**Backup Contents**:

- All player progression data
- Team information
- Configuration snapshots
- Statistics and metrics

**Backup Location**: `plugins/Chunklock/backups/`

---

### `/chunklock backup restore <name>`

**Permission**: `chunklock.admin`  
**Description**: Restores data from a backup.

```
/chunklock backup restore backup-2025-10-10
/cl backup restore "pre-update-backup"
```

**Confirmation Required**: Type `CONFIRM RESTORE` to proceed.

**Warning**: This will overwrite all current data with backup data.

---

### `/chunklock backup list`

**Permission**: `chunklock.admin`  
**Description**: Lists all available backups.

```
/chunklock backup list
/cl backup list
```

**Backup List Output**:

```
=== Available Backups ===
1. auto-backup-2025-10-10-14-30 (2.3MB) - 2 hours ago
2. pre-update-backup (2.1MB) - 1 day ago
3. weekly-backup-2025-10-03 (1.8MB) - 1 week ago
4. backup-2025-09-28 (1.5MB) - 2 weeks ago
```

---

## Emergency Commands

### `/chunklock emergency stop`

**Permission**: `chunklock.admin`  
**Description**: Emergency stops all Chunklock operations.

```
/chunklock emergency stop
/cl emergency stop
```

**Emergency Stop Effects**:

- Halts all chunk operations
- Stops border updates
- Disables hologram updates
- Prevents new player actions
- Maintains data integrity

---

### `/chunklock emergency start`

**Permission**: `chunklock.admin`  
**Description**: Restarts operations after an emergency stop.

```
/chunklock emergency start
/cl emergency start
```

---

## Batch Operations

### `/chunklock batch unlock <player> <count>`

**Permission**: `chunklock.admin`  
**Description**: Unlocks multiple random adjacent chunks for a player.

```
/chunklock batch unlock PlayerName 5
/cl batch unlock PlayerName 10
```

**Notes**:

- Unlocks chunks in order of lowest difficulty first
- Respects adjacency requirements
- No resource cost for admin batch unlocks

---

### `/chunklock batch reset team <teamname>`

**Permission**: `chunklock.admin`  
**Description**: Resets all progression for an entire team.

```
/chunklock batch reset team TeamName
/cl batch reset team "Problem Team"
```

**Confirmation Required**: Type `CONFIRM TEAM RESET` to proceed.

**Effects**:

- Resets all team members' progression
- Clears team statistics
- Preserves team structure and membership

---

## Monitoring Tools

### `/chunklock monitor start`

**Permission**: `chunklock.admin`  
**Description**: Starts real-time monitoring mode.

```
/chunklock monitor start
/cl monitor start
```

**Monitoring Features**:

- Live chunk unlock notifications
- Performance metric updates
- Error and warning alerts
- Player activity tracking

---

### `/chunklock monitor stop`

**Permission**: `chunklock.admin`  
**Description**: Stops real-time monitoring mode.

```
/chunklock monitor stop
/cl monitor stop
```

---

## Command Examples

### Daily Admin Tasks

```bash
# Check server health
/chunklock debug

# Review player activity
/chunklock stats

# Monitor performance
/chunklock monitor start
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
# Enable maintenance mode
/chunklock maintenance enable

# Create backup before updates
/chunklock backup create "pre-maintenance"

# Reload configuration
/chunklock reload

# Disable maintenance mode
/chunklock maintenance disable
```

## Best Practices

1. **Regular Backups**: Create backups before major changes
2. **Monitor Performance**: Use debug commands to track system health
3. **Gradual Changes**: Test configuration changes on small groups first
4. **Documentation**: Keep records of admin actions for accountability
5. **Player Communication**: Inform players before maintenance operations

---

_For player commands, see the [Player Commands](Player-Commands) guide._
