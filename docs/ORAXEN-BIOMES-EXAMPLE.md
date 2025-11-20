# 💎 Oraxen Integration - Example Biome Configuration

**Purpose:** Example of how to integrate Oraxen custom items into Chunklock biomes  
**Custom Items:** Amethyst, Ruby, Onyx, Orax (from Oraxen items.yml)  
**Biomes:** 7 Basic Overworld Biomes with progressive difficulty

---

## 🎯 Integration Strategy

### Item Rarity Levels:
- **Common Gems** (Abundant): Amethyst, Onyx - lower amounts, easier biomes
- **Rare Gems** (Scarce): Ruby - moderate amounts, medium biomes
- **Epic Gems** (Very Rare): Orax - high amounts/low counts, difficult biomes

### Progression:
1. **PLAINS** - Easiest, no custom items (baseline)
2. **FOREST** - Adds Amethyst (common gem)
3. **BIRCH_FOREST** - Adds Ruby (rare gem)
4. **DARK_FOREST** - Mixed gems (complexity)
5. **OLD_GROWTH_BIRCH_FOREST** - Multiple gems + high vanilla cost
6. **OLD_GROWTH_PINE_TAIGA** - Rare + epic gems
7. **OLD_GROWTH_SPRUCE_TAIGA** - Maximum difficulty, Orax (epic)

---

## 📝 YAML Configuration

### PLAINS (No Custom Items - Baseline)
```yaml
PLAINS:
  WHEAT: 8
  HAY_BLOCK: 2
```
**Difficulty:** ⭐ (Easiest - reference biome)

---

### FOREST (Easy - Amethyst Introduction)
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
**Cost:** 16 Oak Log + 4 Apple + 1 Amethyst

---

### BIRCH_FOREST (Medium - Ruby Addition)
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
**Cost:** 16 Birch Log + 4 Mushroom Stew + 1 Ruby

---

### DARK_FOREST (Medium-Hard - Multiple Gems)
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
**Cost:** 16 Dark Oak Log + 4 Dark Oak Sapling + 2 Amethyst + 1 Ruby

---

### OLD_GROWTH_BIRCH_FOREST (Hard - High Vanilla + Gems)
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
**Cost:** 24 Birch Log + 1 Golden Apple + 3 Amethyst + 2 Onyx

---

### OLD_GROWTH_PINE_TAIGA (Very Hard - Mixed Rarity)
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
**Cost:** 24 Spruce Log + 16 Mossy Cobblestone + 2 Ruby + 3 Onyx + 2 Amethyst

---

### OLD_GROWTH_SPRUCE_TAIGA (Maximum - Epic Gems + High Vanilla)
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
**Cost:** 32 Spruce Log + 16 Podzol + 3 Orax + 3 Ruby + 2 Onyx

---

## 📋 Full Configuration Block

Copy this entire block into your `config.yml` under the `biome-unlocks:` section:

```yaml
    # === BASIC OVERWORLD BIOMES WITH ORAXEN INTEGRATION ===
    PLAINS:
      WHEAT: 8
      HAY_BLOCK: 2
    
    FOREST:
      vanilla:
        OAK_LOG: 16
        APPLE: 4
      custom:
        - plugin: oraxen
          item: amethyst
          amount: 1
    
    BIRCH_FOREST:
      vanilla:
        BIRCH_LOG: 16
        MUSHROOM_STEW: 4
      custom:
        - plugin: oraxen
          item: ruby
          amount: 1
    
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

## 🎮 Gameplay Progression

### Early Game (Starting Biomes)
- **PLAINS** - Tutorial biome, pure vanilla
- **FOREST** - Easy intro to custom items (1 Amethyst)

### Mid Game (Intermediate Biomes)
- **BIRCH_FOREST** - Meet rare gems (1 Ruby)
- **DARK_FOREST** - First multi-gem requirement (2 Amethyst + 1 Ruby)

### Late Game (Hard Biomes)
- **OLD_GROWTH_BIRCH_FOREST** - High vanilla cost + multiple gems (3 Amethyst + 2 Onyx)
- **OLD_GROWTH_PINE_TAIGA** - Complex multi-gem mix (2 Ruby + 3 Onyx + 2 Amethyst)

### End Game (Maximum Difficulty)
- **OLD_GROWTH_SPRUCE_TAIGA** - Legendary tier with epic Orax gem (3 Orax + 3 Ruby + 2 Onyx + 32 Spruce Log)

---

## 💡 Customization Ideas

### Option 1: Make Oraxen Items Rarer
Reduce amounts across the board:
```yaml
FOREST:
  vanilla:
    OAK_LOG: 16
    APPLE: 4
  custom:
    - plugin: oraxen
      item: amethyst
      amount: 1  # Could reduce to 1 or increase to 2
```

### Option 2: Make Vanilla Items Less Important
Focus more on custom gems:
```yaml
OLD_GROWTH_SPRUCE_TAIGA:
  vanilla:
    SPRUCE_LOG: 16  # Reduced from 32
    PODZOL: 8       # Reduced from 16
  custom:
    - plugin: oraxen
      item: orax
      amount: 5     # Increased
    - plugin: oraxen
      item: ruby
      amount: 5     # Increased
```

### Option 3: Add Economy Integration
If using Vault economy mode, you could supplement costs:
```yaml
FOREST:
  vanilla:
    OAK_LOG: 16
    APPLE: 4
  custom:
    - plugin: oraxen
      item: amethyst
      amount: 1
  economy_cost: 100  # $100 in addition to items
```

### Option 4: Create Oraxen-Only Biomes
For exclusive, gem-focused unlocks:
```yaml
MYSTIC_CRYSTAL_CAVERN:  # Hypothetical custom biome
  custom:
    - plugin: oraxen
      item: orax
      amount: 5
    - plugin: oraxen
      item: amethyst
      amount: 10
```

---

## 🧪 Testing This Configuration

### Step 1: Add to config.yml
Paste the full YAML block above into your `config.yml` under `biome-unlocks:`

### Step 2: Give Yourself Items
```
/give @s spruce_log 32
/give @s podzol 16
/oraxen give @s orax 3
/oraxen give @s ruby 3
/oraxen give @s onyx 2
```

### Step 3: Test the Unlock
```
/chunklock unlock
(Click OLD_GROWTH_SPRUCE_TAIGA)
```

### Step 4: Verify Items Consumed
- Check inventory - all items should be gone
- Chunk should be unlocked
- Console should show success

---

## 📊 Item Distribution Summary

| Biome | Vanilla Items | Amethyst | Ruby | Onyx | Orax | Difficulty |
|-------|--------------|----------|------|------|------|------------|
| PLAINS | ✅ | - | - | - | - | ⭐ |
| FOREST | ✅ | 1 | - | - | - | ⭐⭐ |
| BIRCH_FOREST | ✅ | - | 1 | - | - | ⭐⭐⭐ |
| DARK_FOREST | ✅ | 2 | 1 | - | - | ⭐⭐⭐⭐ |
| OLD_GROWTH_BIRCH_FOREST | ✅ | 3 | - | 2 | - | ⭐⭐⭐⭐⭐ |
| OLD_GROWTH_PINE_TAIGA | ✅ | 2 | 2 | 3 | - | ⭐⭐⭐⭐⭐⭐ |
| OLD_GROWTH_SPRUCE_TAIGA | ✅ | - | 3 | 2 | 3 | ⭐⭐⭐⭐⭐⭐⭐ |

---

## 🎨 Visual Progression

```
PLAINS                     (Pure Vanilla - Tutorial)
    ↓
FOREST                     (+ Amethyst - Easy)
    ↓
BIRCH_FOREST               (+ Ruby - Medium)
    ↓
DARK_FOREST                (+ 2x Amethyst + Ruby - Medium-Hard)
    ↓
OLD_GROWTH_BIRCH_FOREST    (+ 3x Amethyst + 2x Onyx - Hard)
    ↓
OLD_GROWTH_PINE_TAIGA      (+ 2x Amethyst + 2x Ruby + 3x Onyx - Very Hard)
    ↓
OLD_GROWTH_SPRUCE_TAIGA    (+ 3x Orax + 3x Ruby + 2x Onyx - Legendary)
```

---

## ✅ Next Steps

1. **Copy the YAML block** into your config.yml
2. **Verify indentation** (2 spaces, not tabs)
3. **Reload Chunklock:** `/chunklock reload`
4. **Test with the guide** from ORAXEN-INTEGRATION-TEST.md
5. **Adjust amounts** based on testing and difficulty feel
6. **Document your changes** for other admins

---

## 🎯 Pro Tips

- **Test from easy to hard** - Start with FOREST, work up to SPRUCE_TAIGA
- **Use `/give` to test quickly** - No need to farm items manually
- **Check console for errors** - Any item name typos will show up
- **Screenshot the GUI** - Custom items will display with their textures
- **Track which gem is rarest** - Adjust amounts to make Orax feel truly epic

---

**Ready to enhance your world progression with custom gems! 💎✨**
