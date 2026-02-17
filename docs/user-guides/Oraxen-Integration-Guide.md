# 💎 Oraxen Integration Guide for Chunklock

Complete guide for integrating Oraxen custom items into Chunklock biome unlocks. Includes setup, testing procedures, example configurations, and troubleshooting.

**Plugin Version:** Chunklock 2.1.0 (Paper 1.21.10)  
**Integration Target:** Oraxen 1.195.1+  
**Status:** Production Ready

---

## 📋 Table of Contents

- [Quick Start](#-quick-start)
- [Prerequisites](#-prerequisites)
- [Setup & Installation](#-phase-1-setup--installation)
- [Configuration Examples](#-configuration-examples)
- [Testing Procedures](#-testing-procedures)
- [Troubleshooting](#-troubleshooting)
- [Example Progression System](#-example-progression-system-with-oraxen)

---

## 🚀 Quick Start

**Want to jump in immediately?** Here's the minimal working config:

```yaml
biome-unlocks:
  FOREST:
    vanilla:
      OAK_LOG: 16
      APPLE: 4
    custom:
      - plugin: oraxen
        item: your_oraxen_item_id
        amount: 1
```

**Finding your Oraxen item IDs:** `/oraxen list` in-game

Then reload: `/chunklock reload`

**Ready for comprehensive setup?** Continue reading below.

---

## 📋 Prerequisites

### Server Requirements

- ✅ Paper 1.21.10 or later (1.21.x)
- ✅ Java 17+
- ✅ 2GB+ RAM allocated

### Required Plugins

- ✅ **Chunklock 2.1.0** - Main plugin
- ✅ **Oraxen 1.195.1+** - Custom items plugin
- ✅ **FancyHolograms 2.4.2** - Optional but recommended (visual display)

### Optional But Recommended

- ✅ **Vault** - For economy integration
- ✅ **EssentialsX** - For easier player management in testing

---

## 🔧 Phase 1: Setup & Installation

### Step 1.1: Deploy Plugins

1. **Create plugins folder** (if not exists):
   ```
   mkdir server/plugins
   ```

2. **Copy plugins:**
   ```
   server/plugins/
   ├── Chunklock.jar ✅
   ├── Oraxen.jar ✅
   └── FancyHolograms.jar ✅ (optional)
   ```

3. **Start server:**
   ```
   java -Xmx2G -jar paper-1.21.10.jar nogui
   ```

4. **Wait for startup:**
   ```
   [Server thread/INFO]: Done (X.XXXs)! For help, type "help"
   ```

### Step 1.2: Verify Plugin Loading

**In-game command:**

```
/plugins
```

**Expected output:**

```
Plugins (X):
- Chunklock
- Oraxen
- (FancyHolograms)
```

**Check console for:**

```
✅ [Chunklock] ✅ Plugin initialized successfully
✅ [Chunklock] ✅ Configuration loaded
✅ [Oraxen] Successfully loaded Oraxen
```

**If RED errors appear:** Check error messages and fix before proceeding.

### Step 1.3: Identify Available Oraxen the**

**Find custom items in Oraxen:**

```
/oraxen list
```

**Example output:**

```
- amethyst
- ruby
- onyx
- orax
- custom_sword
- mythic_bow
```

**Or give yourself test items:**

```
/oraxen give @s amethyst 1
/oraxen give @s ruby 1
```

---

## 🎨 Configuration Examples

### Example 1: Simple Oraxen Addition

Adding a single custom item to an existing biome:

```yaml
FOREST:
  vanilla:
    OAK_LOG: 16
    APPLE: 4
  custom:
    - plugin: oraxen
      item: amethyst
      amount: 1
```

**What this means:**

- Player needs: 16 OAK_LOG + 4 APPLE + 1 amethyst
- All-or-nothing system: missing ANY item prevents unlock

---

### Example 2: Multiple Custom Items

Adding several Oraxen items to one biome:

```yaml
DARK_FOREST:
  vanilla:
    DARK_OAK_LOG: 16
    DARK_OAK_SAPLING: 4
  custom:
    - plugin: oraxen
      item: amethyst
      amount: 2
    - plugin: oraxen
      item: ruby
      amount: 1
```

---

### Example 3: Oraxen-Only Biome

No vanilla items required, only custom items:

```yaml
JUNGLE:
  custom:
    - plugin: oraxen
      item: custom_sword
      amount: 2
    - plugin: oraxen
      item: glowing_dust
      amount: 5
```

**Use case:** Special challenge biomes that require rare custom items.

---

### Example 4: Mixed Format (Vanilla-Only Reference)

For comparison, a purely vanilla biome (flat format):

```yaml
PLAINS:
  WHEAT: 8
  HAY_BLOCK: 2
```

**Note:** No `vanilla:` or `custom:` keys needed when using only vanilla items.

---

## 💎 Example Progression System with Oraxen

A complete 7-tier progression system using Oraxen gems (amethyst, ruby, onyx, orax):

### Integration Strategy

**Item Rarity Levels:**

- **Common Gems** (Abundant): Amethyst, Onyx - lower amounts, easier biomes
- **Rare Gems** (Scarce): Ruby - moderate amounts, medium biomes
- **Epic Gems** (Very Rare): Orax - high amounts/low counts, difficult biomes

### Tier 1: Starting (No Custom Items)

```yaml
PLAINS:
  WHEAT: 8
  HAY_BLOCK: 2
```

**Difficulty:** ⭐ (Easiest - tutorial biome)

---

### Tier 2: Easy (Common Gem Introduction)

```yaml
FOREST:
  vanilla:
    OAK_LOG: 16
    APPLE: 4
  custom:
    - plugin: oraxen
      item: amethyst
      amount: 1
```

**Difficulty:** ⭐⭐ (Easy - introduces custom items)

---

### Tier 3: Medium (Rare Gem Addition)

```yaml
BIRCH_FOREST:
  vanilla:
    BIRCH_LOG: 16
    MUSHROOM_STEW: 4
  custom:
    - plugin: oraxen
      item: ruby
      amount: 1
```

**Difficulty:** ⭐⭐⭐ (Medium - rare gem required)

---

### Tier 4: Medium-Hard (Multiple Gems)

```yaml
DARK_FOREST:
  vanilla:
    DARK_OAK_LOG: 16
    DARK_OAK_SAPLING: 4
  custom:
    - plugin: oraxen
      item: amethyst
      amount: 2
    - plugin: oraxen
      item: ruby
      amount: 1
```

**Difficulty:** ⭐⭐⭐⭐ (Hard - multiple gem types)

---

### Tier 5: Hard (High Vanilla + Gems)

```yaml
OLD_GROWTH_BIRCH_FOREST:
  vanilla:
    BIRCH_LOG: 24
    GOLDEN_APPLE: 1
  custom:
    - plugin: oraxen
      item: amethyst
      amount: 3
    - plugin: oraxen
      item: onyx
      amount: 2
```

**Difficulty:** ⭐⭐⭐⭐⭐ (Very Hard - high resource cost)

---

### Tier 6: Very Hard (Mixed Rarity)

```yaml
OLD_GROWTH_PINE_TAIGA:
  vanilla:
    SPRUCE_LOG: 24
    MOSSY_COBBLESTONE: 16
  custom:
    - plugin: oraxen
      item: ruby
      amount: 2
    - plugin: oraxen
      item: onyx
      amount: 3
    - plugin: oraxen
      item: amethyst
      amount: 2
```

**Difficulty:** ⭐⭐⭐⭐⭐⭐ (Epic - multiple gems required)

---

### Tier 7: Maximum (Epic Gems + High Vanilla)

```yaml
OLD_GROWTH_SPRUCE_TAIGA:
  vanilla:
    SPRUCE_LOG: 32
    PODZOL: 16
  custom:
    - plugin: oraxen
      item: orax
      amount: 3
    - plugin: oraxen
      item: ruby
      amount: 3
    - plugin: oraxen
      item: onyx
      amount: 2
```

**Difficulty:** ⭐⭐⭐⭐⭐⭐⭐ (Legendary - hardest unlock)

---

### Full Configuration Block

Copy this entire block for a complete progression system:

```yaml
biome-unlocks:
  # Tier 1 - Tutorial
  PLAINS:
    WHEAT: 8
    HAY_BLOCK: 2

  # Tier 2 - Easy (Common Gem)
  FOREST:
    vanilla:
      OAK_LOG: 16
      APPLE: 4
    custom:
      - plugin: oraxen
        item: amethyst
        amount: 1

  # Tier 3 - Medium (Rare Gem)
  BIRCH_FOREST:
    vanilla:
      BIRCH_LOG: 16
      MUSHROOM_STEW: 4
    custom:
      - plugin: oraxen
        item: ruby
        amount: 1

  # Tier 4 - Medium-Hard (Multiple Gems)
  DARK_FOREST:
    vanilla:
      DARK_OAK_LOG: 16
      DARK_OAK_SAPLING: 4
    custom:
      - plugin: oraxen
        item: amethyst
        amount: 2
      - plugin: oraxen
        item: ruby
        amount: 1

  # Tier 5 - Hard
  OLD_GROWTH_BIRCH_FOREST:
    vanilla:
      BIRCH_LOG: 24
      GOLDEN_APPLE: 1
    custom:
      - plugin: oraxen
        item: amethyst
        amount: 3
      - plugin: oraxen
        item: onyx
        amount: 2

  # Tier 6 - Very Hard
  OLD_GROWTH_PINE_TAIGA:
    vanilla:
      SPRUCE_LOG: 24
      MOSSY_COBBLESTONE: 16
    custom:
      - plugin: oraxen
        item: ruby
        amount: 2
      - plugin: oraxen
        item: onyx
        amount: 3
      - plugin: oraxen
        item: amethyst
        amount: 2

  # Tier 7 - Legendary
  OLD_GROWTH_SPRUCE_TAIGA:
    vanilla:
      SPRUCE_LOG: 32
      PODZOL: 16
    custom:
      - plugin: oraxen
        item: orax
        amount: 3
      - plugin: oraxen
        item: ruby
        amount: 3
      - plugin: oraxen
        item: onyx
        amount: 2
```

---

### Item Distribution Summary

| Biome                   | Vanilla | Amethyst | Ruby | Onyx | Orax | Difficulty     |
| ----------------------- | ------- | -------- | ---- | ---- | ---- | -------------- |
| PLAINS                  | ✅      | -        | -    | -    | -    | ⭐             |
| FOREST                  | ✅      | 1        | -    | -    | -    | ⭐⭐           |
| BIRCH_FOREST            | ✅      |- 1        | 1    | -    | -    | ⭐⭐⭐         |
| DARK_FOREST             | ✅      | 2        | 1    | -    | -    | ⭐⭐⭐⭐       |
| OLD_GROWTH_BIRCH_FOREST | ✅      | 3        | -    | 2    | -    | ⭐⭐⭐⭐⭐     |
| OLD_GROWTH_PINE_TAIGA   | ✅      | 2        | 2    | 3    | -    | ⭐⭐⭐⭐⭐⭐   |
| OLD_GROWTH_SPRUCE_TAIGA | ✅      | -        | 3    | 2    | 3    | ⭐⭐⭐⭐⭐⭐⭐ |

---

## 🧪 Testing Procedures

### Test 1: Vanilla-Only Baseline

**Purpose:** Verify baseline functionality before adding custom items.

**Steps:**

1. Clear inventory: `/clear @s`
2. Give vanilla items: `/give @s wheat 8` and `/give @s hay_block 2`
3. Try to unlock: `/chunklock unlock`
4. Click PLAINS

**Expected:**

- ✅ GUI shows PLAINS option
- ✅ Can click to unlock
- ✅ Items consumed
- ✅ Chunk unlocks

---

### Test 2: Mixed Vanilla + Oraxen

**Purpose:** Verify mixed item types work together.

**Steps:**

1. Clear inventory: `/clear @s`
2. Give vanilla items:
   ```
   /give @s oak_log 16
   /give @s apple 4
   ```
3. Give custom items:
   ```
   /oraxen give @s amethyst 1
   ```
4. Try to unlock: `/chunklock unlock`
5. Click FOREST

**Expected:**

- ✅ GUI shows all costs (vanilla + custom)
- ✅ Custom items display correctly
- ✅ All items consumed
- ✅ Chunk unlocks

**What to verify:**

- [ ] Custom items show in GUI
- [ ] Custom items consume correctly
- [ ] Vanilla items consume correctly
- [ ] Chunk actually unlocks

---

### Test 3: Oraxen-Only Unlock

**Purpose:** Verify pure custom item unlocks (no vanilla items).

**Steps:**

1. Clear inventory: `/clear @s`
2. Give ONLY Oraxen items:
   ```
   /oraxen give @s custom_sword 2
   /oraxen give @s glowing_dust 5
   ```
3. Try to unlock: `/chunklock unlock`
4. Click JUNGLE

**Expected:**

- ✅ GUI shows ONLY Oraxen items
- ✅ No vanilla items required
- ✅ Can unlock with just custom items
- ✅ Items consumed correctly

---

### Test 4: Missing Items (Negative Test)

**Purpose:** Verify all-or-nothing system prevents partial unlocks.

**Steps:**

1. Clear inventory: `/clear @s`
2. Give incomplete items:
   ```
   /give @s oak_log 16
   /give @s apple 4
   (Do NOT give amethyst)
   ```
3. Try to unlock: `/chunklock unlock`
4. Attempt to click FOREST

**Expected:**

- ✅ Cannot unlock (missing custom item)
- ✅ Error message: "You don't have all required items"
- ✅ Items NOT consumed
- ✅ All-or-nothing system enforced

---

### Test 5: GUI Display

**Purpose:** Verify visual display of custom items.

**Steps:**

1. Open unlock GUI: `/chunklock unlock`
2. Visually inspect the display

**What to verify:**

- [ ] Oraxen item names display correctly
- [ ] Item amounts show
- [ ] Colors/formatting looks good
- [ ] All items listed (vanilla + custom)
- [ ] Icons/textures display (if configured)

---

### Test 6: Hologram Display

**Purpose:** Verify holograms show custom item costs.

**Steps:**

1. Move to edge of unlocked chunk
2. Look for holograms at borders

**Expected text:**

```
═══════════════════
Unlock: FOREST
━━━━━━━━━━━━━━━━━━
Cost: OAK_LOG x16
       APPLE x4
       amethyst x1
```

**What to verify:**

- [ ] Holograms appear
- [ ] Show chunk info
- [ ] Display custom item costs
- [ ] Update correctly

---

## 🐛 Troubleshooting

### Issue: Oraxen Items Not Recognized

**Symptoms:**

- Custom items don't show in GUI
- Cannot unlock even with items
- Console error: "Custom item not found"

**Solutions:**

1. Check item names match exactly: `/oraxen list`
2. Verify YAML indentation (2 spaces, no tabs)
3. Confirm Oraxen is loaded: `/plugins`
4. Reload config: `/chunklock reload`
5. Check console for specific errors

**Example:**

```yaml
# ❌ Wrong - item name doesn't exist
- plugin: oraxen
  item: nonexistent_gem
  amount: 1

# ✅ Correct - matches /oraxen list
- plugin: oraxen
  item: amethyst
  amount: 1
```

---

### Issue: Items Show But Don't Consume

**Symptoms:**

- GUI shows items correctly
- Click to unlock
- But items still in inventory

**Solutions:**

1. Check all-or-nothing logic is working
2. Verify amounts are exactly as configured
3. Check for inventory limit issues
4. Review console errors
5. Reload plugin: `/plugman reload Chunklock`

---

### Issue: GUI Shows Nothing

**Symptoms:**

- `/chunklock unlock` opens empty GUI
- No items or costs displayed

**Solutions:**

1. Verify config has biome-unlocks section
2. Check YAML syntax (indentation, colons)
3. Reload config: `/chunklock reload`
4. Check console for parsing errors
5. Verify biome names are valid Minecraft biomes

---

### Issue: Wrong Item Type in Config

**Example problem:**

```yaml
- plugin: oraxen
  item: mispelled_item
  amount: 1
```

**Expected behavior:**

- Console warning: "Custom item not found: mispelled_item"
- Plugin handles gracefully
- No crash
- Biome not available for unlock

**Fix:**

Correct the item name to match `/oraxen list` output.

---

### Issue: Zero or Negative Amounts

**Example problem:**

```yaml
- plugin: oraxen
  item: amethyst
  amount: 0  # or -5
```

**Expected behavior:**

- Validation error on config load
- Config rejected or item ignored
- Clear error message in console

**Fix:**

Use positive integers only (1, 2, 3, etc.).

---

## 💡 Customization Ideas

### Option 1: Make Oraxen Items Rarer

Reduce amounts across the board for greater challenge:

```yaml
FOREST:
  vanilla:
    OAK_LOG: 16
    APPLE: 4
  custom:
    - plugin: oraxen
      item: amethyst
      amount: 1  # Could increase to 3-5 for rarity
```

---

### Option 2: Focus on Custom Items

Reduce vanilla requirements, increase custom gem costs:

```yaml
OLD_GROWTH_SPRUCE_TAIGA:
  vanilla:
    SPRUCE_LOG: 16  # Reduced from 32
    PODZOL: 8       # Reduced from 16
  custom:
    - plugin: oraxen
      item: orax
      amount: 5     # Increased from 3
    - plugin: oraxen
      item: ruby
      amount: 5     # Increased from 3
```

---

### Option 3: Create Oraxen-Only Challenge Biomes

For exclusive, gem-focused unlocks:

```yaml
MYSTIC_CRYSTAL_CAVERN:
  custom:
    - plugin: oraxen
      item: orax
      amount: 5
    - plugin: oraxen
      item: amethyst
      amount: 10
    - plugin: oraxen
      item: ruby
      amount: 10
```

---

## 📊 Testing Checklist

**Fill in after testing:**

```
✅/⚠️/❌ = Success / Partial / Failed

Setup:
  - Plugin Loading: ✅ ⚠️ ❌
  - Configuration: ✅ ⚠️ ❌

Basic Functionality:
  - Vanilla Items (Baseline): ✅ ⚠️ ❌
  - Mixed Items: ✅ ⚠️ ❌
  - Oraxen-Only: ✅ ⚠️ ❌

Validation:
  - All-or-Nothing System: ✅ ⚠️ ❌
  - Missing Item Prevention: ✅ ⚠️ ❌

Display:
  - GUI Display: ✅ ⚠️ ❌
  - Holograms: ✅ ⚠️ ❌

Error Handling:
  - Graceful Failures: ✅ ⚠️ ❌
  - Clear Error Messages: ✅ ⚠️ ❌
```

---

## 📞 Debug Commands

### Check Plugin Status

```
/chunklock status
```

### Check Available Biomes

```
/chunklock unlock
```

### Check Oraxen Integration

```
/oraxen list                    # See all available items
/oraxen give @s <item_id> 1    # Test receiving items
```

### Reload After Config Changes

```
/chunklock reload
```

### Check Console for Errors

Watch server console during tests for:

- `[ERROR]` messages
- Stack traces
- Custom item related messages

---

## ✅ Success Criteria

### Full Success (Ready for Production)

- All tests pass ✅
- No errors or exceptions
- All items work as expected
- GUI displays correctly
- Holograms function properly

### Partial Success (Needs Fixes)

- Some features work ✅
- Some features fail ⚠️
- Issues documented
- Known limitations listed

### Failed (Not Ready)

- Multiple failures
- Crashes or exceptions
- Major functionality broken

---

## 📚 Related Documentation

- **[Biome Unlocks Guide](Biome-Unlocks-Guide.md)** - Complete biome configuration reference
- **[Integration Testing Guide](INTEGRATION-TESTING-GUIDE.md)** - MMOItems + other custom item plugins
- **[Configuration Reference](wiki/Configuration-Reference.md)** - Full config documentation
- **[Troubleshooting](wiki/Troubleshooting.md)** - Common issues and solutions

---

## 🎓 Best Practices

1. **Start simple** - Test with one custom item before building complex systems
2. **Use /oraxen list** - Always verify item names before adding to config
3. **Test incrementally** - Add one biome at a time, test, then add more
4. **Document your gems** - Add YAML comments explaining rarity levels
5. **Balance progression** - Ensure difficulty scales appropriately
6. **Keep backups** - Always backup config before major changes
7. **Test with players** - Real player testing reveals balance issues

---

**Ready to enhance your world progression with custom Oraxen items! 💎✨**
