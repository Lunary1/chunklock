# Installation Guide

This guide will walk you through installing and configuring Chunklock on your Minecraft server.

## System Requirements

### Server Requirements

- **Minecraft Version**: 1.20.4 or higher
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

After the first startup, you'll find the following files in `plugins/Chunklock/`:

```
plugins/Chunklock/
├── config.yml          # Main configuration file
├── data.yml            # Player and chunk data storage
└── logs/               # Plugin logs directory
```

### 4. Basic Configuration

Edit `plugins/Chunklock/config.yml` to configure basic settings:

```yaml
# Economy type: "materials" (default) or "vault"
economy:
  type: "materials" # Change to "vault" for money-based economy

# World settings
worlds:
  world:
    name: "chunklock_world" # Dedicated Chunklock world name
    diameter: 30000 # World diameter in blocks
  claims:
    min-distance-between-claims: 2 # Distance between starting areas

# Visual effects
holograms:
  enabled: true
  provider: "FancyHolograms" # Requires FancyHolograms plugin

# Borders
borders:
  enabled: true
  material: "GLASS"
  show-to-all: false
```

### 5. Optional Dependencies

#### Vault Economy Integration

If you want to use money instead of materials for chunk unlocking:

1. **Install Vault**: Download from [SpigotMC](https://www.spigotmc.org/resources/vault.34315/)
2. **Install Economy Plugin**: Such as EssentialsX or CMI
3. **Configure Chunklock**:
   ```yaml
   economy:
     type: "vault"
     vault:
       base-cost: 100.0
       cost-per-unlocked: 25.0
   ```

#### FancyHolograms Integration

For enhanced hologram displays:

1. **Install FancyHolograms**: Download from [SpigotMC](https://www.spigotmc.org/resources/fancyholograms.96592/)
2. **Configure Chunklock**:
   ```yaml
   holograms:
     enabled: true
     provider: "FancyHolograms"
   ```

### 6. Verify Installation

1. **Restart your server** after configuration changes
2. **Check the console** for any error messages
3. **Test basic functionality**:
   ```
   /chunklock status
   /chunklock help
   ```

## Configuration Examples

### Small Server Setup (1-20 players)

```yaml
economy:
  type: "materials"
worlds:
  world:
    name: "chunklock_world"
    diameter: 20000
  claims:
    min-distance-between-claims: 3
performance:
  chunk-loading-threads: 2
  border-update-interval: 60
```

### Medium Server Setup (20-100 players)

```yaml
economy:
  type: "vault"
  vault:
    base-cost: 200.0
    cost-per-unlocked: 50.0
world:
  spawn-chunk-size: 5x5
  max-world-size: 75
performance:
  chunk-loading-threads: 4
  border-update-interval: 30
```

### Large Server Setup (100+ players)

```yaml
economy:
  type: "vault"
  vault:
    base-cost: 500.0
    cost-per-unlocked: 100.0
world:
  spawn-chunk-size: 7x7
  max-world-size: 100
performance:
  chunk-loading-threads: 8
  border-update-interval: 15
  enable-metrics: true
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
```

### 2. Set Up World

1. **Create a dedicated world** for Chunklock (recommended):

   ```
   /mv create chunklock_world normal
   ```

2. **Configure world spawn** to ensure proper starting locations

3. **Test player progression** with a test account

### 3. Performance Optimization

For optimal performance on larger servers:

1. **Adjust view distance**: Keep reasonable view distances (8-12 chunks)
2. **Configure garbage collection**: Use appropriate JVM flags
3. **Monitor resource usage**: Use `/chunklock status` for metrics

## Troubleshooting Installation

### Common Issues

#### Plugin doesn't load

- **Check Java version**: Ensure you're running Java 17+
- **Verify server software**: Use Paper, Spigot, or Pufferfish
- **Check console logs**: Look for specific error messages

#### Configuration errors

- **YAML syntax**: Ensure proper indentation and syntax
- **Invalid values**: Check that all configuration values are valid
- **File permissions**: Ensure the server can read/write to plugin directories

#### Economy integration issues

- **Vault not found**: Install Vault plugin for economy features
- **Economy plugin missing**: Install a compatible economy plugin
- **Insufficient funds**: Ensure players have enough money/materials

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
