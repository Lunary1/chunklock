# 🧪 Oraxen Integration Testing Guide - Chunklock 2.0

**Plugin Version:** Chunklock 2.0 (Paper 1.21.10)  
**Integration Target:** Oraxen 1.195.1  
**Test Date:** November 19, 2025  
**Status:** Ready for Testing

---

## 📋 Prerequisites

### Server Requirements
- ✅ Paper 1.21.10 or later (1.21.x)
- ✅ Java 17+
- ✅ 2GB+ RAM allocated

### Required Plugins
- ✅ **Chunklock 2.0** - Main plugin (freshly built)
- ✅ **Oraxen 1.195.1** - Custom items plugin
- ✅ **FancyHolograms 2.4.2** - Optional but recommended (for visual display)

### Optional But Recommended
- ✅ **Vault** - For economy integration
- ✅ **EssentialsX** - For easier player management in testing

---

## 🚀 Phase 1: Setup & Verification (15 min)

### Step 1.1: Deploy Plugins

1. **Create plugins folder** (if not exists):
   ```
   mkdir server/plugins
   ```

2. **Copy plugins:**
   ```
   Copy: C:\Users\woute.FELIX\Desktop\chunklock-plugin\target\Chunklock.jar
   To:   server/plugins/Chunklock.jar
   
   Copy: [Your Oraxen JAR]
   To:   server/plugins/Oraxen.jar
   
   Copy: [Your FancyHolograms JAR] (optional)
   To:   server/plugins/FancyHolograms.jar
   ```

3. **Verify files exist:**
   ```
   server/plugins/
   ├── Chunklock.jar ✅
   ├── Oraxen.jar ✅
   └── FancyHolograms.jar ✅
   ```

### Step 1.2: Start Server

```
java -Xmx2G -jar paper-1.21.10.jar nogui
```

Wait for:
```
[XX:XX:XX] [Server thread/INFO]: Done (X.XXXs)! For help, type "help"
```

### Step 1.3: Verify Plugin Loading

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
✅ [Chunklock] ✅ All managers initialized
✅ [Oraxen] Successfully loaded Oraxen
```

**If RED errors appear:** Check error messages and fix before proceeding.

### Step 1.4: Verify Configs Loaded

**Check Chunklock config:**
```
/chunklock status
```

**Expected:**
- Plugin responds
- No errors
- Config loaded successfully

**Check Oraxen:**
```
/oraxen list
```

**Expected:**
- Lists available custom items
- At least some items shown

---

## 🎯 Phase 2: Basic Oraxen Setup (10 min)

### Step 2.1: Identify Available Oraxen Items

**Find custom items in Oraxen:**
```
/oraxen list
```

**Write down some item IDs you see:**
```
Example output:
- custom_sword
- mythic_bow
- ancient_artifact
- glowing_dust
```

**Or give yourself some items:**
```
/oraxen give @s custom_sword 1
/oraxen give @s mythic_bow 1
```

### Step 2.2: Verify You Can Get Oraxen Items

**Test giving items:**
```
/oraxen give @s <item_id> 1
```

**Verify items appear in inventory:**
- You should see the custom item with custom name/color
- Unique texture/model if configured in Oraxen

---

## 🔧 Phase 3: Configure Chunklock for Oraxen (15 min)

### Step 3.1: Edit Chunklock Config

**File location:**
```
server/plugins/Chunklock/config.yml
```

### Step 3.2: Find Biome Unlocks Section

Look for:
```yaml
biome-unlocks:
  PLAINS:
    WHEAT: 8
    HAY_BLOCK: 2
  FOREST:
    OAK_LOG: 16
    APPLE: 4
```

### Step 3.3: Add Oraxen Items - SIMPLE TEST

**Add to PLAINS biome (simple test):**

**Before:**
```yaml
  PLAINS:
    WHEAT: 8
    HAY_BLOCK: 2
```

**After:**
```yaml
  PLAINS:
    vanilla:
      WHEAT: 8
      HAY_BLOCK: 2
    custom:
      - plugin: oraxen
        item: custom_sword
        amount: 1
```

**What this means:**
- Player needs: 8 WHEAT + 2 HAY_BLOCK + 1 custom_sword (ALL items)
- Any one missing = cannot unlock

### Step 3.4: Add Oraxen Items - MEDIUM TEST

**Add to FOREST biome (with multiple custom items):**

```yaml
  FOREST:
    vanilla:
      OAK_LOG: 16
      APPLE: 4
    custom:
      - plugin: oraxen
        item: custom_sword
        amount: 1
      - plugin: oraxen
        item: mythic_bow
        amount: 1
```

**What this means:**
- Player needs: 16 OAK_LOG + 4 APPLE + 1 custom_sword + 1 mythic_bow

### Step 3.5: Create Oraxen-Only Test Biome

**Create a new biome with ONLY Oraxen items:**

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

**What this means:**
- NO vanilla items needed
- ONLY custom Oraxen items required
- Pure test of Oraxen integration

### Step 3.6: Save and Reload Config

**Save the file** (keep YAML indentation!)

**Reload Chunklock:**
```
/chunklock reload
```

**Expected output:**
```
[Chunklock] ✅ Configuration reloaded successfully
```

**If errors appear:**
- Check YAML indentation (2 spaces)
- Verify item names match exactly `/oraxen list`
- Check console for specific errors

---

## 🧪 Phase 4: Basic Oraxen Item Unlock Test (20 min)

### Test 4.1: Vanilla-Only PLAINS (Baseline)

**Clear inventory:**
```
/clear @s
```

**Give vanilla items only:**
```
/give @s wheat 8
/give @s hay_block 2
```

**Try to unlock PLAINS:**
```
/chunklock unlock
```

**Expected flow:**
1. GUI shows PLAINS option
2. You can click to unlock
3. Items consumed
4. Chunk unlocks ✅

**Result:** ✅ Baseline works

---

### Test 4.2: Vanilla + Oraxen Mixed - FOREST

**Clear inventory:**
```
/clear @s
```

**Give ALL required items:**
```
/give @s oak_log 16
/give @s apple 4
/oraxen give @s custom_sword 1
/oraxen give @s mythic_bow 1
```

**Try to unlock FOREST:**
```
/chunklock unlock
```

**Expected flow:**
1. GUI shows costs
2. Shows: OAK_LOG (16), APPLE (4), + Oraxen items
3. All items displayed correctly
4. Can click to unlock
5. ALL items consumed
6. Chunk unlocks ✅

**What to verify:**
- [ ] Custom items show in GUI
- [ ] Custom items consume
- [ ] Vanilla items consume
- [ ] Chunk actually unlocks

**Result:** ✅ Mixed items work

---

### Test 4.3: Oraxen-Only Unlock - JUNGLE

**Clear inventory:**
```
/clear @s
```

**Give ONLY Oraxen items (no vanilla):**
```
/oraxen give @s custom_sword 2
/oraxen give @s glowing_dust 5
```

**Try to unlock JUNGLE:**
```
/chunklock unlock
```

**Expected flow:**
1. GUI shows JUNGLE option
2. Shows ONLY Oraxen items needed
3. No vanilla items required
4. Can unlock with just custom items
5. Items consumed ✅

**What to verify:**
- [ ] No vanilla items required
- [ ] Custom items are sole requirement
- [ ] Pure Oraxen integration works
- [ ] Items consumed correctly

**Result:** ✅ Oraxen-only works

---

### Test 4.4: Missing Oraxen Items (Negative Test)

**Clear inventory and give incomplete items:**
```
/clear @s
/give @s oak_log 16
/give @s apple 4
/oraxen give @s custom_sword 1
(NO mythic_bow given)
```

**Try to unlock FOREST:**
```
/chunklock unlock
```

**Expected behavior:**
1. Cannot unlock (all-or-nothing system)
2. Error message: "You don't have all required items"
3. Items NOT consumed ✅

**What to verify:**
- [ ] Missing item prevents unlock
- [ ] All-or-nothing system enforced
- [ ] No partial unlocks allowed

**Result:** ✅ Validation works correctly

---

## 🔄 Phase 5: GUI Display Testing (15 min)

### Step 5.1: Check Cost Display

**Open unlock GUI:**
```
/chunklock unlock
```

**Visually inspect:**
- [ ] Oraxen item names display correctly
- [ ] Item amounts show
- [ ] Colors/formatting looks good
- [ ] All items listed (vanilla + custom)

**Screenshot/Document:**
Take a screenshot or note what you see.

### Step 5.2: Check Hologram Display

**Build near chunk border:**
```
Move to edge of unlocked chunk
```

**Look for holograms:**
- [ ] Holograms appear
- [ ] Show chunk info
- [ ] Display costs
- [ ] Update correctly

**Expected text:**
```
═══════════════════
Unlock: FOREST
━━━━━━━━━━━━━━━━━━
Cost: OAK_LOG x16
       APPLE x4
       custom_sword x1
       mythic_bow x1
(Or similar format)
```

---

## ⚠️ Phase 6: Edge Cases & Error Handling (15 min)

### Test 6.1: Wrong Item Type

**If Oraxen item name is wrong in config:**
```yaml
- plugin: oraxen
  item: nonexistent_item
  amount: 1
```

**Expected:**
- Console warning: "Custom item not found"
- Plugin handles gracefully
- No crash ✅

### Test 6.2: Duplicate Items

**If same item listed twice:**
```yaml
custom:
  - plugin: oraxen
    item: custom_sword
    amount: 1
  - plugin: oraxen
    item: custom_sword
    amount: 1
```

**Expected:**
- Requires 2 total custom_sword items
- Or error if not allowed
- No crash ✅

### Test 6.3: Zero Amount

**If amount is 0:**
```yaml
- plugin: oraxen
  item: custom_sword
  amount: 0
```

**Expected:**
- Item requirement ignored
- Or error message
- No crash ✅

### Test 6.4: Negative Amount

**If amount is negative:**
```yaml
- plugin: oraxen
  item: custom_sword
  amount: -5
```

**Expected:**
- Validation error
- Config rejected
- Clear error message ✅

---

## 📊 Phase 7: Documentation & Verification (10 min)

### Step 7.1: Test Summary

**Fill in this checklist:**

```
✅/⚠️/❌ = Success / Partial / Failed

Plugin Loading: ✅ ⚠️ ❌
  - /plugins shows Chunklock
  - /plugins shows Oraxen
  
Configuration: ✅ ⚠️ ❌
  - Config loaded without errors
  - Biome-unlocks section present
  
Vanilla Items (Baseline): ✅ ⚠️ ❌
  - PLAINS unlock works with vanilla items
  - Items consumed correctly
  
Mixed Items: ✅ ⚠️ ❌
  - FOREST unlock works with mixed items
  - All items displayed in GUI
  - All items consumed
  
Oraxen-Only: ✅ ⚠️ ❌
  - JUNGLE unlock works with only Oraxen items
  - No vanilla items needed
  - Custom items consumed
  
Validation: ✅ ⚠️ ❌
  - Cannot unlock without all items
  - All-or-nothing system works
  
GUI Display: ✅ ⚠️ ❌
  - Oraxen items show in unlock GUI
  - Names and amounts display
  - Formatting looks correct
  
Holograms: ✅ ⚠️ ❌
  - Holograms appear at borders
  - Custom items display in holograms
  
Error Handling: ✅ ⚠️ ❌
  - No crashes on errors
  - Graceful error messages
```

### Step 7.2: Issues Encountered

**Document any problems:**
```
Issue 1: [Description]
Location: [File/Command/Config]
Expected: [What should happen]
Actual: [What happened]
Severity: [Low/Medium/High]

Issue 2: [Description]
...
```

### Step 7.3: Console Logs

**Save console output:**
- Any error messages
- Warnings
- Info messages about Oraxen items

```
[Chunklock] [Details about item recognition]
[Oraxen] [Details about item availability]
```

---

## 🎯 Success Criteria

### ✅ Full Success (Ready for Production)
- All checkboxes pass ✅
- No errors or exceptions
- All items work as expected
- Documentation complete
- Ready to release

### ⚠️ Partial Success (Needs Fixes)
- Some features work ✅
- Some features fail ⚠️
- Issues documented
- Known limitations listed
- Requires fixes before release

### ❌ Failed (Not Ready)
- Multiple failures
- Crashes or exceptions
- Major functionality broken
- Needs significant work

---

## 🔧 Troubleshooting

### Issue: Oraxen Items Not Recognized

**Symptoms:**
- Custom items don't show in GUI
- Cannot unlock even with items

**Solutions:**
1. Check item names match exactly: `/oraxen list`
2. Verify YAML indentation (2 spaces)
3. Check Oraxen is loaded: `/plugins`
4. Reload config: `/chunklock reload`
5. Check console for errors

### Issue: Items Show But Don't Consume

**Symptoms:**
- GUI shows items
- Click to unlock
- But items still in inventory

**Solutions:**
1. Check all-or-nothing logic
2. Verify amounts are correct
3. Check for inventory limit issues
4. Review console errors
5. Reload plugin: `/plugman reload Chunklock`

### Issue: GUI Shows Nothing

**Symptoms:**
- `/chunklock unlock` opens empty GUI
- No items or costs displayed

**Solutions:**
1. Verify config has items
2. Check YAML syntax
3. Reload config: `/chunklock reload`
4. Check console for parsing errors
5. Verify biome names are valid

### Issue: Plugin Crashes

**Symptoms:**
- Server console shows exceptions
- Plugin unloads

**Solutions:**
1. Check Java version (17+)
2. Check Paper version (1.21.10)
3. Review full error stack trace
4. Check config YAML syntax
5. Look for conflicting plugins

---

## 📞 Debug Commands

### Check Plugin Status
```
/chunklock status
```

### Check Available Biomes
```
/chunklock unlock
(See what biomes are available)
```

### Check Oraxen Integration
```
/oraxen list
(Verify items exist)
/oraxen give @s <item_id> 1
(Verify you can receive items)
```

### Reload After Config Changes
```
/chunklock reload
```

### Check Console for Errors
```
Watch server console during tests
Look for [ERROR] or stack traces
Note any custom item related messages
```

---

## 📝 Report Template

Save your test results:

```
═══════════════════════════════════════
CHUNKLOCK ORAXEN INTEGRATION TEST REPORT
═══════════════════════════════════════

Test Date: [Date]
Tester: [Your Name]
Plugin Version: Chunklock 2.0
Oraxen Version: 1.195.1
Paper Version: 1.21.10

═ SETUP ═
- Paper 1.21.10: ✅ ⚠️ ❌
- Chunklock Deployed: ✅ ⚠️ ❌
- Oraxen Loaded: ✅ ⚠️ ❌
- Config Created: ✅ ⚠️ ❌

═ TESTING RESULTS ═
Phase 4 (Unlocks): ✅ ⚠️ ❌
Phase 5 (GUI): ✅ ⚠️ ❌
Phase 6 (Errors): ✅ ⚠️ ❌
Phase 7 (Display): ✅ ⚠️ ❌

═ ISSUES ═
[List any issues found]

═ CONCLUSION ═
Overall Result: ✅ PASS / ⚠️ PARTIAL / ❌ FAIL

Status: [Ready for production / Needs fixes / Critical issues]

Recommendations:
[Any recommendations or next steps]
```

---

## 🎉 Next Steps

### If All Tests Pass ✅
1. Document the successful configuration
2. Create example config for users
3. Release version 2.1 with Oraxen support
4. Add to plugin documentation
5. Create user guide for setup

### If Issues Found ⚠️
1. Document all issues clearly
2. Prioritize by severity
3. Create fixes
4. Re-test after each fix
5. Iterate until all pass

### If Critical Failures ❌
1. Analyze root causes
2. Review API compatibility
3. Check plugin interactions
4. Fix major issues
5. Re-test from Phase 1

---

## ✅ Final Checklist

- [ ] All prerequisites installed
- [ ] Plugins deployed
- [ ] Server started
- [ ] All phases completed
- [ ] Test results documented
- [ ] Issues identified and logged
- [ ] Console output saved
- [ ] Report filled out
- [ ] Ready to proceed (document status)

---

**Good luck with testing! 🧪✨**

Report your findings when complete, and we can proceed to the next phase (MMOItems integration or production release).

