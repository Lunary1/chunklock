[SIZE=5][B]Chunklock 2.2.0 - Stability & Economy[/B][/SIZE]

This release completes the resource-scan economy work and fixes several bugs that could block player progression.

[SIZE=4][B]Fixed - Resource-scan costs[/B][/SIZE]
[LIST]
[*]Unlock costs no longer keep demanding the same material (especially acacia and other wood) as you progress
[*]Progression scaling reworked so required amounts vary naturally instead of flattening around a fixed value
[*]Pricing can no longer be gamed by artificially depleting a material in your claimed chunks
[*]Material selection is now deterministic - identical situations produce identical costs
[*]The unlock menu now explains that costs are based on resources in your claimed chunks
[/LIST]

[SIZE=4][B]Fixed - Unlock menu errors[/B][/SIZE]
[LIST]
[*]Fixed an error during background cost calculation that could stop the unlock menu opening or show wrong requirements
[*]Per-player state is now released on disconnect, avoiding unnecessary memory growth on long-running servers
[/LIST]

[SIZE=4][B]Fixed - Progression blockers[/B][/SIZE]
[LIST]
[*]/chunklock start no longer hands out a new starting chunk after a restart on MySQL
[*]Players spawning in chunks without the biome's required materials (e.g. Plains with no trees) can now progress
[*]Fixed unlock failing despite having enough materials, and corrected the "missing" amounts shown
[*]Materials mode requires a single resource again rather than several at once
[*]Fixed items not being consumed when unlocking with materials
[/LIST]

[SIZE=4][B]Added - Database tools[/B][/SIZE]
[LIST]
[*]/chunklock database - active storage backend, connection pool metrics, performance stats, migration status
[*]/chunklock debug database - connection status, query performance, pool statistics
[*]New MySQL Setup Guide covering installation, configuration, migration and troubleshooting
[/LIST]

[SIZE=4][B]Known issues[/B][/SIZE]
[LIST]
[*]Some servers report high memory usage at higher player counts with heavy exploration. This is under active investigation and is [B]not[/B] fixed in this release - I did not want to hold the above fixes back for it.
[/LIST]

[B]Requires:[/B] Paper 1.20.4+ (tested to 1.21.10), Java 17+

Thanks for your patience - development had been paused for a while and this clears the backlog of finished work. Bug reports with your server version, player count and plugin list are always the most useful.