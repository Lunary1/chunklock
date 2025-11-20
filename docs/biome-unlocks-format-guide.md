# Biome Unlocks Configuration Guide

## Overview

The biome unlocks system in Chunklock allows you to define the exact items and quantities required for players to unlock chunks in different biomes. The system supports **both flat format (vanilla items only) and structured format (vanilla + custom items)** with automatic format detection.

## Format Detection

Chunklock automatically detects which format you're using based on the structure of your configuration:

- **Structured Format**: If the biome entry contains `vanilla:` or `custom:` keys
- **Flat Format**: If the biome entry only contains material keys

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

**Example:**

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

**Example:**

```yaml
biome-unlocks:
  PLAINS:
    vanilla:
      WHEAT: 8
      HAY_BLOCK: 2
    custom:
      - plugin: mmoitems
        type: MATERIAL
        item: diamond_ingot
        amount: 3
      - plugin: oraxen
        item: mythic_sword
        amount: 1

  JUNGLE:
    vanilla:
      COCOA_BEANS: 16
      MELON_SLICE: 8
    custom:
      - plugin: mmoitems
        type: MATERIAL
        item: jungle_essence
        amount: 2
      - plugin: mmoitems
        type: CONSUMABLE
        item: jungle_potion
        amount: 1
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

## Migration Guide

### Converting Flat Format to Structured Format

If you want to add custom items to an existing flat format configuration:

**Before (Flat Format):**

```yaml
biome-unlocks:
  FOREST:
    OAK_LOG: 16
    APPLE: 4
```

**After (Structured Format):**

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

**Steps:**

1. Add a `vanilla:` key at the same indentation level as the materials
2. Move all existing materials under `vanilla:`
3. Add a `custom:` key with a list of custom items
4. Each custom item must have: `plugin`, `item`, `amount`, and (for MMOItems) `type`

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
- ❌ Player has 20 wheat + 10 hay blocks → Can unlock (extras are OK!)

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

### Custom Item Configuration

#### MMOItems Format

```yaml
- plugin: mmoitems
  type: MATERIAL # or CONSUMABLE, ARMOR, WEAPON, etc.
  item: item_name # As defined in MMOItems config
  amount: quantity
```

**Finding item names:**

- Check your MMOItems config files
- Use `/mi info` command to see item IDs
- Format: Usually lowercase with underscores (e.g., `diamond_ingot`, `forest_essence`)

#### Oraxen Format

```yaml
- plugin: oraxen
  item: item_id # The ID you defined in Oraxen config
  amount: quantity
```

**Finding item IDs:**

- Check your `itemsadder/data/items.yml` or Oraxen equivalent
- Hold the item and use `/oa info` or similar command
- Format: Usually lowercase with underscores (e.g., `mythic_sword`, `custom_bow`)

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

## Validation and Troubleshooting

### Common Issues

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

### Validation Checklist

- [ ] All biome names are valid Minecraft biomes
- [ ] All vanilla material names are correct and exist
- [ ] Custom items are defined in their respective plugins
- [ ] All amounts are positive integers
- [ ] YAML indentation is consistent (2 or 4 spaces)
- [ ] No duplicate biome entries
- [ ] `plugin:` and `item:` fields are exact matches to plugin config

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
4. **Document Changes**: Add comments explaining biome difficulty/strategy
5. **Test Thoroughly**: Test with actual players before deploying
6. **Version Control**: Keep backups of your config files

---

## Support and Questions

For issues or questions:

- Check the Configuration Reference wiki page
- Review the inline documentation in `config.yml`
- Test format conversion on a development server first
