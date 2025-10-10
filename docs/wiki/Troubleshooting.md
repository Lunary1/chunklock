# Troubleshooting

This guide helps you diagnose and resolve common issues with the Chunklock plugin.

## Quick Diagnostics

### Check Plugin Status

Run these commands to quickly assess the plugin state:

```
/chunklock debug          # Shows system health and debug info
/chunklock status         # Shows your personal status
/plugins                  # Verify Chunklock is loaded and enabled
```

### Common Quick Fixes

1. **Restart the server** - Resolves many temporary issues
2. **Check permissions** - Ensure players have required permissions
3. **Verify dependencies** - Install Vault for economy features
4. **Check configuration** - Validate YAML syntax in config.yml

## Installation Issues

### Plugin Won't Load

**Symptoms**: Plugin doesn't appear in `/plugins` list, console shows errors

**Common Causes**:

- Incompatible server version
- Missing Java 17+
- Corrupted plugin file
- Insufficient permissions

**Solutions**:

1. **Check Server Compatibility**:

   ```
   # Check your server version
   /version

   # Chunklock requires:
   # - Minecraft 1.20.4+
   # - Paper/Spigot/Pufferfish
   # - Java 17+
   ```

2. **Verify Java Version**:

   ```bash
   java -version
   # Should show version 17 or higher
   ```

3. **Check Console Logs**:

   ```
   # Look for error messages in console when starting server
   # Common errors:
   # - "Unsupported API version"
   # - "ClassNotFoundException"
   # - "NoSuchMethodError"
   ```

4. **Re-download Plugin**:
   - Download fresh copy from official source
   - Verify file integrity with SHA256 checksum
   - Ensure file isn't corrupted

### Configuration Errors

**Symptoms**: Plugin loads but shows config errors, features don't work

**Common Issues**:

1. **YAML Syntax Errors**:

   ```yaml
   # WRONG - no space after colon
   economy:enabled: true

   # CORRECT - space after colon
   economy:
     enabled: true
   ```

2. **Invalid Values**:

   ```yaml
   # WRONG - negative values
   world:
     max-world-size: -10

   # CORRECT - positive values
   world:
     max-world-size: 50
   ```

3. **Missing Sections**:
   ```
   # If config.yml is missing sections, delete it
   # and restart server to regenerate default config
   ```

**Solutions**:

1. **Validate YAML Syntax**:

   - Use online YAML validator
   - Check indentation (use spaces, not tabs)
   - Verify quotation marks are balanced

2. **Reset Configuration**:

   ```bash
   # Stop server
   # Delete config.yml
   rm plugins/Chunklock/config.yml
   # Start server - new config will be generated
   ```

3. **Check Console for Specific Errors**:
   ```
   # Look for messages like:
   # "Configuration validation failed"
   # "Invalid value for economy.type"
   # "Missing required configuration section"
   ```

## Runtime Issues

### Commands Not Working

**Symptoms**: Commands return "Unknown command" or permission errors

**Diagnosis**:

```
# Test basic command
/chunklock help

# Check permissions
/lp user <username> permission check chunklock.use

# Verify plugin is enabled
/plugins
```

**Solutions**:

1. **Permission Issues**:

   ```yaml
   # Add to your permission plugin:
   groups:
     default:
       permissions:
         - chunklock.use
         - chunklock.status
         - chunklock.start
         - chunklock.unlock
         - chunklock.spawn
         - chunklock.team
   ```

2. **Command Conflicts**:

   ```yaml
   # If another plugin conflicts, use full command
   /chunklock:chunklock help

   # Or use alias
   /cl help
   ```

3. **Plugin Not Registered**:
   ```
   # Check if plugin registered commands properly
   # Restart server if commands missing
   # Check console for command registration errors
   ```

### Economy Not Working

**Symptoms**: Can't unlock chunks, cost shows as 0, payment errors

**Diagnosis**:

```
/chunklock debug
# Check economy section for:
# - Economy Type: vault/materials
# - Economy Plugin: EssentialsX/CMI/etc
# - Service Health: Healthy/Unhealthy
```

**Solutions**:

1. **Vault Economy Issues**:

   ```bash
   # Install required plugins:
   # 1. Vault plugin
   # 2. Economy plugin (EssentialsX, CMI, etc.)

   # Verify installation:
   /plugins
   # Should show Vault and your economy plugin

   # Test economy
   /balance
   # Should show player balance
   ```

2. **Material Economy Issues**:

   ```yaml
   # Check config.yml
   economy:
     type: "materials" # Must be exactly "materials"
     materials:
       base-requirements:
         STONE: 32 # Valid material names required
         COAL: 16
         IRON_INGOT: 8
   ```

3. **Cost Calculation Problems**:
   ```
   # If costs show as 0 or negative:
   # 1. Check difficulty multipliers in config
   # 2. Verify biome multipliers are positive
   # 3. Check for integer overflow in calculations
   ```

### Chunk Operations Failing

**Symptoms**: Can't unlock chunks, borders not showing, progress not saving

**Diagnosis**:

```
/chunklock status
# Check:
# - Chunks Unlocked count
# - Starting chunk location
# - Current location vs unlocked areas

/chunklock debug
# Check:
# - Chunk service health
# - Database connection
# - Active operations
```

**Solutions**:

1. **Adjacency Issues**:

   ```
   # Players can only unlock chunks adjacent to unlocked ones
   # Stand in an unlocked chunk next to a locked one
   # Use /chunklock gui to see unlockable chunks
   ```

2. **World Issues**:

   ```yaml
   # Check config.yml world settings
   world:
     max-world-size: 50 # Ensure player isn't at limit
     spawn-chunk-size: "3x3" # Valid size format
   ```

3. **Data Corruption**:

   ```bash
   # If player data is corrupted:
   /chunklock reset <player>

   # If all data is corrupted:
   # Stop server
   # Backup data.yml
   # Delete data.yml
   # Start server
   ```

### Team System Problems

**Symptoms**: Can't create teams, invites not working, team commands failing

**Diagnosis**:

```
/chunklock team info
# Should show team information or "not in team"

/chunklock debug
# Check team service health
```

**Solutions**:

1. **Team Creation Issues**:

   ```yaml
   # Check config.yml team settings
   teams:
     enabled: true # Must be true
     max-team-size: 8 # Reasonable limit
     creation-cost: 0 # Set to 0 if economy issues
   ```

2. **Team Permission Issues**:

   ```
   # Only team leaders/moderators can:
   # - Invite players
   # - Kick members
   # - Modify team settings

   # Check player's role:
   /chunklock team info
   ```

3. **Team Data Issues**:

   ```bash
   # If team data is corrupted:
   /chunklock team disband <teamname>  # Admin command

   # Or reset player's team status:
   /chunklock reset <player>
   ```

## Performance Issues

### Server Lag

**Symptoms**: TPS drops when players use Chunklock features

**Diagnosis**:

```
/chunklock debug
# Check performance metrics:
# - Chunk Loading Time
# - Border Update Time
# - Memory Usage
# - Active Operations

# Use server profiling tools:
/spark profiler start
# Use Chunklock features
/spark profiler stop
```

**Solutions**:

1. **Optimize Configuration**:

   ```yaml
   performance:
     # Reduce update frequencies
     border-update-interval: 60 # Increase from 30
     hologram-update-interval: 20 # Increase from 10

     # Limit concurrent operations
     max-operations-per-tick: 3 # Reduce from 5
     batch-process-size: 10 # Reduce from 20

     # Enable async processing
     async-chunk-loading: true
     chunk-loading-threads: 4 # Adjust based on CPU
   ```

2. **Reduce Visual Effects**:

   ```yaml
   borders:
     enabled: false # Disable if causing lag

   holograms:
     enabled: false # Disable if causing lag
     max-holograms-per-player: 5 # Reduce limit
   ```

3. **Database Optimization**:
   ```yaml
   performance:
     database:
       batch-statements: true
       prepared-statements: true
       max-connections: 5 # Reduce for small servers
   ```

### Memory Issues

**Symptoms**: OutOfMemoryError, high RAM usage, frequent garbage collection

**Diagnosis**:

```
/chunklock debug
# Check Memory Usage section

# Monitor with external tools:
# - JVM memory usage
# - Garbage collection frequency
# - Heap dump analysis
```

**Solutions**:

1. **Reduce Cache Sizes**:

   ```yaml
   performance:
     caching:
       player-data-cache: 200 # Reduce from 500
       chunk-data-cache: 1000 # Reduce from 2000
       team-data-cache: 50 # Reduce from 100
       cache-expiry: 900 # Reduce from 1800
   ```

2. **JVM Optimization**:

   ```bash
   # Add to server startup flags:
   -Xmx4G                           # Set appropriate heap size
   -XX:+UseG1GC                     # Use G1 garbage collector
   -XX:G1HeapRegionSize=32m         # Optimize for chunk operations
   -XX:+UnlockExperimentalVMOptions
   -XX:+UseJVMCICompiler            # If using GraalVM
   ```

3. **Data Cleanup**:

   ```bash
   # Clean up old data:
   /chunklock backup create "before-cleanup"

   # Remove inactive players (admin decision)
   # Use database tools to clean old records
   ```

## Integration Issues

### Vault Integration Problems

**Symptoms**: Economy features not working despite Vault being installed

**Diagnosis**:

```
/plugins
# Verify both Vault and economy plugin are loaded

/chunklock debug
# Check economy service status

# Test Vault directly:
/balance
/eco give <player> 100
```

**Solutions**:

1. **Missing Economy Plugin**:

   ```
   # Install compatible economy plugin:
   # - EssentialsX
   # - CMI
   # - TNE (The New Economy)
   # - CraftConomy
   ```

2. **Plugin Load Order**:

   ```yaml
   # In plugin.yml, ensure proper loading order:
   depend: [Vault]
   softdepend: [Essentials, CMI]
   ```

3. **Permission Issues**:
   ```yaml
   # Ensure Chunklock can access Vault:
   # Check for any permission restrictions
   # Verify Vault has proper economy plugin registered
   ```

### Hologram Plugin Integration

**Symptoms**: Holograms not appearing, console errors about hologram providers

**Diagnosis**:

```
/chunklock debug
# Check hologram service status

/plugins
# Verify hologram plugin is loaded (FancyHolograms, HolographicDisplays)
```

**Solutions**:

1. **Install Hologram Plugin**:

   ```
   # Supported plugins:
   # - FancyHolograms (recommended)
   # - HolographicDisplays
   # - DecentHolograms
   ```

2. **Configure Provider**:

   ```yaml
   holograms:
     enabled: true
     provider: "FancyHolograms" # Match your installed plugin
   ```

3. **Fallback to Internal**:
   ```yaml
   holograms:
     provider: "internal" # Use built-in hologram system
   ```

## Data Issues

### Data Corruption

**Symptoms**: Player progress reset, chunks randomly locked/unlocked, errors in console

**Prevention**:

```bash
# Regular backups
/chunklock backup create "daily-backup"

# Monitor data integrity
/chunklock debug
# Check for database errors
```

**Recovery**:

1. **Restore from Backup**:

   ```bash
   # Stop server
   /chunklock backup restore "backup-name"
   # Start server
   ```

2. **Reset Specific Player**:

   ```bash
   /chunklock reset <player>
   # Player will need to start over
   ```

3. **Full Reset** (Last Resort):

   ```bash
   # Create backup first!
   /chunklock backup create "before-full-reset"

   # Reset all data
   /chunklock resetall
   # Type: CONFIRM RESET ALL
   ```

### Missing Data

**Symptoms**: Player progress not loading, teams disappeared, chunks not saving

**Diagnosis**:

```
# Check file permissions
ls -la plugins/Chunklock/
# Ensure server can read/write data.yml

# Check disk space
df -h
# Ensure sufficient space for data files

# Check database connectivity
/chunklock debug
# Verify database status
```

**Solutions**:

1. **File Permission Issues**:

   ```bash
   # Fix file permissions
   chown -R minecraft:minecraft plugins/Chunklock/
   chmod -R 755 plugins/Chunklock/
   ```

2. **Disk Space Issues**:

   ```bash
   # Free up space
   # Move/delete old log files
   # Clean up old backups
   # Consider moving to larger disk
   ```

3. **Database Recovery**:
   ```bash
   # Stop server
   # Restore data.yml from backup
   # Or delete data.yml to start fresh
   # Start server
   ```

## Advanced Troubleshooting

### Enable Debug Logging

Add to your server's `server.properties` or logging config:

```properties
# Enable debug logging for Chunklock
logging.level.me.chunklock=DEBUG
```

Or use plugin debug mode:

```yaml
# In config.yml
debug:
  enabled: true
  log-level: "DEBUG"
  trace-operations: true
```

### Collect Diagnostic Information

For bug reports, collect this information:

```bash
# Server information
/version
/plugins

# Chunklock specific
/chunklock debug
/chunklock stats

# Check logs
tail -n 100 logs/latest.log | grep -i chunklock

# Configuration
cat plugins/Chunklock/config.yml
```

### Performance Profiling

Use these tools to identify performance issues:

1. **Spark Profiler**:

   ```
   /spark profiler start
   # Perform problematic operations
   /spark profiler stop
   /spark profiler open
   ```

2. **TimingsV2** (Paper):

   ```
   /timings on
   # Wait 5-10 minutes
   /timings paste
   ```

3. **Built-in Monitoring**:
   ```
   /chunklock monitor start
   # Monitor real-time performance
   /chunklock monitor stop
   ```

## Getting Additional Help

### Before Reporting Issues

1. **Check this troubleshooting guide** - Most issues have solutions here
2. **Search existing issues** - Someone may have had the same problem
3. **Test with minimal setup** - Disable other plugins temporarily
4. **Gather diagnostic information** - Use commands above

### Reporting Bugs

When creating a bug report, include:

1. **Server Information**:

   - Server software and version
   - Java version
   - Operating system

2. **Plugin Information**:

   - Chunklock version
   - Configuration file
   - List of other plugins

3. **Problem Description**:

   - Steps to reproduce
   - Expected behavior
   - Actual behavior
   - Error messages

4. **Logs**:
   - Relevant console output
   - Error stack traces
   - Debug output

### Community Support

- **GitHub Issues**: [Report bugs and feature requests](https://github.com/Lunary1/chunklock/issues)
- **GitHub Discussions**: [Community help and questions](https://github.com/Lunary1/chunklock/discussions)
- **Discord**: Join our community server for real-time help
- **Wiki**: This documentation for comprehensive guides

---

_If you've followed this guide and still need help, please create a detailed issue report on our GitHub repository._
