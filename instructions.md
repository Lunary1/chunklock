# Chunklock Plugin - Development Instructions

## Project Overview

Chunklock is a strategic progression-based Minecraft plugin for Paper 1.20.4+ that transforms survival gameplay by requiring players to unlock chunks through resource-based scoring. The plugin features team systems, visual effects, hologram integration, glass border visualization, and comprehensive world management.

## Development Environment Setup

### Prerequisites
- Java 17+
- Maven 3.6+
- Paper 1.20.4+ test server
- IDE (IntelliJ IDEA recommended)

### Build Instructions
```bash
# Clone the repository
git clone <repository-url>
cd chunklock

# Build the plugin
mvn clean package

# The compiled JAR will be in target/Chunklock.jar
```

### IDE Setup
1. Import as Maven project
2. Ensure Java 17 is configured
3. Maven will automatically download dependencies

## Architecture Overview

### Core Systems

1. **Chunk Management** (`ChunkLockManager`)
   - Handles chunk locking/unlocking logic
   - Integrates with team systems for shared progress
   - Manages chunk data persistence in data.yml

2. **Evaluation System** (`ChunkEvaluator`)
   - Calculates chunk difficulty based on biome and block content
   - Uses configurable scoring thresholds (Easy/Normal/Hard/Impossible)
   - Optimized block scanning with caching support

3. **Team System** (`EnhancedTeamManager`, `TeamManager`)
   - Advanced team functionality with roles and permissions
   - Cost multipliers based on team size
   - Team data persistence in teams.yml

4. **Hologram System** (`HologramService`)
   - Modular provider system for different hologram plugins
   - Currently supports FancyHolograms
   - Components:
     - `HologramProvider` - Interface for hologram implementations
     - `HologramDisplayService` - Manages hologram display and updates
     - `HologramTracker` - Tracks hologram state
     - `FancyHologramsProvider` - FancyHolograms implementation

5. **Border Visualization** (`ChunkBorderManager`, `BorderPlacementService`)
   - Glass border display system for chunk boundaries
   - Configurable materials and colors
   - Automatic cleanup and refresh mechanisms
   - Border state management and update queue

6. **World Management** (`WorldManager`)
   - Multi-world support
   - World validation and configuration
   - Starting chunk assignment per world

7. **Progress Tracking** (`PlayerProgressTracker`, `PlayerDataManager`)
   - Player progression and statistics
   - Unlocked chunks per player
   - Data persistence and migration

### Command System

Commands are organized using a modular `SubCommand` pattern:
- `ChunklockCommandExecutor` - Main command handler and router
- `ChunklockCommandManager` - Base command management
- Individual command classes for each subcommand:
  - `UnlockCommand` - Unlock chunks
  - `StatusCommand` - View player status
  - `TeamCommand` - Team management
  - `SpawnCommand` - Return to starting chunk
  - `BorderCommand` - Border visualization controls
  - `DebugCommand` - Comprehensive debugging tools
  - `DiagnosticCommand` - System diagnostics
  - `BypassCommand` - Admin bypass controls
  - `ResetCommand` - Reset player data
  - `ReloadCommand` - Reload configuration
  - `HelpCommand` - Display help information

### Configuration Management

- `ConfigValidator` - Validates configuration completeness and correctness
- `DataMigrator` - Migrates legacy configurations from older versions
- Single `config.yml` for all plugin settings
- Automatic migration from legacy files:
  - `chunk_values.yml` → `config.yml`
  - `biome_costs.yml` → `config.yml`
  - `team_config.yml` → `config.yml`
  - `player_chunks.yml` → `data.yml`
  - `player_progress.yml` → `data.yml`
  - `chunk_data.yml` → `data.yml`
  - `teams_enhanced.yml` → `teams.yml`

### UI Components

- `UnlockGui` - Interactive GUI for chunk unlocking
- `UnlockGuiBuilder` - GUI construction and layout
- `UnlockGuiStateManager` - GUI state management
- `UnlockGuiListener` - GUI event handling

### Event Listeners

- `PlayerListener` - Player movement and interaction events
- `BlockProtectionListener` - Protects locked chunks from modifications
- `TeleportListener` - Manages teleportation restrictions
- `BorderListener` - Handles border-related events
- `PlayerJoinQuitListener` - Player join/quit event handling

## Development Guidelines

### Code Style
- Use descriptive variable and method names
- Add comprehensive JavaDoc comments for public APIs
- Follow existing error handling patterns with proper logging
- Maintain null safety checks
- Use Java 17 features where appropriate

### Testing Approach
- Test with Paper 1.20.4+ servers
- Verify functionality in both single-player and team scenarios
- Test configuration migration from older versions
- Validate hologram functionality with FancyHolograms
- Test border visualization performance

### Adding New Features

1. **New Commands**
   ```java
   public class MyCommand extends SubCommand {
       public MyCommand() {
           super("mycommand", "chunklock.mycommand", false);
       }
       
       @Override
       public boolean execute(CommandSender sender, String[] args) {
           // Implementation
           return true;
       }
       
       @Override
       public List<String> getTabCompletions(CommandSender sender, String[] args) {
           return Collections.emptyList();
       }
       
       @Override
       public String getUsage() {
           return "/chunklock mycommand";
       }
       
       @Override
       public String getDescription() {
           return "Description of my command";
       }
   }
   ```
   Register in `ChunklockCommandExecutor.registerSubCommands()`

2. **New Configuration Options**
   - Add to `config.yml` in `src/main/resources/`
   - Update `ConfigValidator` to include validation
   - Follow existing patterns for loading values in respective managers

3. **New Hologram Providers**
   - Implement `HologramProvider` interface
   - Add to `HologramService` initialization
   - Follow the pattern in `FancyHologramsProvider`

### Component Integration

The plugin uses dependency injection patterns in `ChunklockPlugin`:

```java
// Components are initialized in dependency order
this.worldManager = new WorldManager(this);
this.chunkValueRegistry = new ChunkValueRegistry(this);
this.enhancedTeamManager = new EnhancedTeamManager(this);
this.teamManager = new TeamManager(this);
this.progressTracker = new PlayerProgressTracker(this, teamManager);
// ... other components
```

### Error Handling
- Use appropriate logging levels (INFO, WARNING, SEVERE, FINE)
- Provide meaningful error messages to users
- Implement graceful degradation when optional features fail
- Follow existing patterns in `InitializationManager`
- Use try-catch blocks appropriately, especially for external API calls

## Testing

### Development Server Setup
1. Install Paper 1.20.4+
2. Copy built JAR to `plugins/` folder
3. Install FancyHolograms for hologram testing (optional)
4. Configure test worlds in `config.yml`

### Key Test Scenarios
- New player progression from spawn chunk
- Team creation and chunk sharing
- Configuration reload without data loss
- Hologram display and performance
- Border visualization system
- Chunk unlocking with different difficulty tiers
- Data migration from legacy versions

## Deployment

### Production Considerations
- Ensure proper backup procedures for player data (data.yml, teams.yml)
- Monitor performance with large numbers of unlocked chunks
- Configure appropriate memory settings for hologram systems
- Test configuration migration thoroughly before updates
- Review server compatibility (Paper, Pufferfish, Purpur)

### Performance Monitoring
- Use `HologramService.getStatistics()` for hologram metrics
- Monitor chunk evaluation performance through debug commands
- Watch memory usage with large team systems
- Use `/chunklock debug` for system diagnostics

## Troubleshooting

### Common Issues

1. **Configuration Problems**
   - Check `ConfigValidator` logs on startup
   - Verify config.yml format and values
   - Review migration messages for legacy config files

2. **Hologram Issues**
   - Verify FancyHolograms is installed and compatible
   - Check hologram configuration in config.yml
   - Use `/chunklock debug` to inspect hologram service

3. **Team System Problems**
   - Check `EnhancedTeamManager` initialization logs
   - Verify teams.yml is not corrupted
   - Review team permissions and roles

4. **World Loading**
   - Validate world configuration in `WorldManager`
   - Ensure worlds exist and are loaded by server
   - Check starting chunk assignments

5. **Border Visualization**
   - Verify border configuration in config.yml
   - Check for conflicting plugins modifying blocks
   - Use border debug commands to inspect state

### Debug Tools

- `/chunklock debug` - Comprehensive system status
  - `debug chunk` - Current chunk information
  - `debug list` - List unlocked chunks
  - `debug bordertest` - Test border system
  - `debug borderdebug` - Border system diagnostics
  - `debug guidebug` - GUI system diagnostics
  - `debug testunlock` - Test unlock functionality

- `/chunklock diagnostic` - Basic functionality verification

- Server logs - Check for error messages and warnings during startup

## Utility Classes

### Key Utilities

- `MessageUtil` - Formatted message sending with color codes
- `ColorUtil` - Color and gradient utilities
- `BiomeUtil` - Biome-related utilities
- `ChunkUtils` - Chunk coordinate and boundary utilities
- `ServerCompatibility` - Server version compatibility checks
- `ParticleUtil` - Particle effect utilities
- `EnchantmentUtil` - Enchantment utilities for GUI items

## Contributing

### Pull Request Process
1. Create feature branch from main
2. Follow existing code patterns and style
3. Add appropriate JavaDoc comments
4. Ensure configuration migration compatibility
5. Test with multiple Minecraft versions if applicable
6. Update relevant documentation

### Code Review Focus
- Performance impact on chunk operations
- Compatibility with team systems
- Configuration backward compatibility
- Error handling and user experience
- Memory efficiency
- Thread safety considerations

## Project Structure

```
src/main/java/me/chunklock/
├── ChunklockPlugin.java          # Main plugin class
├── border/                        # Border visualization system
│   ├── BorderConfig.java
│   ├── BorderConfigLoader.java
│   ├── BorderPlacementService.java
│   ├── BorderRefreshService.java
│   ├── BorderStateManager.java
│   └── BorderUpdateQueue.java
├── commands/                      # Command system
│   ├── ChunklockCommandExecutor.java
│   ├── ChunklockCommandManager.java
│   ├── SubCommand.java
│   └── [Individual command classes]
├── hologram/                      # Hologram system
│   ├── HologramService.java
│   ├── api/                       # Hologram API
│   ├── config/                    # Hologram configuration
│   ├── display/                   # Display management
│   ├── provider/                  # Provider implementations
│   ├── tracking/                  # State tracking
│   └── util/                      # Hologram utilities
├── listeners/                     # Event listeners
├── managers/                      # Core managers
│   ├── ChunkLockManager.java
│   ├── ChunkEvaluator.java
│   ├── TeamManager.java
│   ├── EnhancedTeamManager.java
│   ├── PlayerProgressTracker.java
│   ├── PlayerDataManager.java
│   ├── ChunkValueRegistry.java
│   ├── BiomeUnlockRegistry.java
│   ├── WorldManager.java
│   └── ChunkBorderManager.java
├── models/                        # Data models
│   ├── ChunkData.java
│   ├── Team.java
│   ├── TeamRole.java
│   ├── TeamSettings.java
│   └── Difficulty.java
├── services/                      # Services
│   └── StartingChunkService.java
├── ui/                            # User interface
│   ├── UnlockGui.java
│   ├── UnlockGuiBuilder.java
│   ├── UnlockGuiStateManager.java
│   └── UnlockGuiListener.java
└── util/                          # Utilities
    ├── ConfigValidator.java
    ├── DataMigrator.java
    ├── InitializationManager.java
    ├── MessageUtil.java
    ├── BiomeUtil.java
    ├── ChunkUtils.java
    ├── ColorUtil.java
    ├── ServerCompatibility.java
    └── [Other utilities]
```

## Version Information

- **Current Version**: 1.2.5
- **Target Minecraft**: 1.20.4+
- **API Version**: 1.20
- **Java Version**: 17

## Dependencies

- **Paper API**: 1.20.4-R0.1-SNAPSHOT (provided)
- **FancyHolograms**: 2.4.2 (soft dependency, optional)
- **bStats**: 3.0.2 (metrics, bundled)

## License

Proprietary / Private - Contact LunaryCraft Dev Team for licensing information.
