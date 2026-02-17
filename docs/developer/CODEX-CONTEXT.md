# Codex Fast Context (Chunklock)

Purpose: give GPT-5.3-Codex a fast, reliable map of where to look and what to edit.

## 1) Project Snapshot

- Type: Paper/Spigot plugin (Java 17, Maven)
- Artifact: `Chunklock-2.1.0`
- Build: `mvn clean package`
- Entry point: `src/main/java/me/chunklock/ChunklockPlugin.java`
- Plugin descriptor: `src/main/resources/plugin.yml` (UTF-16LE + CRLF)

## 2) Core File Map (Highest Signal)

- Bootstrap/lifecycle:
  - `src/main/java/me/chunklock/ChunklockPlugin.java`
- Command routing:
  - `src/main/java/me/chunklock/commands/ChunklockCommandExecutor.java`
  - `src/main/java/me/chunklock/commands/ChunklockCommandManager.java`
  - `src/main/java/me/chunklock/commands/*.java`
- Main gameplay systems:
  - `src/main/java/me/chunklock/managers/ChunkLockManager.java`
  - `src/main/java/me/chunklock/managers/ChunkEvaluator.java`
  - `src/main/java/me/chunklock/managers/BiomeUnlockRegistry.java`
  - `src/main/java/me/chunklock/managers/PlayerProgressTracker.java`
  - `src/main/java/me/chunklock/managers/TeamManager.java`
- Economy + AI cost logic:
  - `src/main/java/me/chunklock/economy/EconomyManager.java`
  - `src/main/java/me/chunklock/economy/calculation/*.java`
  - `src/main/java/me/chunklock/ai/OpenAIChunkCostAgent.java`
- Holograms + borders:
  - `src/main/java/me/chunklock/hologram/**`
  - `src/main/java/me/chunklock/border/**`
  - `src/main/java/me/chunklock/managers/ChunkBorderManager.java`
- Config loading and validation:
  - `src/main/java/me/chunklock/config/ConfigManager.java`
  - `src/main/java/me/chunklock/util/validation/ConfigValidator.java`
  - `src/main/java/me/chunklock/util/migration/ConfigMigrator.java`
- Persistence/data:
  - `src/main/java/me/chunklock/services/ChunkDatabase.java`
  - `src/main/java/me/chunklock/services/PlayerDatabase.java`
  - `src/main/java/me/chunklock/services/DataMigrationService.java`

## 3) Config Files (Runtime Behavior)

- Core: `src/main/resources/config.yml`
- Modular configs:
  - `src/main/resources/economy.yml`
  - `src/main/resources/openai.yml`
  - `src/main/resources/block-values.yml`
  - `src/main/resources/biome-unlocks.yml`
  - `src/main/resources/team-settings.yml`
  - `src/main/resources/borders.yml`
  - `src/main/resources/worlds.yml`
  - `src/main/resources/holograms.yml`
  - `src/main/resources/debug.yml`
  - `src/main/resources/performance.yml`
- Language: `src/main/resources/lang/en.yml`

## 4) Task -> Where to Edit

- Add/change subcommand:
  1. Create/update `src/main/java/me/chunklock/commands/<Name>Command.java`
  2. Register in `src/main/java/me/chunklock/commands/ChunklockCommandExecutor.java`
  3. Update command metadata/permissions in `src/main/resources/plugin.yml`
- Change unlock pricing logic:
  1. `src/main/java/me/chunklock/economy/EconomyManager.java`
  2. Strategy class in `src/main/java/me/chunklock/economy/calculation/`
  3. Related config defaults in `src/main/resources/economy.yml` or `src/main/resources/openai.yml`
- Change biome/material requirements:
  1. `src/main/resources/biome-unlocks.yml`
  2. `src/main/resources/block-values.yml`
  3. Parsing/usage in `src/main/java/me/chunklock/managers/BiomeUnlockRegistry.java`
- Change startup/dependency order:
  1. `src/main/java/me/chunklock/ChunklockPlugin.java`
  2. `src/main/java/me/chunklock/api/container/ServiceContainer.java` (if service lifecycle affected)
- Change UI/hologram display:
  1. `src/main/java/me/chunklock/ui/UnlockGui*.java`
  2. `src/main/java/me/chunklock/hologram/**`

## 5) Fast Search Recipes

Use these first:

```bash
rg -n "registerSubCommand|onCommand|TabCompleter" src/main/java/me/chunklock/commands
rg -n "calculateRequirement|CostCalculationStrategy|AIVaultStrategy|AIMaterialStrategy" src/main/java/me/chunklock
rg -n "BiomeUnlockRegistry|biome-unlocks|UnlockRequirement" src/main/java/me/chunklock src/main/resources
rg -n "ConfigManager|validateConfiguration|reloadConfiguration|config-version" src/main/java/me/chunklock src/main/resources
rg -n "Hologram|Border|ChunkBorderManager|BorderRefreshService" src/main/java/me/chunklock
rg -n "MapDB|ChunkDatabase|PlayerDatabase|DataMigrationService" src/main/java/me/chunklock
```

## 6) Known Gotchas

- `plugin.yml` encoding is UTF-16LE with CRLF. Do not rewrite it as UTF-8 accidentally.
- There are legacy + modular config classes with overlapping names:
  - `me.chunklock.config.EconomyConfig`
  - `me.chunklock.config.modular.EconomyConfig`
  Be explicit with imports.
- Docs may reflect multiple historical phases. For code truth, prefer current Java sources + modular YAML files.
- Worktree may already be heavily modified; avoid reverting unrelated files.

## 7) Minimal Validation After Edits

```bash
mvn test
mvn -DskipTests package
```

If command/permission changes were made, also verify:
- `src/main/resources/plugin.yml` command + permission nodes match code registration.
