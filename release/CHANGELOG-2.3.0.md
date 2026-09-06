# Chunklock 2.3.0

**Economy release.** Chunk prices are now derived from the chunk you are unlocking, and once you have been quoted a price it is honoured until you pay it or deliberately re-roll it. This release also carries the memory fix for the high-RAM reports, and several progression-blocking fixes.

**Servers mid-progression will see prices change after updating.** That is the point of the release, but tell your players before you update.

**[Changed] Chunk prices now come from the chunk you are unlocking**

- A chunk's cost is now based on what is actually in that chunk, not on a pool aggregated across everything your team already owns (#86)
- Forests ask for wood, mountains for andesite, deserts for sand. Two adjacent chunks with different terrain now price differently, and seeds genuinely diverge
- Prices favour what makes a chunk distinctive rather than what it merely holds the most of, so bulk stone and deepslate no longer dominate every quote
- A chunk with no real character falls back to the previous territory-based pricing instead of picking a material at random (#86)
- Required amounts scale with how distinctive the material is, and with your progression, rather than flattening toward a fixed number
- Costs stay capped by your progression tier, so an early chunk cannot ask for something you have no way to reach yet

**[Added] Price commitment and re-rolls**

- Once you have seen a price for a chunk, that price is held until you pay it or re-roll it. It survives relog, server restart and cache expiry, so a long collection goal cannot be invalidated by the plugin changing its mind (#83)
- New re-roll button in the unlock menu (slot 33) for when you want a different requirement: either a one-hour per-chunk cooldown, or an escalating currency cost where Vault is installed
- The re-roll button is greyed out, with the reason shown, when your territory offers only one obtainable material and a re-roll could not change anything (#83)

**[Fixed] Memory growth on long-running servers**

- The hologram state cache no longer grows without bound during exploration; stale entries are evicted and the cache is hard-capped (#74)
- New `/chunklock debug memory` command showing hologram cache totals, evictions and usage percentage, so you can confirm the cache plateaus on your own server

**[Fixed] Unlock costs and progression**

- Unlocked chunks are now actually counted. The counter was never incremented, so every player sat at 0 forever — which capped pricing to tier 3 and left the progression cost multiplier flat. Existing worlds recount themselves once from stored chunk ownership on first read
- The unlock menu no longer changes the required material each time you open it (#82)
- Chunk costs are now written to the database at all. Every write had been failing silently, so no committed price was ever stored (#90)
- Chunk cost and profile storage now follow your configured `database.type` instead of always using a local H2 file. On multi-server MySQL setups, prices no longer diverge per node (#95)
- Messages added by plugin updates now display correctly on servers that were first started on an earlier version, instead of rendering as raw keys like `gui.builder.reroll-title`

**[Fixed] Starting chunks**

- Players no longer spawn in the void after `/chunklock start`. Starting chunks were being selected from ungenerated terrain, which put the spawn point at the world floor
- The assigned starting spawn is now saved, so dying no longer drops you back into the void on respawn

**[Fixed] Hologram diagnostics**

- When holograms are enabled but the provider fails to load, the server log now prints a clear warning naming the provider, whether the plugin is present, where to get it, and the Java-version pitfall that makes it fail silently — instead of a single info line that scrolled past
- `provider: "None"` is now recognised regardless of capitalisation

**[Changed] Documentation**

- The store page and README no longer advertise behaviour the plugin did not have. Resource scanning is now described as it actually works — and with this release, the chunk-contents claim is true for the first time
- Bug and performance issue templates rewritten to ask for server software, Java version, storage backend and console output, rather than GitHub's default questions about browsers and phone models

**[Known Issues]**

- #74's memory fix is validated locally and the cache is bounded in practice, but the original 64GB report from a heavily modified server at high player count has not been reproduced. The issue stays open pending data from a real deployment. If you run a large server, `/chunklock debug memory` output before and after a long session is exactly what is needed
- The guided first-run setup wizard (#97) and the config system consolidation (#98) were planned for this release and deferred to the next one

---

**Minecraft**: Paper 1.20.4+ (tested up to 1.21.10)
**Java**: 17+

> **Heads-up for the next release.** Chunklock 3.0.0 will move to Paper 26.1.2 and **Java 25**. 2.3.0 is the last release that installs on Java 17 without a server migration, so if you are staying on 1.21.10, this is your landing point.
