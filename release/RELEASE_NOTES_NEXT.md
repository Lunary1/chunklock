# Upcoming Release (Unreleased)

## Added

- New `/chunklock database` command to view storage backend status (MapDB or MySQL) with connection pool metrics, performance stats, and migration status
- MySQL Setup Guide with comprehensive instructions for server setup, configuration, migration, performance tuning, and troubleshooting
- `/chunklock debug database` subcommand showing connection status, query performance, and pool statistics
- New **resource-scan** cost mode for material economy — scans your owned chunks and requires materials you can actually gather, preventing situations where players are stuck without the biome's required materials. Enable with `materials.cost-mode: "resource-scan"` in `economy.yml`

## Changed

- Enhanced admin commands documentation with `/chunklock database` command reference and example outputs
- Improved database monitoring capabilities for administrators

## Fixed

- Fixed MySQL restart claim restoration issue where `/chunklock start` could assign a new starting chunk after server restart instead of reusing the player's persisted assigned chunk (#65)
- Fixed critical gameplay blocker where players could spawn in chunks without the biome's required unlock materials (e.g., Plains chunk with no trees), making progression impossible (#69)
- Fixed unlock GUI showing wrong material name (e.g., "Need 2 more Diamond" when the actual cost was wheat) — display and validation now use the same cost source
- Fixed items not being consumed from inventory when unlocking chunks with materials
- Fixed vanilla tooltip leak showing "minecraft:redstone_block 13 component(s)" on the unlock button
- Fixed a regression where unlock could fail despite having enough vanilla materials in inventory, and corrected incorrect "missing" amounts shown in material unlock messages (#71)
- Fixed materials-mode unlocks incorrectly requiring multiple resource types at once; unlocks now require a single resource again as expected
- Fixed repetitive material unlock costs in resource-scan mode (especially repeated acacia/wood requirements) and improved progression scaling so required amounts vary more naturally instead of flattening around a fixed value (#76)
- Fixed resource-scan pricing edge cases that could be gamed by artificially depleting a material in claimed chunks; pricing now uses bounded soft availability influence, deterministic material selection, smoother progression scaling, and clearer in-GUI messaging about dynamic resource-based costs (#77)
- Fixed an error during async unlock cost calculation in resource-scan mode that could cause the unlock menu to fail to open or show incorrect requirements (#78)

## Known Issues

- Some servers report high memory usage and degraded performance at higher player counts, particularly with heavy exploration (#74). Investigation is in progress; a fix is not included in this release.
