# Installation Guide

This guide will walk you through installing and configuring Chunklock on your Minecraft server.

## System Requirements

### Server Requirements

- **Minecraft Version**: 1.21.10 or higher
- **Server Software**: Paper (recommended), Spigot, or Pufferfish
- **Java Version**: 17 or higher
- **RAM**: Minimum 2GB, recommended 4GB+ for large servers
- **Storage**: 100MB+ for plugin data

### Player Requirements

- **Client Version**: Compatible with your server version
- **Internet Connection**: Required for initial setup and updates

## Step-by-Step Installation

### 1. Download Chunklock

1. Download the latest Chunklock JAR file from the releases page
2. Verify the file integrity (SHA256 checksums provided)
3. Ensure you have the correct version for your Minecraft server

### 2. Install the Plugin

1. **Stop your server** if it's currently running
2. **Navigate** to your server's `plugins/` directory
3. **Copy** the `Chunklock.jar` file into the `plugins/` folder
4. **Start your server**

The plugin will automatically generate its configuration files on first startup.

### 3. Initial Configuration

After the first startup, the plugin automatically generates all configuration files. You'll find the following files in `plugins/Chunklock/`:

```
plugins/Chunklock/
├── config.yml          # Main configuration file (minimal - version and language)
├── economy.yml         # Economy and payment settings
├── openai.yml          # OpenAI/ChatGPT integration settings (optional)
├── biome-unlocks.yml  # Biome-specific unlock requirements
├── block-values.yml    # Block values and biome weights for difficulty scoring
├── team-settings.yml   # Team system configuration
├── borders.yml         # Glass border system settings
├── worlds.yml          # World configuration
├── holograms.yml       # Hologram display settings
├── debug.yml           # Debug and logging options
├── performance.yml     # Performance tuning settings
├── database.yml        # Storage backend settings (MapDB/MySQL)
├── lang/
│   └── en.yml          # English language file
├── chunks.db           # Core chunk data (MapDB backend)
└── players.db          # Core player data (MapDB backend)
```

**Note**: 
- All configuration files are **automatically generated** on first startup
- Chunklock uses a modular configuration system with separate files for each feature area
- If a config file is missing, restart the server to regenerate it
- Default storage backend is MapDB (`chunks.db` + `players.db`)
- To use MySQL instead, configure `database.yml` (`database.type: "mysql"`)
- On first MySQL startup, existing MapDB data is auto-imported and `.mysql_migration_completed` is created

### 4. Basic Configuration

Chunklock uses a modular configuration system. Edit the relevant files:

**Main Config** (`config.yml`):
```yaml
config-version: 2
language: "en"
```

**Economy** (`economy.yml`):
```yaml
type: "vault" # or "materials"
vault:
  base-cost: 100.0
  cost-per-unlocked: 25.0
```

**World Setup** (`worlds.yml`):
```yaml
world:
  name: "chunklock_world"
  diameter: 30000
claims:
  min-distance-between-claims: 2
```

**Visual Effects** (`holograms.yml`):
```yaml
enabled: true
provider: "FancyHolograms"
```

**Borders** (`borders.yml`):
```yaml
enabled: true
border-material: "LIGHT_GRAY_STAINED_GLASS"
```

**Storage Backend** (`database.yml`):
```yaml
database:
  type: "mapdb" # or "mysql"
  fail-fast: true
  mysql:
    host: "localhost"
    port: 3306
    database: "chunklock"
    username: "chunklock_user"
    password: "change_me"
```

### 5. World Setup

After configuring, you need to set up your Chunklock world. The setup command will **automatically create** the world if it doesn't exist:

```
/chunklock setup <diameter>
```

Example:
```
/chunklock setup 30000
```

**What the setup command does**:
- Creates a new world named `chunklock_world` (if it doesn't exist)
- Sets the world border to the specified diameter
- Configures world settings for Chunklock use
- Pre-generates chunks for better performance
- Updates `worlds.yml` configuration automatically

**Important Notes**:
- You **do not** need to create the world manually - the setup command creates it
- The world name is automatically set to `chunklock_world` (configurable in `worlds.yml`)
- Setup may take several minutes depending on the diameter size
- Players can use `/chunklock start` after setup is complete

**Minimum Requirements**:
- Diameter must be at least 1,000 blocks
- Maximum diameter is 100,000 blocks (for performance reasons)
- Recommended diameter: 20,000-30,000 blocks for most servers

### 6. Optional Dependencies

#### Vault Economy Integration

If you want to use money instead of materials for chunk unlocking:

1. **Install Vault**: Download from [SpigotMC](https://www.spigotmc.org/resources/vault.34315/)
2. **Install Economy Plugin**: Such as EssentialsX or CMI
3. **Configure Chunklock** (`economy.yml`):
   ```yaml
   type: "vault"
   vault:
     base-cost: 100.0
     cost-per-unlocked: 25.0
   ```

#### FancyHolograms Integration

For enhanced hologram displays:

1. **Install FancyHolograms**: Download from [SpigotMC](https://www.spigotmc.org/resources/fancyholograms.96592/)
2. **Configure Chunklock** (`holograms.yml`):
   ```yaml
   enabled: true
   provider: "FancyHolograms"
   ```

#### OpenAI/ChatGPT Integration (Optional)

For AI-powered dynamic cost calculation:

1. **Get OpenAI API Key**: Sign up at [OpenAI](https://platform.openai.com/)
2. **Configure Chunklock** (`openai.yml`):
   ```yaml
   enabled: true
   api-key: "your-api-key-here"
   model: "gpt-4o-mini"
   cache-duration-minutes: 5
   ```
3. **Or use command**: `/chunklock apikey <your-api-key>`

**Note**: OpenAI integration is optional. The plugin works perfectly without it using traditional cost calculation.

#### Custom Items Integration (Oraxen/MMOItems)

To use custom items in biome unlock requirements:

1. **Install Oraxen**: Download from [SpigotMC](https://www.spigotmc.org/resources/oraxen.72448/)
   - Or install **MMOItems**: Download from [SpigotMC](https://www.spigotmc.org/resources/mmoitems.39267/)
2. **Configure biome requirements** (`biome-unlocks.yml`):
   ```yaml
   PLAINS:
     vanilla:
       WHEAT: 8
     custom:
       - plugin: oraxen
         item: mythic_sword
         amount: 1
   ```
3. The plugin automatically detects installed custom item plugins at runtime.

### 7. Verify Installation

1. **Restart your server** after configuration changes
2. **Check the console** for any error messages
3. **Test basic functionality**:
   ```
   /chunklock status
   /chunklock help
   ```

## Configuration Examples

### Small Server Setup (1-20 players)

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

### Medium Server Setup (20-100 players)

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

### Large Server Setup (100+ players)

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

## Post-Installation Setup

### 1. Configure Permissions

Add appropriate permissions to your permission plugin:

```yaml
groups:
  default:
    permissions:
      - chunklock.use
      - chunklock.status
      - chunklock.start
      - chunklock.unlock
      - chunklock.spawn
      - chunklock.team

  admin:
    permissions:
      - chunklock.admin
      - chunklock.reload
      - chunklock.reset
      - chunklock.bypass
      - chunklock.admin.apikey
```

### 2. Set Up World

1. **Run the setup command** (creates the world automatically):

   ```
   /chunklock setup 30000
   ```

   This command:
   - Creates a new world named `chunklock_world` automatically
   - Sets world border and configuration
   - Pre-generates chunks for better performance
   - Takes several minutes depending on diameter size

2. **Wait for setup to complete** - you'll see a success message when done

3. **Test player progression** with a test account using `/chunklock start`

**Note**: You do **not** need to manually create the world using Multiverse or other world management plugins. The `/chunklock setup` command handles world creation automatically.

### 3. Performance Optimization

For optimal performance on larger servers:

1. **Adjust view distance**: Keep reasonable view distances (8-12 chunks)
2. **Configure garbage collection**: Use appropriate JVM flags
3. **Monitor resource usage**: Use `/chunklock status` for metrics

## Troubleshooting Installation

### Common Issues

#### Plugin doesn't load

- **Check Java version**: Ensure you're running Java 17+
- **Verify server software**: Use Paper 1.21.10+, Spigot, or Pufferfish
- **Check Minecraft version**: Plugin requires Minecraft 1.21.10+
- **Check console logs**: Look for specific error messages

#### Configuration errors

- **YAML syntax**: Ensure proper indentation and syntax in all config files
- **Invalid values**: Check that all configuration values are valid
- **File permissions**: Ensure the server can read/write to plugin directories
- **Modular config**: All config files are generated automatically on first run - don't delete them
- **Missing files**: If a config file is missing, restart the server to regenerate it

#### Economy integration issues

- **Vault not found**: Install Vault plugin for economy features
- **Economy plugin missing**: Install a compatible economy plugin (EssentialsX, CMI, etc.)
- **Insufficient funds**: Ensure players have enough money/materials
- **Config location**: Economy settings are in `economy.yml`, not `config.yml`

#### OpenAI integration issues

- **API key not set**: Use `/chunklock apikey <key>` or edit `openai.yml`
- **API errors**: Check your OpenAI API key is valid and has credits
- **Fallback enabled**: Plugin falls back to traditional calculation if OpenAI fails
- **Caching**: Responses are cached for 5 minutes by default (configurable)

#### Custom items issues

- **Plugin not detected**: Ensure Oraxen or MMOItems is installed and loaded
- **Item not found**: Verify item names match exactly (case-sensitive)
- **Format errors**: Check `biome-unlocks.yml` uses correct structured format

### Getting Help

If you encounter issues:

1. **Check the logs**: Look in `plugins/Chunklock/logs/`
2. **Verify configuration**: Use a YAML validator
3. **Test with minimal setup**: Disable other plugins temporarily
4. **Report bugs**: Create an issue on GitHub with full error logs

## Next Steps

After successful installation:

1. **Read the [Quick Start Tutorial](Quick-Start-Tutorial)** for basic usage
2. **Configure [Permissions](Permissions-Guide)** for your server
3. **Review [Configuration Reference](Configuration-Reference)** for advanced settings
4. **Share with players**: Provide them with the [Player Commands](Player-Commands) guide

---

_Need help? Check our [Troubleshooting](Troubleshooting) guide or create an issue on GitHub._
