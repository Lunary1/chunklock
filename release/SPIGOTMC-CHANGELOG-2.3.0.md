[SIZE=5][B]Chunklock 2.3.0 - Chunks Price Themselves[/B][/SIZE]

Unlock costs are now based on the chunk you are actually unlocking. A forest asks for wood, a mountain for andesite, a desert for sand - and two chunks side by side with different terrain now cost different things.

[B]Servers mid-progression will see prices change after updating.[/B] That is the point of the release, but tell your players before you update.

[SIZE=4][B]Changed - Costs come from the chunk you are unlocking[/B][/SIZE]
[LIST]
[*]A chunk's cost is based on what is in that chunk, not on a pool aggregated across everything your team already owns
[*]Pricing favours what makes a chunk distinctive rather than what it merely holds the most of, so bulk stone and deepslate no longer dominate every quote
[*]A chunk with no real character falls back to the old territory-based pricing instead of picking something at random
[*]Required amounts scale with how distinctive the material is and with your progression, rather than flattening toward a fixed number
[*]Costs stay capped by your progression tier, so an early chunk cannot ask for something you have no way to reach yet
[/LIST]

[SIZE=4][B]Added - Price commitment and re-rolls[/B][/SIZE]
[LIST]
[*]Once you have seen a price for a chunk, it is held until you pay it or re-roll it - surviving relog, restart and cache expiry. A long collection goal can no longer be invalidated by the plugin changing its mind
[*]New re-roll button in the unlock menu for when you want a different requirement: a one-hour per-chunk cooldown, or an escalating currency cost if you run Vault
[*]The button greys out, with the reason shown, when your territory offers only one obtainable material and a re-roll could not change anything
[/LIST]

[SIZE=4][B]Fixed - Memory growth[/B][/SIZE]
[LIST]
[*]The hologram state cache no longer grows without bound during exploration - stale entries are evicted and the cache is hard-capped
[*]New /chunklock debug memory showing cache totals, evictions and usage percentage, so you can confirm it plateaus on your own server
[/LIST]

[SIZE=4][B]Fixed - Costs and progression[/B][/SIZE]
[LIST]
[*]Unlocked chunks are now actually counted. The counter was never incremented, so every player sat at 0 forever - which capped pricing to tier 3 and left progression scaling flat. Existing worlds recount themselves once from stored chunk ownership
[*]The unlock menu no longer changes the required material each time you open it
[*]Chunk costs are now written to the database at all - every write had been failing silently
[*]Cost and profile storage now follows your configured database.type instead of always using a local file. On multi-server MySQL setups, prices no longer differ per node
[*]Messages added by plugin updates now display properly on servers first started on an earlier version, instead of showing raw keys
[/LIST]

[SIZE=4][B]Fixed - Starting chunks[/B][/SIZE]
[LIST]
[*]Players no longer spawn in the void after /chunklock start - starting chunks were being picked from ungenerated terrain, putting the spawn at the world floor
[*]The assigned spawn is now saved, so dying no longer drops you back into the void
[/LIST]

[SIZE=4][B]Fixed - Hologram diagnostics[/B][/SIZE]
[LIST]
[*]If holograms are enabled but the provider fails to load, the log now names the provider, says whether the plugin is present, where to get it, and the Java-version pitfall that makes it fail silently - instead of one info line that scrolls past
[/LIST]

[SIZE=4][B]Known issues[/B][/SIZE]
[LIST]
[*]The memory fix above is validated on my own server, but the original report of very high RAM use on a heavily modified server at high player count has not been reproduced. If you run a large server, /chunklock debug memory before and after a long session is exactly what I need
[/LIST]

[B]Requires:[/B] Paper 1.20.4+ (tested to 1.21.10), Java 17+

[B]Heads-up:[/B] the next release, 3.0.0, moves to Paper 26.1.2 and [B]Java 25[/B]. 2.3.0 is the last release that installs on Java 17 without migrating your server, so if you are staying where you are, this is the one to be on.
