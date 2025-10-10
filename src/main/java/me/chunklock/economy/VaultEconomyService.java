package me.chunklock.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Vault economy integration for Chunklock.
 * Provides money-based chunk unlocking as an alternative to material-based costs.
 */
public class VaultEconomyService {
    
    private final JavaPlugin plugin;
    private Economy economy;
    private boolean vaultAvailable = false;
    
    public VaultEconomyService(JavaPlugin plugin) {
        this.plugin = plugin;
        setupEconomy();
    }
    
    /**
     * Initialize Vault economy integration
     */
    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found - economy features disabled");
            return false;
        }
        
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("Vault found but no economy plugin detected");
            return false;
        }
        
        economy = rsp.getProvider();
        vaultAvailable = economy != null;
        
        if (vaultAvailable) {
            plugin.getLogger().info("Vault economy integration enabled with " + economy.getName());
        } else {
            plugin.getLogger().warning("Failed to initialize Vault economy");
        }
        
        return vaultAvailable;
    }
    
    /**
     * Check if Vault economy is available
     */
    public boolean isVaultAvailable() {
        return vaultAvailable && economy != null;
    }
    
    /**
     * Get the economy provider name
     */
    public String getEconomyName() {
        return isVaultAvailable() ? economy.getName() : "None";
    }
    
    /**
     * Check if player has enough money for the cost
     */
    public boolean hasEnoughMoney(Player player, double cost) {
        if (!isVaultAvailable()) {
            return false;
        }
        
        try {
            return economy.has(player, cost);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error checking player balance for " + player.getName(), e);
            return false;
        }
    }
    
    /**
     * Get player's current balance
     */
    public double getBalance(Player player) {
        if (!isVaultAvailable()) {
            return 0.0;
        }
        
        try {
            return economy.getBalance(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error getting balance for " + player.getName(), e);
            return 0.0;
        }
    }
    
    /**
     * Withdraw money from player's account
     */
    public boolean withdrawMoney(Player player, double cost) {
        if (!isVaultAvailable()) {
            return false;
        }
        
        try {
            var response = economy.withdrawPlayer(player, cost);
            if (response.transactionSuccess()) {
                plugin.getLogger().fine("Withdrew " + economy.format(cost) + " from " + player.getName());
                return true;
            } else {
                plugin.getLogger().warning("Failed to withdraw " + economy.format(cost) + " from " + 
                    player.getName() + ": " + response.errorMessage);
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error withdrawing money from " + player.getName(), e);
            return false;
        }
    }
    
    /**
     * Format currency amount for display
     */
    public String format(double amount) {
        if (!isVaultAvailable()) {
            return String.format("%.2f", amount);
        }
        
        try {
            return economy.format(amount);
        } catch (Exception e) {
            return String.format("%.2f", amount);
        }
    }
    
    /**
     * Get currency name (singular)
     */
    public String getCurrencyName() {
        if (!isVaultAvailable()) {
            return "money";
        }
        
        try {
            return economy.currencyNameSingular();
        } catch (Exception e) {
            return "money";
        }
    }
    
    /**
     * Get currency name (plural)
     */
    public String getCurrencyNamePlural() {
        if (!isVaultAvailable()) {
            return "money";
        }
        
        try {
            return economy.currencyNamePlural();
        } catch (Exception e) {
            return "money";
        }
    }
    
    /**
     * Reload the economy service (useful after config changes)
     */
    public void reload() {
        setupEconomy();
    }
}