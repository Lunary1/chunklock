package me.chunklock.economy;

import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.PlayerProgressTracker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

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
    
    private final JavaPlugin plugin;
    private final VaultEconomyService vaultService;
    private final BiomeUnlockRegistry biomeRegistry;
    private final PlayerProgressTracker progressTracker;
    
    private EconomyType currentType;
    private boolean materialsEnabled;
    private boolean vaultFallbackEnabled;
    
    // Vault configuration
    private double baseCost;
    private double costPerUnlocked;
    
    public EconomyManager(JavaPlugin plugin, BiomeUnlockRegistry biomeRegistry, 
                         PlayerProgressTracker progressTracker) {
        this.plugin = plugin;
        this.biomeRegistry = biomeRegistry;
        this.progressTracker = progressTracker;
        this.vaultService = new VaultEconomyService(plugin);
        
        loadConfiguration();
    }
    
    /**
     * Load economy configuration from config.yml
     */
    public void loadConfiguration() {
        FileConfiguration config = plugin.getConfig();
        
        // Get economy type
        String typeString = config.getString("economy.type", "materials");
        currentType = EconomyType.fromString(typeString);
        
        // Material settings
        materialsEnabled = config.getBoolean("economy.materials.enabled", true);
        vaultFallbackEnabled = config.getBoolean("economy.materials.vault-fallback", false);
        
        // Vault settings
        baseCost = config.getDouble("economy.vault.base-cost", 100.0);
        costPerUnlocked = config.getDouble("economy.vault.cost-per-unlocked", 25.0);
        
        plugin.getLogger().info("Economy type set to: " + currentType.getConfigName() + 
            (currentType == EconomyType.VAULT && !vaultService.isVaultAvailable() ? 
                " (Vault not available, falling back to materials)" : ""));
    }
    
    /**
     * Calculate payment requirement for a chunk unlock
     */
    public PaymentRequirement calculateRequirement(Player player, Biome biome, 
                                                 ChunkEvaluator.ChunkValueData evaluation) {
        // If vault type is selected but not available, fall back to materials
        EconomyType effectiveType = currentType;
        if (currentType == EconomyType.VAULT && !vaultService.isVaultAvailable()) {
            effectiveType = EconomyType.MATERIALS;
        }
        
        if (effectiveType == EconomyType.VAULT) {
            return calculateVaultRequirement(player, biome, evaluation);
        } else {
            return calculateMaterialRequirement(player, biome, evaluation);
        }
    }
    
    /**
     * Calculate vault-based payment requirement
     */
    private PaymentRequirement calculateVaultRequirement(Player player, Biome biome, 
                                                       ChunkEvaluator.ChunkValueData evaluation) {
        FileConfiguration config = plugin.getConfig();
        
        // Base calculation
        double cost = baseCost;
        
        // Add progressive cost based on unlocked chunks
        int unlockedCount = progressTracker.getUnlockedChunkCount(player.getUniqueId());
        cost += unlockedCount * costPerUnlocked;
        
        // Apply difficulty multiplier
        String difficultyPath = "economy.vault.difficulty-multipliers." + evaluation.difficulty.name();
        double difficultyMultiplier = config.getDouble(difficultyPath, 1.0);
        cost *= difficultyMultiplier;
        
        // Apply biome multiplier
        String biomePath = "economy.vault.biome-multipliers." + biome.name();
        double biomeMultiplier = config.getDouble(biomePath, 1.0);
        cost *= biomeMultiplier;
        
        // Apply team multiplier if available
        if (biomeRegistry.isTeamIntegrationActive()) {
            double teamMultiplier = biomeRegistry.getTeamCostMultiplier(player);
            cost *= teamMultiplier;
        }
        
        return new PaymentRequirement(cost);
    }
    
    /**
     * Calculate material-based payment requirement
     */
    private PaymentRequirement calculateMaterialRequirement(Player player, Biome biome, 
                                                          ChunkEvaluator.ChunkValueData evaluation) {
        BiomeUnlockRegistry.UnlockRequirement requirement = 
            biomeRegistry.calculateRequirement(player, biome, evaluation.score);
        return new PaymentRequirement(requirement.material(), requirement.amount());
    }
    
    /**
     * Check if player can afford the payment
     */
    public boolean canAfford(Player player, PaymentRequirement requirement) {
        if (requirement.getType() == EconomyType.VAULT) {
            return vaultService.hasEnoughMoney(player, requirement.getVaultCost());
        } else {
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
     * Process material payment
     */
    private boolean processMaterialPayment(Player player, PaymentRequirement requirement, 
                                         Biome biome, ChunkEvaluator.ChunkValueData evaluation) {
        try {
            biomeRegistry.consumeRequiredItem(player, biome, evaluation.score);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to consume materials for " + player.getName(), e);
            
            // Fallback: Manual item removal
            ItemStack requiredStack = new ItemStack(requirement.getMaterial(), requirement.getMaterialAmount());
            return player.getInventory().removeItem(requiredStack).isEmpty();
        }
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
            return Component.text("💎 Required: ")
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