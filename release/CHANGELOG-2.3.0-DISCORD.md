# Chunklock 2.3.0 is out

Chunks now price themselves. Unlock costs come from the chunk you are actually unlocking - a forest asks for wood, a mountain for andesite, a desert for sand - instead of one shared pool aggregated across everything you already own. Two chunks side by side with different terrain now cost different things, and seeds genuinely play differently.

**Heads up: prices will change on servers mid-progression.** That is what this release is, but warn your players before you update.

**Changed**
- Costs are based on what is in the chunk you are unlocking, ranked by what makes it distinctive rather than what it holds the most of
- Bulk stone and deepslate no longer dominate every quote
- A chunk with nothing interesting in it falls back to the old territory-based pricing rather than picking at random
- Amounts scale with distinctiveness and progression instead of flattening toward a fixed number

**Added**
- Prices are now a commitment. Once you have been quoted a price for a chunk it is honoured until you pay it or re-roll it, surviving relog, restart and cache expiry
- A re-roll button in the unlock menu if you want a different requirement - one-hour per-chunk cooldown, or an escalating currency cost with Vault
- `/chunklock debug memory` for watching the hologram cache on your own server

**Fixed**
- The hologram cache no longer grows without bound while exploring (#74)
- Unlocked chunks are now actually counted. The counter was never incremented, so everyone sat at 0 forever - which quietly capped pricing to tier 3 and flattened progression scaling. Existing worlds fix themselves on first read
- Chunk costs are now written to the database at all; every write had been failing silently (#90)
- On MySQL, cost and profile storage now follows your configured `database.type` instead of a local file, so prices no longer differ between nodes (#95)
- The unlock menu no longer changes the required material each time you open it (#82)
- Players no longer spawn in the void after `/chunklock start`, and dying no longer drops you back into it
- Messages added by updates now display properly on servers first started on an older version, instead of showing raw keys
- If holograms are enabled but the provider fails to load, the log now tells you what to check

**Still open**
The memory fix is validated on my own server, but the original 64GB report from a heavily modified server at high player count was never reproduced, so #74 stays open. If you run a large server, `/chunklock debug memory` output before and after a long session is genuinely the most useful thing you can send me.

Paper 1.20.4+ (tested to 1.21.10), Java 17+

**One more thing:** 3.0.0 moves to Paper 26.1.2 and **Java 25**. 2.3.0 is the last release that installs on Java 17 without migrating your server. If you are staying on 1.21.10, this is your landing point - and if you have thoughts about that jump, now is the time to say so.
