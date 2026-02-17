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
import me.chunklock.ChunklockPlugin;
import me.chunklock.config.LanguageKeys;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.PlayerProgressTracker;
import me.chunklock.managers.TeamManager;
import me.chunklock.ui.UnlockGuiStateManager.PendingUnlock;
import me.chunklock.util.message.MessageUtil;
import org.bukkit.Particle;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
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
        
        // Evaluate chunk and calculate requirements using unified cost system
        var evaluation = chunkLockManager.evaluateChunk(playerId, chunk);
        Biome biome = evaluation.biome;
        UUID teamId = teamManager.getTeamLeader(playerId);
        boolean contested = chunkLockManager.isContestedChunk(chunk, teamId);

        // Use EconomyManager for unified cost calculation (same as validation)
        var paymentRequirement = economyManager.calculateRequirement(player, chunk, biome, evaluation);
        
        // Convert PaymentRequirement to UnlockRequirement for backward compatibility with GUI builder
        BiomeUnlockRegistry.UnlockRequirement requirement;
        if (paymentRequirement.getType() == me.chunklock.economy.EconomyManager.EconomyType.VAULT) {
            // For vault, we still need a material requirement for the builder (it will use paymentRequirement internally)
            // Use a placeholder - the builder will use paymentRequirement for display
            requirement = new BiomeUnlockRegistry.UnlockRequirement(Material.EMERALD, (int) paymentRequirement.getVaultCost());
        } else {
            requirement = new BiomeUnlockRegistry.UnlockRequirement(
                paymentRequirement.getMaterial(), 
                paymentRequirement.getMaterialAmount()
            );
        }
        
        // Apply contested chunk cost multiplier
        if (contested) {
            double multiplier = chunkLockManager.getContestedCostMultiplier();
            if (paymentRequirement.getType() == me.chunklock.economy.EconomyManager.EconomyType.VAULT) {
                // Adjust vault cost
                double adjustedCost = paymentRequirement.getVaultCost() * multiplier;
                paymentRequirement = new me.chunklock.economy.EconomyManager.PaymentRequirement(adjustedCost);
            } else {
                // Adjust material amount
                int adjustedAmount = (int) Math.ceil(requirement.amount() * multiplier);
                requirement = new BiomeUnlockRegistry.UnlockRequirement(requirement.material(), adjustedAmount);
                paymentRequirement = new me.chunklock.economy.EconomyManager.PaymentRequirement(
                    requirement.material(), adjustedAmount);
            }
            
            // Enhanced contested notification
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("multiplier", String.format("%.1f", multiplier));
            String contestedTitle = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_CONTESTED_TITLE);
            String contestedMsg = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_CONTESTED_MULTIPLIER, placeholders);
            player.sendMessage(Component.text(contestedTitle + " ").color(NamedTextColor.GOLD)
                .append(Component.text(contestedMsg).color(NamedTextColor.YELLOW)));
            
            // Play warning sound
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }

        // Build and open the GUI - pass paymentRequirement for unified cost display
        Inventory inventory = builder.build(player, chunk, evaluation, requirement, paymentRequirement, economyManager, biomeUnlockRegistry);
        
        // Store state - use paymentRequirement for consistency
        PendingUnlock pendingUnlock = new PendingUnlock(chunk, biome, requirement, contested);
        pendingUnlock.setPaymentRequirement(paymentRequirement); // Store unified cost
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
        String helpTitle = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_HELP_TITLE);
        player.sendMessage(Component.text("   " + helpTitle).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.empty());
        
        String processTitle = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_HELP_PROCESS_TITLE);
        player.sendMessage(Component.text(processTitle).color(NamedTextColor.YELLOW));
        String step1 = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_HELP_STEP_1);
        player.sendMessage(Component.text(step1).color(NamedTextColor.WHITE));
        String step2 = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_HELP_STEP_2);
        player.sendMessage(Component.text(step2).color(NamedTextColor.WHITE));
        String step3 = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_HELP_STEP_3);
        player.sendMessage(Component.text(step3).color(NamedTextColor.WHITE));
        
        player.sendMessage(Component.empty());
        String tipsTitle = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_HELP_TIPS_TITLE);
        player.sendMessage(Component.text(tipsTitle).color(NamedTextColor.AQUA));
        String tip1 = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_HELP_TIP_1);
        player.sendMessage(Component.text("  • ").color(NamedTextColor.GRAY)
            .append(Component.text(tip1.substring(3)).color(NamedTextColor.WHITE)));
        String tip2 = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_HELP_TIP_2);
        player.sendMessage(Component.text("  • ").color(NamedTextColor.GRAY)
            .append(Component.text(tip2.substring(3)).color(NamedTextColor.WHITE)));
        String tip3 = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_HELP_TIP_3);
        player.sendMessage(Component.text("  • ").color(NamedTextColor.GRAY)
            .append(Component.text(tip3.substring(3)).color(NamedTextColor.WHITE)));
        String tip4 = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_HELP_TIP_4);
        player.sendMessage(Component.text("  • ").color(NamedTextColor.GRAY)
            .append(Component.text(tip4.substring(3)).color(NamedTextColor.RED)));
        
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
                String guiTitle = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_TITLE);
                if (inventoryTitle.contains(guiTitle) || inventoryTitle.startsWith("🔓")) {
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
            String message = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_SESSION_EXPIRED);
            player.sendMessage(Component.text(message).color(NamedTextColor.RED));
            player.closeInventory();
            logger.warning("No pending unlock state for player " + player.getName());
            return;
        }

        UUID teamId = teamManager.getTeamLeader(playerId);

        // Check if state is expired
        if (state.isExpired()) {
            String message = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_SESSION_EXPIRED);
            player.sendMessage(Component.text(message).color(NamedTextColor.RED));
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
            String message = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_ALREADY_UNLOCKED);
            player.sendMessage(Component.text(message).color(NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
            player.closeInventory();
            stateManager.cleanupPlayer(playerId);
            return;
        }

        try {
            // Validate contested chunk claims
            if (state.contested && !progressTracker.canClaimContested(teamId, chunkLockManager.getMaxContestedClaimsPerDay())) {
                String limitMsg = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_CONTESTED_LIMIT);
                player.sendMessage(Component.text(limitMsg).color(NamedTextColor.RED));
                String tipMsg = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_CONTESTED_LIMIT_TIP);
                player.sendMessage(Component.text(tipMsg).color(NamedTextColor.YELLOW));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                return;
            }
            
            // Use stored paymentRequirement if available, otherwise recalculate
            me.chunklock.economy.EconomyManager.PaymentRequirement paymentRequirement = state.getPaymentRequirement();
            if (paymentRequirement == null) {
                // Fallback: recalculate if not stored (backward compatibility)
                var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), state.chunk);
                paymentRequirement = economyManager.calculateRequirement(player, state.chunk, state.biome, evaluation);
            }
            
            // Check if we should use Vault economy or materials
            if (economyManager != null && economyManager.getCurrentType() == me.chunklock.economy.EconomyManager.EconomyType.VAULT 
                && economyManager.isVaultAvailable()) {
                
                if (!economyManager.canAfford(player, paymentRequirement)) {
                    handleInsufficientFunds(player, paymentRequirement, economyManager);
                    return;
                }
                
                // Execute money-based unlock
                executeMoneyUnlock(player, state, teamId, paymentRequirement);
                
            } else {
                // Use material-based unlock (default)
                // Use stored paymentRequirement if available, otherwise recalculate
                me.chunklock.economy.EconomyManager.PaymentRequirement materialPaymentRequirement = state.getPaymentRequirement();
                if (materialPaymentRequirement == null) {
                    // Fallback: recalculate if not stored
                    var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), state.chunk);
                    materialPaymentRequirement = economyManager.calculateRequirement(player, state.chunk, state.biome, evaluation);
                }
                
                // Validate using unified canAfford check (consistent with display)
                if (!economyManager.canAfford(player, materialPaymentRequirement)) {
                    // Find specific missing vanilla item for accurate error message
                    Material requiredMaterial = materialPaymentRequirement.getMaterial();
                    int requiredAmount = materialPaymentRequirement.getMaterialAmount();
                    for (var req : materialPaymentRequirement.getItemRequirements()) {
                        if (req instanceof me.chunklock.economy.items.VanillaItemRequirement vanillaReq
                            && !req.hasInInventory(player)) {
                            requiredMaterial = vanillaReq.getMaterial();
                            requiredAmount = vanillaReq.getAmount();
                            break;
                        }
                    }
                    if (requiredMaterial == null) {
                        requiredMaterial = Material.AIR;
                    }
                    int playerHas = countPlayerItems(player, requiredMaterial);
                    handleInsufficientItems(player, playerHas, requiredAmount, requiredMaterial);
                    return;
                }

                if (debugLogging) {
                    logger.info("Item validation passed for " + player.getName());
                }

                // Execute material-based unlock using unified cost
                executeMaterialUnlock(player, state, teamId);
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing unlock for " + player.getName(), e);
            String message = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_ERROR);
            player.sendMessage(Component.text(message).color(NamedTextColor.RED));
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
        String title = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_INSUFFICIENT_FUNDS_TITLE);
        player.sendMessage(Component.text("   " + title).color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━").color(NamedTextColor.DARK_RED));
        player.sendMessage(Component.empty());
        
        String formattedBalance = economyManager.getVaultService().format(playerBalance);
        String formattedCost = economyManager.getVaultService().format(requiredCost);
        String formattedNeeded = economyManager.getVaultService().format(needed);
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("cost", formattedCost);
        String requiredMsg = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_INSUFFICIENT_FUNDS_REQUIRED, placeholders);
        player.sendMessage(Component.text(requiredMsg).color(NamedTextColor.WHITE));
        
        placeholders.put("balance", formattedBalance);
        String balanceMsg = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_INSUFFICIENT_FUNDS_BALANCE, placeholders);
        player.sendMessage(Component.text(balanceMsg).color(NamedTextColor.RED));
        
        placeholders.put("needed", formattedNeeded);
        String missingMsg = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_INSUFFICIENT_FUNDS_MISSING, placeholders);
        player.sendMessage(Component.text(missingMsg).color(NamedTextColor.YELLOW));
        
        player.sendMessage(Component.empty());
        String tipMsg = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_INSUFFICIENT_FUNDS_TIP);
        String actionMsg = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_INSUFFICIENT_FUNDS_ACTION);
        player.sendMessage(Component.text(tipMsg).color(NamedTextColor.AQUA)
            .append(Component.text(" " + actionMsg).color(NamedTextColor.WHITE)));
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
                String message = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_PAYMENT_FAILED);
                player.sendMessage(Component.text(message).color(NamedTextColor.RED));
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
            String message = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_PAYMENT_PROCESSING_FAILED);
            player.sendMessage(Component.text(message).color(NamedTextColor.RED));
            return;
        }

        // Complete the unlock process
        finishUnlock(player, state, teamId);
    }
    
    /**
     * Execute material-based unlock process using unified cost calculation.
     */
    private void executeMaterialUnlock(Player player, PendingUnlock state, UUID teamId) {
        try {
            // Pre-unlock effects
            player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1.0f, 1.0f);
            
            // Get unified payment requirement (same as display and validation)
            me.chunklock.economy.EconomyManager.PaymentRequirement paymentRequirement = state.getPaymentRequirement();
            if (paymentRequirement == null) {
                // Fallback: recalculate if not stored
                var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), state.chunk);
                paymentRequirement = economyManager.calculateRequirement(player, state.chunk, state.biome, evaluation);
            }
            
            // Process payment - consume items from inventory
            var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), state.chunk);
            if (!economyManager.processPayment(player, paymentRequirement, state.biome, evaluation)) {
                String message = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_PAYMENT_FAILED);
                player.sendMessage(Component.text(message).color(NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                return;
            }
            
            if (debugLogging) {
                logger.info("Consumed items for " + player.getName() + 
                    " using EconomyManager.processPayment");
            }
            
        } catch (Exception e) {
            logger.warning("Material payment processing failed: " + e.getMessage());
            String message = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_PAYMENT_PROCESSING_FAILED);
            player.sendMessage(Component.text(message).color(NamedTextColor.RED));
            return;
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
        
        int needed = Math.max(0, required - playerHas);
        
        // Send formatted message
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━").color(NamedTextColor.DARK_RED));
        String title = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_INSUFFICIENT_RESOURCES_TITLE);
        player.sendMessage(Component.text("   " + title).color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━").color(NamedTextColor.DARK_RED));
        player.sendMessage(Component.empty());
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("required", String.valueOf(required));
        placeholders.put("material", formatMaterialName(material));
        String requiredMsg = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_INSUFFICIENT_RESOURCES_REQUIRED, placeholders);
        player.sendMessage(Component.text(requiredMsg).color(NamedTextColor.WHITE));
        
        placeholders.put("have", String.valueOf(playerHas));
        String haveMsg = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_INSUFFICIENT_RESOURCES_HAVE, placeholders);
        player.sendMessage(Component.text(haveMsg).color(NamedTextColor.RED));
        
        placeholders.put("missing", String.valueOf(needed));
        String missingMsg = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_INSUFFICIENT_RESOURCES_MISSING, placeholders);
        player.sendMessage(Component.text(missingMsg).color(NamedTextColor.YELLOW));
        
        player.sendMessage(Component.empty());
        String tipMsg = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_INSUFFICIENT_RESOURCES_TIP, placeholders);
        player.sendMessage(Component.text(tipMsg).color(NamedTextColor.WHITE));
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

            // Note: Data persistence is handled automatically by ChunkDatabase and PlayerDatabase
            // Both databases commit on save operations (unlockChunk() and incrementUnlockedChunks() already save)

            // Invalidate resource scan cache since owned territory changed
            if (economyManager != null && economyManager.getOwnedChunkScanner() != null) {
                economyManager.getOwnedChunkScanner().invalidateCache(player.getUniqueId());
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
            String message = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_FAILED_COMPLETE);
            player.sendMessage(Component.text(message).color(NamedTextColor.RED));
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
        String title = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_SUCCESS_TITLE);
        player.sendMessage(Component.text("   " + title).color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GREEN));
        player.sendMessage(Component.empty());
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("location", state.chunk.getX() + ", " + state.chunk.getZ());
        String locationMsg = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_SUCCESS_LOCATION, placeholders);
        player.sendMessage(Component.text(locationMsg).color(NamedTextColor.WHITE));
        
        placeholders.put("biome", BiomeUnlockRegistry.getBiomeDisplayName(state.biome));
        String biomeMsg = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_SUCCESS_BIOME, placeholders);
        player.sendMessage(Component.text(biomeMsg).color(NamedTextColor.YELLOW));
        
        placeholders.put("amount", String.valueOf(state.requirement.amount()));
        placeholders.put("material", formatMaterialName(state.requirement.material()));
        String consumedMsg = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_SUCCESS_CONSUMED, placeholders);
        player.sendMessage(Component.text(consumedMsg).color(NamedTextColor.AQUA));
        
        if (state.contested) {
            player.sendMessage(Component.empty());
            String contestedMsg = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_SUCCESS_CONTESTED);
            player.sendMessage(Component.text(contestedMsg).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        }
        
        player.sendMessage(Component.empty());
        String successMsg = MessageUtil.getMessage(LanguageKeys.GUI_UNLOCK_SUCCESS_MESSAGE);
        player.sendMessage(Component.text(successMsg).color(NamedTextColor.GREEN));
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