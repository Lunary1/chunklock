# Chunklock v2.1.0 - Major Feature Release

**Game Changing Updates!**

---

## 🚀 What's New in v2.1.0

### 🌍 Complete Language System - Full Message Customization

- Comprehensive language file support with `lang/en.yml` for all plugin messages
- Support for multiple languages (create `lang/de.yml`, `lang/fr.yml`, etc.)
- All GUI text, commands, holograms, errors, and success messages are now customizable
- Placeholder system for dynamic content (`%player%`, `%cost%`, `%chunk%`, etc.)
- Automatic fallback to English if translation keys are missing
- Easy language switching via config.yml setting
- **Over 200+ customizable message keys** organized by category

### 🎮 Custom Items Integration - Oraxen & MMOItems Support

- Full support for **Oraxen custom items** in biome unlock requirements
- Full support for **MMOItems custom items** in biome unlock requirements
- Structured format support for advanced item configurations
- Automatic detection of custom item plugins at runtime
- Visual display of custom items in unlock GUI and holograms
- Mix vanilla items and custom items in biome requirements

### 🎯 Minecraft 1.21.10 Support

- Upgraded from 1.20.4 to latest Minecraft version **1.21.10**
- Fixed API compatibility issues for Paper 1.21.10
- Updated teleport listener for new switch expression syntax
- Replaced deprecated Enchantment.DURABILITY with UNBREAKING
- Full compatibility with latest Paper API features

### 🎨 Enhanced GUI System

- Improved unlock GUI with localized messages throughout
- Better visual feedback for insufficient funds/items
- Enhanced success messages with detailed unlock information
- Improved help system with step-by-step guides
- Money-based unlock UI with formatted currency display
- Material-based unlock UI with progress tracking

### ⚙️ Modular Configuration System

- Split monolithic config.yml into **focused modular files**
- Separate configs for economy, holograms, borders, teams, worlds, etc.
- Automatic migration from old configuration format
- Better organization and maintainability
- Enhanced inline documentation in all config files
- **10+ focused configuration files** for easy management

### 🌿 Biome Unlocks System Improvements

- Support for both **flat and structured format** configurations
- Auto-detection of format type (no manual configuration needed)
- Enhanced validation and error messages
- Better documentation with comprehensive guides
- Support for complex multi-item requirements
- Mix vanilla items with custom items seamlessly

---

## 🔧 Improvements

### Enhanced Error Handling

- Better error messages with language system integration
- More descriptive validation errors
- Improved logging for troubleshooting
- Graceful fallbacks for missing configuration

### Performance Optimizations

- Message caching system for language files
- Optimized hologram updates
- Better state tracking to prevent duplicate operations
- Improved border update system

---

## 🐛 Fixes

### Language System Integration

- All hardcoded messages replaced with language keys
- Consistent message formatting across all systems
- Proper placeholder replacement throughout
- Fixed missing language keys in GUI and commands

### Configuration Migration

- Improved automatic migration from old config format
- Better handling of legacy configuration files
- Preserved all existing settings during migration
- Enhanced validation during migration process

### API Compatibility

- Fixed switch statement syntax for Paper 1.21.10
- Replaced deprecated enchantment API calls
- Updated teleport cause handling for latest API
- Fixed compatibility issues with latest Paper builds

### Custom Item Detection

- Improved runtime detection of Oraxen and MMOItems
- Better error handling when custom item plugins are missing
- Fixed custom item display in GUI and holograms
- Enhanced validation for custom item requirements

---

## ⚠️ Breaking Changes

**Important:** Please read before updating!

- **Requires Minecraft 1.21.10+** (upgraded from 1.20.4)
- **Configuration format has changed** - automatic migration provided
- **Language files now required** (default English included)

---

## 📋 Migration Notes

**Upgrading from v2.0 or earlier?**

- **Old configs automatically migrate** on first load - no manual work needed!
- **Language files are generated automatically** on first run
- **Custom item integration** requires Oraxen 1.195.1+ or MMOItems 6.10.1+
- **All your data is preserved** - no data loss during migration
- **Backup recommended** before updating (always good practice)

---

## 📦 Dependencies

### Required

- **Paper 1.21.10+** (or Spigot/Pufferfish)
- **Java 17+**

### Optional (Enhanced Features)

- **Oraxen 1.195.1+** - For custom items in biome requirements
- **MMOItems 6.10.1+** - Alternative custom items plugin support
- **FancyHolograms 2.4.2+** - For enhanced hologram displays
- **Vault 1.7+** - For money-based economy integration

---

## 🚀 Installation

### Quick Update Steps

1. Download **Chunklock-2.1.0.jar** from releases
2. Stop your server
3. Replace old JAR file in `/plugins` folder
4. Start your server
5. Configs will auto-migrate on first load
6. Language files will be generated automatically
7. Configure new features as desired
8. Done! Enjoy v2.1.0

### New Installation

1. Download **Chunklock-2.1.0.jar**
2. Place in `/plugins` folder
3. Start server (configs auto-generate)
4. Run `/chunklock setup 30000` to create world
5. Configure settings in `plugins/Chunklock/`
6. Players can use `/chunklock start`

---

## 💡 Tips & Tricks

**New Features to Try:**

- **Language System:** Customize all messages in `lang/en.yml` - make it your own!
- **Custom Items:** Add Oraxen/MMOItems items to `biome-unlocks.yml` for unique requirements
- **Modular Config:** Edit specific config files instead of one huge file - much easier!
- **GUI System:** Players can right-click blocks to open unlock GUI - super convenient!

---

## 📚 Documentation

**Need Help?**

- **Complete Wiki:** [Full Documentation](https://github.com/Lunary1/chunklock/wiki)
- **Installation Guide:** [Step-by-Step Setup](https://github.com/Lunary1/chunklock/wiki/Installation-Guide)
- **Configuration Reference:** [All Config Options](https://github.com/Lunary1/chunklock/wiki/Configuration-Reference)
- **Discord Support:** [Join Our Community](https://discord.com/invite/QuQ2KuTN2Q)
- **GitHub Issues:** [Report Bugs](https://github.com/Lunary1/chunklock/issues)

---

**Thank you for using Chunklock!**

*We hope you enjoy the new features in v2.1.0!*

Questions? Issues? Join our Discord or open a GitHub issue!

