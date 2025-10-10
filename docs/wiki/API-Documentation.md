# API Documentation

This page provides comprehensive documentation for developers who want to integrate with or extend the Chunklock plugin.

## API Overview

Chunklock provides a robust API that allows other plugins to interact with the chunk progression system, access player data, and integrate with the economy and team systems.

### API Access

The Chunklock API is accessible through the main `ChunklockAPI` class:

```java
import me.chunklock.api.ChunklockAPI;

// Get the API instance
ChunklockAPI api = ChunklockAPI.getInstance();
```

### Dependency Setup

#### Maven

```xml
<dependency>
    <groupId>me.chunklock</groupId>
    <artifactId>chunklock-api</artifactId>
    <version>1.2.7</version>
    <scope>provided</scope>
</dependency>
```

#### Gradle

```gradle
dependencies {
    compileOnly 'me.chunklock:chunklock-api:1.2.7'
}
```

#### Plugin Dependencies

Add Chunklock as a dependency in your `plugin.yml`:

```yaml
depend: [Chunklock]
# or
softdepend: [Chunklock]
```

## Service Architecture

Chunklock uses a service-oriented architecture that provides clean interfaces for different functionalities.

### Service Container

Access services through the service container:

```java
import me.chunklock.api.container.ServiceContainer;
import me.chunklock.api.services.*;

// Get service container
ServiceContainer container = ServiceContainer.getInstance();

// Access specific services
ChunkService chunkService = container.getService(ChunkService.class);
TeamService teamService = container.getService(TeamService.class);
PlayerProgressService progressService = container.getService(PlayerProgressService.class);
EconomyService economyService = container.getService(EconomyService.class);
```

### Service Health Monitoring

Monitor service health and status:

```java
// Check if all services are healthy
boolean allHealthy = ChunklockAPI.getInstance().getServiceManager().checkHealth();

// Get detailed health report
Map<String, String> healthReport = ChunklockAPI.getInstance().getServiceManager().getHealthReport();

// Check individual service health
boolean chunkServiceHealthy = chunkService.isHealthy();
```

## Core API Services

### ChunkService

Manage chunk operations and access chunk data.

#### Basic Chunk Operations

```java
import me.chunklock.api.services.ChunkService;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

ChunkService chunkService = container.getService(ChunkService.class);

// Check if a chunk is locked
boolean isLocked = chunkService.isChunkLocked(chunk, player);

// Get chunk data
Optional<ChunkData> chunkData = chunkService.getChunkData(chunk);
if (chunkData.isPresent()) {
    Difficulty difficulty = chunkData.get().getDifficulty();
    List<UUID> owners = chunkData.get().getOwners();
}

// Check if player can unlock a chunk
boolean canUnlock = chunkService.canUnlockChunk(chunk, player);

// Get unlock cost
String cost = chunkService.getUnlockCost(chunk, player);
```

#### Chunk Unlocking

```java
// Unlock a chunk for a player
boolean unlocked = chunkService.unlockChunk(chunk, player);

// Force unlock (admin operation)
boolean forceUnlocked = chunkService.forceUnlockChunk(chunk, player.getUniqueId());

// Lock a chunk (admin operation)
boolean locked = chunkService.lockChunk(chunk, player.getUniqueId());
```

#### Chunk Queries

```java
// Get all unlocked chunks for a player
List<Chunk> unlockedChunks = chunkService.getUnlockedChunks(player);

// Get unlocked chunk count
int unlockedCount = chunkService.getUnlockedChunkCount(player);

// Check adjacency to unlocked chunks
boolean isAdjacent = chunkService.isAdjacentToUnlockedChunk(chunk, player);

// Get chunk difficulty
Difficulty difficulty = chunkService.getChunkDifficulty(chunk);
```

### TeamService

Manage teams and team operations.

#### Team Management

```java
import me.chunklock.api.services.TeamService;
import me.chunklock.models.Team;

TeamService teamService = container.getService(TeamService.class);

// Create a new team
Team newTeam = teamService.createTeam(leader, "TeamName");

// Get player's team
Optional<Team> playerTeam = teamService.getPlayerTeam(player);

// Add player to team
boolean added = teamService.addPlayerToTeam(team, player, TeamRole.MEMBER);

// Remove player from team
boolean removed = teamService.removePlayerFromTeam(player);
```

#### Team Information

```java
// Get all teams
List<Team> allTeams = teamService.getAllTeams();

// Get team members
List<Player> members = teamService.getTeamMembers(team);

// Check if player is in a team
boolean inTeam = teamService.isPlayerInTeam(player);

// Get player's role in team
TeamRole role = teamService.getPlayerRole(player);

// Check team permissions
boolean canInvite = teamService.canPlayerInviteToTeam(player);
boolean canKick = teamService.canPlayerKickFromTeam(player, target);
```

### PlayerProgressService

Track and access player progression data.

#### Progress Tracking

```java
import me.chunklock.api.services.PlayerProgressService;

PlayerProgressService progressService = container.getService(PlayerProgressService.class);

// Get player's progress percentage
double progressPercent = progressService.getProgressPercentage(player);

// Get detailed statistics
Map<String, Object> stats = progressService.getPlayerStatistics(player);

// Get biome progression
Map<Biome, Integer> biomeProgress = progressService.getBiomeProgression(player);

// Get difficulty distribution
Map<Difficulty, Integer> difficultyStats = progressService.getDifficultyProgression(player);
```

#### Achievement and Milestones

```java
// Check if player has reached milestone
boolean milestone = progressService.hasReachedMilestone(player, "first_unlock");

// Get player achievements
List<String> achievements = progressService.getPlayerAchievements(player);

// Award achievement
boolean awarded = progressService.awardAchievement(player, "chunk_master");
```

### EconomyService

Handle economy operations and cost calculations.

#### Economy Information

```java
import me.chunklock.api.services.EconomyService;

EconomyService economyService = container.getService(EconomyService.class);

// Check if economy is enabled
boolean economyEnabled = economyService.isEconomyEnabled();

// Get economy type
String economyType = economyService.getEconomyType(); // "materials" or "vault"

// Check player balance
double balance = economyService.getPlayerBalance(player);
```

#### Cost Calculations

```java
// Calculate unlock cost for specific chunk
BigDecimal cost = economyService.calculateUnlockCost(chunk, player);

// Get cost breakdown
Map<String, Object> costBreakdown = economyService.getCostBreakdown(chunk, player);

// Check if player can afford unlock
boolean canAfford = economyService.canPlayerAfford(player, cost);
```

#### Payment Processing

```java
// Process payment for chunk unlock
PaymentResult result = economyService.processPayment(player, chunk, cost);

if (result.isSuccessful()) {
    // Payment successful
    String transactionId = result.getTransactionId();
} else {
    // Payment failed
    String errorMessage = result.getErrorMessage();
}

// Refund payment
boolean refunded = economyService.refundPayment(player, transactionId);
```

## Event API

Chunklock provides custom events that other plugins can listen to.

### Available Events

#### ChunkUnlockEvent

```java
import me.chunklock.api.events.ChunkUnlockEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ChunklockListener implements Listener {

    @EventHandler
    public void onChunkUnlock(ChunkUnlockEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getChunk();
        BigDecimal cost = event.getCost();

        // Check if event is cancelled
        if (event.isCancelled()) {
            return;
        }

        // Cancel the event if needed
        event.setCancelled(true);
        event.setCancelReason("Custom reason");

        // Access additional data
        boolean isTeamUnlock = event.isTeamUnlock();
        Team team = event.getTeam();
    }
}
```

#### TeamJoinEvent

```java
import me.chunklock.api.events.TeamJoinEvent;

@EventHandler
public void onTeamJoin(TeamJoinEvent event) {
    Player player = event.getPlayer();
    Team team = event.getTeam();
    TeamRole role = event.getRole();

    // Custom logic for team joins
    getLogger().info(player.getName() + " joined team " + team.getName());
}
```

#### PlayerProgressEvent

```java
import me.chunklock.api.events.PlayerProgressEvent;

@EventHandler
public void onPlayerProgress(PlayerProgressEvent event) {
    Player player = event.getPlayer();
    double newProgress = event.getNewProgress();
    double oldProgress = event.getOldProgress();

    // Reward players for progress milestones
    if (newProgress >= 50.0 && oldProgress < 50.0) {
        // Player reached 50% progress
        player.sendMessage("Congratulations on reaching 50% progress!");
    }
}
```

### Event Registration

Register your event listeners in your plugin's `onEnable()` method:

```java
@Override
public void onEnable() {
    // Register event listeners
    getServer().getPluginManager().registerEvents(new ChunklockListener(), this);
}
```

## Data Access API

Access raw data and statistics from the plugin.

### Player Data

```java
import me.chunklock.api.data.PlayerDataAccess;

PlayerDataAccess dataAccess = ChunklockAPI.getInstance().getPlayerDataAccess();

// Get raw player data
PlayerData playerData = dataAccess.getPlayerData(player.getUniqueId());

// Get player statistics
Map<String, Object> stats = dataAccess.getPlayerStatistics(player.getUniqueId());

// Update player data
dataAccess.updatePlayerData(playerData);
```

### Chunk Data

```java
import me.chunklock.api.data.ChunkDataAccess;

ChunkDataAccess chunkAccess = ChunklockAPI.getInstance().getChunkDataAccess();

// Get chunk information
ChunkData chunkData = chunkAccess.getChunkData(world, x, z);

// Get all chunks for player
List<ChunkData> playerChunks = chunkAccess.getPlayerChunks(player.getUniqueId());

// Update chunk data
chunkAccess.updateChunkData(chunkData);
```

### Team Data

```java
import me.chunklock.api.data.TeamDataAccess;

TeamDataAccess teamAccess = ChunklockAPI.getInstance().getTeamDataAccess();

// Get all teams
List<Team> allTeams = teamAccess.getAllTeams();

// Get team by ID
Team team = teamAccess.getTeam(teamId);

// Get team statistics
Map<String, Object> teamStats = teamAccess.getTeamStatistics(teamId);
```

## Configuration API

Access and modify plugin configuration programmatically.

### Configuration Access

```java
import me.chunklock.api.config.ConfigAccess;

ConfigAccess configAccess = ChunklockAPI.getInstance().getConfigAccess();

// Get configuration values
boolean economyEnabled = configAccess.getBoolean("economy.enabled");
double baseCost = configAccess.getDouble("economy.vault.base-cost");
String economyType = configAccess.getString("economy.type");

// Get complex configuration objects
Map<String, Double> biomeMults = configAccess.getBiomeMultipliers();
Map<Difficulty, Double> diffMults = configAccess.getDifficultyMultipliers();
```

### Configuration Updates

```java
// Update configuration values
configAccess.setValue("economy.vault.base-cost", 200.0);
configAccess.setValue("world.max-world-size", 75);

// Save configuration changes
configAccess.saveConfig();

// Reload configuration
configAccess.reloadConfig();
```

## Integration Examples

### Economy Plugin Integration

```java
public class MyEconomyIntegration {

    private EconomyService economyService;

    public void initialize() {
        economyService = ServiceContainer.getInstance().getService(EconomyService.class);
    }

    @EventHandler
    public void onChunkUnlock(ChunkUnlockEvent event) {
        if (economyService.getEconomyType().equals("vault")) {
            // Give bonus money for unlocking rare biome chunks
            Chunk chunk = event.getChunk();
            if (isRareBiome(chunk.getBlock(8, 64, 8).getBiome())) {
                economyService.depositPlayer(event.getPlayer(), 100.0);
                event.getPlayer().sendMessage("§aBonus $100 for unlocking rare biome!");
            }
        }
    }
}
```

### Statistics Plugin Integration

```java
public class StatsIntegration {

    @EventHandler
    public void onPlayerProgress(PlayerProgressEvent event) {
        Player player = event.getPlayer();
        double progress = event.getNewProgress();

        // Update external statistics system
        MyStatsPlugin.updateStat(player, "chunklock_progress", progress);

        // Award points for milestones
        if (progress % 10.0 == 0) { // Every 10% progress
            MyStatsPlugin.awardPoints(player, 50);
        }
    }
}
```

### Custom GUI Integration

```java
public class CustomGUIIntegration {

    public void openChunkMap(Player player) {
        ChunkService chunkService = ServiceContainer.getInstance().getService(ChunkService.class);

        // Create custom inventory GUI
        Inventory gui = Bukkit.createInventory(null, 54, "§6Chunk Map");

        // Populate with chunk information
        List<Chunk> unlockedChunks = chunkService.getUnlockedChunks(player);

        for (int i = 0; i < Math.min(unlockedChunks.size(), 54); i++) {
            Chunk chunk = unlockedChunks.get(i);
            ItemStack item = createChunkItem(chunk, chunkService);
            gui.setItem(i, item);
        }

        player.openInventory(gui);
    }

    private ItemStack createChunkItem(Chunk chunk, ChunkService chunkService) {
        Material material = Material.MAP;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6Chunk " + chunk.getX() + ", " + chunk.getZ());

        List<String> lore = Arrays.asList(
            "§7Difficulty: §f" + chunkService.getChunkDifficulty(chunk),
            "§7Biome: §f" + chunk.getBlock(8, 64, 8).getBiome(),
            "§7Status: §aUnlocked"
        );
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }
}
```

## Best Practices

### Error Handling

Always check for null values and handle exceptions:

```java
try {
    ChunkService chunkService = ServiceContainer.getInstance().getService(ChunkService.class);

    if (chunkService != null && chunkService.isHealthy()) {
        boolean result = chunkService.unlockChunk(chunk, player);
        // Handle result
    } else {
        // Service not available
        getLogger().warning("ChunkService not available");
    }
} catch (Exception e) {
    getLogger().severe("Error accessing Chunklock API: " + e.getMessage());
}
```

### Async Operations

Use async operations for database-heavy tasks:

```java
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
    // Heavy operations
    PlayerProgressService progressService = container.getService(PlayerProgressService.class);
    Map<String, Object> stats = progressService.getPlayerStatistics(player);

    // Return to main thread for Bukkit API calls
    Bukkit.getScheduler().runTask(plugin, () -> {
        // Update UI with stats
        updatePlayerGUI(player, stats);
    });
});
```

### Event Priority

Use appropriate event priorities for your listeners:

```java
@EventHandler(priority = EventPriority.HIGH)
public void onChunkUnlock(ChunkUnlockEvent event) {
    // High priority listener - executes before most other plugins
}

@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void onChunkUnlockMonitor(ChunkUnlockEvent event) {
    // Monitor priority - only for logging/stats, don't modify event
}
```

## API Compatibility

### Version Compatibility

| API Version | Plugin Version | Minecraft Version |
| ----------- | -------------- | ----------------- |
| 1.2.x       | 1.2.0 - 1.2.7  | 1.20.4+           |
| 1.1.x       | 1.1.0 - 1.1.9  | 1.20.1+           |
| 1.0.x       | 1.0.0 - 1.0.5  | 1.19.4+           |

### Deprecation Policy

- Deprecated methods are marked with `@Deprecated` annotation
- Deprecated methods remain functional for at least 2 minor versions
- Migration guides are provided in release notes

### API Changes

Check the [Change Log](Change-Log) for API changes and migration instructions when updating.

---

_For more examples and advanced usage, see our [GitHub repository](https://github.com/Lunary1/chunklock) examples folder._
