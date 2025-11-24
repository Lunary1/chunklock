package me.chunklock.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.PlayerProgressTracker;
import me.chunklock.managers.TeamManager;
import me.chunklock.ui.UnlockGuiStateManager.PendingUnlock;
import me.chunklock.ChunklockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Particle;
import org.bukkit.Location;
import java.util.logging.Level;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Main coordinator for the chunk unlock GUI system.
 * Handles opening GUIs, coordinating with other systems, and managing the unlock process.
 */
public class UnlockGui {
    
    // Core dependencies
    private final ChunklockPlugin plugin;
    private final java.util.logging.Logger logger;
    private final ChunkLockManager chunkLockManager;
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    private final PlayerProgressTracker progressTracker;
    private final TeamManager teamManager;
    private final me.chunklock.economy.EconomyManager economyManager;
    
    // UI components
    private final UnlockGuiBuilder builder;
    private final UnlockGuiStateManager stateManager;
    
    // Debug configuration
    private boolean debugLogging;
    
    // Constants - Updated for new GUI
    public static final String GUI_TITLE_PREFIX = "🔓 Unlock Chunk";
    private static final int UNLOCK_BUTTON_SLOT = 31; // Updated slot for new layout
    
    public UnlockGui(ChunklockPlugin plugin,
                     ChunkLockManager chunkLockManager, 
                     BiomeUnlockRegistry biomeUnlockRegistry,
                     PlayerProgressTracker progressTracker,
                     TeamManager teamManager,
                     me.chunklock.economy.EconomyManager economyManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.chunkLockManager = chunkLockManager;
        this.biomeUnlockRegistry = biomeUnlockRegistry;
        this.progressTracker = progressTracker;
        this.teamManager = teamManager;
        this.economyManager = economyManager;
        this.builder = new UnlockGuiBuilder();
        this.stateManager = new UnlockGuiStateManager();
        
        // Load debug configuration
        loadDebugConfiguration();
    }
    
    /**
     * Load debug configuration from config.yml
     */
    private void loadDebugConfiguration() {
        // Use modular debug config
        me.chunklock.config.modular.DebugConfig debugConfig = plugin.getConfigManager().getDebugConfig();
        boolean masterDebug = debugConfig != null ? debugConfig.isEnabled() : false;
        this.debugLogging = masterDebug && (debugConfig != null ? debugConfig.isUnlockGuiDebug() : false);
    }
    
    /**
     * Reload debug configuration (called during plugin reload)
     */
    public void reloadConfiguration() {
        loadDebugConfiguration();
    }    /**
     * Open the unlock GUI for a player looking at a specific chunk.
     */
    public void open(Player player, Chunk chunk) {
        UUID playerId = player.getUniqueId();
        
        // Clean up any existing state first
        stateManager.cleanupPlayer(playerId);
        
        // Evaluate chunk and calculate requirements
        var evaluation = chunkLockManager.evaluateChunk(playerId, chunk);
        Biome biome = evaluation.biome;
        UUID teamId = teamManager.getTeamLeader(playerId);
        boolean contested = chunkLockManager.isContestedChunk(chunk, teamId);

        var requirement = biomeUnlockRegistry.calculateRequirement(player, biome, evaluation.score);
        
        // Apply contested chunk cost multiplier
        if (contested) {
            double multiplier = chunkLockManager.getContestedCostMultiplier();
            int adjustedAmount = (int) Math.ceil(requirement.amount() * multiplier);
            requirement = new BiomeUnlockRegistry.UnlockRequirement(requirement.material(), adjustedAmount);
            
            // Enhanced contested notification
            player.sendMessage(Component.text("⚔ ").color(NamedTextColor.RED)
                .append(Component.text("Contested Chunk! ").color(NamedTextColor.GOLD))
                .append(Component.text("Cost multiplied by ").color(NamedTextColor.YELLOW))
                .append(Component.text("x" + multiplier).color(NamedTextColor.RED)));
            
            // Play warning sound
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }

        // Build and open the GUI
        Inventory inventory = builder.build(player, chunk, evaluation, requirement, economyManager, biomeUnlockRegistry);
        
        // Store state
        PendingUnlock pendingUnlock = new PendingUnlock(chunk, biome, requirement, contested);
        stateManager.setPendingUnlock(playerId, pendingUnlock);
        stateManager.setActiveGui(playerId, inventory);
        
        // Enhanced visual feedback on open
        playGuiOpenEffects(player);
        
        // Debug logging
        if (debugLogging) {
            logger.info("Opening unlock GUI for " + player.getName() + 
                " - chunk " + chunk.getX() + "," + chunk.getZ() + 
                " - required: " + requirement.amount() + "x " + requirement.material() +
                (contested ? " [CONTESTED]" : ""));
        }
        
        player.openInventory(inventory);
    }
    
    /**
     * Play effects when GUI opens
     */
    private void playGuiOpenEffects(Player player) {
        // Play open sound
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 1.2f);
        
        // Spawn some particles around the player
        player.getWorld().spawnParticle(
            Particle.END_ROD,
            player.getLocation().add(0, 1, 0),
            10,
            0.5, 0.5, 0.5,
            0.05
        );
    }
    
    /**
     * Handle inventory click events.
     */
    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        UUID playerId = player.getUniqueId();
        
        // Check if this is our GUI
        if (!isOurGui(playerId, event.getClickedInventory())) {
            return;
        }

        event.setCancelled(true);
        
        int clickedSlot = event.getRawSlot();
        
        // Debug logging
        logger.fine("Player " + player.getName() + 
            " clicked slot " + clickedSlot + " in unlock GUI");
        
        // Handle different slot clicks
        switch (clickedSlot) {
            case UNLOCK_BUTTON_SLOT -> processUnlockAttempt(player);
            case 49 -> { // Help book slot
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                sendHelpMessage(player);
            }
            default -> {
                // Play a subtle click sound for non-interactive slots
                if (clickedSlot >= 0 && clickedSlot < 54) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 2.0f);
                }
            }
        }
    }
    
    /**
     * Send help information to the player
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("   📖 CHUNKLOCK HELP").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.empty());
        
        player.sendMessage(Component.text("🔓 How to Unlock Chunks:").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("  1. ").color(NamedTextColor.GRAY)
            .append(Component.text("Gather the required materials").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  2. ").color(NamedTextColor.GRAY)
            .append(Component.text("Right-click glass borders around locked chunks").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  3. ").color(NamedTextColor.GRAY)
            .append(Component.text("Click the green emerald button when ready").color(NamedTextColor.WHITE)));
        
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("💡 Tips:").color(NamedTextColor.AQUA));
        player.sendMessage(Component.text("  • ").color(NamedTextColor.GRAY)
            .append(Component.text("Different biomes require different materials").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  • ").color(NamedTextColor.GRAY)
            .append(Component.text("Harder chunks (higher score) cost more").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  • ").color(NamedTextColor.GRAY)
            .append(Component.text("Team members share all unlocked chunks").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  • ").color(NamedTextColor.GRAY)
            .append(Component.text("Contested chunks cost 2x resources!").color(NamedTextColor.RED)));
        
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.DARK_GRAY));
    }
    
    /**
     * Handle inventory close events.
     */
    public void handleInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        
        UUID playerId = player.getUniqueId();
        
        // Clean up GUI state when inventory is closed
        if (stateManager.hasActiveGui(playerId)) {
            logger.fine("Cleaning up GUI state for " + player.getName());
            stateManager.clearActiveGui(playerId);
            
            // Play close sound
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 0.6f, 1.0f);
        }
    }
    
    /**
     * Check if an inventory belongs to our GUI system.
     */
    private boolean isOurGui(UUID playerId, Inventory clickedInventory) {
        if (clickedInventory == null) {
            return false;
        }
        
        // Check if this is the player's inventory
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && clickedInventory.equals(player.getInventory())) {
            return false;
        }
        
        // Check if we have an active GUI for this player
        if (stateManager.isPlayerGui(playerId, clickedInventory)) {
            return true;
        }
        
        // Check by title pattern
        if (stateManager.hasPendingUnlock(playerId) && player != null && player.getOpenInventory() != null) {
            try {
                String inventoryTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(player.getOpenInventory().title());
                if (inventoryTitle.startsWith(GUI_TITLE_PREFIX)) {
                    return true;
                }
            } catch (Exception e) {
                logger.fine("Title check failed: " + e.getMessage());
            }
        }
        
        return false;
    }
    
    /**
     * Process an unlock attempt from a player.
     */
    private void processUnlockAttempt(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Get pending unlock state
        PendingUnlock state = stateManager.getPendingUnlock(playerId);
        if (state == null) {
            player.sendMessage(Component.text("❌ Unlock session expired. Please try again.")
                .color(NamedTextColor.RED));
            player.closeInventory();
            logger.warning("No pending unlock state for player " + player.getName());
            return;
        }

        UUID teamId = teamManager.getTeamLeader(playerId);

        // Check if state is expired
        if (state.isExpired()) {
            player.sendMessage(Component.text("❌ Unlock session expired. Please try again.")
                .color(NamedTextColor.RED));
            player.closeInventory();
            stateManager.cleanupPlayer(playerId);
            logger.warning("Expired unlock state for player " + player.getName());
            return;
        }
        
        if (debugLogging) {
            logger.info("Processing unlock for " + player.getName() + 
                " - chunk " + state.chunk.getX() + "," + state.chunk.getZ());
        }

        // Check if chunk is still locked
        if (!chunkLockManager.isLocked(state.chunk)) {
            player.sendMessage(Component.text("✅ Chunk already unlocked!")
                .color(NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
            player.closeInventory();
            stateManager.cleanupPlayer(playerId);
            return;
        }

        try {
            // Validate contested chunk claims
            if (state.contested && !progressTracker.canClaimContested(teamId, chunkLockManager.getMaxContestedClaimsPerDay())) {
                player.sendMessage(Component.text("❌ Contested claim limit reached for today.")
                    .color(NamedTextColor.RED));
                player.sendMessage(Component.text("💡 You can claim more contested chunks tomorrow!")
                    .color(NamedTextColor.YELLOW));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                return;
            }
            
            // Check if we should use Vault economy or materials
            if (economyManager != null && economyManager.getCurrentType() == me.chunklock.economy.EconomyManager.EconomyType.VAULT 
                && economyManager.isVaultAvailable()) {
                
                // Use money-based unlock
                var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), state.chunk);
                var paymentRequirement = economyManager.calculateRequirement(player, state.biome, evaluation);
                
                if (!economyManager.canAfford(player, paymentRequirement)) {
                    handleInsufficientFunds(player, paymentRequirement, economyManager);
                    return;
                }
                
                // Execute money-based unlock
                executeMoneyUnlock(player, state, teamId, paymentRequirement);
                
            } else {
                // Use material-based unlock (default)
                // Re-evaluate chunk to get current score
                var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), state.chunk);
                
                // Check if player has ALL required items (vanilla + custom)
                if (!biomeUnlockRegistry.hasRequiredItems(player, state.biome, evaluation.score)) {
                    // Get first vanilla item for display purposes only
                    int playerHas = countPlayerItems(player, state.requirement.material());
                    int required = state.requirement.amount();
                    handleInsufficientItems(player, playerHas, required, state.requirement.material());
                    return;
                }

                if (debugLogging) {
                    logger.info("Item validation passed for " + player.getName());
                }

                // Execute material-based unlock
                executeMaterialUnlock(player, state, teamId);
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing unlock for " + player.getName(), e);
            player.sendMessage(Component.text("❌ An error occurred while unlocking the chunk.")
                .color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_HURT, 1.0f, 1.0f);
        }
    }
    
    /**
     * Handle case where player doesn't have enough money.
     */
    private void handleInsufficientFunds(Player player,
                                       me.chunklock.economy.EconomyManager.PaymentRequirement requirement,
                                       me.chunklock.economy.EconomyManager economyManager) {
        // Play error sound
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
        
        double playerBalance = economyManager.getVaultService().getBalance(player);
        double requiredCost = requirement.getVaultCost();
        double needed = requiredCost - playerBalance;
        
        // Send formatted message
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━").color(NamedTextColor.DARK_RED));
        player.sendMessage(Component.text("   💸 INSUFFICIENT FUNDS").color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━").color(NamedTextColor.DARK_RED));
        player.sendMessage(Component.empty());
        
        String formattedBalance = economyManager.getVaultService().format(playerBalance);
        String formattedCost = economyManager.getVaultService().format(requiredCost);
        String formattedNeeded = economyManager.getVaultService().format(needed);
        
        player.sendMessage(Component.text("💰 Required: ").color(NamedTextColor.GRAY)
            .append(Component.text(formattedCost).color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("🏦 Your balance: ").color(NamedTextColor.GRAY)
            .append(Component.text(formattedBalance).color(NamedTextColor.RED)));
        player.sendMessage(Component.text("❗ Missing: ").color(NamedTextColor.GRAY)
            .append(Component.text(formattedNeeded).color(NamedTextColor.YELLOW)));
        
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("💡 Tip: ").color(NamedTextColor.AQUA)
            .append(Component.text("Earn more money and try again!").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.empty());
        
        logger.info("Player " + player.getName() + 
            " missing funds: has " + formattedBalance + ", needs " + formattedCost);
    }
    
    /**
     * Execute money-based unlock process.
     */
    private void executeMoneyUnlock(Player player, PendingUnlock state, UUID teamId,
                                  me.chunklock.economy.EconomyManager.PaymentRequirement requirement) {
        try {
            // Pre-unlock effects
            player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1.0f, 1.0f);
            
            // Get current evaluation (required for processPayment)
            var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), state.chunk);
            
            // Process payment
            if (!economyManager.processPayment(player, requirement, state.biome, evaluation)) {
                player.sendMessage(Component.text("❌ Payment failed. Please try again.")
                    .color(NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                return;
            }
            
            if (debugLogging) {
                String formattedCost = economyManager.getVaultService().format(requirement.getVaultCost());
                logger.info("Charged " + formattedCost + 
                    " from " + player.getName() + " for chunk unlock");
            }
            
        } catch (Exception e) {
            logger.warning("Money processing failed: " + e.getMessage());
            player.sendMessage(Component.text("❌ Payment processing failed. Please try again.")
                .color(NamedTextColor.RED));
            return;
        }

        // Complete the unlock process
        finishUnlock(player, state, teamId);
    }
    
    /**
     * Execute material-based unlock process.
     */
    private void executeMaterialUnlock(Player player, PendingUnlock state, UUID teamId) {
        try {
            // Pre-unlock effects
            player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1.0f, 1.0f);
            
            // Get current evaluation (might have changed)
            var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), state.chunk);
            
            // Use the registry's consumption method which handles team integration
            biomeUnlockRegistry.consumeRequiredItem(player, state.biome, evaluation.score);
            
            if (debugLogging) {
                logger.info("Consumed items for " + player.getName() + 
                    " using BiomeUnlockRegistry method");
            }
            
        } catch (Exception e) {
            logger.warning("BiomeUnlockRegistry consumption failed, using fallback: " + e.getMessage());
            
            // Fallback: Manual item removal
            ItemStack requiredStack = new ItemStack(state.requirement.material(), state.requirement.amount());
            player.getInventory().removeItem(requiredStack);
        }

        // Complete the unlock process
        finishUnlock(player, state, teamId);
    }
    
    /**
     * Handle case where player doesn't have enough items.
     */
    private void handleInsufficientItems(Player player, int playerHas, int required, Material material) {
        // Play error sound
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
        
        int needed = required - playerHas;
        
        // Send formatted message
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━").color(NamedTextColor.DARK_RED));
        player.sendMessage(Component.text("   ❌ INSUFFICIENT RESOURCES").color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━").color(NamedTextColor.DARK_RED));
        player.sendMessage(Component.empty());
        
        player.sendMessage(Component.text("📦 Required: ").color(NamedTextColor.GRAY)
            .append(Component.text(required + "x " + formatMaterialName(material)).color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("🎒 You have: ").color(NamedTextColor.GRAY)
            .append(Component.text(playerHas + "x").color(NamedTextColor.RED)));
        player.sendMessage(Component.text("❗ Missing: ").color(NamedTextColor.GRAY)
            .append(Component.text(needed + "x").color(NamedTextColor.YELLOW)));
        
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("💡 Tip: ").color(NamedTextColor.AQUA)
            .append(Component.text("Find more " + formatMaterialName(material) + " and try again!").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.empty());
        
        logger.info("Player " + player.getName() + 
            " missing items: has " + playerHas + ", needs " + required);
    }
    
    /**
     * Complete the unlock process after payment has been processed.
     */
    private void finishUnlock(Player player, PendingUnlock state, UUID teamId) {
        try {
            // Unlock chunk
            chunkLockManager.unlockChunk(state.chunk, teamId);
            if (state.contested) {
                progressTracker.incrementContestedClaims(teamId);
            }
            logger.info("Unlocked chunk " + state.chunk.getX() + 
                "," + state.chunk.getZ() + " for player " + player.getName());

            // CRITICAL: Save all data immediately after unlock to ensure persistence
            try {
                chunkLockManager.saveAll();
                progressTracker.saveAll();
                // playerDataManager is saved by the PlayerListener on join, so persistent spawn is already saved
                logger.fine("Saved chunk and progress data after unlock for " + player.getName());
            } catch (Exception e) {
                logger.warning("Error saving data after unlock: " + e.getMessage());
            }

            // Record team statistics if available
            try {
                var enhancedTeamManager = plugin.getEnhancedTeamManager();
                if (enhancedTeamManager != null) {
                    enhancedTeamManager.recordChunkUnlock(player.getUniqueId(), 
                        BiomeUnlockRegistry.getBiomeDisplayName(state.biome));
                }
            } catch (Exception e) {
                logger.fine("Team statistics recording failed: " + e.getMessage());
            }

            // Play success effects
            playUnlockSuccessEffects(player, state.chunk);

            // Send success message
            sendUnlockSuccessMessage(player, state);

            // Clean up and close GUI
            player.closeInventory();
            stateManager.cleanupPlayer(player.getUniqueId());

            // Trigger related systems
            notifyUnlockSystems(player, state.chunk);
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to finish unlock for " + player.getName(), e);
            player.sendMessage(Component.text("❌ Failed to complete unlock. Please try again.")
                .color(NamedTextColor.RED));
        }
    }
    
    /**
     * Play visual and audio effects for successful unlock
     */
    private void playUnlockSuccessEffects(Player player, Chunk chunk) {
        // Success sound
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
        
        // Firework-style particles at chunk center
        Location chunkCenter = new Location(
            chunk.getWorld(),
            chunk.getX() * 16 + 8,
            player.getLocation().getY() + 2,
            chunk.getZ() * 16 + 8
        );
        
        // Multiple particle bursts
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int count = 0;
            
            @Override
            public void run() {
                if (count >= 3) {
                    return;
                }
                
                me.chunklock.util.world.ParticleUtil.spawnFireworkParticles(
                    chunkCenter,
                    30,
                    4, 2, 4,
                    0.1
                );
                
                me.chunklock.util.world.ParticleUtil.spawnHappyVillagerParticles(
                    player.getLocation().add(0, 1, 0),
                    20,
                    1, 1, 1,
                    0
                );
                
                count++;
            }
        }, 0L, 10L);
    }
    
    /**
     * Send formatted success message
     */
    private void sendUnlockSuccessMessage(Player player, PendingUnlock state) {
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GREEN));
        player.sendMessage(Component.text("   🎉 CHUNK UNLOCKED!").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GREEN));
        player.sendMessage(Component.empty());
        
        player.sendMessage(Component.text("📍 Location: ").color(NamedTextColor.GRAY)
            .append(Component.text(state.chunk.getX() + ", " + state.chunk.getZ()).color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("🌿 Biome: ").color(NamedTextColor.GRAY)
            .append(Component.text(BiomeUnlockRegistry.getBiomeDisplayName(state.biome)).color(NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("📦 Consumed: ").color(NamedTextColor.GRAY)
            .append(Component.text(state.requirement.amount() + "x " + formatMaterialName(state.requirement.material())).color(NamedTextColor.AQUA)));
        
        if (state.contested) {
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("⚔ Contested chunk claimed!").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        }
        
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("✨ You can now build and explore in this chunk!").color(NamedTextColor.GREEN));
        player.sendMessage(Component.empty());
    }
    
    /**
     * Notify other systems that a chunk was unlocked.
     */
    private void notifyUnlockSystems(Player player, Chunk chunk) {
        try {
            // Update borders with comprehensive approach
            var borderManager = plugin.getChunkBorderManager();
            if (borderManager != null) {
                logger.fine("Updating borders after chunk unlock for " + player.getName() + 
                    " at chunk " + chunk.getX() + "," + chunk.getZ());
                
                // Use a delay to ensure the chunk unlock is fully processed first
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        try {
                            // Comprehensive border update approach
                            borderManager.onChunkUnlocked(player, chunk);
                            
                            // Force a border update for all neighboring unlocked chunks
                            World world = chunk.getWorld();
                            
                            for (int dx = -1; dx <= 1; dx++) {
                                for (int dz = -1; dz <= 1; dz++) {
                                    if (dx == 0 && dz == 0) continue;
                                    
                                    try {
                                        Chunk neighbor = world.getChunkAt(chunk.getX() + dx, chunk.getZ() + dz);
                                        
                                        // Initialize neighbor to ensure we have current lock status
                                        plugin.getChunkLockManager().initializeChunk(neighbor, player.getUniqueId());
                                        
                                        // If neighbor is unlocked, refresh its borders
                                        if (!plugin.getChunkLockManager().isLocked(neighbor)) {
                                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                                if (player.isOnline()) {
                                                    borderManager.scheduleBorderUpdate(player);
                                                }
                                            }, (dx + 1) * 3 + (dz + 1));
                                        }
                                    } catch (Exception e) {
                                        logger.fine("Error updating neighbor borders: " + e.getMessage());
                                    }
                                }
                            }
                            
                            logger.fine("Completed comprehensive border update after unlock for " + player.getName());
                        } catch (Exception e) {
                            logger.log(Level.WARNING, 
                                "Error during post-unlock border update for " + player.getName(), e);
                        }
                    }
                }, 10L); // 0.5 second delay
            }
            
            // Update holograms - ENHANCED: Clean up chunk holograms and refresh active set
            try {
                var hologramService = plugin.getHologramService();
                if (hologramService != null) {
                    // Immediately despawn all holograms for this specific chunk
                    hologramService.despawnChunkHolograms(player, chunk);
                    
                    // Schedule refresh of active holograms to update the visible set
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            // Update active hologram set to reflect new unlocked chunk
                            hologramService.updateActiveHologramsForPlayer(player);
                            logger.fine("Updated active holograms for " + player.getName() + 
                                " after unlocking chunk " + chunk.getX() + "," + chunk.getZ());
                        }
                    }, 5L); // 0.25 second delay
                    
                    logger.fine("Stopped hologram updates and scheduled restart after chunk unlock for " + player.getName() + 
                        " at chunk " + chunk.getX() + "," + chunk.getZ());
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, 
                    "Error refreshing holograms after chunk unlock: " + e.getMessage());
            }
            
            // Trigger unlock effects
            try {
                Object effectsManager = null;
                try {
                    effectsManager = ChunklockPlugin.getInstance().getClass().getMethod("getUnlockEffectsManager").invoke(ChunklockPlugin.getInstance());
                } catch (Exception e) {
                    // UnlockEffectsManager doesn't exist yet
                }
                
                if (effectsManager != null) {
                    effectsManager.getClass().getMethod("playUnlockEffects", Player.class, Chunk.class).invoke(effectsManager, player, chunk);
                }
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().fine("Unlock effects not available: " + e.getMessage());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error notifying unlock systems", e);
        }
    }
    
    /**
     * Count how many of a specific material the player has.
     */
    private int countPlayerItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }
    
    /**
     * Format material name for display.
     */
    private String formatMaterialName(Material material) {
        return me.chunklock.util.item.MaterialUtil.formatMaterialName(material);
    }
    
    /**
     * Get statistics about the GUI system.
     */
    public UnlockGuiStateManager.StateStats getStats() {
        return stateManager.getStats();
    }
    
    /**
     * Clean up expired state.
     */
    public int cleanupExpired() {
        return stateManager.cleanupExpired();
    }
}