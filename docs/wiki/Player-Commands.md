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

**Process**:

1. Checks if chunk can be unlocked
2. Calculates unlock cost based on difficulty and biome
3. Prompts for payment confirmation
4. Unlocks chunk if payment is successful

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

## Advanced Commands

### `/chunklock gui`

**Permission**: `chunklock.use`  
**Description**: Opens the chunk unlock GUI interface.

```
/chunklock gui
/cl gui
```

**Features**:

- Visual representation of unlockable chunks
- Click to unlock chunks
- Shows costs and requirements
- Displays team member progress

---

### `/chunklock progress`

**Permission**: `chunklock.use`  
**Description**: Shows detailed progression statistics.

```
/chunklock progress
/cl progress
```

**Example Output**:

```
=== Progression Details ===
Biomes Unlocked: 5/12
- Plains: 8 chunks
- Forest: 12 chunks
- Desert: 6 chunks
- Ocean: 3 chunks
- Mountains: 2 chunks

Difficulty Distribution:
- Easy: 15 chunks
- Normal: 12 chunks
- Hard: 4 chunks
- Impossible: 0 chunks

Resources Spent:
- Stone: 2,456
- Iron: 1,234
- Coal: 3,789
- Diamonds: 67
```

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

# Open unlock GUI
/chunklock gui

# Check progression
/chunklock progress
```

## Command Permissions

| Command               | Permission Node    | Default |
| --------------------- | ------------------ | ------- |
| `/chunklock help`     | `chunklock.use`    | ✅ True |
| `/chunklock status`   | `chunklock.status` | ✅ True |
| `/chunklock start`    | `chunklock.start`  | ✅ True |
| `/chunklock spawn`    | `chunklock.spawn`  | ✅ True |
| `/chunklock unlock`   | `chunklock.unlock` | ✅ True |
| `/chunklock team *`   | `chunklock.team`   | ✅ True |
| `/chunklock gui`      | `chunklock.use`    | ✅ True |
| `/chunklock progress` | `chunklock.use`    | ✅ True |

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

**"Chunk is not adjacent"**

- You can only unlock chunks next to already unlocked chunks

**"You don't have permission"**

- Contact your server administrator about permission setup

**"Team is full"**

- Teams have a maximum member limit (usually 8 players)

## Tips for Players

1. **Start Early**: Use `/chunklock start` as soon as you join the server
2. **Team Up**: Join or create teams for faster progression
3. **Plan Ahead**: Use `/chunklock gui` to plan your expansion strategy
4. **Resource Management**: Balance exploration with resource gathering
5. **Communication**: Coordinate with team members for efficient unlocking

---

_For administrative commands, see the [Admin Commands](Admin-Commands) guide._
