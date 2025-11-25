# Configuration Reference

This page provides a comprehensive reference for all configuration options available in Chunklock's modular configuration system.

## Modular Configuration System

Chunklock uses a modular configuration system with separate files for each feature area. This makes configuration easier to manage, understand, and maintain.

### Configuration Files Overview

All configuration files are located in `plugins/Chunklock/`:

```
plugins/Chunklock/
├── config.yml          # Main configuration (minimal)
├── economy.yml         # Economy and payment settings
├── openai.yml          # OpenAI/ChatGPT integration
├── biome-unlocks.yml  # Biome-specific unlock requirements
├── block-values.yml    # Block values and biome weights
├── team-settings.yml   # Team system configuration
├── borders.yml         # Glass border system settings
├── worlds.yml         # World configuration
├── holograms.yml      # Hologram display settings
├── debug.yml          # Debug and logging options
├── performance.yml    # Performance tuning settings
└── lang/
    └── en.yml         # English language file
```

**Note**: All configuration files are automatically generated on first plugin startup with default values. If a file is missing, restart the server to regenerate it.

---

## config.yml

The main configuration file contains only essential settings.

```yaml
# Configuration version (used for migration detection)
config-version: 2

# First run flag (automatically set to false after first load)
first-run: true

# Language setting (language file must exist in lang/ directory)
# Available languages: en (English), de (German), fr (French), etc.
language: "en"
```

### Settings

- **config-version**: Internal version number for configuration migration (do not modify)
- **first-run**: Automatically managed by the plugin (do not modify)
- **language**: Default language code. Must match a language file in `lang/` directory (e.g., `en.yml`, `de.yml`)

---

## economy.yml

Controls how players pay for chunk unlocks and economic balance.

### Basic Economy Settings

```yaml
# Economy type: "materials" or "vault"
# "materials" = Players pay with items/resources (default behavior)
# "vault" = Players pay with money through Vault economy plugin
type: "vault"

# Vault economy settings (only used if type = "vault")
vault:
  # Base cost multiplier for vault payments
  base-cost: 100.0
  # Additional cost per unlocked chunk (progressive pricing)
  cost-per-unlocked: 25.0
  # Difficulty multipliers
  difficulty-multipliers:
    EASY: 0.5
    NORMAL: 1.0
    HARD: 2.0
    IMPOSSIBLE: 4.0
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
    SNOWY_PLAINS: 1.7
    DEEP_OCEAN: 2.5

# Material economy settings (only used if type = "materials")
materials:
  # Enable/disable material requirements
  enabled: true
  # Allow fallback to vault if materials not available
  vault-fallback: false

# Material-to-vault conversion values (used when AI converts material costs to vault)
# Values represent vault cost per unit of material
material-values:
  DIAMOND: 50.0
  EMERALD: 45.0
  GOLD_INGOT: 25.0
  IRON_INGOT: 10.0
  COPPER_INGOT: 5.0
  COAL: 2.0
  COBBLESTONE: 0.5
```

### Economy Type Options

**Vault Economy** (`type: "vault"`):
- Requires Vault plugin and an economy plugin (EssentialsX, CMI, etc.)
- Players pay with server currency
- Costs scale with difficulty, biome, and progress
- Supports progressive pricing (costs increase with each unlock)

**Materials Economy** (`type: "materials"`):
- No external dependencies required
- Players pay with items from their inventory
- Uses biome-specific requirements from `biome-unlocks.yml`
- Can include custom items from Oraxen or MMOItems

---

## openai.yml

Configures OpenAI/ChatGPT integration for AI-powered dynamic cost calculation.

```yaml
# Master switch - set to true to enable AI-powered cost calculation
enabled: true

# Your OpenAI API key (keep this secret!)
api-key: "REPLACE_WITH_YOUR_OPENAI_API_KEY"

# OpenAI model settings
model: "gpt-4o-mini" # OpenAI model to use (gpt-4o-mini is cost-effective)
max-tokens: 300 # Maximum tokens per request
temperature: 0.3 # Response consistency (0.0-2.0, lower = more consistent)

# User experience settings
transparency: false # Show AI explanations to players
fallback-on-error: true # Use traditional calculation if OpenAI fails

# Performance settings
cache-duration-minutes: 5 # How long to cache responses to reduce API calls
request-timeout-seconds: 10 # Timeout for API requests

# Safety settings - prevents AI from making costs too extreme
cost-bounds:
  min-multiplier: 0.3 # Minimum cost multiplier (30% of base cost)
  max-multiplier: 3.0 # Maximum cost multiplier (300% of base cost)
```

### OpenAI Settings Explained

- **enabled**: Master switch for OpenAI integration. Set to `false` to use traditional cost calculation.
- **api-key**: Your OpenAI API key. Can also be set via `/chunklock apikey <key>` command.
- **model**: OpenAI model to use. `gpt-4o-mini` is recommended for cost-effectiveness.
- **cache-duration-minutes**: How long to cache AI responses. Reduces API calls and costs.
- **fallback-on-error**: If `true`, plugin falls back to traditional calculation if OpenAI fails.
- **cost-bounds**: Safety limits to prevent AI from setting extreme costs.

**Note**: OpenAI integration is completely optional. The plugin works perfectly without it.

---

## biome-unlocks.yml

Defines biome-specific unlock requirements. Supports both vanilla items and custom items from Oraxen/MMOItems.

### Format Types

Chunklock supports two formats for biome requirements:

**Flat Format** (Legacy - Vanilla Items Only):
```yaml
PLAINS:
  WHEAT: 8
  HAY_BLOCK: 2

FOREST:
  OAK_LOG: 16
  APPLE: 4
```

**Structured Format** (New - Vanilla + Custom Items):
```yaml
PLAINS:
  vanilla:
    WHEAT: 8
    HAY_BLOCK: 2
  custom:
    - plugin: oraxen
      item: mythic_sword
      amount: 1
    - plugin: mmoitems
      type: MATERIAL
      item: diamond_ingot
      amount: 3

FOREST:
  vanilla:
    OAK_LOG: 16
    APPLE: 4
```

### Format Auto-Detection

The plugin automatically detects which format you're using:
- If a biome has `vanilla:` or `custom:` keys → Structured format
- If a biome has only material keys → Flat format

### Custom Items Format

**Oraxen Items**:
```yaml
custom:
  - plugin: oraxen
    item: mythic_sword  # Item ID from Oraxen
    amount: 1
```

**MMOItems**:
```yaml
custom:
  - plugin: mmoitems
    type: MATERIAL      # MATERIAL or CONSUMABLE
    item: diamond_ingot # Item ID from MMOItems database
    amount: 3
```

### Important Notes

- All items listed are **REQUIRED** (all-or-nothing system)
- Vanilla items use exact Minecraft material names (case-sensitive)
- Custom items require the plugin to be installed and loaded
- Mix vanilla and custom items freely in structured format
- The plugin automatically detects installed custom item plugins

---

## block-values.yml

Defines block values and biome weights for chunk difficulty scoring.

```yaml
# Thresholds for score difficulty mapping
thresholds:
  easy: 30    # Slightly higher threshold for easier starting chunks
  normal: 50
  hard: 80

# How valuable each biome is (higher = more difficulty)
biomes:
  PLAINS: 5
  FOREST: 8
  DESERT: 10
  JUNGLE: 20
  WINDSWEPT_HILLS: 12
  OCEAN: 15
  SWAMP: 11
  BADLANDS: 16
  SAVANNA: 9
  TAIGA: 10
  SNOWY_PLAINS: 14
  DEEP_OCEAN: 18

# How much each block contributes to chunk score
blocks:
  OAK_LOG: 6
  BIRCH_LOG: 6
  SPRUCE_LOG: 7
  ACACIA_LOG: 7
  DARK_OAK_LOG: 7
  JUNGLE_LOG: 8
  WHEAT: 4
  CARROTS: 4
  POTATOES: 4
  BEETROOTS: 3
  WATER: 2
  COAL_ORE: 5
  IRON_ORE: 10
  COPPER_ORE: 7
  GOLD_ORE: 15
  REDSTONE_ORE: 12
  LAPIS_ORE: 12
  DIAMOND_ORE: 25
  EMERALD_ORE: 30
  STONE: 1
  DIRT: 1
  GRASS_BLOCK: 1
```

### How It Works

Chunk difficulty is calculated by:
1. Scanning blocks in the chunk
2. Adding up block values
3. Adding biome weight
4. Comparing total score to thresholds:
   - Score ≤ 30 → Easy
   - Score ≤ 50 → Normal
   - Score ≤ 80 → Hard
   - Score > 80 → Impossible

---

## team-settings.yml

Configures the team system behavior and limitations.

```yaml
# General Settings
max-team-size: 6 # Maximum members per team
max-teams-per-server: 100 # Server-wide team limit
allow-solo-teams: true # Allow single-player teams
join-request-ttl-hours: 72 # Auto-expire join requests after X hours

# Cost Scaling
team-cost-multiplier: 0.15 # Additional cost per extra team member (15%)
base-team-cost: 1.0 # Base multiplier for teams (1.0 = no change)
max-cost-multiplier: 3.0 # Cap on total cost multiplier
contested-cost-multiplier: 3.0 # Cost multiplier when claiming enemy chunks
max-contested-claims-per-day: 5 # Limit on contested claims per team each day

# Team Features
enable-team-chat: true # Allow team chat functionality
enable-leaderboards: true # Show team leaderboards (future feature)
```

### Team Cost Calculation

Team costs scale based on team size:
- Base cost × (1.0 + (team-size - 1) × team-cost-multiplier)
- Example: 4-person team = base × (1.0 + 3 × 0.15) = base × 1.45

---

## borders.yml

Configures the glass border system that visually shows chunk boundaries.

```yaml
# Visual Settings
enabled: true # Enable/disable the glass border system
use-full-height: true # Use full world height (bedrock to max height)
border-height: 3 # How many blocks high (only used if use-full-height is false)
min-y-offset: -2 # Blocks below base Y level (only used if use-full-height is false)
max-y-offset: 4 # Blocks above base Y level (only used if use-full-height is false)

# Performance Settings
scan-range: 8 # How many chunks to scan around player for borders
update-delay: 20 # Ticks to wait before updating borders (1 second = 20 ticks)
update-cooldown: 2000 # Milliseconds between border updates per player

# Behavior Settings
show-for-bypass-players: false # Whether bypass players should see borders
auto-update-on-movement: true # Update borders when players change chunks
restore-original-blocks: true # Restore original blocks when removing borders
debug-logging: false # Enable debug logging for border system

# Block Settings
border-material: LIGHT_GRAY_STAINED_GLASS # Material to use for borders
```

### Border Behavior

- Borders automatically appear around locked chunks
- Borders are removed when chunks are unlocked
- Original blocks are restored when borders are removed
- Borders update automatically as players move

---

## worlds.yml

Configures world settings for Chunklock.

```yaml
# Single World System - Use this instead of per-player worlds
world:
  name: "chunklock_world" # Name of the dedicated ChunkLock world
  diameter: 30000 # World diameter in blocks (set by setup command)

claims:
  min-distance-between-claims: 2 # Minimum chunks between player starting areas
```

### World Setup

After configuring `worlds.yml`, you must initialize the world:

```
/chunklock setup <worldname> <diameter>
```

Example:
```
/chunklock setup chunklock_world 30000
```

This command:
- Configures the world for Chunklock use
- Sets world boundaries
- Enables chunk locking in that world

---

## holograms.yml

Configures hologram display settings.

```yaml
enabled: true
provider: "FancyHolograms" # Force FancyHolograms only
update-interval: 20 # ticks (1 second)
view-distance: 64 # blocks (increased for better visibility)
debug-logging: false # Enable debug logging for troubleshooting

# Positioning Configuration
positioning:
  wall-offset: 0.5 # Distance outside chunk boundary (blocks)
  center-offset: 8.0 # Position along wall (8.0 = center of 16-block chunk edge)
  ground-clearance: 3.0 # Height above ground (blocks)
  min-height: 64 # Minimum Y level for holograms

# Performance Settings
performance:
  scan-range: 3 # Range to scan for chunks (3x3 around player)
  max-holograms-per-player: 16 # Maximum holograms per player to prevent lag
  cleanup-interval: 100 # ticks between hologram cleanup cycles
  debounce-delay-ticks: 3 # Delay between rapid hologram updates to prevent spam
  max-active-per-player: 100 # Maximum active holograms per player for distance culling
  max-view-distance: 128.0 # Maximum view distance for holograms in blocks
  culling-sweep-period: 60 # Ticks between distance culling sweeps (3 seconds)

# Display Settings
display:
  wall-facing: true # Make holograms face toward the center of the chunk
  fixed-billboard: true # Use FIXED billboard mode (no player following)
  show-distance: true # Show distance info in debug logs
```

### Hologram Providers

- **FancyHolograms** (recommended): Enhanced hologram displays with better performance
- **Internal**: Built-in hologram system (fallback if FancyHolograms not installed)

---

## debug.yml

Controls debug logging and troubleshooting options.

```yaml
# Master switch for all debug logging (set to true for detailed startup logs)
enabled: false

# Individual debug switches (only active if enabled: true above)
borders: false # Debug logging for border system
unlock-gui: false # Debug logging for unlock GUI interactions
chunk-finding: false # Debug logging for chunk allocation and finding
performance: false # Debug logging for performance metrics
```

### Debug Options

- **enabled**: Master switch. Set to `true` to enable all debug logging.
- **borders**: Log border system operations (useful for troubleshooting border issues)
- **unlock-gui**: Log GUI interactions (useful for troubleshooting GUI problems)
- **chunk-finding**: Log chunk allocation and finding (useful for troubleshooting starting chunk issues)
- **performance**: Log performance metrics (useful for optimization)

**Note**: Debug logging can generate large log files. Only enable when troubleshooting.

---

## performance.yml

Performance tuning settings for server optimization.

```yaml
# Border update performance
border-update-delay: 2 # Ticks between border updates
max-border-updates-per-tick: 10 # Maximum border updates processed per tick
```

### Performance Tuning

- **border-update-delay**: Lower values = more responsive borders but higher server load
- **max-border-updates-per-tick**: Higher values = faster border updates but may cause lag spikes

**Recommended Values**:
- Small servers (1-20 players): `border-update-delay: 2`, `max-border-updates-per-tick: 5`
- Medium servers (20-100 players): `border-update-delay: 2`, `max-border-updates-per-tick: 10`
- Large servers (100+ players): `border-update-delay: 1`, `max-border-updates-per-tick: 15`

---

## lang/en.yml

Language file for customizing all plugin messages.

### Language File Structure

The language file contains all user-facing messages organized by category:

- **commands**: Command usage and help messages
- **unlock**: Chunk unlock messages
- **team**: Team system messages
- **economy**: Economy-related messages
- **errors**: Error messages
- **gui**: GUI text and labels
- **holograms**: Hologram display text

### Placeholders

Messages support placeholders for dynamic content:
- `%player%` - Player name
- `%chunk%` - Chunk coordinates
- `%cost%` - Unlock cost
- `%world%` - World name
- `%team%` - Team name
- And many more...

### Creating Custom Languages

1. Copy `lang/en.yml` to `lang/<language-code>.yml` (e.g., `lang/de.yml` for German)
2. Translate all messages
3. Set `language: "<language-code>"` in `config.yml`
4. Reload the plugin

---

## Configuration Examples

### Small Server Setup

**economy.yml**:
```yaml
type: "materials"
```

**worlds.yml**:
```yaml
world:
  name: "chunklock_world"
  diameter: 20000
claims:
  min-distance-between-claims: 3
```

**performance.yml**:
```yaml
border-update-delay: 2
max-border-updates-per-tick: 5
```

### Medium Server Setup

**economy.yml**:
```yaml
type: "vault"
vault:
  base-cost: 200.0
  cost-per-unlocked: 50.0
```

**worlds.yml**:
```yaml
world:
  name: "chunklock_world"
  diameter: 30000
claims:
  min-distance-between-claims: 2
```

**performance.yml**:
```yaml
border-update-delay: 2
max-border-updates-per-tick: 10
```

### Large Server Setup

**economy.yml**:
```yaml
type: "vault"
vault:
  base-cost: 500.0
  cost-per-unlocked: 100.0
```

**worlds.yml**:
```yaml
world:
  name: "chunklock_world"
  diameter: 50000
claims:
  min-distance-between-claims: 2
```

**performance.yml**:
```yaml
border-update-delay: 1
max-border-updates-per-tick: 15
```

---

## Configuration Validation

The plugin automatically validates configuration on startup. Common issues:

### YAML Syntax Errors

- **Indentation**: Use spaces, not tabs
- **Colons**: Space required after colons
- **Quotes**: Use quotes for strings with special characters

### Invalid Values

- **Numbers**: Must be positive for counts and sizes
- **Materials**: Must be valid Minecraft block/item names
- **Biomes**: Must be valid Minecraft biome names

### Missing Files

If a configuration file is missing:
1. Restart the server
2. The plugin will generate the file with default values
3. Edit the file as needed

---

## Reloading Configuration

After making configuration changes:

```
/chunklock reload
```

This reloads all configuration files without restarting the server.

**Note**: Some changes (like world setup) may require a full server restart.

---

_For troubleshooting configuration issues, see the [Troubleshooting](Troubleshooting) guide._
