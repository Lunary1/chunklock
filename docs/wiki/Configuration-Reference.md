# Configuration Reference

This page provides a comprehensive reference for all configuration options available in Chunklock's `config.yml` file.

## Configuration File Structure

The configuration file is located at `plugins/Chunklock/config.yml` and is organized into several main sections:

```yaml
economy: # Economy and payment settings
world: # World and chunk settings
holograms: # Hologram and visual effects
borders: # Chunk border settings
teams: # Team system configuration
performance: # Performance optimization
messages: # Custom messages and localization
```

## Economy Configuration

Controls how players pay for chunk unlocks and economic balance.

### Basic Economy Settings

```yaml
economy:
  # Economy type: "materials" or "vault"
  # "materials" = Players pay with items/resources (default)
  # "vault" = Players pay with money through Vault economy plugin
  type: "materials"

  # Enable/disable economy features entirely
  enabled: true

  # Show cost previews before unlocking
  show-cost-preview: true
```

### Vault Economy Settings

Used when `economy.type` is set to `"vault"`:

```yaml
economy:
  vault:
    # Base cost for the first chunk unlock
    base-cost: 100.0

    # Additional cost per already unlocked chunk (progressive pricing)
    cost-per-unlocked: 25.0

    # Maximum cost cap to prevent excessive prices
    max-cost: 10000.0

    # Difficulty-based cost multipliers
    difficulty-multipliers:
      EASY: 0.5 # 50% of base cost
      NORMAL: 1.0 # 100% of base cost (default)
      HARD: 2.0 # 200% of base cost
      IMPOSSIBLE: 4.0 # 400% of base cost

    # Biome-specific cost multipliers
    biome-multipliers:
      PLAINS: 1.0
      FOREST: 1.2
      DESERT: 1.5
      JUNGLE: 2.0
      OCEAN: 1.8
      SWAMP: 1.6
      BADLANDS: 2.2
      SAVANNA: 1.3
      TAIGA: 1.4
      MOUNTAINS: 2.5
      NETHER_WASTES: 3.0
      END_HIGHLANDS: 5.0

    # Team discount settings
    team-discount:
      enabled: true
      # Discount percentage for team unlocks (0.0 to 1.0)
      discount-percentage: 0.15 # 15% discount
      # Minimum team size to qualify for discount
      min-team-size: 3
```

### Material Economy Settings

Used when `economy.type` is set to `"materials"`:

```yaml
economy:
  materials:
    # Base materials required for unlocking chunks
    base-requirements:
      # Material type and quantity
      STONE: 32
      COAL: 16
      IRON_INGOT: 8

    # Progressive cost increase per unlocked chunk
    progression-multiplier: 1.1 # 10% increase per unlock

    # Difficulty-based material multipliers
    difficulty-multipliers:
      EASY: 0.6
      NORMAL: 1.0
      HARD: 1.8
      IMPOSSIBLE: 3.0

    # Biome-specific material requirements
    biome-requirements:
      DESERT:
        SAND: 64
        CACTUS: 16
      OCEAN:
        KELP: 32
        PRISMARINE: 8
      NETHER_WASTES:
        NETHERRACK: 64
        NETHER_QUARTZ: 16
        BLAZE_POWDER: 4

    # Alternative payment options
    alternative-payments:
      # Players can pay with XP instead of materials
      allow-xp-payment: true
      xp-cost-multiplier: 5.0 # XP levels = material cost * multiplier

      # Players can pay with time (must wait instead of paying)
      allow-time-payment: true
      time-cost-hours: 2.0 # Hours to wait instead of paying
```

## World Configuration

Controls world generation, chunk behavior, and player worlds.

### Basic World Settings

```yaml
world:
  # Starting chunk configuration
  spawn-chunk-size: "3x3" # Starting area size (1x1, 3x3, 5x5, 7x7)
  spawn-protection: true # Protect spawn chunks from griefing

  # World size limits
  max-world-size: 50 # Maximum radius in chunks from spawn
  warn-at-size: 40 # Warn players when approaching limit

  # Chunk loading behavior
  preload-adjacent: true # Preload chunks adjacent to unlocked ones
  auto-load-on-login: true # Load player's chunks when they log in

  # World type configuration
  world-type: "per-player" # "per-player", "shared", or "team-based"
```

### Per-Player World Settings

Used when `world.world-type` is `"per-player"`:

```yaml
world:
  per-player:
    # World name template (%player% = player name, %uuid% = player UUID)
    world-name-template: "%player%_world"

    # World generation settings
    world-type: "NORMAL" # NORMAL, FLAT, LARGE_BIOMES, etc.
    generate-structures: true # Villages, dungeons, etc.

    # Border settings for individual worlds
    initial-border-size: 48 # Starting world border size
    expand-border-on-unlock: true # Expand border when unlocking chunks
    border-expand-amount: 16 # Chunks to expand border by

    # Resource management
    auto-cleanup-inactive: true # Delete inactive player worlds
    inactive-days: 30 # Days before considering world inactive
    backup-before-cleanup: true # Backup worlds before deletion
```

### Shared World Settings

Used when `world.world-type` is `"shared"`:

```yaml
world:
  shared:
    # Main world name for shared progression
    world-name: "chunklock_world"

    # Spawn location management
    spawn-distance-min: 1000 # Minimum distance between player spawns
    spawn-attempts: 10 # Attempts to find suitable spawn location

    # Chunk ownership
    allow-chunk-transfer: true # Players can transfer chunks to others
    ownership-timeout: 7 # Days before abandoned chunks become available

    # Conflict resolution
    contested-chunks: true # Allow multiple players to contest chunks
    contest-duration: 24 # Hours for chunk contests
    contest-cost-multiplier: 2.0 # Cost multiplier for contested chunks
```

## Hologram Configuration

Controls visual hologram displays throughout the plugin.

### Basic Hologram Settings

```yaml
holograms:
  # Enable/disable hologram system
  enabled: true

  # Hologram provider: "FancyHolograms", "HolographicDisplays", or "internal"
  provider: "FancyHolograms"

  # Global hologram settings
  update-interval: 10 # Seconds between hologram updates
  view-distance: 32 # Chunks within which holograms are visible

  # Performance settings
  max-holograms-per-player: 20 # Limit holograms per player to reduce lag
  async-updates: true # Update holograms asynchronously
```

### Hologram Display Settings

```yaml
holograms:
  displays:
    # Chunk status holograms
    chunk-status:
      enabled: true
      height-offset: 2.5 # Blocks above ground
      show-to-owner-only: false
      content:
        - "&6Chunk Status"
        - "&7Owner: &f%owner%"
        - "&7Difficulty: &f%difficulty%"
        - "&7Cost: &f%cost%"

    # Progress tracking holograms
    progress-tracker:
      enabled: true
      height-offset: 3.0
      update-frequency: 30 # Seconds
      content:
        - "&bProgress Tracker"
        - "&7Chunks: &f%unlocked%/%total%"
        - "&7Progress: &f%percentage%%"

    # Team information holograms
    team-info:
      enabled: true
      show-near-spawn: true
      content:
        - "&aTeam: &f%team_name%"
        - "&7Members: &f%member_count%/%max_members%"
        - "&7Team Chunks: &f%team_chunks%"

    # Economy information
    economy-display:
      enabled: true
      show-costs: true
      content:
        - "&eUnlock Cost"
        - "&7Next Chunk: &f%next_cost%"
        - "&7Your Balance: &f%balance%"
```

## Border Configuration

Controls the visual chunk borders and their appearance.

### Basic Border Settings

```yaml
borders:
  # Enable/disable border system
  enabled: true

  # Border material and appearance
  material: "GLASS" # Block type for borders
  show-to-all: false # Show borders to all players or just owners
  height: 3 # Height of border walls

  # Border behavior
  auto-remove-on-unlock: true # Remove borders when chunks are unlocked
  fade-duration: 5 # Seconds for border fade animation

  # Performance settings
  update-interval: 30 # Seconds between border updates
  batch-size: 50 # Chunks to update per batch
```

### Advanced Border Settings

```yaml
borders:
  # Different materials for different chunk states
  materials:
    locked: "RED_STAINED_GLASS"
    unlockable: "YELLOW_STAINED_GLASS"
    unlocked: "GREEN_STAINED_GLASS"
    contested: "ORANGE_STAINED_GLASS"

  # Border effects
  effects:
    particles: true # Show particle effects on borders
    particle-type: "FLAME" # Particle type for effects
    particle-count: 5 # Particles per effect

    sounds: true # Play sounds for border events
    unlock-sound: "BLOCK_GLASS_BREAK"
    lock-sound: "BLOCK_GLASS_PLACE"

  # Border visibility rules
  visibility:
    hide-from-bypassed: true # Hide borders from players with bypass
    show-only-adjacent: true # Only show borders for adjacent chunks
    fade-with-distance: true # Fade borders based on player distance
```

## Team Configuration

Controls team system behavior and limitations.

### Basic Team Settings

```yaml
teams:
  # Enable/disable team system
  enabled: true

  # Team size and limits
  max-team-size: 8 # Maximum players per team
  min-team-size: 2 # Minimum players to form team
  max-teams-per-server: 100 # Global team limit

  # Team creation settings
  creation-cost: 0 # Cost to create team (Vault economy)
  creation-materials: # Materials required to create team
    DIAMOND: 2
    GOLD_INGOT: 8

  # Team naming rules
  name-min-length: 3
  name-max-length: 16
  allow-special-characters: false
  blocked-words: # Blocked words in team names
    - "admin"
    - "staff"
    - "owner"
```

### Team Functionality Settings

```yaml
teams:
  functionality:
    # Shared progression
    shared-unlocks: true # Team members share unlocked chunks
    individual-costs: false # Each member pays individually

    # Leadership and roles
    auto-promote-on-leave: true # Auto-promote when leader leaves
    moderator-permissions: # What moderators can do
      - "invite"
      - "kick_members"
      - "manage_chunks"

    # Team communication
    team-chat: true # Enable team-only chat
    chat-prefix: "&a[TEAM] " # Prefix for team messages

    # Shared resources
    shared-inventory: false # Share team inventory (advanced feature)
    shared-economy: true # Pool team funds for unlocks

  # Team benefits and bonuses
  bonuses:
    unlock-speed-bonus: 1.2 # 20% faster unlocks with team
    cost-reduction: 0.15 # 15% cost reduction for teams
    experience-multiplier: 1.5 # Extra XP for team activities
```

## Performance Configuration

Settings to optimize plugin performance for different server sizes.

### Basic Performance Settings

```yaml
performance:
  # Threading and async operations
  async-chunk-loading: true # Load chunks asynchronously
  chunk-loading-threads: 4 # Threads for chunk operations

  # Update intervals (in seconds)
  border-update-interval: 30 # How often to update borders
  hologram-update-interval: 10 # How often to update holograms
  data-save-interval: 300 # How often to save data (5 minutes)

  # Batch processing
  max-operations-per-tick: 5 # Max operations per server tick
  batch-process-size: 20 # Items to process per batch

  # Memory management
  cache-size: 1000 # Number of chunks to cache in memory
  cleanup-interval: 600 # Seconds between memory cleanup
```

### Database and Storage Settings

```yaml
performance:
  database:
    # Database type: "file" or "mysql"
    type: "file"

    # Connection pooling
    max-connections: 10
    connection-timeout: 30 # Seconds

    # Query optimization
    batch-statements: true
    prepared-statements: true

    # File database settings (SQLite)
    file-database:
      auto-backup: true
      backup-interval: 3600 # Seconds (1 hour)
      compress-backups: true

  # Caching settings
  caching:
    player-data-cache: 500 # Players to cache
    chunk-data-cache: 2000 # Chunks to cache
    team-data-cache: 100 # Teams to cache
    cache-expiry: 1800 # Seconds (30 minutes)
```

### Large Server Optimization

```yaml
performance:
  large-server:
    # Enable large server optimizations
    enabled: false # Set to true for 100+ players

    # Resource limits
    max-chunk-operations: 100 # Max concurrent chunk operations
    player-update-limit: 50 # Players to update per cycle

    # Load balancing
    distribute-operations: true # Spread operations across time
    priority-queue: true # Prioritize important operations

    # Monitoring and alerts
    performance-monitoring: true
    alert-threshold-ms: 50 # Alert if operations take longer
    log-slow-operations: true
```

## Messages and Localization

Customize all plugin messages and add multi-language support.

### Basic Message Settings

```yaml
messages:
  # Default language
  default-language: "en"

  # Message formatting
  prefix: "&8[&6Chunklock&8] "
  error-prefix: "&8[&cError&8] "
  success-prefix: "&8[&aSuccess&8] "

  # Colors and formatting
  primary-color: "&6" # Gold
  secondary-color: "&7" # Gray
  accent-color: "&e" # Yellow
  error-color: "&c" # Red
  success-color: "&a" # Green
```

### Custom Messages

```yaml
messages:
  # Player feedback messages
  chunk-unlocked: "%prefix%&aChunk unlocked successfully!"
  chunk-locked: "%prefix%&cThis chunk is locked!"
  insufficient-funds: "%prefix%&cYou need %cost% to unlock this chunk."

  # Team messages
  team-created: "%prefix%&aTeam '%team%' created successfully!"
  team-joined: "%prefix%&aYou joined team '%team%'!"
  team-left: "%prefix%&7You left your team."

  # Error messages
  no-permission: "%prefix%&cYou don't have permission to do that."
  player-not-found: "%prefix%&cPlayer '%player%' not found."
  team-not-found: "%prefix%&cTeam '%team%' not found."

  # Status messages
  status-format:
    - "&6=== Chunklock Status ==="
    - "&7Player: &f%player%"
    - "&7Chunks Unlocked: &f%unlocked%"
    - "&7Team: &f%team%"
    - "&7Progress: &f%progress%%"
```

## Configuration Examples

### Small Server (1-20 players)

```yaml
economy:
  type: "materials"
world:
  spawn-chunk-size: "3x3"
  max-world-size: 30
performance:
  chunk-loading-threads: 2
  border-update-interval: 60
teams:
  max-team-size: 4
  enabled: true
holograms:
  enabled: true
  update-interval: 15
```

### Medium Server (20-100 players)

```yaml
economy:
  type: "vault"
  vault:
    base-cost: 200.0
    cost-per-unlocked: 50.0
world:
  spawn-chunk-size: "5x5"
  max-world-size: 50
performance:
  chunk-loading-threads: 4
  border-update-interval: 30
  async-chunk-loading: true
teams:
  max-team-size: 6
  shared-unlocks: true
holograms:
  enabled: true
  update-interval: 10
  max-holograms-per-player: 15
```

### Large Server (100+ players)

```yaml
economy:
  type: "vault"
  vault:
    base-cost: 500.0
    cost-per-unlocked: 100.0
    max-cost: 50000.0
world:
  spawn-chunk-size: "7x7"
  max-world-size: 75
  world-type: "shared"
performance:
  chunk-loading-threads: 8
  border-update-interval: 15
  async-chunk-loading: true
  large-server:
    enabled: true
    max-chunk-operations: 200
teams:
  max-team-size: 8
  shared-unlocks: true
  bonuses:
    cost-reduction: 0.20
holograms:
  enabled: true
  update-interval: 5
  max-holograms-per-player: 10
```

## Configuration Validation

The plugin automatically validates configuration on startup. Common issues:

### YAML Syntax Errors

- **Indentation**: Use spaces, not tabs
- **Colons**: Space required after colons
- **Quotes**: Use quotes for strings with special characters

### Invalid Values

- **Numbers**: Must be positive for counts and sizes
- **Colors**: Must be valid Minecraft color codes
- **Materials**: Must be valid Minecraft block/item names

### Performance Warnings

- High thread counts on small servers
- Very frequent update intervals
- Large cache sizes on limited memory

---

_For troubleshooting configuration issues, see the [Troubleshooting](Troubleshooting) guide._
