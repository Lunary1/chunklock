# 🧪 Integration Testing Guide - Oraxen + MMOItems

**Version:** Chunklock 2.0 (Paper 1.21.10)  
**Plugins Under Test:** Oraxen 1.195.1 + MMOItems 6.10.1  
**Test Date:** November 19, 2025

---

## ✅ Pre-Testing Checklist

### Server Setup
- [ ] Paper 1.21.10 server running
- [ ] Chunklock plugin deployed (target/Chunklock.jar)
- [ ] Oraxen 1.195.1 installed
- [ ] MMOItems 6.10.1 installed
- [ ] FancyHolograms 2.4.2 installed (optional but recommended)
- [ ] Server started and all plugins loaded

### Plugin Verification
```
/plugins
Should show:
✅ Chunklock
✅ Oraxen
✅ MMOItems
✅ FancyHolograms (if installed)
```

### Config Verification
```
Location: plugins/Chunklock/config.yml
Should have:
✅ economy section
✅ biome-unlocks section (69 biomes)
✅ openai-agent section
✅ team-settings section
✅ glass-borders section
✅ holograms section
```

---

## 🎯 Testing Phases

### Phase 1: Basic Plugin Load (5 min)

**Objective:** Verify plugin loads without errors

**Steps:**
1. Check server console for errors
2. Look for initialization messages
3. Verify no RED ERROR messages

**Expected Output:**
```
[Chunklock] ✅ Plugin initialized successfully
[Chunklock] ✅ Configuration loaded
[Chunklock] ✅ All managers initialized
```

**Success Criteria:** ✅ No errors in console

---

### Phase 2: Configuration Loading (5 min)

**Objective:** Verify all config sections load correctly

**Commands to Run:**
```
/chunklock status
/chunklock help
```

**What to Look For:**
- [ ] Status command works
- [ ] Help message displays
- [ ] No config-related errors

**Success Criteria:** ✅ Commands work, no config errors

---

### Phase 3: Hologram System (10 min)

**Objective:** Verify holograms display correctly

**Commands to Run:**
```
/chunklock start
```

**What to Look For:**
- [ ] Holograms appear at chunk borders
- [ ] Holograms are readable
- [ ] Glass borders visible (if enabled)

**Success Criteria:** ✅ Holograms display correctly

---

### Phase 4: Vanilla Biome Unlocking (10 min)

**Objective:** Test with vanilla items first (baseline)

**Test Cases:**

**Test 4.1: PLAINS Unlock (Simple)**
```
Required: WHEAT: 8, HAY_BLOCK: 2
```

Steps:
1. Give yourself items: `/give @s wheat 8` `/give @s hay_block 2`
2. Run `/chunklock unlock`
3. Select PLAINS biome
4. Verify chunk unlocks

Expected:
- [ ] Items consumed
- [ ] Chunk unlocks successfully
- [ ] No errors

**Test 4.2: FOREST Unlock (Medium)**
```
Required: OAK_LOG: 16, APPLE: 4
```

Steps:
1. Give yourself items
2. Run `/chunklock unlock`
3. Select FOREST biome
4. Verify chunk unlocks

Expected:
- [ ] Items consumed correctly
- [ ] Hologram updates
- [ ] Cost displayed correctly

**Success Criteria:** ✅ Vanilla items work correctly

---

### Phase 5: MMOItems Integration (20 min)

**Objective:** Test custom items from MMOItems

**Prerequisites:**
- Verify MMOItems items exist: `/mi list`
- Verify items can be obtained

**Test 5.1: Check MMOItems Integration**
```
/mi list
Should show available items
```

**Test 5.2: Modify Config to Use MMOItems**

Edit `plugins/Chunklock/config.yml`:

```yaml
biome-unlocks:
  PLAINS:
    vanilla:
      WHEAT: 8
      HAY_BLOCK: 2
    custom:
      - plugin: mmoitems
        type: MATERIAL
        item: diamond_ingot  # or your custom item name
        amount: 2
```

Save and reload:
```
/chunklock reload
```

**Test 5.3: Try Unlock with Custom Items**

Steps:
1. Get MMOItems item: `/mi give <player> MATERIAL:diamond_ingot 2`
2. Get vanilla items: `/give @s wheat 8` `/give @s hay_block 2`
3. Run `/chunklock unlock`
4. Verify all items are required (all-or-nothing)

Expected:
- [ ] Plugin recognizes custom items
- [ ] Custom items count toward unlock cost
- [ ] Without custom items, unlock fails
- [ ] With all items, unlock succeeds

**Success Criteria:** ✅ MMOItems integration works

---

### Phase 6: Oraxen Integration (20 min)

**Objective:** Test custom items from Oraxen

**Prerequisites:**
- Verify Oraxen items exist
- Verify items can be obtained

**Test 6.1: Check Oraxen Integration**
```
/oraxen list  (or appropriate command)
Should show available custom items
```

**Test 6.2: Modify Config to Use Oraxen**

Edit `plugins/Chunklock/config.yml`:

```yaml
biome-unlocks:
  JUNGLE:
    vanilla:
      COCOA_BEANS: 16
      MELON_SLICE: 8
    custom:
      - plugin: oraxen
        item: mythic_sword  # or your Oraxen item ID
        amount: 1
```

Save and reload:
```
/chunklock reload
```

**Test 6.3: Try Unlock with Oraxen Items**

Steps:
1. Get Oraxen item: `/oraxen give <player> mythic_sword 1`
2. Get vanilla items: `/give @s cocoa_beans 16` `/give @s melon_slice 8`
3. Run `/chunklock unlock`
4. Verify all items are required

Expected:
- [ ] Plugin recognizes Oraxen items
- [ ] Oraxen items display correctly
- [ ] All-or-nothing requirement works
- [ ] Unlock succeeds with all items

**Success Criteria:** ✅ Oraxen integration works

---

### Phase 7: Mixed Custom Items (15 min)

**Objective:** Test both MMOItems and Oraxen in same biome

**Test Config:**
```yaml
biome-unlocks:
  DARK_FOREST:
    vanilla:
      DARK_OAK_LOG: 16
      DARK_OAK_SAPLING: 4
    custom:
      - plugin: mmoitems
        type: MATERIAL
        item: forest_essence
        amount: 2
      - plugin: oraxen
        item: mythic_sword
        amount: 1
```

**Steps:**
1. Give all items (vanilla + both custom)
2. Run `/chunklock unlock`
3. Select DARK_FOREST
4. Verify unlock succeeds

Expected:
- [ ] All items types recognized
- [ ] All items consumed
- [ ] No conflicts between plugins
- [ ] Chunk unlocks successfully

**Success Criteria:** ✅ Mixed items work correctly

---

### Phase 8: Economy System Test (10 min)

**Objective:** Verify economy costs work with custom items

**Test 8.1: Check Cost Display**
```
/chunklock unlock
(Look at shown costs)
```

Expected:
- [ ] Costs display for each biome
- [ ] Format is readable
- [ ] All biomes have costs

**Test 8.2: Test Vault Mode (if enabled)**

Edit config:
```yaml
economy:
  type: "vault"
```

Reload: `/chunklock reload`

Expected:
- [ ] Costs show in money
- [ ] Unlock requires currency
- [ ] Currency deducted on unlock

**Success Criteria:** ✅ Economy system functional

---

### Phase 9: Glass Borders Test (10 min)

**Objective:** Verify visual chunk indicators

**Steps:**
1. Check config: `glass-borders.enabled: true`
2. Move near chunk boundaries
3. Look for glass blocks at borders

Expected:
- [ ] Glass borders appear
- [ ] Correct material: LIGHT_GRAY_STAINED_GLASS
- [ ] Full height visible
- [ ] Update as you move

**Success Criteria:** ✅ Borders display correctly

---

### Phase 10: Full Integration Test (15 min)

**Objective:** Complete end-to-end test with all features

**Scenario:**
```
1. Start fresh player
2. Run /chunklock start
3. Collect vanilla + custom items
4. Unlock chunk in DARK_FOREST
5. Check: items consumed, chunk unlocked, hologram updated
```

**Expected Flow:**
```
[Player] /chunklock start
→ Starting chunk assigned
→ Holograms display

[Player] Collects items (vanilla + custom)

[Player] /chunklock unlock
→ GUI shows: DARK_FOREST cost
→ Shows all required items
→ Player selects biome

[Player] Items consumed
→ Chunk unlocks
→ Hologram updates
→ New neighbors visible
```

**Success Criteria:** ✅ Full workflow succeeds

---

## 📊 Test Results Template

```
Build Date: November 19, 2025
Plugin Version: 2.0
Paper Version: 1.21.10
Oraxen Version: 1.195.1
MMOItems Version: 6.10.1

Phase 1 - Plugin Load: ✅ / ⚠️ / ❌
Phase 2 - Config Load: ✅ / ⚠️ / ❌
Phase 3 - Holograms: ✅ / ⚠️ / ❌
Phase 4 - Vanilla Items: ✅ / ⚠️ / ❌
Phase 5 - MMOItems: ✅ / ⚠️ / ❌
Phase 6 - Oraxen: ✅ / ⚠️ / ❌
Phase 7 - Mixed Items: ✅ / ⚠️ / ❌
Phase 8 - Economy: ✅ / ⚠️ / ❌
Phase 9 - Borders: ✅ / ⚠️ / ❌
Phase 10 - Full Test: ✅ / ⚠️ / ❌

Overall Status: ✅ READY / ⚠️ PARTIAL / ❌ FAILED

Issues Found: [List here]
```

---

## 🐛 Troubleshooting

### Plugin Won't Load
```
Check: Paper 1.21.10 compatibility
Check: No conflicting plugins
Check: Console for error messages
Action: Post error message for analysis
```

### Custom Items Not Recognized
```
Check: MMOItems/Oraxen installed
Check: Items exist in plugin config
Check: Item names match exactly (case-sensitive)
Action: Verify item IDs in respective plugins
```

### Items Not Consumed
```
Check: Inventory has exact amounts
Check: All items present (all-or-nothing)
Check: Player permissions correct
Action: Try with admin account first
```

### Holograms Not Showing
```
Check: FancyHolograms installed
Check: holograms.enabled: true in config
Check: View distance settings
Action: Check FancyHolograms console output
```

---

## 📝 Testing Notes

Space for notes during testing:

```
[Phase X Notes]
Time: 
Result: 
Issues: 
```

---

## ✅ Sign-Off

Once all phases complete successfully:

**Tested by:** [Your Name]  
**Date:** [Date]  
**Overall Result:** ✅ READY FOR PRODUCTION

Signature: _________________

---

## 🚀 Next Steps After Successful Testing

1. [ ] Document any issues found
2. [ ] Fix any bugs discovered
3. [ ] Rebuild if fixes needed
4. [ ] Re-test critical paths
5. [ ] Release version 2.1 (or 2.0.1)
6. [ ] Announce 1.21.10 support
7. [ ] Publish integration guide

---

**Good luck with testing! 🎮✨**

