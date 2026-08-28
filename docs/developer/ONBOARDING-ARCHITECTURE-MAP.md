# Chunklock One-Page Architecture Map (Onboarding)

## 1) Mental model in one minute
Chunklock is a **startup orchestrator + manager graph**:

- `ChunklockPlugin` wires everything in dependency order.
- Player movement triggers lock checks in `PlayerListener`.
- Locked chunk entry opens `UnlockGui`.
- `UnlockGui` asks `EconomyManager` for the cost and payment processing.
- `ChunkLockManager` commits ownership/lock state to storage.
- Storage is selected by `StorageFactory` (MapDB or MySQL).

Think in this pipeline:

**Event -> Validation -> Cost -> Payment -> Unlock -> Side-effects (borders/holograms/cache)**

---

## 2) Core runtime layers (what each owns)

### A. Orchestration / lifecycle
- `src/main/java/me/chunklock/ChunklockPlugin.java`
- Responsibilities:
  - startup validation/config migration
  - storage bootstrap + migrations
  - manager construction order
  - listener + command registration
  - service layer boot/shutdown

### B. Command surface
- `src/main/java/me/chunklock/commands/ChunklockCommandExecutor.java`
- `src/main/java/me/chunklock/commands/ChunklockCommandManager.java`
- Responsibilities:
  - subcommand registration/routing
  - permission/sender checks
  - world validation wrapper

### C. Gameplay core
- `src/main/java/me/chunklock/listeners/PlayerListener.java`
- `src/main/java/me/chunklock/ui/UnlockGui.java`
- `src/main/java/me/chunklock/managers/ChunkLockManager.java`
- Responsibilities:
  - detect locked chunk interactions
  - present unlock UX
  - enforce unlock rules and write ownership

### D. Economy + pricing
- `src/main/java/me/chunklock/economy/EconomyManager.java`
- `src/main/java/me/chunklock/economy/calculation/ResourceBasedMaterialStrategy.java`
- `src/main/java/me/chunklock/services/AsyncCostCalculationService.java`
- Responsibilities:
  - choose economy mode (materials/vault)
  - calculate requirements using strategy
  - process payment
  - async pre-calc and cache behavior

### E. Config + DI/API boundaries
- `src/main/java/me/chunklock/config/ConfigManager.java`
- `src/main/java/me/chunklock/api/container/ServiceContainer.java`
- `src/main/java/me/chunklock/api/ChunklockAPI.java`
- Responsibilities:
  - modular config loading and validation
  - service registration/lifecycle
  - external API facade

### F. Persistence boundary
- `src/main/java/me/chunklock/services/StorageFactory.java`
- Responsibilities:
  - choose MySQL vs MapDB
  - handle fail-fast/fallback startup behavior

---

## 3) Real unlock flow (what actually happens)

1. Player moves into a new chunk.
2. `PlayerListener` initializes chunk data and checks lock state.
3. If locked, it evaluates chunk and opens `UnlockGui`.
4. `UnlockGui` validates session, contested rules, and affordability.
5. `EconomyManager` computes/validates requirement and processes payment.
6. `ChunkLockManager.unlockChunk(...)` writes unlocked owner state.
7. Post-actions run: progress/team stats, border refresh, holograms, cache invalidation.

This is your highest-value trace path for understanding and debugging most gameplay issues.

---

## 4) 60-minute reading path (in order)

### 0-10 min: startup map
1. `src/main/java/me/chunklock/ChunklockPlugin.java`
   - Focus on: `onEnable`, `initializeComponents`, `registerEventListeners`, `registerCommands`.

### 10-20 min: command and player entry points
2. `src/main/java/me/chunklock/commands/ChunklockCommandExecutor.java`
3. `src/main/java/me/chunklock/commands/ChunklockCommandManager.java`
4. `src/main/java/me/chunklock/listeners/PlayerListener.java`
   - Focus on movement -> locked chunk -> `unlockGui.open(...)`.

### 20-35 min: unlock transaction core
5. `src/main/java/me/chunklock/ui/UnlockGui.java`
   - Focus on `open`, `processUnlockAttempt`, `executeMaterialUnlock`, `executeMoneyUnlock`, `finishUnlock`.
6. `src/main/java/me/chunklock/managers/ChunkLockManager.java`
   - Focus on `initializeChunk`, `unlockChunk`, ownership/contested helpers.

### 35-50 min: pricing and payment
7. `src/main/java/me/chunklock/economy/EconomyManager.java`
8. `src/main/java/me/chunklock/economy/calculation/ResourceBasedMaterialStrategy.java`
9. `src/main/java/me/chunklock/services/AsyncCostCalculationService.java`

### 50-60 min: boundaries and config
10. `src/main/java/me/chunklock/config/ConfigManager.java`
11. `src/main/java/me/chunklock/services/StorageFactory.java`
12. `src/main/java/me/chunklock/api/container/ServiceContainer.java`
13. `src/main/java/me/chunklock/api/ChunklockAPI.java`

---

## 5) How to work safely in this repo (Java + large codebase)

- Change only one flow at a time (unlock flow first).
- Before edits, run a narrow grep for the method you plan to change.
- After edits, run tests related to touched classes, then full build.
- Prefer `ConfigManager` and manager/service interfaces over direct config access in new code.
- Keep a simple note per session:
  - "Entry point"
  - "State mutated"
  - "Persistence touched"
  - "Side effects triggered"

---

## 6) First practical exercises (to build confidence)

1. Add a temporary log line in `UnlockGui.processUnlockAttempt` and verify when it fires.
2. Toggle economy mode (`materials` vs `vault`) and follow `EconomyManager.selectCalculationStrategy`.
3. Step through one unlock in debugger and record the exact order of method calls.

If you can explain those 3 exercises end-to-end, you already control the core architecture.
