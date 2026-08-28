# Chunklock 2.2.0 is out

Development has been paused since 2.1.0 back in November. I'm picking it back up, and this release ships everything that was finished but never released, plus a crash fix found while reviewing that work.

**Fixed**
- Unlock costs no longer keep demanding the same material (acacia/wood especially) as you progress
- Costs can no longer be gamed by depleting a material in your claimed chunks
- Fixed an error that could stop the unlock menu opening or show wrong requirements
- `/chunklock start` no longer gives a new starting chunk after a restart on MySQL
- Players spawning without their biome's required materials can now progress
- Fixed items not being consumed when unlocking, and wrong "missing amount" messages
- Materials mode requires one resource again, not several at once

**Added**
- `/chunklock database` and `/chunklock debug database` for storage backend visibility
- MySQL setup guide

**Not fixed yet**
Some servers see high memory use at higher player counts with heavy exploration (#74). That is my next priority - I did not want to hold these fixes back while I work on it. If you are hitting it, `/chunklock debug` output and your player count genuinely help.

Paper 1.20.4+ (tested to 1.21.10), Java 17+