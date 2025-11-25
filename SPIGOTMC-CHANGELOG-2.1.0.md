[CENTER][SIZE=7][COLOR=#2E8B57][B]CHUNKLOCK v2.1.0 UPDATE[/B][/COLOR][/SIZE]

[SIZE=5][COLOR=#4169E1][B]Major Feature Release - Game Changing Updates![/B][/COLOR][/SIZE][/CENTER]

[CENTER][SIZE=6][COLOR=#FF4500][B]🚀 WHAT'S NEW IN v2.1.0 🚀[/B][/COLOR][/SIZE][/CENTER]

[CENTER][SIZE=5][COLOR=#FF6347][B]✨ MAJOR NEW FEATURES ✨[/B][/COLOR][/SIZE][/CENTER]

[COLOR=#FF1493][B][SIZE=5]🌍 Complete Language System - Full Message Customization[/SIZE][/B][/COLOR]

[LIST]
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Comprehensive language file support with [B]lang/en.yml[/B] for all plugin messages
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Support for multiple languages (create [B]lang/de.yml[/B], [B]lang/fr.yml[/B], etc.)
[*][COLOR=#32CD32][B]✓[/B][/COLOR] All GUI text, commands, holograms, errors, and success messages are now customizable
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Placeholder system for dynamic content ([B]%player%[/B], [B]%cost%[/B], [B]%chunk%[/B], etc.)
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Automatic fallback to English if translation keys are missing
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Easy language switching via config.yml setting
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Over [B]200+ customizable message keys[/B] organized by category
[/LIST]

[COLOR=#FF1493][B][SIZE=5]🎮 Custom Items Integration - Oraxen & MMOItems Support[/SIZE][/B][/COLOR]

[LIST]
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Full support for [B]Oraxen custom items[/B] in biome unlock requirements
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Full support for [B]MMOItems custom items[/B] in biome unlock requirements
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Structured format support for advanced item configurations
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Automatic detection of custom item plugins at runtime
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Visual display of custom items in unlock GUI and holograms
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Mix vanilla items and custom items in biome requirements
[/LIST]

[COLOR=#FF1493][B][SIZE=5]🎯 Minecraft 1.21.10 Support[/SIZE][/B][/COLOR]

[LIST]
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Upgraded from 1.20.4 to latest Minecraft version [B]1.21.10[/B]
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Fixed API compatibility issues for Paper 1.21.10
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Updated teleport listener for new switch expression syntax
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Replaced deprecated Enchantment.DURABILITY with UNBREAKING
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Full compatibility with latest Paper API features
[/LIST]

[COLOR=#FF1493][B][SIZE=5]🎨 Enhanced GUI System[/SIZE][/B][/COLOR]

[LIST]
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Improved unlock GUI with localized messages throughout
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Better visual feedback for insufficient funds/items
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Enhanced success messages with detailed unlock information
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Improved help system with step-by-step guides
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Money-based unlock UI with formatted currency display
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Material-based unlock UI with progress tracking
[/LIST]

[COLOR=#FF1493][B][SIZE=5]⚙️ Modular Configuration System[/SIZE][/B][/COLOR]

[LIST]
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Split monolithic config.yml into [B]focused modular files[/B]
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Separate configs for economy, holograms, borders, teams, worlds, etc.
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Automatic migration from old configuration format
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Better organization and maintainability
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Enhanced inline documentation in all config files
[*][COLOR=#32CD32][B]✓[/B][/COLOR] [B]10+ focused configuration files[/B] for easy management
[/LIST]

[COLOR=#FF1493][B][SIZE=5]🌿 Biome Unlocks System Improvements[/SIZE][/B][/COLOR]

[LIST]
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Support for both [B]flat and structured format[/B] configurations
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Auto-detection of format type (no manual configuration needed)
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Enhanced validation and error messages
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Better documentation with comprehensive guides
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Support for complex multi-item requirements
[*][COLOR=#32CD32][B]✓[/B][/COLOR] Mix vanilla items with custom items seamlessly
[/LIST]

[CENTER][SIZE=6][COLOR=#4682B4][B]🔧 IMPROVEMENTS[/B][/COLOR][/SIZE][/CENTER]

[COLOR=#4169E1][B][SIZE=4]Enhanced Error Handling[/SIZE][/B][/COLOR]
[LIST]
[*]Better error messages with language system integration
[*]More descriptive validation errors
[*]Improved logging for troubleshooting
[*]Graceful fallbacks for missing configuration
[/LIST]

[COLOR=#4169E1][B][SIZE=4]Performance Optimizations[/SIZE][/B][/COLOR]
[LIST]
[*]Message caching system for language files
[*]Optimized hologram updates
[*]Better state tracking to prevent duplicate operations
[*]Improved border update system
[/LIST]

[CENTER][SIZE=6][COLOR=#32CD32][B]🐛 FIXES[/B][/COLOR][/SIZE][/CENTER]

[COLOR=#228B22][B][SIZE=4]Language System Integration[/SIZE][/B][/COLOR]
[LIST]
[*]All hardcoded messages replaced with language keys
[*]Consistent message formatting across all systems
[*]Proper placeholder replacement throughout
[*]Fixed missing language keys in GUI and commands
[/LIST]

[COLOR=#228B22][B][SIZE=4]Configuration Migration[/SIZE][/B][/COLOR]
[LIST]
[*]Improved automatic migration from old config format
[*]Better handling of legacy configuration files
[*]Preserved all existing settings during migration
[*]Enhanced validation during migration process
[/LIST]

[COLOR=#228B22][B][SIZE=4]API Compatibility[/SIZE][/B][/COLOR]
[LIST]
[*]Fixed switch statement syntax for Paper 1.21.10
[*]Replaced deprecated enchantment API calls
[*]Updated teleport cause handling for latest API
[*]Fixed compatibility issues with latest Paper builds
[/LIST]

[COLOR=#228B22][B][SIZE=4]Custom Item Detection[/SIZE][/B][/COLOR]
[LIST]
[*]Improved runtime detection of Oraxen and MMOItems
[*]Better error handling when custom item plugins are missing
[*]Fixed custom item display in GUI and holograms
[*]Enhanced validation for custom item requirements
[/LIST]

[CENTER][SIZE=6][COLOR=#FF6347][B]⚠️ BREAKING CHANGES[/B][/COLOR][/SIZE][/CENTER]

[COLOR=#DC143C][B]Important:[/B][/COLOR] Please read before updating!

[LIST]
[*][B]Requires Minecraft 1.21.10+[/B] (upgraded from 1.20.4)
[*][B]Configuration format has changed[/B] - automatic migration provided
[*][B]Language files now required[/B] (default English included)
[/LIST]

[CENTER][SIZE=6][COLOR=#FF8C00][B]📋 MIGRATION NOTES[/B][/COLOR][/SIZE][/CENTER]

[COLOR=#FF8C00][B]Upgrading from v2.0 or earlier?[/B][/COLOR]

[LIST]
[*][B]Old configs automatically migrate[/B] on first load - no manual work needed!
[*][B]Language files are generated automatically[/B] on first run
[*][B]Custom item integration[/B] requires Oraxen 1.195.1+ or MMOItems 6.10.1+
[*][B]All your data is preserved[/B] - no data loss during migration
[*][B]Backup recommended[/B] before updating (always good practice)
[/LIST]

[CENTER][SIZE=6][COLOR=#20B2AA][B]📦 DEPENDENCIES[/B][/COLOR][/SIZE][/CENTER]

[COLOR=#006400][B][SIZE=4]Required:[/SIZE][/B][/COLOR]
[LIST]
[*][B]Paper 1.21.10+[/B] (or Spigot/Pufferfish)
[*][B]Java 17+[/B]
[/LIST]

[COLOR=#006400][B][SIZE=4]Optional (Enhanced Features):[/SIZE][/B][/COLOR]
[LIST]
[*][B]Oraxen 1.195.1+[/B] - For custom items in biome requirements
[*][B]MMOItems 6.10.1+[/B] - Alternative custom items plugin support
[*][B]FancyHolograms 2.4.2+[/B] - For enhanced hologram displays
[*][B]Vault 1.7+[/B] - For money-based economy integration
[/LIST]

[CENTER][SIZE=6][COLOR=#9932CC][B]🚀 INSTALLATION[/B][/COLOR][/SIZE][/CENTER]

[COLOR=#8B008B][B]Quick Update Steps:[/B][/COLOR]

[LIST=1]
[*]Download [B]Chunklock-2.1.0.jar[/B] from releases
[*]Stop your server
[*]Replace old JAR file in [B]/plugins[/B] folder
[*]Start your server
[*]Configs will auto-migrate on first load
[*]Language files will be generated automatically
[*]Configure new features as desired
[*]Done! Enjoy v2.1.0
[/LIST]

[COLOR=#8B008B][B]New Installation:[/B][/COLOR]

[LIST=1]
[*]Download [B]Chunklock-2.1.0.jar[/B]
[*]Place in [B]/plugins[/B] folder
[*]Start server (configs auto-generate)
[*]Run [B]/chunklock setup 30000[/B] to create world
[*]Configure settings in [B]plugins/Chunklock/[/B]
[*]Players can use [B]/chunklock start[/B]
[/LIST]

[CENTER][SIZE=6][COLOR=#FF69B4][B]💡 TIPS & TRICKS[/B][/COLOR][/SIZE][/CENTER]

[COLOR=#FF1493][B]New Features to Try:[/B][/COLOR]

[LIST]
[*][B]Language System:[/B] Customize all messages in [B]lang/en.yml[/B] - make it your own!
[*][B]Custom Items:[/B] Add Oraxen/MMOItems items to [B]biome-unlocks.yml[/B] for unique requirements
[*][B]Modular Config:[/B] Edit specific config files instead of one huge file - much easier!
[*][B]OpenAI (Optional):[/B] Set API key with [B]/chunklock apikey <key>[/B] for AI-powered costs
[*][B]GUI System:[/B] Players can right-click blocks to open unlock GUI - super convenient!
[/LIST]

[CENTER][SIZE=6][COLOR=#FF0000][B]📚 DOCUMENTATION[/B][/COLOR][/SIZE][/CENTER]

[COLOR=#DC143C][B]Need Help?[/B][/COLOR]

[LIST]
[*][B]Complete Wiki:[/B] [URL='https://github.com/Lunary1/chunklock/wiki']Full Documentation[/URL]
[*][B]Installation Guide:[/B] [URL='https://github.com/Lunary1/chunklock/wiki/Installation-Guide']Step-by-Step Setup[/URL]
[*][B]Configuration Reference:[/B] [URL='https://github.com/Lunary1/chunklock/wiki/Configuration-Reference']All Config Options[/URL]
[*][B]Discord Support:[/B] [URL='https://discord.com/invite/QuQ2KuTN2Q']Join Our Community[/URL]
[*][B]GitHub Issues:[/B] [URL='https://github.com/Lunary1/chunklock/issues']Report Bugs[/URL]
[/LIST]

[CENTER][SIZE=5][COLOR=#228B22][B]🎉 Thank you for using Chunklock![/B][/COLOR][/SIZE]

[SIZE=4][I]We hope you enjoy the new features in v2.1.0![/I][/SIZE]

[SIZE=3][COLOR=#808080]Questions? Issues? Join our Discord or open a GitHub issue![/COLOR][/SIZE][/CENTER]

