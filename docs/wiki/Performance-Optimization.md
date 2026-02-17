# Performance Optimization

This guide helps server administrators optimize Chunklock for the best performance across different server sizes and hardware configurations.

## Performance Overview

Chunklock is designed to be lightweight and efficient, but proper configuration is essential for optimal performance, especially on servers with many active players or limited resources.

### Performance Metrics

Monitor these key metrics to assess plugin performance:

```
/chunklock debug
# Check these sections:
# - Performance Metrics
# - Memory Usage
# - Operation Timings
# - Cache Hit Rates
# - Database Performance
```

### Target Performance Goals

**Small Servers (1-20 players)**:

- Chunk operations: <5ms average
- Memory usage: <100MB
- TPS impact: <0.1 TPS loss

**Medium Servers (20-100 players)**:

- Chunk operations: <10ms average
- Memory usage: <250MB
- TPS impact: <0.2 TPS loss

**Large Servers (100+ players)**:

- Chunk operations: <15ms average
- Memory usage: <500MB
- TPS impact: <0.3 TPS loss

## Configuration Optimization

### Core Performance Settings

**performance.yml**:
```yaml
# Border update performance
border-update-delay: 2 # Ticks between border updates
max-border-updates-per-tick: 10 # Maximum border updates processed per tick
```

**holograms.yml** (Performance section):
```yaml
performance:
  scan-range: 3 # Range to scan for chunks (3x3 around player)
  max-holograms-per-player: 16 # Maximum holograms per player to prevent lag
  cleanup-interval: 100 # Ticks between hologram cleanup cycles
  debounce-delay-ticks: 3 # Delay between rapid hologram updates to prevent spam
  max-active-per-player: 100 # Maximum active holograms per player for distance culling
  max-view-distance: 128.0 # Maximum view distance for holograms in blocks
  culling-sweep-period: 60 # Ticks between distance culling sweeps (3 seconds)
```

**openai.yml** (Performance settings):
```yaml
cache-duration-minutes: 5 # How long to cache AI responses to reduce API calls
request-timeout-seconds: 10 # Timeout for API requests
```

### Caching Systems

Chunklock uses multiple caching systems for optimal performance:

**OpenAI Response Caching** (`openai.yml`):
- AI responses are cached for 5 minutes by default
- Reduces API calls and costs
- Configurable via `cache-duration-minutes`

**Core Data Storage** (`database.yml`):
- Default backend is MapDB (`chunks.db`, `players.db`)
- Optional MySQL backend supports pooled connections for larger networks
- MySQL backend uses configurable in-memory cache (`database.mysql.cache.ttl-ms`)

Example:
```yaml
database:
  type: "mysql"
  fail-fast: true
  mysql:
    pool:
      max-size: 20
      min-idle: 2
      connection-timeout-ms: 30000
    cache:
      ttl-ms: 300000
```

**Hologram Caching** (`holograms.yml`):
- Hologram data is cached per player
- Distance culling reduces active holograms
- Automatic cleanup of unused holograms

### Server Size Configurations

#### Small Server (1-20 players)

**performance.yml**:
```yaml
border-update-delay: 2
max-border-updates-per-tick: 5
```

**holograms.yml**:
```yaml
update-interval: 20 # Less frequent updates
performance:
  max-holograms-per-player: 10
  scan-range: 2
```

**openai.yml** (if using OpenAI):
```yaml
cache-duration-minutes: 10 # Longer cache for small servers
```

#### Medium Server (20-100 players)

**performance.yml**:
```yaml
border-update-delay: 2
max-border-updates-per-tick: 10
```

**holograms.yml**:
```yaml
update-interval: 20 # Standard updates
performance:
  max-holograms-per-player: 16
  scan-range: 3
```

**openai.yml** (if using OpenAI):
```yaml
cache-duration-minutes: 5 # Standard cache duration
```

#### Large Server (100+ players)

**performance.yml**:
```yaml
border-update-delay: 1
max-border-updates-per-tick: 15
```

**holograms.yml**:
```yaml
update-interval: 20 # More frequent updates
performance:
  max-holograms-per-player: 20
  scan-range: 3
```

**openai.yml** (if using OpenAI):
```yaml
cache-duration-minutes: 3 # Shorter cache for more dynamic pricing
```

## Visual Effects Optimization

Visual effects can significantly impact performance. Optimize based on your server's needs:

### Border Optimization

```yaml
borders:
  enabled: true # Disable if causing lag

  # Particle settings
  particle-type: "REDSTONE" # Lightweight particle
  density: 5 # Reduce from default 10
  height: 3 # Reduce from default 5

  # Update frequency
  update-interval: 60 # Increase for better performance
  player-range: 32 # Reduce visible range

  # Performance limits
  max-particles-per-player: 100 # Limit particle count
  max-borders-per-player: 20 # Limit visible borders
```

### Hologram Optimization

```yaml
holograms:
  enabled: true # Disable if not needed
  provider: "internal" # Use internal for best performance

  # Display limits
  max-holograms-per-player: 10 # Limit concurrent holograms
  max-distance: 16 # Reduce visible distance

  # Update frequency
  update-interval: 20 # Less frequent updates

  # Content optimization
  show-unlock-cost: true # Most important info
  show-chunk-info: false # Disable if not needed
  show-team-info: false # Disable if not needed
```

### GUI Optimization

```yaml
ui:
  # Cache GUI data
  cache-gui-data: true
  gui-cache-expiry: 300 # 5-minute cache

  # Pagination
  items-per-page: 45 # Standard inventory size
  max-pages: 10 # Limit large GUIs

  # Update frequency
  auto-refresh: false # Disable auto-refresh
  refresh-interval: 30 # If auto-refresh enabled
```

## Database Optimization

Database performance is crucial for chunk operations:

### Database Configuration

```yaml
database:
  # Connection pooling
  max-connections: 10 # Adjust based on concurrent users
  min-connections: 2 # Maintain minimum connections
  connection-timeout: 30000 # 30 seconds

  # Statement optimization
  prepare-statements: true # Always enable
  batch-statements: true # Enable for bulk operations

  # Cache settings
  cache-prepared-statements: true # Cache prepared statements
  statement-cache-size: 100 # Statement cache size
```

### SQLite Optimization (Default)

```yaml
database:
  type: "sqlite"
  sqlite:
    # Performance settings
    journal-mode: "WAL" # Write-Ahead Logging
    synchronous: "NORMAL" # Good balance
    cache-size: 2000 # Memory cache pages

    # Optimization
    auto-vacuum: "INCREMENTAL" # Gradual space reclaim
    temp-store: "MEMORY" # Use memory for temp data
```

### MySQL Optimization (High Performance)

```yaml
database:
  type: "mysql"
  mysql:
    host: "localhost"
    port: 3306
    database: "chunklock"
    username: "chunklock_user"
    password: "secure_password"

    # Connection optimization
    max-connections: 20
    connection-timeout: 30000
    socket-timeout: 60000

    # Performance settings
    use-ssl: false # Disable if on same server
    use-server-prep-stmts: true # Enable prepared statements
    cache-prep-stmts: true # Cache prepared statements
    prep-stmt-cache-size: 250 # Statement cache size
    prep-stmt-cache-sql-limit: 2048 # Max SQL length to cache
```

## Memory Management

Effective memory management prevents OutOfMemoryErrors and garbage collection lag:

### JVM Optimization

Add these flags to your server startup script:

```bash
# Basic memory settings
-Xms2G -Xmx4G                       # Set heap size

# Garbage collection optimization
-XX:+UseG1GC                        # Use G1 collector
-XX:G1HeapRegionSize=32m            # Optimize for chunk operations
-XX:MaxGCPauseMillis=50             # Target GC pause time
-XX:G1NewSizePercent=20             # Young generation size
-XX:G1MaxNewSizePercent=40          # Max young generation
-XX:G1HeapWastePercent=5            # Heap waste threshold

# Memory allocation optimization
-XX:+UseStringDeduplication         # Reduce string memory usage
-XX:+OptimizeStringConcat           # Optimize string operations

# Monitoring (optional)
-XX:+UnlockExperimentalVMOptions
-XX:+UseJVMCICompiler               # If using GraalVM
```

### Plugin Memory Configuration

```yaml
performance:
  memory:
    # Cache size limits
    max-cache-memory: 256 # Max cache memory in MB
    cache-cleanup-interval: 600 # Cleanup interval in seconds

    # Object pooling
    enable-object-pooling: true # Reuse objects
    pool-size: 100 # Object pool size

    # Memory monitoring
    memory-warning-threshold: 80 # Warn at 80% usage
    memory-cleanup-threshold: 90 # Force cleanup at 90%
```

## Network Optimization

Reduce network overhead for better performance:

### Packet Optimization

```yaml
network:
  # Packet batching
  batch-packets: true # Batch multiple updates
  batch-size: 50 # Packets per batch
  batch-delay: 1 # Ticks to wait before sending

  # Update frequency
  position-update-interval: 5 # Player position checks
  chunk-update-interval: 10 # Chunk state updates

  # Range optimization
  update-range: 48 # 3 chunk radius
  max-updates-per-player: 20 # Limit concurrent updates
```

### Async Processing

```yaml
async:
  # Core async operations
  chunk-loading: true # Always enable
  border-updates: true # Enable for performance
  hologram-updates: true # Enable if using holograms
  database-operations: true # Enable for DB performance

  # Thread pool configuration
  core-threads: 4 # Match CPU cores
  max-threads: 8 # Maximum threads
  queue-size: 1000 # Task queue size
  thread-timeout: 60 # Thread idle timeout
```

## Monitoring and Diagnostics

### Built-in Monitoring

Enable monitoring to track performance:

```yaml
monitoring:
  enabled: true # Enable performance monitoring
  interval: 60 # Monitoring interval in seconds

  # Metrics to track
  track-operations: true # Track operation performance
  track-memory: true # Track memory usage
  track-database: true # Track database performance
  track-cache: true # Track cache performance

  # Logging
  log-performance: true # Log performance data
  log-warnings: true # Log performance warnings
  performance-log-interval: 300 # Log every 5 minutes
```

### Performance Commands

Use these commands to monitor performance:

```
/chunklock debug                    # General debug information
/chunklock monitor start            # Start real-time monitoring
/chunklock monitor stop             # Stop monitoring
/chunklock stats                    # Performance statistics
/chunklock cache stats              # Cache performance
/chunklock cache clear              # Clear caches
```

### External Monitoring Tools

#### Spark Profiler

```bash
# Install Spark plugin
# https://spark.lucko.me/

# Profile Chunklock performance
/spark profiler start
# Use Chunklock features for 5-10 minutes
/spark profiler stop
/spark profiler open
```

#### TimingsV2 (Paper)

```bash
# Enable timings
/timings on

# Wait 10-15 minutes during normal usage
/timings paste
# Share the URL for analysis
```

#### Custom Monitoring

Create custom monitoring scripts:

```bash
#!/bin/bash
# monitor-chunklock.sh

while true; do
    echo "$(date): Checking Chunklock performance..."

    # Check memory usage
    grep -i "chunklock" /proc/$(pgrep java)/status

    # Check TPS
    screen -S minecraft -X stuff "/tps\n"

    # Wait 5 minutes
    sleep 300
done
```

## Troubleshooting Performance Issues

### Common Performance Problems

#### High Memory Usage

**Symptoms**: OutOfMemoryError, frequent garbage collection

**Solutions**:

1. Reduce cache sizes
2. Increase JVM heap size
3. Enable memory cleanup
4. Check for memory leaks

```yaml
performance:
  memory:
    max-cache-memory: 128 # Reduce cache size
    cache-cleanup-interval: 300 # More frequent cleanup
```

#### TPS Drops

**Symptoms**: Server lag, slow chunk operations

**Solutions**:

1. Reduce operation limits
2. Increase async processing
3. Optimize visual effects
4. Check database performance

```yaml
performance:
  max-operations-per-tick: 3 # Reduce load per tick
  async-chunk-loading: true # Enable async processing
```

#### Database Lag

**Symptoms**: Slow chunk unlocks, data saving delays

**Solutions**:

1. Optimize database configuration
2. Enable batch processing
3. Consider MySQL for large servers
4. Add database indexes

```yaml
database:
  batch-statements: true # Enable batching
  batch-size: 100 # Larger batches
```

### Performance Tuning Workflow

1. **Baseline Measurement**:

   ```
   /chunklock monitor start
   # Use server normally for 30 minutes
   /chunklock monitor stop
   ```

2. **Identify Bottlenecks**:

   - Check operation timings
   - Monitor memory usage
   - Analyze TPS impact

3. **Apply Optimizations**:

   - Start with caching improvements
   - Optimize visual effects
   - Tune async processing

4. **Measure Improvements**:

   - Compare before/after metrics
   - Test during peak usage
   - Monitor for stability

5. **Iterate**:
   - Continue optimizing bottlenecks
   - Monitor long-term stability
   - Adjust based on server growth

## Hardware Recommendations

### Minimum Requirements

**Small Server (1-20 players)**:

- CPU: 2 cores, 2.5GHz+
- RAM: 4GB (2GB for Minecraft + 2GB system)
- Storage: SSD recommended
- Network: Stable internet connection

### Recommended Specifications

**Medium Server (20-100 players)**:

- CPU: 4 cores, 3.0GHz+
- RAM: 8GB (6GB for Minecraft + 2GB system)
- Storage: NVMe SSD
- Network: High-speed dedicated connection

**Large Server (100+ players)**:

- CPU: 8+ cores, 3.5GHz+
- RAM: 16GB+ (12GB+ for Minecraft)
- Storage: NVMe SSD with high IOPS
- Network: Enterprise-grade connection

### Storage Optimization

**SSD Configuration**:

- Use SSD for world files and database
- Consider NVMe for highest performance
- Separate OS and Minecraft data if possible

**Database Storage**:

- SQLite: Fast SSD sufficient
- MySQL: Consider dedicated database server
- Backups: Use separate storage device

## Load Testing

### Testing Scenarios

**Concurrent Chunk Unlocks**:

```bash
# Test multiple players unlocking chunks simultaneously
# Use multiple test accounts
# Monitor performance during peak operations
```

**Visual Effect Load**:

```bash
# Enable all visual effects
# Have players gather in same area
# Monitor particle and hologram performance
```

**Database Stress Test**:

```bash
# Rapid chunk operations
# Frequent save operations
# Monitor database response times
```

### Performance Benchmarks

Establish performance benchmarks for your server:

```
Target Metrics:
- Chunk unlock time: <10ms average
- Border update time: <5ms average
- Database operation: <20ms average
- Memory usage: <300MB steady state
- Cache hit rate: >85%
```

---

_Regular performance monitoring and optimization ensures Chunklock runs smoothly even as your server grows._
