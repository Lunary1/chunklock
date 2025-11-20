# Configuration Reference - Biome Unlocks

## Overview

Biome unlocks define what items and quantities players need to unlock chunks in different biomes. Chunklock supports two configuration formats that can be freely mixed:

- **Flat Format**: Simple, vanilla-only items
- **Structured Format**: Vanilla + custom items (from plugins)

**Format is automatically detected** based on structure - no manual configuration needed.

## Quick Comparison

| Feature       | Flat Format      | Structured Format         |
| ------------- | ---------------- | ------------------------- |
| Vanilla items | ✅ Yes           | ✅ Yes                    |
| Custom items  | ❌ No            | ✅ Yes (MMOItems, Oraxen) |
| Complexity    | ⭐ Simple        | ⭐⭐ Moderate             |
| Readability   | ⭐⭐⭐ Excellent | ⭐⭐ Good                 |
| Best for      | Vanilla servers  | Plugin-rich servers       |

---

## Flat Format

Use for vanilla Minecraft items only.

### Single-Line Syntax

```yaml
biome-unlocks:
  PLAINS: { WHEAT: 8, HAY_BLOCK: 2 }
  FOREST: { OAK_LOG: 16, APPLE: 4 }
```

### Multi-Line Syntax

```yaml
biome-unlocks:
  PLAINS:
    WHEAT: 8
    HAY_BLOCK: 2

  FOREST:
    OAK_LOG: 16
    APPLE: 4
```

### Format Rules

- List material names and required amounts
- Material names are case-sensitive (use `OAK_LOG`, not `oak_log`)
- Amounts must be positive integers
- All items are required (all-or-nothing system)

---

## Structured Format

Use when mixing vanilla and custom items.

### Syntax

```yaml
biome-unlocks:
  BIOME_NAME:
    vanilla:
      MATERIAL_1: amount
      MATERIAL_2: amount
    custom:
      - plugin: plugin_name
        type: item_type # Only for MMOItems
        item: item_id
        amount: quantity
```

### Example

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
      - plugin: oraxen
        item: mythic_sword
        amount: 1
```

### MMOItems Items

```yaml
- plugin: mmoitems
  type: MATERIAL # MATERIAL, CONSUMABLE, ARMOR, WEAPON, etc.
  item: item_name # From MMOItems database
  amount: quantity
```

### Oraxen Items

```yaml
- plugin: oraxen
  item: item_id # From Oraxen ItemsAdder config
  amount: quantity
```

---

## Vanilla Material Names

### Common Items

```
WHEAT, CARROT, POTATO, BEETROOT_SEEDS
HAY_BLOCK, GRASS_BLOCK, DIRT, COARSE_DIRT
OAK_LOG, BIRCH_LOG, DARK_OAK_LOG, SPRUCE_LOG
STONE, COBBLESTONE, DIORITE, GRANITE, ANDESITE
DIAMOND, EMERALD, GOLD_INGOT, IRON_INGOT
SAND, RED_SAND, GRAVEL, SOUL_SAND
APPLE, MELON_SLICE, COCOA_BEANS, PUMPKIN
```

### How to Find All Material Names

1. Check Spigot docs: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Material.html
2. Use `/material list` command (if available on server)
3. Check material names are UPPERCASE with UNDERSCORES

---

## Biome Names

### Valid Biome Names

```
PLAINS, FOREST, DESERT, JUNGLE, OCEAN
SWAMP, BADLANDS, SAVANNA, TAIGA, SNOWY_PLAINS
MOUNTAINS, DEEP_OCEAN, BEACH, RIVER, MUSHROOM_FIELDS
And more... (check full Spigot documentation)
```

### How to Find All Biome Names

1. Check Spigot docs: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/block/Biome.html
2. Use `/biome list` command (if available)
3. Biome names are UPPERCASE with UNDERSCORES

---

## Economics System Integration

Biome costs integrate with the economy system. When a player unlocks a chunk:

1. **Material Mode**: Player must have all items in inventory

   - Items are removed when chunk is unlocked
   - Works in structured AND flat formats

2. **Vault Mode**: Players pay with money
   - Base cost + per-chunk cost applied
   - Biome multipliers adjust the price
   - Independent from item requirements

---

## Configuration Examples

### Example 1: Simple Vanilla Server

```yaml
biome-unlocks:
  PLAINS:
    WHEAT: 8
    HAY_BLOCK: 2

  FOREST:
    OAK_LOG: 16
    APPLE: 4

  DESERT:
    SAND: 32
    CACTUS: 8
```

### Example 2: Vanilla + MMOItems

```yaml
biome-unlocks:
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

### Example 3: Complex Progression

```yaml
biome-unlocks:
  # Tier 1
  PLAINS:
    WHEAT: 8

  # Tier 2
  FOREST:
    vanilla:
      OAK_LOG: 16
      APPLE: 4
    custom:
      - plugin: mmoitems
        type: MATERIAL
        item: forest_essence
        amount: 2

  # Tier 3
  MOUNTAINS:
    vanilla:
      STONE: 64
      COAL_ORE: 16
    custom:
      - plugin: mmoitems
        type: MATERIAL
        item: ancient_ore
        amount: 5
```

---

## Validation & Troubleshooting

### Configuration Validation

The plugin automatically validates your configuration:

✅ **Valid:**

- All biome names exist in Minecraft
- All material names are correct
- All custom items exist in plugins
- All amounts are positive integers

❌ **Invalid:**

- Unknown biome: `MYSTICAL_DIMENSION`
- Unknown material: `WOOD` (use `OAK_LOG`)
- Missing custom item plugin
- Invalid YAML indentation

### Common Issues

**Issue:** `Unknown biome: FOREST_DARK`

- **Fix:** Check exact biome name - use `DARK_FOREST` or check documentation

**Issue:** `Unknown material: WOOD`

- **Fix:** Use full name - `OAK_LOG`, `BIRCH_LOG`, etc.

**Issue:** `Custom item not found: mmoitems/diamond_ingot`

- **Fix:** Verify item exists in MMOItems `/plugins/MMOItems/items/`

**Issue:** YAML parsing error

- **Fix:** Check indentation - use spaces, not tabs

---

## Key Concepts

### All-or-Nothing System

Players must have **ALL** items in the exact amounts specified:

- ✅ Has 8 wheat + 2 hay → Can unlock
- ❌ Has 8 wheat + 1 hay → Cannot unlock
- ✅ Has 16 wheat + 4 hay → Can unlock (extras OK!)

### Item Consumption

When a chunk is unlocked:

- All required items are removed from player inventory
- Items are consumed in the exact amounts specified
- Process is atomic (all-or-nothing)

### Format Auto-Detection

The system **automatically detects** which format is used:

```
Config biome entry
       ↓
Has "vanilla:" or "custom:" keys?
    ↙YES            ↘NO
STRUCTURED       FLAT FORMAT
FORMAT
```

No manual configuration needed - just write your config and it works!

---

## Migration Guide

### From Flat to Structured

If you want to add custom items to a flat-format biome:

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
  custom:
    - plugin: mmoitems
      type: MATERIAL
      item: forest_essence
      amount: 2
```

**Steps:**

1. Create `vanilla:` section with indentation
2. Move all materials under `vanilla:`
3. Add `custom:` section
4. List custom items with full details
5. Restart server to reload config

---

## Performance Notes

- **Format Detection**: Zero runtime cost (done at startup)
- **Item Checking**: Very fast (millisecond scale)
- **Scalability**: Supports 50+ biomes without issues
- **Custom Items**: Minimal overhead, cached after load

---

## Related Configuration

See also in `config.yml`:

- **economy** section: Currency and vault settings
- **openai-agent** section: AI-powered cost calculation
- **world-restrictions** section: World-specific settings

---

## Next Steps

1. **Create your config**: Edit `config.yml` and add biome costs
2. **Test in-game**: Verify players can see costs and unlock chunks
3. **Adjust costs**: Rebalance based on gameplay testing
4. **Add custom items**: Migrate to structured format if needed

---

## See Also

- [Full Format Guide](../docs/biome-unlocks-format-guide.md)
- [Quick Reference](../docs/biome-unlocks-quick-reference.md)
- [Configuration Cleanup Summary](../docs/configuration-cleanup-summary.md)
- [Main Configuration File](../../config.yml)
