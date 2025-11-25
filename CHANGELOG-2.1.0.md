# ChunkLock 2.1.0

**[New] Complete Language System - Full Message Customization**

- Comprehensive language file support with `lang/en.yml` for all plugin messages
- Support for multiple languages (create `lang/de.yml`, `lang/fr.yml`, etc.)
- All GUI text, commands, holograms, errors, and success messages are now customizable
- Placeholder system for dynamic content (%player%, %cost%, %chunk%, etc.)
- Automatic fallback to English if translation keys are missing
- Easy language switching via config.yml setting
- Over 200+ customizable message keys organized by category

**[New] Custom Items Integration - Oraxen Support**

- Full support for Oraxen custom items in biome unlock requirements
- Structured format support for advanced item configurations
- Automatic detection of custom item plugins at runtime
- Visual display of custom items in unlock GUI and holograms

**[New] Minecraft 1.21.10 Support**

- Upgraded from 1.20.4 to latest Minecraft version 1.21.10
- Fixed API compatibility issues for Paper 1.21.10
- Updated teleport listener for new switch expression syntax
- Replaced deprecated Enchantment.DURABILITY with UNBREAKING
- Full compatibility with latest Paper API features

**[New] Enhanced GUI System**

- Improved unlock GUI with localized messages throughout
- Better visual feedback for insufficient funds/items
- Enhanced success messages with detailed unlock information
- Improved help system with step-by-step guides
- Money-based unlock UI with formatted currency display
- Material-based unlock UI with progress tracking

**[New] Modular Configuration System**

- Split monolithic config.yml into focused modular files
- Separate configs for economy, holograms, borders, teams, worlds, etc.
- Automatic migration from old configuration format
- Better organization and maintainability
- Enhanced inline documentation in all config files

**[New] Biome Unlocks System Improvements**

- Support for both flat and structured format configurations
- Auto-detection of format type (no manual configuration needed)
- Enhanced validation and error messages
- Better documentation with comprehensive guides
- Support for complex multi-item requirements

**[Improvement] Enhanced Error Handling**

- Better error messages with language system integration
- More descriptive validation errors
- Improved logging for troubleshooting
- Graceful fallbacks for missing configuration

**[Improvement] Performance Optimizations**

- Message caching system for language files
- Optimized hologram updates
- Better state tracking to prevent duplicate operations
- Improved border update system

**[Fix] Language System Integration**

- All hardcoded messages replaced with language keys
- Consistent message formatting across all systems
- Proper placeholder replacement throughout
- Fixed missing language keys in GUI and commands

**[Fix] Configuration Migration**

- Improved automatic migration from old config format
- Better handling of legacy configuration files
- Preserved all existing settings during migration
- Enhanced validation during migration process

**[Fix] API Compatibility**

- Fixed switch statement syntax for Paper 1.21.10
- Replaced deprecated enchantment API calls
- Updated teleport cause handling for latest API
- Fixed compatibility issues with latest Paper builds

**[Fix] Custom Item Detection**

- Improved runtime detection of Oraxen and MMOItems
- Better error handling when custom item plugins are missing
- Fixed custom item display in GUI and holograms
- Enhanced validation for custom item requirements

---

**Breaking Changes:**

- Requires Minecraft 1.21.10+ (upgraded from 1.20.4)
- Configuration format has changed - automatic migration provided
- Language files now required (default English included)

**Migration Notes:**

- Old configs automatically migrate on first load
- Language files are generated automatically on first run
- Custom item integration requires Oraxen 1.195.1+ or MMOItems 6.10.1+

**Dependencies:**

- Paper 1.21.10+ required
- Java 17+ required
- Optional: Oraxen 1.195.1+, MMOItems 6.10.1+, FancyHolograms 2.4.2+, Vault 1.7+
