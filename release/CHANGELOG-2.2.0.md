# Chunklock 2.2.0

**Stability and economy release.** This release completes the resource-scan economy work and fixes several progression-blocking bugs.

**[Fixed] Resource-scan cost calculation**

- Unlock costs no longer repeatedly demand the same material (especially acacia and other wood) as you progress (#76)
- Progression scaling reworked so required amounts vary naturally instead of flattening around a fixed value (#76)
- Pricing can no longer be gamed by artificially depleting a material in your claimed chunks; availability now applies a bounded influence rather than a hard cap (#77)
- Material selection is now deterministic, so identical situations produce identical costs (#77)
- The unlock GUI now explains that costs are based on the resources in your claimed chunks (#77)

**[Fixed] Async cost calculation crash**

- Fixed an error during background unlock cost calculation that could cause the unlock menu to fail to open or display incorrect requirements (#78)
- Per-player calculation state is now released when a player disconnects, preventing unnecessary memory growth on long-running servers

**[Fixed] Starting chunk and materials**

- `/chunklock start` no longer assigns a new starting chunk after a server restart on MySQL; the player's persisted chunk is restored and reused (#65)
- Players spawning in chunks without the biome's required materials (such as a Plains chunk with no trees) can now progress (#69)
- Fixed unlock failing despite having enough materials in inventory, and corrected the "missing" amounts shown in unlock messages (#71)
- Materials-mode unlocks require a single resource again rather than several at once (#72)
- Fixed items not being consumed from inventory when unlocking with materials
- Fixed the unlock GUI showing a different material than the one actually required

**[Added] Database visibility**

- New `/chunklock database` command showing the active storage backend, connection pool metrics, performance stats, and migration status
- New `/chunklock debug database` subcommand with connection status, query performance, and pool statistics
- MySQL Setup Guide covering installation, configuration, migration, performance tuning, and troubleshooting

**[Known Issues]**

- Some servers report high memory usage and degraded performance at higher player counts, particularly with heavy exploration (#74). This is under active investigation and is not fixed in this release.

---

**Minecraft**: Paper 1.20.4+ (tested up to 1.21.10)
**Java**: 17+