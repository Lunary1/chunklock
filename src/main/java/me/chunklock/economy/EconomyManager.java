package me.chunklock.economy;

import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.PlayerProgressTracker;
import me.chunklock.ChunklockPlugin;
import me.chunklock.economy.calculation.CostCalculationStrategy;
import me.chunklock.economy.calculation.TraditionalVaultStrategy;
import me.chunklock.economy.calculation.TraditionalMaterialStrategy;
import me.chunklock.economy.calculation.ResourceBasedMaterialStrategy;
import me.chunklock.economy.calculation.OwnedChunkScanner;
import me.chunklock.util.world.BiomeUtil;
import me.chunklock.util.item.MaterialUtil;
import me.chunklock.economy.items.ItemRequirement;
import me.chunklock.economy.items.VanillaItemRequirement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Unified economy manager that handles both material-based and vault-based payments.
 * Allows admins to choose their preferred economy type via configuration.
 */
public class EconomyManager {
    
    public enum EconomyType {
        MATERIALS("materials"),
        VAULT("vault");
        
        private final String configName;
        
        EconomyType(String configName) {
            this.configName = configName;
        }
        
        public String getConfigName() {
            return configName;
        }
        
        public static EconomyType fromString(String type) {
            for (EconomyType economyType : values()) {
                if (economyType.configName.equalsIgnoreCase(type)) {
                    return economyType;
                }
            }
            return MATERIALS; // Default fallback
        }
    }
    
    /**
     * Represents a payment requirement for unlocking a chunk.
     * Supports both material-based (multiple items) and vault-based payments.
     */
    public static class PaymentRequirement {
        private final EconomyType type;
        private final List<ItemRequirement> itemRequirements; // For materials mode
        private final double vaultCost; // For vault mode
        
        // Material-based requirement with multiple items
        public PaymentRequirement(List<ItemRequirement> itemRequirements) {
            this.type = EconomyType.MATERIALS;
            this.itemRequirements = itemRequirements != null ? new ArrayList<>(itemRequirements) : new ArrayList<>();
            this.vaultCost = 0.0;
        }
        
        // Material-based requirement (backward compatibility - single material)
        public PaymentRequirement(Material material, int amount) {
            this.type = EconomyType.MATERIALS;
            this.itemRequirements = new ArrayList<>();
            if (material != null && amount > 0) {
                this.itemRequirements.add(new VanillaItemRequirement(material, amount));
            }
            this.vaultCost = 0.0;
        }
        
        // Vault-based requirement
        public PaymentRequirement(double cost) {
            this.type = EconomyType.VAULT;
            this.itemRequirements = Collections.emptyList();
            this.vaultCost = cost;
        }
        
        public EconomyType getType() { return type; }
        public double getVaultCost() { return vaultCost; }
        
        /**
         * Get all item requirements (for materials mode)
         */
        public List<ItemRequirement> getItemRequirements() {
            return Collections.unmodifiableList(itemRequirements);
        }
        
        /**
         * Get primary display material for backward compatibility.
         * Returns the first vanilla material found, or null if none.
         */
        public Material getMaterial() {
            for (ItemRequirement req : itemRequirements) {
                if (req instanceof VanillaItemRequirement) {
                    return ((VanillaItemRequirement) req).getMaterial();
                }
            }
            return null;
        }
        
        /**
         * Get primary display amount for backward compatibility.
         * Returns the amount of the first vanilla material found, or 0 if none.
         */
        public int getMaterialAmount() {
            for (ItemRequirement req : itemRequirements) {
                if (req instanceof VanillaItemRequirement) {
                    return req.getAmount();
                }
            }
            return 0;
        }
        
        /**
         * Get primary display requirement for backward compatibility.
         * Returns the first item requirement, or null if none.
         */
        public ItemRequirement getPrimaryDisplay() {
            return itemRequirements.isEmpty() ? null : itemRequirements.get(0);
        }
    }
    
    private final ChunklockPlugin plugin;
    private final VaultEconomyService vaultService;
    private final ChunkPriceRerollService rerollService;
    private final BiomeUnlockRegistry biomeRegistry;
    private final PlayerProgressTracker progressTracker;
    
    // Config objects stored as fields for efficient access
    private me.chunklock.config.modular.EconomyConfig economyConfig;
    
    // Material-to-vault converter
    private MaterialVaultConverter materialConverter;
    
    // Resource scanner for resource-based cost mode
    private OwnedChunkScanner ownedChunkScanner;
    
    // Calculation strategy (selected based on economy type and AI enabled state)
    private CostCalculationStrategy calculationStrategy;

    // How long a main-thread caller will wait for the committed-price lookup before giving up
    // and recalculating. Short on purpose: the lookup normally hits an in-memory cache, and a
    // slow disk must never stall the server tick. Missing the commitment costs price
    // stability for one view; blocking the main thread costs everyone their TPS.
    private static final long COMMITTED_PRICE_LOOKUP_TIMEOUT_MS = 50L;
    
    private EconomyType currentType;
    private boolean materialsEnabled;
    private boolean vaultFallbackEnabled;
    private boolean resourceScanMode;
    
    // Vault configuration
    private double baseCost;
    private double costPerUnlocked;
    
    public EconomyManager(ChunklockPlugin plugin, BiomeUnlockRegistry biomeRegistry, 
                         PlayerProgressTracker progressTracker, ChunkEvaluator chunkEvaluator) {
        this.plugin = plugin;
        this.biomeRegistry = biomeRegistry;
        this.progressTracker = progressTracker;
        this.vaultService = new VaultEconomyService(plugin);
        this.rerollService = new ChunkPriceRerollService(plugin, vaultService);
        
        // Load configs once from ConfigManager
        me.chunklock.config.ConfigManager configManager = plugin.getConfigManager();
        this.economyConfig = configManager.getModularEconomyConfig();
        
        // Initialize material converter
        this.materialConverter = new MaterialVaultConverter(plugin, economyConfig);
        
        // Initialize owned chunk scanner for resource-scan mode
        this.ownedChunkScanner = new OwnedChunkScanner(plugin, plugin.getChunkDatabase(), plugin.getTeamManager());
        
        loadConfiguration();
    }
    
    /**
     * Load economy configuration from modular config files
     */
    public void loadConfiguration() {
        // Reload configs if they were updated
        me.chunklock.config.ConfigManager configManager = plugin.getConfigManager();
        this.economyConfig = configManager.getModularEconomyConfig();
        
        // Get economy type
        String typeString = economyConfig != null ? economyConfig.getEconomyType() : "materials";
        currentType = EconomyType.fromString(typeString);
        
        // Material settings
        materialsEnabled = economyConfig != null ? economyConfig.isMaterialsEnabled() : true;
        vaultFallbackEnabled = economyConfig != null ? economyConfig.isVaultFallbackEnabled() : false;
        resourceScanMode = economyConfig != null ? economyConfig.isResourceScanMode() : false;

        if (currentType == EconomyType.MATERIALS && !resourceScanMode) {
            plugin.getLogger().warning("Materials mode is configured without resource-scan. Enabling deterministic resource-scan fallback to prevent progression deadlocks.");
            resourceScanMode = true;
        }
        
        // Configure owned chunk scanner from config
        if (ownedChunkScanner != null && economyConfig != null) {
            ownedChunkScanner.setMinAbundance(economyConfig.getResourceScanMinAbundance());
            ownedChunkScanner.setCacheTtlMs(economyConfig.getResourceScanCacheDuration() * 1000L);
        }
        
        // Vault settings
        if (rerollService != null && economyConfig != null) {
            rerollService.setCooldownMinutes(economyConfig.getRerollCooldownMinutes());
        }

        baseCost = economyConfig != null ? economyConfig.getVaultBaseCost() : 100.0;
        costPerUnlocked = economyConfig != null ? economyConfig.getVaultCostPerUnlocked() : 25.0;
        
        plugin.getLogger().info("Economy Configuration:");
        plugin.getLogger().info("  Type: " + currentType.getConfigName());
        plugin.getLogger().info("  Vault available: " + vaultService.isVaultAvailable());
        plugin.getLogger().info("  Resource-scan mode: " + resourceScanMode);
        plugin.getLogger().info("  Base cost: $" + baseCost);
        plugin.getLogger().info("  Cost per unlocked: $" + costPerUnlocked);
        
        // Select appropriate calculation strategy
        selectCalculationStrategy();
        
        plugin.getLogger().info("Economy type set to: " + currentType.getConfigName() + 
            (currentType == EconomyType.VAULT && !vaultService.isVaultAvailable() ? 
                " (Vault not available, falling back to materials)" : ""));
    }
    
    /**
     * Select the appropriate calculation strategy based on economy type and AI enabled state
     */
    private void selectCalculationStrategy() {
        if (currentType == EconomyType.VAULT) {
            calculationStrategy = new TraditionalVaultStrategy(plugin, economyConfig,
                biomeRegistry, progressTracker, baseCost, costPerUnlocked);
        } else {
            if (resourceScanMode && ownedChunkScanner != null) {
                // Resource-scan mode: cost based on blocks in player's owned chunks
                ResourceBasedMaterialStrategy resourceStrategy = new ResourceBasedMaterialStrategy(
                    plugin, ownedChunkScanner, biomeRegistry, progressTracker);
                if (economyConfig != null) {
                    resourceStrategy.setBaseCost(economyConfig.getResourceScanBaseCost());
                    resourceStrategy.setMaxCost(economyConfig.getResourceScanMaxCost());
                    resourceStrategy.setMinCost(economyConfig.getResourceScanMinCost());
                }
                calculationStrategy = resourceStrategy;
            } else {
                calculationStrategy = new TraditionalMaterialStrategy(plugin, biomeRegistry, progressTracker);
            }
        }
    }
    
    /**
     * Calculate payment requirement for a chunk unlock
     */
    public PaymentRequirement calculateRequirement(Player player, org.bukkit.Chunk chunk, Biome biome, 
                                                 ChunkEvaluator.ChunkValueData evaluation) {
        // If vault type is selected but not available, fall back to materials
        EconomyType effectiveType = currentType;
        if (currentType == EconomyType.VAULT && !vaultService.isVaultAvailable()) {
            plugin.getLogger().warning("Vault economy selected but not available, falling back to materials");
            effectiveType = EconomyType.MATERIALS;
            // Temporarily switch strategy for this calculation
            CostCalculationStrategy originalStrategy = calculationStrategy;
            if (resourceScanMode && ownedChunkScanner != null) {
                ResourceBasedMaterialStrategy resourceStrategy = new ResourceBasedMaterialStrategy(
                    plugin, ownedChunkScanner, biomeRegistry, progressTracker);
                if (economyConfig != null) {
                    resourceStrategy.setBaseCost(economyConfig.getResourceScanBaseCost());
                    resourceStrategy.setMaxCost(economyConfig.getResourceScanMaxCost());
                    resourceStrategy.setMinCost(economyConfig.getResourceScanMinCost());
                }
                calculationStrategy = resourceStrategy;
            } else {
                calculationStrategy = new TraditionalMaterialStrategy(plugin, biomeRegistry, progressTracker);
            }
            PaymentRequirement result = calculationStrategy.calculate(player, chunk, biome, evaluation);
            calculationStrategy = originalStrategy;
            return result;
        }
        
        plugin.getLogger().fine("Calculating requirement for " + player.getName() +
            " - Type: " + effectiveType.getConfigName() +
            ", Biome: " + BiomeUtil.getBiomeName(biome) +
            ", Difficulty: " + evaluation.difficulty.name() +
            ", Score: " + evaluation.score);

        // A price the player has already been shown is a commitment (#83). Honour it before
        // recalculating anything.
        PaymentRequirement committed = getCommittedRequirement(player, chunk);
        if (committed != null) {
            return committed;
        }

        PaymentRequirement calculated = calculationStrategy.calculate(player, chunk, biome, evaluation);
        commitRequirement(player, chunk, biome, evaluation, calculated);
        return calculated;
    }

    /**
     * Look up a previously committed requirement for this chunk, or null if there is none.
     *
     * <p>The lookup is asynchronous underneath, but this method is called from the main
     * thread by GUI and hologram paths, so it waits briefly rather than restructuring every
     * caller. A miss, a timeout, or a storage error all return null, which simply means
     * "calculate it" - the commitment is a guarantee about price stability, never a reason
     * to block the server or fail an unlock.</p>
     */
    private PaymentRequirement getCommittedRequirement(Player player, org.bukkit.Chunk chunk) {
        if (chunk == null) {
            return null;
        }
        try {
            var costDatabase = plugin.getCostDatabase();
            String configHash = costDatabase.generateConfigHash();
            return costDatabase.getCachedCost(player, chunk, configHash)
                .get(COMMITTED_PRICE_LOOKUP_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            plugin.getLogger().fine("Committed-price lookup timed out for " + player.getName()
                + "; recalculating");
            return null;
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Committed-price lookup failed; recalculating", e);
            return null;
        }
    }

    /**
     * Store a freshly calculated requirement so it becomes this chunk's committed price.
     *
     * <p>Storage failures are logged and swallowed: an unstored price degrades to the old
     * recalculate-every-time behaviour, which is worse but not broken.</p>
     */
    private void commitRequirement(Player player, org.bukkit.Chunk chunk, Biome biome,
                                   ChunkEvaluator.ChunkValueData evaluation,
                                   PaymentRequirement requirement) {
        if (chunk == null || requirement == null) {
            return;
        }
        try {
            var costDatabase = plugin.getCostDatabase();
            costDatabase.storeCost(player, chunk, requirement,
                BiomeUtil.getBiomeName(biome),
                evaluation.difficulty.name(), evaluation.score,
                false, "", costDatabase.generateConfigHash());
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Failed to commit chunk price; "
                + "price will be recalculated on next view", e);
        }
    }

    /**
     * Discard this chunk's committed price so the next calculation produces a new one.
     *
     * <p>Called after a completed unlock, and after
     * {@link me.chunklock.economy.ChunkPriceRerollService} has taken payment for a
     * deliberate re-roll.</p>
     */
    public void clearCommittedRequirement(Player player, org.bukkit.Chunk chunk) {
        if (chunk == null) {
            return;
        }
        try {
            plugin.getCostDatabase().clearStoredCost(player, chunk);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Failed to clear committed chunk price", e);
        }
    }
    
    
    
    /**
     * Check if player can afford the payment.
     * For material-based economy, validates ALL requirements (vanilla + custom items).
     */
    public boolean canAfford(Player player, PaymentRequirement requirement) {
        if (requirement.getType() == EconomyType.VAULT) {
            return vaultService.hasEnoughMoney(player, requirement.getVaultCost());
        } else {
            // Check all item requirements
            for (ItemRequirement req : requirement.getItemRequirements()) {
                if (!req.hasInInventory(player)) {
                    return false;
                }
            }
            return true;
        }
    }
    
    /**
     * Process the payment
     */
    public boolean processPayment(Player player, PaymentRequirement requirement, Biome biome, 
                                ChunkEvaluator.ChunkValueData evaluation) {
        if (requirement.getType() == EconomyType.VAULT) {
            return processVaultPayment(player, requirement.getVaultCost());
        } else {
            return processMaterialPayment(player, requirement, biome, evaluation);
        }
    }
    
    /**
     * Process vault payment
     */
    private boolean processVaultPayment(Player player, double cost) {
        return vaultService.withdrawMoney(player, cost);
    }
    
    /**
     * Process material payment.
     * Consumes ALL required items from PaymentRequirement.
     * This is an all-or-nothing operation: if any item is missing, it fails.
     */
    private boolean processMaterialPayment(Player player, PaymentRequirement requirement, 
                                         Biome biome, ChunkEvaluator.ChunkValueData evaluation) {
        // First verify all items are available
        if (!canAfford(player, requirement)) {
            if (vaultFallbackEnabled && vaultService.isVaultAvailable()) {
                double fallbackCost = estimateVaultFallbackCost(requirement);
                return vaultService.withdrawMoney(player, fallbackCost);
            }
            return false;
        }
        
        try {
            // Consume all items from PaymentRequirement
            for (ItemRequirement req : requirement.getItemRequirements()) {
                req.consumeFromInventory(player);
            }
            
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to consume materials for " + player.getName(), e);
            
            return false;
        }
    }

    private double estimateVaultFallbackCost(PaymentRequirement requirement) {
        double total = 0.0;
        for (ItemRequirement req : requirement.getItemRequirements()) {
            if (req instanceof VanillaItemRequirement vanillaRequirement) {
                total += materialConverter.convertToVaultCost(vanillaRequirement.getMaterial(), req.getAmount());
            } else {
                total += req.getAmount() * 5.0;
            }
        }
        return Math.max(1.0, total);
    }
    
    /**
     * Compatibility no-op: unlock attempt telemetry is no longer used for AI pricing.
     */
    public void recordUnlockAttempt(Player player, org.bukkit.Chunk chunk, boolean successful, 
                                   int actualCost, double timeTaken, boolean abandoned) {
        // Intentionally empty
    }
    
    /**
     * Get display components for the payment requirement
     */
    public Component getRequirementDisplay(PaymentRequirement requirement) {
        if (requirement.getType() == EconomyType.VAULT) {
            return Component.text("💰 Cost: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(vaultService.format(requirement.getVaultCost()))
                    .color(NamedTextColor.GOLD))
                .decoration(TextDecoration.ITALIC, false);
        } else {
            return Component.text("📦 Required: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(requirement.getMaterialAmount() + "x " + 
                    formatMaterialName(requirement.getMaterial()))
                    .color(NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false);
        }
    }
    
    /**
     * Get affordability status component
     */
    public Component getAffordabilityStatus(Player player, PaymentRequirement requirement) {
        boolean canAfford = canAfford(player, requirement);
        
        if (requirement.getType() == EconomyType.VAULT) {
            double balance = vaultService.getBalance(player);
            Component status = Component.text(canAfford ? "✅ Affordable" : "❌ Insufficient funds")
                .color(canAfford ? NamedTextColor.GREEN : NamedTextColor.RED);
            
            Component balanceInfo = Component.text(" (Balance: " + vaultService.format(balance) + ")")
                .color(NamedTextColor.GRAY);
                
            return status.append(balanceInfo).decoration(TextDecoration.ITALIC, false);
        } else {
            int playerHas = countPlayerItems(player, requirement.getMaterial());
            Component status = Component.text(canAfford ? "✅ Available" : "❌ Missing items")
                .color(canAfford ? NamedTextColor.GREEN : NamedTextColor.RED);
                
            Component countInfo = Component.text(" (" + playerHas + "/" + requirement.getMaterialAmount() + ")")
                .color(NamedTextColor.GRAY);
                
            return status.append(countInfo).decoration(TextDecoration.ITALIC, false);
        }
    }
    
    /**
     * Count items in player inventory
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
     * Format material name for display
     */
    private String formatMaterialName(Material material) {
        return MaterialUtil.formatMaterialName(material);
    }
    
    // Getters
    public EconomyType getCurrentType() { return currentType; }
    public boolean isVaultAvailable() { return vaultService.isVaultAvailable(); }
    public VaultEconomyService getVaultService() { return vaultService; }
    public boolean isMaterialsEnabled() { return materialsEnabled; }
    public boolean isVaultFallbackEnabled() { return vaultFallbackEnabled; }
    public boolean isAiCostingEnabled() { return false; }
    public boolean isResourceScanMode() { return resourceScanMode; }
    public OwnedChunkScanner getOwnedChunkScanner() { return ownedChunkScanner; }

    /**
     * Release per-player calculation state when a player quits, so long-running
     * servers do not accumulate entries for players who have left.
     */
    public void invalidatePlayer(java.util.UUID playerId) {
        if (playerId == null) return;
        if (ownedChunkScanner != null) {
            ownedChunkScanner.invalidateCache(playerId);
        }
        if (calculationStrategy instanceof ResourceBasedMaterialStrategy resourceStrategy) {
            resourceStrategy.invalidatePlayer(playerId);
        }
    }

    /**
     * Record that a player completed an unlock paying the given requirement, so the
     * resource-scan strategy can vary what it asks for next time.
     *
     * <p>Only completed unlocks advance that history. Calculating a cost for display -
     * opening the unlock GUI, drawing a hologram, pre-calculating adjacent chunks - must
     * leave it untouched, or the requirement shown for a chunk changes as the player looks
     * at it (issue #82).</p>
     */
    /**
     * The re-roll policy for committed chunk prices (#83).
     *
     * <p>Lives here because it needs the Vault service, which this class already owns, and
     * because every caller that can trigger a re-roll already holds an EconomyManager.</p>
     */
    public ChunkPriceRerollService getRerollService() {
        return rerollService;
    }

    /**
     * Whether a re-roll could actually hand this player a different material.
     *
     * <p>A player whose territory yields exactly one obtainable material has nothing to
     * re-roll into: selection is deterministic (#82), so recalculating returns what it just
     * returned no matter what they pay. The GUI uses this to grey the button out and
     * {@link #tryRerollChunkPrice} to refuse the click.</p>
     *
     * <p>Only the resource-scan strategy derives requirements from owned chunks. Every other
     * mode picks from biome configuration, where the question does not arise - so they report
     * true and behave exactly as before.</p>
     *
     * <p>The chunk is passed through because under #86 a re-roll walks down <em>that chunk's</em>
     * ranked materials rather than the player's whole territory, so the count has to be asked
     * of the same chunk being priced. Passing null asks the owned-chunk question, which is
     * what callers without a chunk in hand want.</p>
     */
    public boolean hasRerollAlternatives(Player player, org.bukkit.Chunk chunk) {
        if (player == null) {
            return false;
        }
        if (!(calculationStrategy instanceof ResourceBasedMaterialStrategy resourceStrategy)) {
            return true;
        }
        return resourceStrategy.countSelectableCandidates(player, chunk) > 1;
    }

    /** As above, without a specific chunk. */
    public boolean hasRerollAlternatives(Player player) {
        return hasRerollAlternatives(player, null);
    }

    /**
     * Charge for and perform a deliberate re-roll of a chunk's committed price.
     *
     * <p>The cost is a cooldown on servers without Vault, and escalating currency where
     * Vault is present - see {@link ChunkPriceRerollService}. Payment is taken first; only
     * then is the stored requirement discarded, so a failed payment cannot hand out a free
     * re-roll.</p>
     *
     * <p>Refuses outright when the player has only one material to be asked for. The GUI
     * greys the button out in that case, but this check is the one that matters: an
     * already-open inventory still holds a clickable button, so guarding only the rendering
     * would still charge a player whose last alternative disappeared while the screen was
     * open.</p>
     *
     * @return true if the player paid and the chunk's price was cleared
     */
    public boolean tryRerollChunkPrice(Player player, org.bukkit.Chunk chunk, double chunkVaultPrice) {
        if (rerollService == null || player == null || chunk == null) {
            return false;
        }

        if (!hasRerollAlternatives(player, chunk)) {
            return false;
        }

        // Capture what is being rejected before the stored price is discarded.
        PaymentRequirement rejected = getCommittedRequirement(player, chunk);

        if (!rerollService.tryConsumeRerollCost(player, chunk, chunkVaultPrice)) {
            return false;
        }

        // Record the rejection BEFORE clearing, so the recalculation that follows sees it.
        //
        // Without this the re-roll does nothing visible: selectMaterial is deterministic
        // (the #82 fix), so clearing a price and recalculating from unchanged inputs returns
        // the identical material. The player pays and sees no change. Advancing the
        // anti-repeat history is what actually moves selection to a different candidate.
        if (rejected != null && calculationStrategy instanceof ResourceBasedMaterialStrategy resourceStrategy) {
            Material rejectedMaterial = rejected.getMaterial();
            if (rejectedMaterial != null) {
                resourceStrategy.recordRejectedSelection(player.getUniqueId(), rejectedMaterial);
            }
        }

        clearCommittedRequirement(player, chunk);
        return true;
    }

    public void recordCompletedUnlock(Player player, PaymentRequirement requirement) {
        if (player == null || requirement == null) return;
        if (!(calculationStrategy instanceof ResourceBasedMaterialStrategy resourceStrategy)) {
            return;
        }
        Material paid = requirement.getMaterial();
        if (paid == null) {
            return;
        }
        resourceStrategy.recordUnlockSelection(player.getUniqueId(), paid);
    }

    /**
     * Reload configuration
     */
    public void reload() {
        loadConfiguration();
        vaultService.reload();
        if (materialConverter != null) {
            materialConverter.reload();
        }
        if (ownedChunkScanner != null) {
            ownedChunkScanner.clearCache();
        }
    }
    
    /**
     * Reload configuration (alias for reload method for consistency with other components)
     */
    public void reloadConfiguration() {
        reload();
    }
}
