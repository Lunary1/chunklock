# Biome Unlocks Format - Quick Reference

## 📋 Format Selector

**Use FLAT FORMAT if:**

- ✅ You only need vanilla Minecraft items
- ✅ You want simple, clean configuration
- ✅ You prefer readability over features
- ✅ You don't have MMOItems or Oraxen

**Use STRUCTURED FORMAT if:**

- ✅ You need custom items (MMOItems/Oraxen)
- ✅ You want future flexibility
- ✅ You're building a complex progression system
- ✅ You mix multiple item sources

---

## 🔧 Quick Configuration Examples

### FLAT FORMAT (Vanilla Only)

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

**✨ Format is detected automatically!**

---

### STRUCTURED FORMAT (Vanilla + Custom)

**Basic Structure:**

```yaml
biome-unlocks:
  BIOME_NAME:
    vanilla:
      MATERIAL_1: amount
      MATERIAL_2: amount
    custom:
      - plugin: mmoitems
        type: MATERIAL
        item: custom_item_1
        amount: quantity
      - plugin: oraxen
        item: custom_item_2
        amount: quantity
```

**Practical Example:**

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

## 🎯 Common Materials & Amounts

### Starting Biomes

| Biome  | Material  | Amount | Reason                |
| ------ | --------- | ------ | --------------------- |
| PLAINS | WHEAT     | 8      | Easy to collect       |
| PLAINS | HAY_BLOCK | 2      | Crafted from wheat    |
| FOREST | OAK_LOG   | 16     | Common, requires work |
| FOREST | APPLE     | 4      | Drops from leaves     |

### Mid-Tier Biomes

| Biome  | Material    | Amount | Reason               |
| ------ | ----------- | ------ | -------------------- |
| DESERT | SAND        | 32     | Common but bulk      |
| JUNGLE | COCOA_BEANS | 16     | Requires exploration |
| OCEAN  | PRISMARINE  | 24     | Requires diving      |
| SWAMP  | SLIMEBALL   | 12     | Dangerous to collect |

### Advanced Biomes

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

---

## ⚙️ Plugin-Specific Item Formats

### MMOItems

```yaml
- plugin: mmoitems
  type: MATERIAL # Type is REQUIRED for MMOItems
  item: item_name # From MMOItems config
  amount: quantity # Total needed
```

**Finding MMOItems item names:**

- Check `/plugins/MMOItems/items/`
- Use `/mi info` command
- Format: Usually lowercase_with_underscores

### Oraxen

```yaml
- plugin: oraxen
  item: item_id # Only 'item' needed, no type
  amount: quantity # Total needed
```

**Finding Oraxen item IDs:**

- Check ItemsAdder data files
- Use `/oa info` command
- Format: Usually lowercase_with_underscores

---

## ✅ Validation Checklist

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

## 🐛 Troubleshooting

| Problem              | Solution                                   |
| -------------------- | ------------------------------------------ |
| Biome not recognized | Use exact name: PLAINS not Plains          |
| Material not found   | Check spelling: OAK_LOG not OAK or WOOD    |
| Config won't load    | Check YAML indentation (no tabs!)          |
| Custom item fails    | Verify plugin is installed and item exists |
| Players can't unlock | Check amounts in config match requirements |

---

## 📊 Progression Example

A complete progression system:

```yaml
biome-unlocks:
  # Tier 1 - Starting biomes
  PLAINS:
    WHEAT: 8
    HAY_BLOCK: 2

  # Tier 2 - Resource gathering
  FOREST:
    vanilla:
      OAK_LOG: 32
      APPLE: 8

  # Tier 3 - Mining
  MOUNTAINS:
    vanilla:
      STONE: 64
      COAL_ORE: 16

  # Tier 4 - Advanced
  DEEP_DARK:
    vanilla:
      SCULK_BLOCK: 32
      SCULK_CATALYST: 4
    custom:
      - plugin: mmoitems
        type: MATERIAL
        item: ancient_essence
        amount: 5
```

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

## 📚 Learn More

- **Full Guide**: See `biome-unlocks-format-guide.md`
- **Configuration Cleanup**: See `configuration-cleanup-summary.md`
- **Wiki**: Check Configuration Reference in wiki/
- **Support**: Review the docs/ directory for additional help
