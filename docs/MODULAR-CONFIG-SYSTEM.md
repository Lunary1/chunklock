# Modular Configuration System

## Overview

The Chunklock plugin has been refactored to use a modular configuration system, replacing the previous monolithic `config.yml` file with multiple focused configuration files. This improves maintainability, clarity, and scalability.

## Configuration Files

The plugin now uses the following configuration files:

### Core Configuration

- **`config.yml`** - Core plugin settings (version info, first-run flag)

### Modular Configuration Files

- **`economy.yml`** - Economy and payment system settings
- **`openai.yml`** - OpenAI integration settings
- **`block-values.yml`** - Block values and biome weights for chunk scoring
- **`biome-unlocks.yml`** - Biome-specific unlock requirements
- **`team-settings.yml`** - Team system configuration
- **`borders.yml`** - Glass border system settings
- **`worlds.yml`** - World configuration
- **`holograms.yml`** - Hologram display settings
- **`debug.yml`** - Debug and logging options
- **`performance.yml`** - Performance tuning settings

## Migration

### Automatic Migration

When the plugin detects an old configuration format (version < 2), it automatically:

1. Backs up the old `config.yml` to `config.yml.backup`
2. Migrates all sections to the appropriate modular config files
3. Updates `config.yml` to the new minimal format
4. Preserves all existing values

### Manual Migration

If you prefer to migrate manually:

1. Copy the relevant sections from your old `config.yml` to the new modular files
2. Update `config.yml` to set `config-version: 2`
3. Restart the plugin

## Configuration Access

### For Developers

All configuration is accessed through the `ConfigManager`:

```java
ConfigManager configManager = ChunklockPlugin.getInstance().getConfigManager();

// Access modular configs
EconomyConfig economyConfig = configManager.getModularEconomyConfig();
OpenAIConfig openAIConfig = configManager.getOpenAIConfig();
BlockValuesConfig blockValuesConfig = configManager.getBlockValuesConfig();
// ... etc
```

### Configuration Classes

Each modular config file has a corresponding Java class in `me.chunklock.config.modular`:

- `EconomyConfig`
- `OpenAIConfig`
- `BlockValuesConfig`
- `BiomeUnlocksConfig`
- `TeamSettingsConfig`
- `BordersConfig`
- `WorldsConfig`
- `HologramsConfig`
- `DebugConfig`
- `PerformanceConfig`

## Benefits

1. **Better Organization** - Each file has a single, clear responsibility
2. **Easier Maintenance** - Changes to one system don't affect others
3. **Improved Scalability** - New features can add their own config files
4. **Better Validation** - Each config can be validated independently
5. **Clearer Documentation** - Each file can have focused comments

## Backward Compatibility

The plugin maintains backward compatibility:

- Old config files are automatically migrated
- Legacy config access methods still work (with deprecation warnings)
- All existing functionality is preserved

## File Locations

All configuration files are located in the plugin's data folder:

```
plugins/Chunklock/
├── config.yml
├── economy.yml
├── openai.yml
├── block-values.yml
├── biome-unlocks.yml
├── team-settings.yml
├── borders.yml
├── worlds.yml
├── holograms.yml
├── debug.yml
└── performance.yml
```

## Reloading Configuration

To reload configuration:

1. Use `/chunklock reload` command
2. Or restart the server

All modular configs are reloaded automatically when the command is executed.

## Troubleshooting

### Missing Config Files

If a modular config file is missing, the plugin will:

1. Create it with default values from resources
2. Log a warning message
3. Continue with default settings

### Migration Issues

If migration fails:

1. Check the console for error messages
2. Verify your old `config.yml` is valid YAML
3. Check file permissions in the plugin data folder
4. Restore from `config.yml.backup` if needed

## Future Enhancements

The modular system makes it easy to add:

- Per-world configuration overrides
- Configuration profiles
- Runtime configuration validation
- Configuration versioning per module
