package me.chunklock.economy;

import me.chunklock.ai.OpenAIChunkCostAgent;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.PlayerProgressTracker;
import me.chunklock.ChunklockPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
     * Represents a payment requirement for unlocking a chunk
     */
    public static class PaymentRequirement {
        private final EconomyType type;
        private final Material material;
        private final int materialAmount;
        private final double vaultCost;
        
        // Material-based requirement
        public PaymentRequirement(Material material, int amount) {
            this.type = EconomyType.MATERIALS;
            this.material = material;
            this.materialAmount = amount;
            this.vaultCost = 0.0;
        }
        
        // Vault-based requirement
        public PaymentRequirement(double cost) {
            this.type = EconomyType.VAULT;
            this.material = null;
            this.materialAmount = 0;
            this.vaultCost = cost;
        }
        
        public EconomyType getType() { return type; }
        public Material getMaterial() { return material; }
        public int getMaterialAmount() { return materialAmount; }
        public double getVaultCost() { return vaultCost; }
    }
    
    private final ChunklockPlugin plugin;
    private final VaultEconomyService vaultService;
    private final BiomeUnlockRegistry biomeRegistry;
    private final PlayerProgressTracker progressTracker;
    private final OpenAIChunkCostAgent openAIAgent; // OpenAI ChatGPT integration
    
    private EconomyType currentType;
    private boolean materialsEnabled;
    private boolean vaultFallbackEnabled;
    private boolean aiCostingEnabled;
    
    // Vault configuration
    private double baseCost;
    private double costPerUnlocked;
    
    public EconomyManager(ChunklockPlugin plugin, BiomeUnlockRegistry biomeRegistry, 
                         PlayerProgressTracker progressTracker, ChunkEvaluator chunkEvaluator) {
        this.plugin = plugin;
        this.biomeRegistry = biomeRegistry;
        this.progressTracker = progressTracker;
        this.vaultService = new VaultEconomyService(plugin);
        this.openAIAgent = new OpenAIChunkCostAgent(plugin, chunkEvaluator, biomeRegistry);
        
        loadConfiguration();
    }
    
    /**
     * Load economy configuration from modular config files
     */
    public void loadConfiguration() {
        // Use modular config system
        me.chunklock.config.modular.EconomyConfig economyConfig = null;
        me.chunklock.config.modular.OpenAIConfig openAIConfig = null;
        
        if (plugin instanceof me.chunklock.ChunklockPlugin) {
            me.chunklock.config.ConfigManager configManager = ((me.chunklock.ChunklockPlugin) plugin).getConfigManager();
            economyConfig = configManager.getModularEconomyConfig();
            openAIConfig = configManager.getOpenAIConfig();
        } else {
            economyConfig = new me.chunklock.config.modular.EconomyConfig(plugin);
            openAIConfig = new me.chunklock.config.modular.OpenAIConfig(plugin);
        }
        
        // Get economy type
        String typeString = economyConfig != null ? economyConfig.getEconomyType() : "materials";
        currentType = EconomyType.fromString(typeString);
        
        // Material settings
        materialsEnabled = economyConfig != null ? economyConfig.isMaterialsEnabled() : true;
        vaultFallbackEnabled = economyConfig != null ? economyConfig.isVaultFallbackEnabled() : false;
        
        // Vault settings
        baseCost = economyConfig != null ? economyConfig.getVaultBaseCost() : 100.0;
        costPerUnlocked = economyConfig != null ? economyConfig.getVaultCostPerUnlocked() : 25.0;
        
        // AI settings
        aiCostingEnabled = openAIConfig != null ? openAIConfig.isEnabled() : false;
        
        plugin.getLogger().info("Economy Configuration:");
        plugin.getLogger().info("  Type: " + currentType.getConfigName());
        plugin.getLogger().info("  Vault available: " + vaultService.isVaultAvailable());
        plugin.getLogger().info("  AI costing enabled: " + aiCostingEnabled);
        plugin.getLogger().info("  OpenAI agent available: " + (openAIAgent != null));
        plugin.getLogger().info("  Base cost: $" + baseCost);
        plugin.getLogger().info("  Cost per unlocked: $" + costPerUnlocked);
        
        // Check OpenAI configuration
        if (aiCostingEnabled && openAIAgent != null && openAIConfig != null) {
            String apiKey = openAIConfig.getApiKey();
            boolean transparencyEnabled = openAIConfig.isTransparencyEnabled();
            plugin.getLogger().info("  OpenAI API key set: " + (!apiKey.isEmpty()));
            plugin.getLogger().info("  AI transparency enabled: " + transparencyEnabled);
            
            if (apiKey.isEmpty()) {
                plugin.getLogger().warning("  ⚠️ OpenAI agent enabled but no API key provided!");
                plugin.getLogger().warning("     Add your OpenAI API key to openai.yml");
                aiCostingEnabled = false; // Disable if no API key
            }
        }
        
        plugin.getLogger().info("Economy type set to: " + currentType.getConfigName() + 
            (currentType == EconomyType.VAULT && !vaultService.isVaultAvailable() ? 
                " (Vault not available, falling back to materials)" : "") +
            (aiCostingEnabled ? " with OpenAI ChatGPT optimization" : ""));
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
        }
        
        plugin.getLogger().fine("Calculating requirement for " + player.getName() + 
            " - Type: " + effectiveType.getConfigName() + 
            ", Biome: " + biome.name() + 
            ", Difficulty: " + evaluation.difficulty.name() + 
            ", Score: " + evaluation.score);
        
        if (effectiveType == EconomyType.VAULT) {
            return calculateVaultRequirement(player, chunk, biome, evaluation);
        } else {
            return calculateMaterialRequirement(player, chunk, biome, evaluation);
        }
    }
    
    /**
     * Legacy compatibility method - calculates requirement without AI features
     */
    public PaymentRequirement calculateRequirement(Player player, Biome biome, 
                                                 ChunkEvaluator.ChunkValueData evaluation) {
        // For legacy calls, we won't use AI features since we don't have chunk context
        BiomeUnlockRegistry.UnlockRequirement requirement = 
            biomeRegistry.calculateRequirement(player, biome, evaluation.score);
        return new PaymentRequirement(requirement.material(), requirement.amount());
    }
    
    /**
     * Calculate vault-based payment requirement with optional chunk parameter for OpenAI
     */
    private PaymentRequirement calculateVaultRequirement(Player player, org.bukkit.Chunk chunk, Biome biome, 
                                                       ChunkEvaluator.ChunkValueData evaluation) {
        // Get modular config
        me.chunklock.config.modular.EconomyConfig economyConfig = null;
        if (plugin instanceof me.chunklock.ChunklockPlugin) {
            economyConfig = ((me.chunklock.ChunklockPlugin) plugin).getConfigManager().getModularEconomyConfig();
        } else {
            economyConfig = new me.chunklock.config.modular.EconomyConfig(plugin);
        }
        
        // Try OpenAI integration first if enabled and chunk is available
        if (aiCostingEnabled && openAIAgent != null && chunk != null) {
            try {
                // First, check if we have a cached result from database
                me.chunklock.ChunklockPlugin pluginInstance = (me.chunklock.ChunklockPlugin) plugin;
                me.chunklock.services.ChunkCostDatabase costDatabase = pluginInstance.getCostDatabase();
                String configHash = costDatabase.generateConfigHash();
                
                // Check database cache first (async but should be very fast for recent calculations)
                try {
                    PaymentRequirement cachedResult = costDatabase.getCachedCost(player, chunk, configHash).get();
                    if (cachedResult != null) {
                        plugin.getLogger().fine("Using cached cost from database for player " + player.getName() + ": $" + cachedResult.getVaultCost());
                        return cachedResult;
                    }
                } catch (Exception e) {
                    plugin.getLogger().fine("Database cache miss for player " + player.getName() + ": " + e.getMessage());
                }
                
                // If no cache available, proceed with synchronous calculation
                plugin.getLogger().fine("No cached cost available, calculating synchronously for player " + player.getName());
                plugin.getLogger().fine("No cached cost available, calculating synchronously for player " + player.getName());
                plugin.getLogger().fine("Attempting OpenAI vault cost calculation for player " + player.getName());
                
                OpenAIChunkCostAgent.OpenAICostResult aiResult = openAIAgent.calculateOptimizedCost(player, chunk);
                
                plugin.getLogger().fine("OpenAI result: Material=" + aiResult.getMaterial().name() + 
                    ", Amount=" + aiResult.getAmount() + ", AI Processed=" + aiResult.isAiProcessed() + 
                    ", Explanation=" + aiResult.getExplanation());
                
                // Convert material cost to vault cost using conversion rate
                double baseVaultCost = convertMaterialToVaultCost(aiResult.getMaterial(), aiResult.getAmount());
                
                plugin.getLogger().fine("Converted material cost to vault: " + aiResult.getAmount() + " " + 
                    aiResult.getMaterial().name() + " = $" + baseVaultCost);
                
                // Apply vault-specific multipliers to the AI-suggested base cost
                double cost = baseVaultCost;
                plugin.getLogger().fine("Starting vault cost calculation with base: $" + cost);
                
                // Add progressive cost based on unlocked chunks
                int unlockedCount = progressTracker.getUnlockedChunkCount(player.getUniqueId());
                double progressiveCost = unlockedCount * (costPerUnlocked * 0.5); // Reduced since AI already accounts for difficulty
                cost += progressiveCost;
                plugin.getLogger().fine("After progressive cost (+" + unlockedCount + " chunks, +" + progressiveCost + "): $" + cost);
                
                // Apply difficulty multiplier (reduced impact since AI accounts for this)
                double difficultyMultiplier = economyConfig != null ? 
                    economyConfig.getDifficultyMultiplier(evaluation.difficulty.name()) : 1.0;
                cost *= Math.pow(difficultyMultiplier, 0.7); // Reduced power since AI considers difficulty
                plugin.getLogger().fine("After difficulty multiplier (" + evaluation.difficulty.name() + " = " + difficultyMultiplier + "^0.7): $" + cost);
                
                // Apply biome multiplier (reduced impact since AI accounts for this)
                double biomeMultiplier = economyConfig != null ? 
                    economyConfig.getBiomeMultiplier(biome.name()) : 1.0;
                cost *= Math.pow(biomeMultiplier, 0.7); // Reduced power since AI considers biome
                plugin.getLogger().fine("After biome multiplier (" + biome.name() + " = " + biomeMultiplier + "^0.7): $" + cost);
                
                // Apply team multiplier if available
                if (biomeRegistry.isTeamIntegrationActive()) {
                    double teamMultiplier = biomeRegistry.getTeamCostMultiplier(player);
                    cost *= teamMultiplier;
                    plugin.getLogger().fine("After team multiplier (" + teamMultiplier + "): $" + cost);
                }
                
                // Ensure minimum cost
                cost = Math.max(cost, 10.0); // Minimum $10
                plugin.getLogger().fine("Final AI-optimized vault cost: $" + cost);
                
                // Send AI explanation to player for transparency if AI was actually used
                if (aiResult.isAiProcessed() && !aiResult.getExplanation().isEmpty()) {
                    // Get transparency from modular config
                    me.chunklock.config.modular.OpenAIConfig openAIConfig = null;
                    if (plugin instanceof me.chunklock.ChunklockPlugin) {
                        openAIConfig = ((me.chunklock.ChunklockPlugin) plugin).getConfigManager().getOpenAIConfig();
                    } else {
                        openAIConfig = new me.chunklock.config.modular.OpenAIConfig(plugin);
                    }
                    boolean transparencyEnabled = openAIConfig != null ? openAIConfig.isTransparencyEnabled() : false;
                    if (transparencyEnabled) {
                        player.sendMessage(Component.text("💡 ")
                            .color(NamedTextColor.AQUA)
                            .append(Component.text("AI Cost Analysis: " + aiResult.getExplanation())
                                .color(NamedTextColor.YELLOW))
                            .decoration(TextDecoration.ITALIC, false));
                    }
                }
                
                PaymentRequirement calculatedResult = new PaymentRequirement(cost);
                
                // Store the calculated result in database cache for future use
                try {
                    costDatabase.storeCost(player, chunk, calculatedResult, 
                        biome.name(), evaluation.difficulty.name(), evaluation.score, 
                        aiResult.isAiProcessed(), aiResult.getExplanation(), configHash);
                    plugin.getLogger().fine("Stored AI-calculated cost in database cache for player " + player.getName());
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to store cost in database cache: " + e.getMessage());
                }
                
                return calculatedResult;
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "OpenAI vault cost calculation failed, using traditional method", e);
                // Fall through to traditional calculation
            }
        } else {
            plugin.getLogger().fine("OpenAI vault cost calculation skipped - aiCostingEnabled=" + aiCostingEnabled + 
                ", openAIAgent=" + (openAIAgent != null) + ", chunk=" + (chunk != null));
        }
        
        // Traditional vault calculation (fallback or when AI disabled)
        plugin.getLogger().info("Using traditional vault cost calculation");
        
        // Base calculation
        double cost = baseCost;
        plugin.getLogger().info("Base cost: $" + cost);
        
        // Add progressive cost based on unlocked chunks
        int unlockedCount = progressTracker.getUnlockedChunkCount(player.getUniqueId());
        cost += unlockedCount * costPerUnlocked;
        plugin.getLogger().info("After progressive cost (+" + unlockedCount + " chunks): $" + cost);
        
        // Apply difficulty multiplier
        double difficultyMultiplier = economyConfig != null ? 
            economyConfig.getDifficultyMultiplier(evaluation.difficulty.name()) : 1.0;
        cost *= difficultyMultiplier;
        plugin.getLogger().info("After difficulty multiplier (" + evaluation.difficulty.name() + " = " + difficultyMultiplier + "): $" + cost);
        
        // Apply biome multiplier
        double biomeMultiplier = economyConfig != null ? 
            economyConfig.getBiomeMultiplier(biome.name()) : 1.0;
        cost *= biomeMultiplier;
        plugin.getLogger().info("After biome multiplier (" + biome.name() + " = " + biomeMultiplier + "): $" + cost);
        
        // Apply team multiplier if available
        if (biomeRegistry.isTeamIntegrationActive()) {
            double teamMultiplier = biomeRegistry.getTeamCostMultiplier(player);
            cost *= teamMultiplier;
            plugin.getLogger().info("After team multiplier (" + teamMultiplier + "): $" + cost);
        }
        
        // Ensure minimum cost
        cost = Math.max(cost, 1.0); // Minimum $1
        plugin.getLogger().info("Final traditional vault cost: $" + cost);
        
        PaymentRequirement traditionalResult = new PaymentRequirement(cost);
        
        // Store the traditional calculation result in database cache if chunk is available
        if (chunk != null) {
            try {
                me.chunklock.ChunklockPlugin pluginInstance = (me.chunklock.ChunklockPlugin) plugin;
                me.chunklock.services.ChunkCostDatabase costDatabase = pluginInstance.getCostDatabase();
                String configHash = costDatabase.generateConfigHash();
                
                costDatabase.storeCost(player, chunk, traditionalResult, 
                    biome.name(), evaluation.difficulty.name(), evaluation.score, 
                    false, "", configHash);
                plugin.getLogger().fine("Stored traditional cost in database cache for player " + player.getName());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to store traditional cost in database cache: " + e.getMessage());
            }
        }
        
        return traditionalResult;
    }
    
    /**
     * Convert material cost to equivalent vault cost
     */
    private double convertMaterialToVaultCost(Material material, int amount) {
        // Simple conversion based on material rarity/value
        // This could be made configurable in the future
        double baseValue = switch (material) {
            case DIAMOND -> 50.0;
            case EMERALD -> 45.0;
            case GOLD_INGOT -> 25.0;
            case IRON_INGOT -> 10.0;
            case COPPER_INGOT -> 5.0;
            case COAL -> 2.0;
            case COBBLESTONE -> 0.5;
            default -> 5.0; // Default value for unknown materials
        };
        
        return baseValue * amount;
    }
    
    /**
     * Calculate material-based payment requirement with optional OpenAI optimization
     * NOTE: This returns the first vanilla material for backward compatibility, but
     * hasRequiredItems() should be called to validate ALL items (vanilla + custom)
     */
    private PaymentRequirement calculateMaterialRequirement(Player player, org.bukkit.Chunk chunk, Biome biome, 
                                                          ChunkEvaluator.ChunkValueData evaluation) {
        if (aiCostingEnabled && openAIAgent != null) {
            // Use OpenAI ChatGPT for intelligent cost calculation
            try {
                OpenAIChunkCostAgent.OpenAICostResult aiResult = openAIAgent.calculateOptimizedCost(player, chunk);
                
                // Create enhanced payment requirement with AI explanation
                PaymentRequirement requirement = new PaymentRequirement(aiResult.getMaterial(), aiResult.getAmount());
                
                // Send AI explanation to player for transparency if AI was actually used
                if (aiResult.isAiProcessed() && !aiResult.getExplanation().isEmpty()) {
                    boolean transparencyEnabled = plugin.getConfig().getBoolean("openai-agent.transparency", false);
                    if (transparencyEnabled) {
                        player.sendMessage(Component.text("💡 ")
                            .color(NamedTextColor.AQUA)
                            .append(Component.text(aiResult.getExplanation())
                                .color(NamedTextColor.YELLOW))
                            .decoration(TextDecoration.ITALIC, false));
                    }
                }
                
                return requirement;
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "OpenAI cost calculation failed, using fallback", e);
                // Fall through to traditional calculation
            }
        }
        
        // Traditional calculation (fallback or when AI disabled)
        // Returns the first vanilla material for backward compatibility display
        BiomeUnlockRegistry.UnlockRequirement requirement = 
            biomeRegistry.calculateRequirement(player, biome, evaluation.score);
        return new PaymentRequirement(requirement.material(), requirement.amount());
    }
    
    /**
     * Check if player can afford the payment.
     * For material-based economy, this now validates ALL requirements (vanilla + custom items).
     * The PaymentRequirement is a simplified view; the actual check uses BiomeUnlockRegistry's
     * complete ItemRequirement list.
     */
    public boolean canAfford(Player player, PaymentRequirement requirement) {
        if (requirement.getType() == EconomyType.VAULT) {
            return vaultService.hasEnoughMoney(player, requirement.getVaultCost());
        } else {
            // For materials, we can do a quick check with the first material shown
            // but in practice, the unlock system will check ALL items via BiomeUnlockRegistry.hasRequiredItems()
            ItemStack required = new ItemStack(requirement.getMaterial(), requirement.getMaterialAmount());
            return player.getInventory().containsAtLeast(required, requirement.getMaterialAmount());
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
     * Process material payment and record OpenAI learning data.
     * Consumes ALL required items (vanilla + custom) via BiomeUnlockRegistry.
     * This is an all-or-nothing operation: if any item is missing, it fails.
     */
    private boolean processMaterialPayment(Player player, PaymentRequirement requirement, 
                                         Biome biome, ChunkEvaluator.ChunkValueData evaluation) {
        try {
            biomeRegistry.consumeRequiredItem(player, biome, evaluation.score);
            
            // Record OpenAI learning data for success
            if (aiCostingEnabled && openAIAgent != null) {
                recordPaymentForOpenAI(player, requirement, true, 0, false);
            }
            
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to consume materials for " + player.getName(), e);
            
            // Record OpenAI learning data for failure
            if (aiCostingEnabled && openAIAgent != null) {
                recordPaymentForOpenAI(player, requirement, false, 0, false);
            }
            
            // Fallback: Manual item removal (only for first vanilla item shown in PaymentRequirement)
            ItemStack requiredStack = new ItemStack(requirement.getMaterial(), requirement.getMaterialAmount());
            boolean fallbackSuccess = player.getInventory().removeItem(requiredStack).isEmpty();
            
            if (fallbackSuccess && aiCostingEnabled && openAIAgent != null) {
                recordPaymentForOpenAI(player, requirement, true, 0, false);
            }
            
            return fallbackSuccess;
        }
    }
    
    /**
     * Record unlock attempt data for OpenAI learning
     */
    public void recordUnlockAttempt(Player player, org.bukkit.Chunk chunk, boolean successful, 
                                   int actualCost, double timeTaken, boolean abandoned) {
        if (aiCostingEnabled && openAIAgent != null) {
            openAIAgent.recordUnlockAttempt(player, chunk, successful, actualCost, timeTaken, abandoned);
        }
    }
    
    /**
     * Helper method to record payment data for OpenAI learning
     */
    private void recordPaymentForOpenAI(Player player, PaymentRequirement requirement, 
                                       boolean successful, double timeTaken, boolean abandoned) {
        // This would need chunk context, so we'll implement this when integrated with unlock system
        // For now, just note that the payment was processed
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
        return material.name().toLowerCase().replace('_', ' ');
    }
    
    // Getters
    public EconomyType getCurrentType() { return currentType; }
    public boolean isVaultAvailable() { return vaultService.isVaultAvailable(); }
    public VaultEconomyService getVaultService() { return vaultService; }
    public boolean isMaterialsEnabled() { return materialsEnabled; }
    public boolean isVaultFallbackEnabled() { return vaultFallbackEnabled; }
    public boolean isAiCostingEnabled() { return aiCostingEnabled; }
    
    /**
     * Reload configuration
     */
    public void reload() {
        loadConfiguration();
        vaultService.reload();
    }
    
    /**
     * Reload configuration (alias for reload method for consistency with other components)
     */
    public void reloadConfiguration() {
        reload();
    }
}
