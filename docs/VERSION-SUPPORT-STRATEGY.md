# Version Support Strategy Analysis: MC 1.20.4 → 1.21.10

**Date:** November 19, 2025  
**Current Build:** Paper 1.20.4  
**Target Range:** 1.20.4 - 1.21.10  
**Java Version:** 17  
**Current Status:** Single version targeting

---

## 📊 Situation Analysis

### Current State

- **Plugin Version:** 2.0
- **API Version:** 1.20 (plugin.yml)
- **Paper Dependency:** 1.20.4-R0.1-SNAPSHOT (pom.xml)
- **Latest Available:** Paper 1.21.10
- **Gap:** 7+ minor versions, 1-2 years of changes

### Available Test Resources

- ✅ Oraxen 1.195.1.jar (latest)
- ✅ MMOItems 6.10.1 (recent build from Nov 3, 2025)
- ✅ Paper 1.21.10 (latest)

---

## 🎯 Version Gap Analysis

### Minecraft Versions Between 1.20.4 - 1.21.10

```
1.20.4 (current)
    ↓ (patch versions in 1.20.x)
1.20.5 ← NEW features
1.20.6 ← NEW features
1.21   ← MAJOR version (Java version implications?)
1.21.1 ← features/fixes
...
1.21.10 (target) ← Latest
```

### Major Changes in this Range

- **1.20.5-1.21:** API adjustments, potential breaking changes
- **Mojang Updates:** New blocks, entities, mechanics
- **Paper Updates:** Bug fixes, performance improvements
- **Dependency Changes:** Libraries may have been updated

### What Could Break

- ❓ Paper API compatibility (likely - Paper releases new API between versions)
- ❓ EventHandler signatures (possible)
- ❓ Configuration serialization (unlikely if YAML compatible)
- ❓ World height changes (MC 1.21 may have world height changes)
- ❓ Plugin dependency APIs (FancyHolograms, Vault, MMOItems, Oraxen versions)

---

## 🛠️ Approach Options

### **OPTION 1: Single Version Upgrade (Recommended)**

**Target:** Paper 1.21.10 only

#### Pros ✅

- Simplest approach
- Fastest development time
- Clearest error messages
- Easiest testing
- Reduces build complexity
- Player base on 1.21.10 gets latest features
- Future-proof (1.21.10 is current)

#### Cons ❌

- Players on 1.20.4-1.21.9 cannot use this version
- If players haven't upgraded, they're blocked
- Only works if your test environment is 1.21.10

#### Implementation Time

- **Estimated:** 2-4 hours
- Mostly: Build changes, testing for API breaks
- Risk: LOW (Paper API usually backward compatible within minor versions)

#### Current Reality Check

- Your test plugins (Oraxen 1.195.1, MMOItems 6.10.1) are built for 1.21.x
- Player already has 1.21.10 Paper running
- This is likely the path already chosen by plugin ecosystem

---

### **OPTION 2: Multi-Version Support (Ambitious)**

**Target:** 1.20.4 + 1.21.x compatibility in same JAR

#### Pros ✅

- Supports wider range of players
- Single JAR works across versions
- Good for long-term maintenance
- Professional plugin approach

#### Cons ❌

- **Much** more complex code
- Version detection logic needed
- Conditional event handling
- Testing becomes 7x harder (7 versions to test)
- Dependency hell (which version of libs to shade?)
- Build complexity increases significantly
- Longer development time
- Higher maintenance burden
- **Problem:** Can't easily test on one version for another

#### Implementation Requirements

- Reflection for version-specific API calls
- Feature detection system
- Conditional compilation or runtime version checks
- Comprehensive testing across 7+ versions
- Documentation for each version's quirks

#### Implementation Time

- **Estimated:** 2-4 weeks
- Multiple version testing cycles
- Debugging version-specific issues
- Risk: **HIGH** (many moving parts)

#### Reality Check

- You only have 1 test environment (likely 1.21.10)
- Testing all versions properly requires multiple servers
- Dependency plugins also need to support each version
- Much harder to support without multiple test servers

---

### **OPTION 3: Gradual Multi-Version (Compromise)**

**Phase 1:** Upgrade to 1.21.10  
**Phase 2:** Add backward compatibility later if needed

#### Pros ✅

- Get to testing immediately
- Release 1.21.10 version quickly
- Add compatibility later if players request it
- Less risk upfront
- Easier rollback if issues arise

#### Cons ❌

- Requires two build cycles
- Players on older versions still blocked initially
- Extra work later

#### Implementation Time

- **Phase 1:** 2-4 hours (upgrade to 1.21.10)
- **Phase 2:** 2-4 weeks (if needed later)
- Risk: **LOW** (can release early)

---

## 📋 Detailed Comparison Table

| Aspect                 | Option 1: Single | Option 2: Multi | Option 3: Gradual |
| ---------------------- | ---------------- | --------------- | ----------------- |
| **Dev Time**           | 2-4 hrs          | 2-4 weeks       | 2-4 hrs + later   |
| **Testing Effort**     | Low              | Very High       | Low initial       |
| **Code Complexity**    | Simple           | Complex         | Simple            |
| **Player Support**     | Latest only      | All versions    | Latest + later    |
| **Maintenance**        | Easy             | Difficult       | Easy              |
| **Risk Level**         | Low              | High            | Low               |
| **Can Test Now**       | Yes (1.21.10)    | Partially       | Yes               |
| **Release Speed**      | Fast             | Slow            | Fast              |
| **Plugin API Support** | ✅ Good          | ⚠️ Depends      | ✅ Good           |

---

## 🎮 Real-World Considerations

### Your Test Environment

- Paper 1.21.10 ✅ You have this
- Oraxen 1.195.1 ✅ You have this (built for 1.21.x)
- MMOItems 6.10.1 ✅ You have this (built for 1.21.x)

**This means:** You're **already set up for 1.21.10 testing**

### Dependency Plugin Status

- **Oraxen 1.195.1:** Likely requires 1.21.x+
- **MMOItems 6.10.1:** Likely requires 1.21.x+ (recent build)
- **FancyHolograms 2.4.2:** Need to check version support
- **Vault API 1.7:** Generally version-agnostic

### Your Player Base

- Do you know what version they're running?
- Are they asking for 1.21.10 support?
- Would they upgrade if you require it?

---

## ⚠️ Potential Issues with Each Approach

### Option 1 Issues (Single 1.21.10)

- ❌ Breaks existing 1.20.4 servers
- ⚠️ Need to document version requirement clearly
- ✅ Simplest to implement

### Option 2 Issues (Multi-Version)

- ❌ Paper API changes between versions (likely breaks)
- ❌ Dependency plugins may not support all versions
- ❌ Reflection code is brittle and version-specific
- ❌ Testing nightmare (can't easily test 1.20.4 on 1.21.10 machine)
- ⚠️ If Oraxen/MMOItems don't support old versions, you're blocked anyway

### Option 3 Issues (Gradual)

- ⚠️ Two development cycles needed
- ✅ Can defer harder decisions
- ✅ Get feedback from 1.21.10 users first

---

## 🔍 Critical Question

**What versions do your users actually need?**

This determines everything. If:

- Users are on 1.21.10 → **Use Option 1**
- Users span 1.20.4-1.21.10 → **Use Option 2 or 3**
- You want to serve everyone → **Need to understand their versions first**

---

## 📌 My Recommendation

### **BEST PATH: Option 1 (Single Version Upgrade to 1.21.10)**

**Why?**

1. **You're already set up:** Your test environment, Oraxen, and MMOItems are all 1.21.10+
2. **Fastest time to working integration:** 2-4 hours vs. 2-4 weeks
3. **Cleanest code:** No version detection/reflection complexity
4. **Easiest testing:** Test directly on target version
5. **Lowest risk:** Fewer things can go wrong
6. **Plugin ecosystem is there:** Latest dependency versions ready
7. **Future-proof:** 1.21.10 is latest, no immediate further upgrades needed
8. **Realistic multi-version issue:** Oraxen 1.195.1 and MMOItems 6.10.1 likely don't support 1.20.4 anyway

### **Path Forward**

```
1. Confirm your player base needs 1.21.10+ support
2. Update pom.xml: Paper 1.20.4 → 1.21.10
3. Update plugin.yml: api-version 1.20 → 1.21
4. Run `mvn clean package`
5. Test MMOItems + Oraxen integration on 1.21.10
6. Fix any API breaks (likely minimal)
7. Release as v2.1 (or v2.0.1 if minor fixes)
```

### **If later you need backward compatibility:**

Option 3 path: You can then decide to support 1.20.x as a separate branch/version if players request it.

---

## 🚀 What I'm Ready To Do

### If You Choose Option 1 (Recommended)

I can immediately:

1. ✅ Update pom.xml to Paper 1.21.10
2. ✅ Update plugin.yml api-version
3. ✅ Run build and identify API breaks
4. ✅ Fix compatibility issues
5. ✅ Create integration testing guide for MMOItems + Oraxen
6. ✅ Document any version-specific changes needed

### If You Choose Option 3

I can:

1. ✅ Do Phase 1 (upgrade to 1.21.10) immediately
2. ✅ Document what would be needed for Phase 2 multi-version support

### If You Choose Option 2

I can help, but need to know:

- How many test environments can you set up?
- Do you have access to Paper 1.20.4, 1.20.5, 1.21, 1.21.10?
- Can you test dependency plugins across versions?

---

## ✅ Decision Template

**Please confirm:**

1. **Version Target:** Which option resonates with you?

   - [ ] Option 1: Single upgrade to 1.21.10 (Recommended)
   - [ ] Option 2: Multi-version support
   - [ ] Option 3: Gradual approach

2. **Player Base Clarity:** Do you know if players need 1.20.4 support?

   - [ ] Yes, players are on 1.21.10+
   - [ ] Yes, players are on 1.20.4
   - [ ] Mixed, need both
   - [ ] Unsure

3. **Timeline:** What's your priority?

   - [ ] Get working ASAP with 1.21.10
   - [ ] Support all versions right away
   - [ ] Phase approach is OK

4. **Risk Tolerance:**
   - [ ] Prefer simple, low-risk approach
   - [ ] OK with complex for maximum compatibility

---

## Summary

**My strong recommendation: Option 1 - Single Version Upgrade to 1.21.10**

**Reasoning:**

- You have the infrastructure for 1.21.10
- Dependency plugins are 1.21.10+
- Fastest to working integration
- Cleanest code
- Lowest risk
- Player ecosystem expects latest support

**Next step:** Confirm you're comfortable with this approach, and I'll immediately start the upgrade and integration testing.
