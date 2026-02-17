# Biome Unlocks Configuration Guide

Complete reference for configuring biome unlock costs in Chunklock. This guide covers both vanilla (materials-only) and custom item integration (MMOItems, Oraxen).

---

## 📋 Quick Start

**New to biome unlocks?** Start here:

- Jump to [Minimal Config](#-getting-started) to get running in 2 minutes
- Use [Format Selector](#-format-selector) to choose the right approach
- Check [Common Materials](#-common-materials--progression) for balanced values

**Ready to customize?** These sections have what you need:

- [Format Specifications](#format-specifications) - Detailed syntax reference
- [Real-World Examples](#real-world-examples) - Complete progression systems
- [Custom Items](#custom-item-configuration) - MMOItems & Oraxen integration
- [Troubleshooting](#validation-and-troubleshooting) - Fix common issues

---

## 🔧 Format Selector

Chunklock supports **two formats** with automatic detection. Choose based on your server setup:

### **Use FLAT FORMAT if:**

- ✅ You only need vanilla Minecraft items
- ✅ You want simple, clean configuration
- ✅ You prefer readability over features
- ✅ You don't have MMOItems or Oraxen

### **Use STRUCTURED FORMAT if:**

- ✅ You need custom items (MMOItems/Oraxen)
- ✅ You want future flexibility
- ✅ You're building a complex progression system
- ✅ You mix multiple item sources

**Format is detected automatically** - no need to declare it!

---

## 🚀 Getting Started

### Minimal Config (Start Here)

```yaml
biome-unlocks:
  PLAINS:
    WHEAT: 8
    HAY_BLOCK: 2
  FOREST:
    OAK_LOG: 16
    APPLE: 4
```

### With Economy

```yaml
economy:
  type: "materials"

biome-unlocks:
  PLAINS:
    WHEAT: 8
    HAY_BLOCK: 2
  FOREST:
    OAK_LOG: 16
    APPLE: 4
```

### With Custom Items

```yaml
biome-unlocks:
  MYSTICAL_FOREST:
    vanilla:
      OAK_LOG: 16
      APPLE: 4
    custom:
      - plugin: mmoitems
        type: MATERIAL
        item: forest_essence
        amount: 2
```

---

## Format Specifications

### Flat Format (Legacy - Backward Compatible)

Use this format when you only need vanilla Minecraft items. It's simpler and more concise.

**Syntax:**

```yaml
biome-unlocks:
  BIOME_NAME:
    MATERIAL_NAME: amount
    MATERIAL_NAME: amount
```

**Single Line (Compact):**

```yaml
biome-unlocks:
  PLAINS: { WHEAT: 8, HAY_BLOCK: 2 }
  FOREST: { OAK_LOG: 16, APPLE: 4 }
```

**Multi-Line (Readable):**

```yaml
biome-unlocks:
  PLAINS:
    WHEAT: 8
    HAY_BLOCK: 2

  FOREST:
    OAK_LOG: 16
    APPLE: 4
```

**Advantages:**

- Simple, clean syntax
- Perfect for vanilla-only servers
- Backward compatible with existing configs
- Easy to read and maintain

**When to use:**

- No custom item plugins (MMOItems, Oraxen, etc.)
- Quick configuration
- Standard survival servers

---

### Structured Format (New - Custom Item Support)

Use this format when you need to mix vanilla items with custom items from plugins like MMOItems or Oraxen.

**Syntax:**

```yaml
biome-unlocks:
  BIOME_NAME:
    vanilla:
      MATERIAL_NAME: amount
      MATERIAL_NAME: amount
    custom:
      - plugin: plugin_name
        type: ITEM_TYPE          # Only for MMOItems
        item: item_name
        amount: quantity
      - plugin: plugin_name
        item: item_name
        amount: quantity
```

**Basic Example:**

```yaml
biome-unlocks:
  MYSTICAL_FOREST:
    vanilla:
      OAK_LOG: 16
      APPLE: 4
    custom:
      - plugin: mmoitems
        type: MATERIAL
        item: forest_essence
        amount: 2
```

**Advantages:**

- Mix vanilla and custom items
- Future-proof configuration
- Flexible item support
- Can use items from multiple plugins
- Progressive gameplay potential

**When to use:**

- Servers with MMOItems or Oraxen
- Custom progression systems
- Mixed vanilla + plugin gameplay

---

## 🎯 Common Materials & Progression

### Starting Biomes (Tier 1)

| Biome  | Material  | Amount | Reason                |
| ------ | --------- | ------ | --------------------- |
| PLAINS | WHEAT     | 8      | Easy to collect       |
| PLAINS | HAY_BLOCK | 2      | Crafted from wheat    |
| FOREST | OAK_LOG   | 16     | Common, requires work |
| FOREST | APPLE     | 4      | Drops from leaves     |

### Mid-Tier Biomes (Tier 2)

| Biome  | Material    | Amount | Reason               |
| ------ | ----------- | ------ | -------------------- |
| DESERT | SAND        | 32     | Common but bulk      |
| JUNGLE | COCOA_BEANS | 16     | Requires exploration |
| OCEAN  | PRISMARINE  | 24     | Requires diving      |
| SWAMP  | SLIMEBALL   | 12     | Dangerous to collect |

### Advanced Biomes (Tier 3)

| Biome      | Material    | Amount | Reason               |
| ---------- | ----------- | ------ | -------------------- |
| BADLANDS   | TERRACOTTA  | 64     | Resource heavy       |
| MOUNTAINS  | DIAMOND     | 8      | Rare, valuable       |
| DEEP_OCEAN | SEA_LANTERN | 32     | Rare structure block |

---

## 🔄 Format Conversion

### Converting Flat → Structured

**Before:**

```yaml
FOREST:
  OAK_LOG: 16
  APPLE: 4
```

**After:**

```yaml
FOREST:
  vanilla:
    OAK_LOG: 16
    APPLE: 4
  custom: [] # or remove this line entirely
```

**Or with custom items:**

```yaml
FOREST:
  vanilla:
    OAK_LOG: 16
    APPLE: 4
  custom:
    - plugin: mmoitems
      type: MATERIAL
      item: forest_essence
      amount: 2
```

**Steps:**

1. Add a `vanilla:` key at the same indentation level as the materials
2. Move all existing materials under `vanilla:`
3. Add a `custom:` key with a list of custom items
4. Each custom item must have: `plugin`, `item`, `amount`, and (for MMOItems) `type`

---

## Custom Item Configuration

### MMOItems Format

```yaml
- plugin: mmoitems
  type: MATERIAL # or CONSUMABLE, ARMOR, WEAPON, etc. (REQUIRED)
  item: item_name # From MMOItems config
  amount: quantity # Total needed
```

**Finding MMOItems item names:**

- Check `/plugins/MMOItems/items/` directory
- Use `/mi info` command in-game
- Format: Usually `lowercase_with_underscores`
- The `type` field is **required** for MMOItems

**Example:**

```yaml
custom:
  - plugin: mmoitems
    type: MATERIAL
    item: diamond_ingot
    amount: 3
  - plugin: mmoitems
    type: CONSUMABLE
    item: forest_potion
    amount: 1
```

---

### Oraxen Format

```yaml
- plugin: oraxen
  item: item_id # Only 'item' needed, no type
  amount: quantity # Total needed
```

**Finding Oraxen item IDs:**

- Check your Oraxen config files
- Use `/oa info` command in-game
- Format: Usually `lowercase_with_underscores`
- No `type` field needed for Oraxen

**Example:**

```yaml
custom:
  - plugin: oraxen
    item: mythic_sword
    amount: 1
  - plugin: oraxen
    item: quantum_crystal
    amount: 2
```

---

## Real-World Examples

### Example 1: Simple Vanilla Server

```yaml
biome-unlocks:
  PLAINS:
    WHEAT: 8
    CARROTS: 4

  FOREST:
    OAK_LOG: 16
    APPLE: 4

  DESERT:
    SAND: 32
    CACTUS: 8

  JUNGLE:
    COCOA_BEANS: 16
    MELON_SLICE: 12

  OCEAN:
    PRISMARINE: 24
    SEA_LANTERN: 6
```

---

### Example 2: Mixed Vanilla + MMOItems

```yaml
biome-unlocks:
  MYSTICAL_FOREST:
    vanilla:
      OAK_LOG: 16
      APPLE: 4
    custom:
      - plugin: mmoitems
        type: MATERIAL
        item: forest_essence
        amount: 2
      - plugin: mmoitems
        type: MATERIAL
        item: ancient_wood
        amount: 1

  DRAGON_LAIR:
    vanilla:
      OBSIDIAN: 64
      PURPLE_CONCRETE: 32
    custom:
      - plugin: mmoitems
        type: MATERIAL
        item: dragon_scale
        amount: 5
      - plugin: mmoitems
        type: MATERIAL
        item: dragon_blood
        amount: 3
```

---

### Example 3: Mixed Vanilla + Oraxen

```yaml
biome-unlocks:
  TECH_DIMENSION:
    vanilla:
      DIAMOND: 16
      REDSTONE: 64
    custom:
      - plugin: oraxen
        item: advanced_circuit
        amount: 8
      - plugin: oraxen
        item: quantum_crystal
        amount: 2
```

---

### Example 4: Fully Custom (Mixed Plugins)

```yaml
biome-unlocks:
  LEGENDARY_REALM:
    vanilla:
      NETHERITE_BLOCK: 4
      AMETHYST_BLOCK: 16
    custom:
      - plugin: mmoitems
        type: MATERIAL
        item: legendary_ore
        amount: 3
      - plugin: mmoitems
        type: CONSUMABLE
        item: essence_of_power
        amount: 5
      - plugin: oraxen
        item: mythic_rune
        amount: 2
      - plugin: oraxen
        item: celestial_dust
        amount: 10
```

---

### Example 5: Complete Progression System

A tiered system from starter to endgame:

```yaml
biome-unlocks:
  # Tier 1 - Starting biomes (easy materials)
  PLAINS:
    WHEAT: 8
    HAY_BLOCK: 2

  FOREST:
    OAK_LOG: 32
    APPLE: 8

  # Tier 2 - Resource gathering (moderate difficulty)
  DESERT:
    SAND: 32
    CACTUS: 8

  JUNGLE:
    COCOA_BEANS: 16
    MELON_SLICE: 12

  # Tier 3 - Mining (requires exploration)
  MOUNTAINS:
    vanilla:
      STONE: 64
      COAL_ORE: 16
      IRON_ORE: 8

  # Tier 4 - Advanced (rare materials + custom items)
  DEEP_DARK:
    vanilla:
      SCULK_BLOCK: 32
      SCULK_CATALYST: 4
    custom:
      - plugin: mmoitems
        type: MATERIAL
        item: ancient_essence
        amount: 5

  # Tier 5 - Endgame (heavy costs)
  NETHER_WASTES:
    vanilla:
      NETHERITE_SCRAP: 12
      ANCIENT_DEBRIS: 8
    custom:
      - plugin: mmoitems
        type: MATERIAL
        item: infernal_core
        amount: 3
```

---

## Important Concepts

### All-or-Nothing System

The biome unlocks use an **all-or-nothing system**:

- Players must have **ALL items** required for the biome
- They must have the **EXACT amounts** specified
- All items are consumed together when unlocking

**Example:** If PLAINS requires `WHEAT: 8` and `HAY_BLOCK: 2`:

- ✅ Player has 8 wheat + 2 hay blocks → Can unlock
- ❌ Player has 8 wheat + 1 hay block → Cannot unlock
- ✅ Player has 20 wheat + 10 hay blocks → Can unlock (extras are kept!)

---

### Vanilla Item Names

Use exact Minecraft material names (case-sensitive):

**Common Examples:**

- `WHEAT`, `DIAMOND`, `EMERALD`
- `OAK_LOG`, `DARK_OAK_LOG`, `BIRCH_LOG`
- `HAY_BLOCK`, `GRASS_BLOCK`, `DIRT`
- `WHEAT`, `CARROT`, `POTATO`, `COCOA_BEANS`
- `MELON_SLICE`, `APPLE`, `GOLDEN_APPLE`

**Find all material names:**

- Check Spigot documentation: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Material.html
- Use `/material list` in game (if your server has it)

---

## Validation and Troubleshooting

### Validation Checklist

Before deploying your config:

- [ ] **Biome Names**: All are valid Minecraft biomes
  - Examples: PLAINS, FOREST, DESERT, JUNGLE, OCEAN
  - Check: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/block/Biome.html

- [ ] **Vanilla Materials**: All exist in Minecraft
  - Examples: WHEAT, OAK_LOG, DIAMOND, COBBLESTONE
  - Check: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Material.html

- [ ] **Custom Items**: Exist in their respective plugins
  - Verify item is in `/plugins/PluginName/` config
  - Use in-game commands to confirm: `/mi info`, `/oa info`

- [ ] **Quantities**: All are positive numbers
  - Bad: `-5`, `0`, `abc`
  - Good: `1`, `8`, `64`

- [ ] **YAML Formatting**:
  - Indentation: 2 or 4 spaces (NOT tabs)
  - No trailing spaces
  - Colons followed by space: `key: value`

- [ ] **No Duplicates**: Each biome defined only once
  - Bad: PLAINS twice
  - Good: Each biome once

---

### Common Issues & Solutions

| Problem              | Solution                                                       |
| -------------------- | -------------------------------------------------------------- |
| Biome not recognized | Use exact name: `PLAINS` not `Plains`                          |
| Material not found   | Check spelling: `OAK_LOG` not `OAK` or `WOOD`                  |
| Config won't load    | Check YAML indentation (no tabs!)                              |
| Custom item fails    | Verify plugin is installed and item exists                     |
| Players can't unlock | Check amounts in config match requirements                     |
| Format detection     | Ensure keys are exact: `vanilla:`, `custom:`, `plugin:`, etc.  |

---

### Detailed Error Messages

**Issue: Biome not recognized**

```
ERROR: Unknown biome: MYSTICAL_FOREST
```

- Use exact Minecraft biome names: `PLAINS`, `FOREST`, `DESERT`, etc.
- Check available biomes: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/block/Biome.html

**Issue: Material not recognized**

```
ERROR: Unknown material: WOOD
```

- Use full material name: `OAK_LOG` instead of `WOOD`
- Check Material enum: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Material.html

**Issue: Custom item not found**

```
ERROR: Custom item not found: mmoitems/diamond_ingot
```

- Verify the custom item exists in the plugin's config
- Check exact item name (case-sensitive)
- Ensure the plugin is installed and enabled

**Issue: Format detection failed**

```
WARN: Could not determine biome format
```

- Check YAML indentation (2 or 4 spaces, not tabs)
- Ensure keys are exact: `vanilla:`, `custom:`, `plugin:`, `item:`, `amount:`

---

## Performance Considerations

1. **Item Checking**: Checking if players have items is fast (milliseconds)
2. **Large Lists**: You can safely have 50+ biomes configured
3. **Custom Items**: Each custom item adds slight overhead; usually negligible
4. **Caching**: Format is cached on load; no runtime parsing overhead

---

## Best Practices

1. **Keep Costs Balanced**: Harder biomes should require more items
2. **Progressive Costs**: Costs should increase with world expansion
3. **Mix Vanilla + Custom**: Use vanilla items for base costs, custom for progression
4. **Document Changes**: Add YAML comments explaining biome difficulty/strategy
5. **Test Thoroughly**: Test with actual players before deploying
6. **Version Control**: Keep backups of your config files

**Example with comments:**

```yaml
biome-unlocks:
  # Starting area - intentionally cheap
  PLAINS:
    WHEAT: 8
    HAY_BLOCK: 2

  # Mid-game resource sink - requires established farm
  JUNGLE:
    COCOA_BEANS: 32  # Forces player to find jungle first
    MELON_SLICE: 16  # Secondary farming requirement
```

---

## Support and Questions

For issues or questions:

- Check the [Configuration Reference](wiki/Configuration-Reference.md) wiki page
- Review the inline documentation in `config.yml`
- Test format conversion on a development server first
- See [Troubleshooting](wiki/Troubleshooting.md) for common issues

---

## Related Documentation

- **[MySQL Setup Guide](MySQL-Setup-Guide.md)** - Configure external database storage
- **[Quick Start Deploy](QUICK-START-DEPLOY.md)** - Get your server running quickly
- **[Integration Testing Guide](INTEGRATION-TESTING-GUIDE.md)** - Test with MMOItems/Oraxen
