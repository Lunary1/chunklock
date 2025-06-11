# chunklock

Chunklock is a Minecraft plugin that restricts players to specific chunks until they unlock them. Chunks can be unlocked by providing biome-specific items. The amount and rarity of those items scale with the chunk's difficulty score and how many chunks the player has already opened. The plugin also tracks how many chunks each player has unlocked. Players can team up with `/chunklock team <player>` to share progress.

## Commands

- `/chunklock status` – show how many chunks you have unlocked.
- `/chunklock unlock` – open a GUI to attempt unlocking your current chunk. The same interface appears automatically when you try to enter a locked chunk.
- `/chunklock bypass [player]` – admin: toggle bypass mode for a player.
- `/chunklock reset <player>` – admin: reset a player's progress.
- `/chunklock spawn` – return to your starting chunk.
- `/chunklock team <player>` – join another player's team and share progress.
