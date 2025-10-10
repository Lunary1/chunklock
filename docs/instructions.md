# Chunklock Plugin - Development Instructions

## Project Overview

Chunklock is a sophisticated progression-based Minecraft plugin for Paper 1.20.4+ that transforms survival gameplay by requiring players to unlock chunks through resource-based scoring. The plugin features comprehensive team systems, advanced visual effects with hologram integration, glass border visualization, and per-player world support.

## Development Environment Setup

### Prerequisites

- Java 17+
- Maven 3.6+
- Paper 1.20.4+ test server
- IDE (IntelliJ IDEA recommended)
- FancyHolograms plugin (for hologram features)

### Build Instructions

```bash
# Clone the repository
git clone <repository-url>
cd chunklock-plugin

# Build the plugin
mvn clean package

# The compiled JAR will be in target/Chunklock.jar
```

### IDE Setup

1. Import as Maven project
2. Ensure Java 17 is configured
3. Maven will automatically download dependencies including Paper API and FancyHolograms

## Architecture Overview

### Core Systems

1. **Chunk Management** (`ChunkLockManager`)

   - Handles chunk locking/unlocking logic with resource-based evaluation
   - Integrates with team systems for shared progress
   - Maintains chunk state persistence across server restarts

2. **Evaluation System** (`ChunkEvaluator`)

   - Calculates chunk difficulty based on biome type and block content
   - Uses configurable scoring thresholds (Easy/Normal/Hard/Impossible)
   - Supports dynamic difficulty scaling based on world configuration

3. **Enhanced Team System** (`EnhancedTeamManager`, `BasicTeamCommandHandler`)

   - Advanced team functionality with roles (Owner/Officer/Member) and permissions
   - Cost multipliers based on team size with configurable scaling
   - Team chat, join requests, and comprehensive management features

4. **Hologram System** (`HologramService`, `HologramDisplayService`)

   - Modular provider system supporting FancyHolograms
   - Real-time chunk unlock requirement display
   - Performance-optimized with view distance culling and update batching

5. **Border Visualization** (`ChunkBorderManager`, `BorderPlacementService`)

   - Glass border system for visual chunk boundaries
   - Interactive borders that open unlock GUIs when clicked
   - Configurable materials and height settings

6. **World Management** (`WorldManager`)
   - Per-player world support with individual progression tracking
   - World validation and configuration management
   - Multi-world compatibility with selective enabling

### Command System

Commands use a modular `SubCommand` pattern with comprehensive validation:

- `ChunklockCommandExecutor` - Main command coordinator
- Individual command classes for each subcommand (debug, team, status, etc.)
- Automatic permission checking and tab completion
- World validation for command execution

### Configuration Management

- `ConfigValidator` - Ensures configuration completeness and validates settings
- `DataMigrator` - Handles migration from legacy configurations
- Single `config.yml` with comprehensive biome costs and chunk values
- Hot-reload capability without data loss

## Development Guidelines

### Code Style

- Use descriptive variable and method names following camelCase convention
- Add comprehensive JavaDoc comments for all public APIs
- Follow existing error handling patterns with proper logging levels
- Maintain null safety checks and defensive programming practices
- Use final classes and immutable objects where appropriate

### Component Architecture

The plugin uses dependency injection patterns in `ChunklockPlugin.java`:

```java
// Components are initialized in strict dependency order
this.worldManager = new WorldManager(this);
this.chunkValueRegistry = new ChunkValueRegistry(this);
this.enhancedTeamManager = new EnhancedTeamManager(this);
// ... other components follow
```

### Testing Approach

- Test with Paper 1.20.4+ servers (Pufferfish and Purpur compatibility)
- Verify functionality in both single-player and team scenarios
- Test configuration migration from older versions
- Validate hologram functionality with FancyHolograms integration
- Performance testing with high player counts and large numbers of unlocked chunks

### Adding New Features

1. **New Commands**

   ```java
   public class MyCommand extends SubCommand {
       public MyCommand() {
           super("mycommand", "chunklock.mycommand", true);
       }

       @Override
       public boolean execute(CommandSender sender, String[] args) {
           // Implementation with proper validation
           return true;
       }
   }
   ```

   Register in `ChunklockCommandExecutor.registerSubCommands()`

2. **New Configuration Options**

   - Add to `config.yml` with appropriate defaults
   - Update `ConfigValidator.validateAndEnsureComplete()`
   - Follow existing patterns for loading values in respective managers

3. **New Hologram Providers**
   - Implement `HologramProvider` interface
   - Add provider detection in `HologramService.initializeProvider()`
   - Follow FancyHolograms provider as reference implementation

### Error Handling Standards

- Use appropriate logging levels (INFO for normal operations, WARNING for recoverable issues, SEVERE for critical errors)
- Provide meaningful error messages to users through Components with color coding
- Implement graceful degradation when optional features (like holograms) fail
- Follow patterns in `InitializationManager` for component initialization

### Performance Considerations

- Hologram updates are batched and distance-culled for optimal performance
- Border updates use queuing system to prevent server lag
- Chunk evaluation results are cached when appropriate
- Use async processing for expensive operations where possible

## Testing

### Development Server Setup

1. Install Paper 1.20.4+ server
2. Install FancyHolograms plugin for hologram testing
3. Copy built JAR to `plugins/` folder
4. Configure test worlds in `config.yml`
5. Set up test teams and progression scenarios

### Key Test Scenarios

- New player progression from spawn chunk
- Team creation, joining, and collaborative chunk unlocking
- Configuration reload without data loss
- Hologram display performance with multiple players
- Border visualization system across different biomes
- Multi-world functionality and world-specific configurations

### Debug Tools

- Built-in performance monitoring through component statistics and admin commands

## Deployment

### Production Considerations

- Ensure proper backup procedures for player data and team configurations
- Monitor performance with large numbers of unlocked chunks (use admin commands)
- Configure appropriate memory settings for hologram systems
- Test configuration migration thoroughly before version updates

### Performance Monitoring

- Use `HologramService.getStatistics()` for hologram performance metrics
- Monitor chunk evaluation performance through debug commands
- Watch memory usage with large team systems using border statistics
- Regular monitoring of background task performance

### Server Compatibility

- Primary target: Paper 1.20.4+
- Tested compatibility: Pufferfish, Purpur
- Requires FancyHolograms for visual features
- Compatible with most standard plugins (Vault integration planned)

## Troubleshooting

### Common Issues

1. **Configuration Problems**: Check `ConfigValidator` logs and use `/chunklock debug config`
2. **Hologram Issues**: Verify FancyHolograms version compatibility and check provider availability
3. **Team System Problems**: Check `EnhancedTeamManager` initialization and team data file permissions
4. **Border Display Issues**: Use `/chunklock borders info` for border system diagnostics
5. **World Loading**: Validate world configuration in `WorldManager` and check world-specific settings
6. **"World not set up" after restart**: If you get "The Chunklock world has not been set up yet" after restarting the server, but the world exists, this is normal. The plugin will automatically detect and load existing ChunkLock worlds from disk. If the issue persists, check that the world folder exists in your server directory and verify the world name in your config matches the folder name.

### Performance Issues

- High memory usage: Check hologram count and border statistics
- Server lag: Monitor background task performance and adjust update frequencies
- Chunk loading delays: Verify world management configuration

### Data Recovery

- Team data stored in `plugins/Chunklock/teams/`
- Player data in `plugins/Chunklock/players/`
- Configuration backups created automatically during migration
- Use debug commands to verify data integrity

## Contributing

### Pull Request Process

1. Create feature branch from main
2. Follow existing code patterns, architecture, and style guidelines
3. Add appropriate tests and update documentation
4. Ensure configuration migration compatibility
5. Test with multiple Minecraft versions if applicable
6. Update changelog and version numbers appropriately

### Code Review Focus Areas

- Performance impact on chunk operations and hologram systems
- Compatibility with existing team systems and configurations
- Configuration backward compatibility and migration safety
- Error handling, user experience, and accessibility
- Security considerations for command permissions and data access

### Documentation Standards

- Comprehensive inline JavaDoc for all public methods
- Clear configuration examples and usage guides
- Troubleshooting documentation for common issues
- Migration guides and upgrade instructions
- Performance tuning recommendations for server administrators
