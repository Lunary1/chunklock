# Dependency Testing Guide

This guide explains how to test and verify that the Chunklock plugin's dependencies (Vault and FancyHolograms) are working correctly.

## Overview

Chunklock now includes comprehensive dependency checking during startup and provides debug commands to test integrations.

## Automatic Dependency Checking

When the plugin starts up, it automatically checks all dependencies and logs their status:

- **✅ Available**: Plugin found, enabled, and fully functional
- **❌ Not Found**: Plugin not installed
- **⚠️ Disabled**: Plugin installed but not enabled
- **🔴 Incompatible**: Plugin installed but wrong version/API
- **⚠️ Misconfigured**: Plugin available but missing configuration

## Dependencies Checked

### FancyHolograms (Optional)

- **Purpose**: Hologram display functionality
- **What's checked**: Plugin presence, enabled state, API compatibility
- **If missing**: Hologram features will be disabled
- **API classes tested**:
  - `de.oliver.fancyholograms.api.FancyHologramsPlugin`
  - `de.oliver.fancyholograms.api.data.TextHologramData`
  - `de.oliver.fancyholograms.api.hologram.Hologram`
  - `de.oliver.fancyholograms.api.HologramManager`

### Vault (Optional)

- **Purpose**: Economy integration for money-based chunk unlocking
- **What's checked**: Plugin presence, enabled state, economy service availability
- **If missing**: Economy features will use materials only
- **Additional checks**: Looks for an economy plugin (like EssentialsX) that provides the Economy service

## Debug Commands

### Check All Dependencies

```
/chunklock debug deps
```

or

```
/chunklock debug dependencies
```

This command re-runs the dependency check and displays the results in both chat and console.

### Test Vault Integration Specifically

```
/chunklock debug vault
```

This command:

1. Tests if Vault is properly integrated
2. Checks if an economy service is available
3. Displays your current balance (if Vault is working)
4. Logs detailed results to console

## Example Console Output

### Successful Dependency Check

```
[INFO] === Dependency Check ===
[INFO] ✅ FancyHolograms [OPTIONAL] v2.0.7 - API accessible - hologram features enabled
[INFO] ✅ Vault [OPTIONAL] v1.7.3 - Economy integration available with EssentialsX Economy (Economy Provider: EssentialsX Economy)
[INFO] === Dependency Check Complete ===
```

### Missing Dependencies

```
[INFO] === Dependency Check ===
[WARN] ❌ FancyHolograms [OPTIONAL] - Plugin not installed - hologram features will be disabled
[WARN] ❌ Vault [OPTIONAL] - Plugin not installed - economy features will use materials only
[INFO] === Dependency Check Complete ===
```

### Vault Without Economy Plugin

```
[INFO] === Dependency Check ===
[WARN] ⚠️ Vault [OPTIONAL] v1.7.3 - No economy plugin found - install an economy plugin (like EssentialsX) for money features
[INFO] === Dependency Check Complete ===
```

## How to Test Your Setup

1. **Start your server** - Check the console for automatic dependency checking during startup
2. **Run the debug commands** - Use `/chunklock debug deps` and `/chunklock debug vault` in-game
3. **Check functionality**:
   - For FancyHolograms: Look for chunk unlock holograms when approaching locked chunks
   - For Vault: Try configuring economy mode in config.yml and test chunk unlocking with money

## Troubleshooting

### FancyHolograms Issues

- **Not Found**: Download and install FancyHolograms plugin
- **Incompatible**: Update to a compatible version of FancyHolograms
- **Disabled**: Enable the FancyHolograms plugin

### Vault Issues

- **Not Found**: Download and install Vault plugin
- **No Economy Plugin**: Install an economy plugin like EssentialsX
- **Misconfigured**: Check that your economy plugin is properly set up

### Testing Economy Integration

1. Install Vault and an economy plugin (e.g., EssentialsX)
2. Set up player balances using your economy plugin
3. Configure Chunklock to use vault economy in config.yml:
   ```yaml
   economy:
     type: vault
   ```
4. Use `/chunklock debug vault` to verify integration
5. Test chunk unlocking with money instead of materials

## Configuration

To enable Vault economy mode, edit your `config.yml`:

```yaml
economy:
  type: vault # Change from "materials" to "vault"
  vault:
    base-cost: 100.0
    cost-per-unlocked: 25.0
    difficulty-multipliers:
      EASY: 0.5
      MEDIUM: 1.0
      HARD: 2.0
    biome-multipliers:
      DESERT: 1.5
      OCEAN: 2.0
      # Add more biome multipliers as needed
```

This will switch the plugin from using materials (like diamonds) to using money for chunk unlocking.
