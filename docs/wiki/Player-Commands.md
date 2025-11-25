# Player Commands

This page documents all commands available to players using the Chunklock plugin.

## Command Syntax

All Chunklock commands use the following format:

```
/chunklock <subcommand> [arguments]
```

**Aliases**: `/cl` can be used instead of `/chunklock`

## Basic Commands

### `/chunklock help`

**Permission**: `chunklock.use`  
**Description**: Shows a list of available commands and their usage.

```
/chunklock help
/cl help
```

**Output**: Displays all commands you have permission to use with brief descriptions.

---

### `/chunklock status`

**Permission**: `chunklock.status`  
**Description**: Shows your current chunk progression status and statistics.

```
/chunklock status
/cl status
```

**Example Output**:

```
=== Chunklock Status ===
Player: YourUsername
Chunks Unlocked: 12
Starting Chunk: (0, 0) in world_chunklock
Team: MyTeam (3 members)
Progress: 24% complete
Economy: Materials mode
Next Unlock Cost: 32 Stone, 16 Iron Ore
```

---

### `/chunklock start`

**Permission**: `chunklock.start`  
**Description**: Initializes your chunk progression and teleports you to your starting chunk.

```
/chunklock start
/cl start
```

**Notes**:

- Can only be used once per player
- Creates your personal starting area
- Teleports you to your designated spawn chunk

---

### `/chunklock spawn`

**Permission**: `chunklock.spawn`  
**Description**: Teleports you back to your starting chunk.

```
/chunklock spawn
/cl spawn
```

**Cooldown**: 30 seconds (configurable)  
**Notes**: Works from anywhere in your world

---

### `/chunklock unlock`

**Permission**: `chunklock.unlock`  
**Description**: Attempts to unlock the chunk you're currently standing in.

```
/chunklock unlock
/cl unlock
```

**Requirements**:

- Must be standing in a locked chunk
- Chunk must be adjacent to an already unlocked chunk
- Must have sufficient resources/money to pay the unlock cost
- Must meet biome-specific requirements (may include custom items from Oraxen/MMOItems)

**Process**:

1. Checks if chunk can be unlocked
2. Calculates unlock cost based on difficulty, biome, and progress
3. Checks for required materials (vanilla items and/or custom items)
4. Prompts for payment confirmation
5. Unlocks chunk if payment is successful

**Note**: Biome requirements may include custom items from Oraxen or MMOItems. Check the unlock GUI or holograms to see exact requirements.

---

## Team Commands

### `/chunklock team info`

**Permission**: `chunklock.team`  
**Description**: Shows information about your current team.

```
/chunklock team info
/cl team info
```

**Example Output**:

```
=== Team Information ===
Team Name: AwesomeTeam
Leader: PlayerLeader
Members: 4/8
- PlayerLeader (Leader)
- YourUsername (Member)
- FriendName (Member)
- AnotherPlayer (Moderator)
Team Chunks: 45
Team Progress: 67%
```

---

### `/chunklock team create <teamname>`

**Permission**: `chunklock.team`  
**Description**: Creates a new team with you as the leader.

```
/chunklock team create MyTeam
/cl team create "Team Name With Spaces"
```

**Requirements**:

- You must not already be in a team
- Team name must be unique
- Team name must be 3-16 characters long

---

### `/chunklock team invite <player>`

**Permission**: `chunklock.team`  
**Description**: Invites a player to join your team.

```
/chunklock team invite PlayerName
/cl team invite PlayerName
```

**Requirements**:

- You must be team leader or moderator
- Target player must not be in a team
- Team must not be full (max 8 members by default)

---

### `/chunklock team accept`

**Permission**: `chunklock.team`  
**Description**: Accepts a pending team invitation.

```
/chunklock team accept
/cl team accept
```

**Notes**:

- Must have a pending invitation
- Joins you to the inviting team

---

### `/chunklock team decline`

**Permission**: `chunklock.team`  
**Description**: Declines a pending team invitation.

```
/chunklock team decline
/cl team decline
```

---

### `/chunklock team leave`

**Permission**: `chunklock.team`  
**Description**: Leaves your current team.

```
/chunklock team leave
/cl team leave
```

**Notes**:

- Leaders cannot leave; they must transfer leadership first
- Confirmation required

---

### `/chunklock team kick <player>`

**Permission**: `chunklock.team`  
**Description**: Removes a player from your team.

```
/chunklock team kick PlayerName
/cl team kick PlayerName
```

**Requirements**:

- You must be team leader or moderator
- Cannot kick players with equal or higher rank
- Target must be in your team

---

### `/chunklock team promote <player>`

**Permission**: `chunklock.team`  
**Description**: Promotes a team member to moderator.

```
/chunklock team promote PlayerName
/cl team promote PlayerName
```

**Requirements**:

- You must be team leader
- Target must be a regular member of your team

---

### `/chunklock team demote <player>`

**Permission**: `chunklock.team`  
**Description**: Demotes a moderator to regular member.

```
/chunklock team demote PlayerName
/cl team demote PlayerName
```

**Requirements**:

- You must be team leader
- Target must be a moderator in your team

---

### `/chunklock team transfer <player>`

**Permission**: `chunklock.team`  
**Description**: Transfers team leadership to another member.

```
/chunklock team transfer PlayerName
/cl team transfer PlayerName
```

**Requirements**:

- You must be team leader
- Target must be in your team
- Confirmation required

---

## GUI System

### Opening the Unlock GUI

**Permission**: `chunklock.use`  
**Description**: Opens the interactive chunk unlock GUI interface.

**Method 1 - Right-Click** (Recommended):
- Right-click any block while standing in an unlocked chunk
- GUI opens automatically showing adjacent unlockable chunks

**Method 2 - Command**:
```
/chunklock gui
/cl gui
```

**GUI Features**:

- Visual map of your unlocked chunks and adjacent unlockable chunks
- Click chunks to unlock them directly from the GUI
- Shows exact costs (materials or money)
- Displays biome-specific requirements including custom items
- Shows difficulty ratings (Easy, Normal, Hard, Impossible)
- Real-time cost calculations
- Team member progress indicators
- Color-coded chunk states (unlocked, unlockable, locked)

**Tips**:
- The GUI updates in real-time as you unlock chunks
- Hover over chunks to see detailed information
- Custom items from Oraxen/MMOItems are shown with their display names
- Costs may vary if OpenAI integration is enabled (dynamic pricing)

---

---

## Command Examples

### Starting Your Journey

```bash
# Initialize your progression
/chunklock start

# Check your status
/chunklock status

# Try to unlock an adjacent chunk
/chunklock unlock
```

### Team Collaboration

```bash
# Create a team
/chunklock team create "Explorers"

# Invite friends
/chunklock team invite Alice
/chunklock team invite Bob

# Check team progress
/chunklock team info
```

### Daily Usage

```bash
# Return to spawn
/chunklock spawn

# Open unlock GUI (or right-click a block)
/chunklock gui

# Check your status
/chunklock status
```

## Command Permissions

| Command               | Permission Node    | Default |
| --------------------- | ------------------ | ------- |
| Command               | Permission Node    | Default |
| --------------------- | ------------------ | ------- |
| `/chunklock help`     | `chunklock.use`    | ✅ True |
| `/chunklock status`   | `chunklock.status` | ✅ True |
| `/chunklock start`    | `chunklock.start`  | ✅ True |
| `/chunklock spawn`    | `chunklock.spawn`  | ✅ True |
| `/chunklock unlock`   | `chunklock.unlock` | ✅ True |
| `/chunklock team *`   | `chunklock.team`   | ✅ True |
| GUI (right-click)      | `chunklock.use`    | ✅ True |

## Command Cooldowns

Some commands have cooldowns to prevent spam:

| Command                  | Cooldown   | Reason                     |
| ------------------------ | ---------- | -------------------------- |
| `/chunklock spawn`       | 30 seconds | Prevent teleport spam      |
| `/chunklock unlock`      | 5 seconds  | Prevent accidental unlocks |
| `/chunklock team invite` | 10 seconds | Prevent invite spam        |

## Error Messages

### Common Error Scenarios

**"You are not in a team"**

- Join a team first using `/chunklock team accept` or create one with `/chunklock team create`

**"Insufficient resources"**

- Gather more materials or earn more money to unlock chunks
- Check if biome requires custom items (Oraxen/MMOItems) - these must be in your inventory
- Use `/chunklock status` or the GUI to see exact requirements

**"Chunk is not adjacent"**

- You can only unlock chunks next to already unlocked chunks

**"You don't have permission"**

- Contact your server administrator about permission setup

**"Team is full"**

- Teams have a maximum member limit (usually 8 players)

## Tips for Players

1. **Start Early**: Use `/chunklock start` as soon as you join the server
2. **Use the GUI**: Right-click blocks to open the unlock GUI - it's the easiest way to see costs and unlock chunks
3. **Team Up**: Join or create teams for faster progression and cost reductions
4. **Check Requirements**: Some biomes require custom items - check the GUI or holograms before attempting to unlock
5. **Plan Ahead**: Use the GUI to plan your expansion strategy and see which chunks are cheapest
6. **Resource Management**: Balance exploration with resource gathering - costs increase with progress
7. **Language Customization**: Server admins can customize all messages - ask them if you need clarification
8. **AI Costs**: If OpenAI is enabled, costs may vary dynamically - check costs right before unlocking

---

_For administrative commands, see the [Admin Commands](Admin-Commands) guide._
