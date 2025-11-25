ChunkLock 2.1.0:

[New] Complete language system - Full message customization support with lang/en.yml
[New] Multi-language support - Create lang/de.yml, lang/fr.yml, etc. for translations
[New] Over 200+ customizable message keys for all GUI text, commands, holograms, and messages
[New] Custom items integration - Full Oraxen support for custom items in biome requirements
[New] Custom items integration - Full MMOItems support for custom items in biome requirements
[New] Mix vanilla and custom items - Use both in the same biome unlock requirement
[New] Minecraft 1.21.10 support - Upgraded from 1.20.4 to latest version
[New] Enhanced GUI system - Improved unlock interface with localized messages throughout
[New] Modular configuration system - Split config into focused files (economy.yml, holograms.yml, etc.)
[New] Biome unlocks improvements - Support for flat and structured format configurations
[New] Auto-format detection - Automatically detects configuration format type
[New] Placeholder system - Dynamic content replacement (%player%, %cost%, %chunk%, etc.)
[New] Message caching - Performance optimization for language file loading

[Improvement] Enhanced error handling - Better error messages with language system integration
[Improvement] Performance optimizations - Optimized hologram updates and state tracking
[Improvement] Configuration migration - Automatic migration from old config format
[Improvement] Custom item detection - Improved runtime detection of Oraxen and MMOItems
[Improvement] GUI visual feedback - Better display for insufficient funds/items
[Improvement] Success messages - Enhanced unlock success messages with detailed information

[Fix] Language system integration - All hardcoded messages replaced with language keys
[Fix] API compatibility - Fixed switch statement syntax for Paper 1.21.10
[Fix] Deprecated API usage - Replaced Enchantment.DURABILITY with UNBREAKING
[Fix] Custom item display - Fixed custom items in GUI and holograms
[Fix] Configuration validation - Enhanced validation during migration process
[Fix] Teleport listener - Updated for latest Paper API teleport causes

**Breaking Changes:**

- Requires Minecraft 1.21.10+ (upgraded from 1.20.4)
- Configuration format changed - automatic migration provided
- Language files now required (default English included automatically)

**Dependencies:**

- Paper 1.21.10+ required
- Java 17+ required
- Optional: Oraxen 1.195.1+, MMOItems 6.10.1+, FancyHolograms 2.4.2+, Vault 1.7+
