package me.chunklock.managers;

import me.chunklock.ChunklockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.logging.Level;

/**
 * Manages chunk purchase rewards system.
 * Executes console commands when players reach purchase milestones in specific biomes.
 * 
 * Rewards are configured per biome and per purchase milestone.
 * When a player purchases a chunk in a biome, all reward thresholds at or below
 * their new total purchase count are executed (once per threshold).
 * 
 * Configuration format:
 * REWARDS:
 *   PLAINS:
 *     1:
 *       - "say Player %player_name% bought their first Plains chunk!"
 *       - "give %player_name% diamond 1"
 *     10:
 *       - "say Player %player_name% bought their 10th Plains chunk!"
 */
public class RewardManager {
    
    private final ChunklockPlugin plugin;
    
    // Structure: biome -> (milestone -> [commands])
    private final Map<String, Map<Integer, List<String>>> rewards = new HashMap<>();
    
    // Track which rewards have been executed for each player-biome combo to avoid duplicates
    // Structure: playerUUID -> (biome -> maxMilestoneReached)
    private final Map<UUID, Map<String, Integer>> playerBiomeRewards = new HashMap<>();
    
    public RewardManager(ChunklockPlugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }
    
    /**
     * Load reward configuration from config.yml
     */
    private void loadConfiguration() {
        try {
            FileConfiguration config = plugin.getConfig();
            ConfigurationSection rewardsSection = config.getConfigurationSection("REWARDS");
            
            if (rewardsSection == null) {
                plugin.getLogger().fine("No REWARDS section found in config.yml - reward system inactive");
                return;
            }
            
            // Parse biome -> milestone -> commands structure
            for (String biomeName : rewardsSection.getKeys(false)) {
                ConfigurationSection biomeSection = rewardsSection.getConfigurationSection(biomeName);
                if (biomeSection == null) continue;
                
                Map<Integer, List<String>> milestones = new HashMap<>();
                
                for (String milestoneStr : biomeSection.getKeys(false)) {
                    try {
                        int milestone = Integer.parseInt(milestoneStr);
                        List<String> commands = biomeSection.getStringList(milestoneStr);
                        
                        if (!commands.isEmpty()) {
                            milestones.put(milestone, new ArrayList<>(commands));
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid milestone number '" + milestoneStr + 
                            "' in REWARDS section for biome '" + biomeName + "'");
                    }
                }
                
                if (!milestones.isEmpty()) {
                    rewards.put(biomeName, milestones);
                    plugin.getLogger().fine("Loaded " + milestones.size() + " reward milestones for biome: " + biomeName);
                }
            }
            
            if (rewards.isEmpty()) {
                plugin.getLogger().info("✅ No rewards configured in config.yml");
            } else {
                plugin.getLogger().info("✅ Loaded rewards for " + rewards.size() + " biomes");
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error loading REWARDS configuration", e);
        }
    }
    
    /**
     * Check and execute any matching rewards after a chunk purchase in a biome.
     * 
     * @param player The player who purchased the chunk
     * @param biomeName The biome name where the chunk was purchased (e.g., "PLAINS")
     * @param newPurchaseCount The player's new total purchase count in that biome
     */
    public void checkAndExecuteRewards(Player player, String biomeName, int newPurchaseCount) {
        // No rewards configured
        if (rewards.isEmpty()) {
            return;
        }
        
        // This biome has no rewards
        Map<Integer, List<String>> biomeRewards = rewards.get(biomeName);
        if (biomeRewards == null || biomeRewards.isEmpty()) {
            return;
        }
        
        // Track which rewards this player has already received
        Map<String, Integer> playerRewards = playerBiomeRewards.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        int maxPreviousMilestone = playerRewards.getOrDefault(biomeName, 0);
        
        // Find all milestones between previous max and new count
        List<Integer> applicableMilestones = new ArrayList<>();
        for (int milestone : biomeRewards.keySet()) {
            if (milestone > maxPreviousMilestone && milestone <= newPurchaseCount) {
                applicableMilestones.add(milestone);
            }
        }
        
        // Sort milestones ascending so they execute in order
        Collections.sort(applicableMilestones);
        
        // Execute rewards for each new milestone reached
        for (int milestone : applicableMilestones) {
            List<String> commands = biomeRewards.get(milestone);
            if (commands != null) {
                for (String command : commands) {
                    executeRewardCommand(player, command);
                }
            }
            // Update tracking to highest milestone executed
            playerRewards.put(biomeName, milestone);
        }
    }
    
    /**
     * Execute a single reward command with placeholder substitution.
     * 
     * @param player The player who triggered the reward
     * @param command The command to execute (with placeholders)
     */
    private void executeRewardCommand(Player player, String command) {
        try {
            // Substitute %player_name% placeholder
            String processedCommand = command.replace("%player_name%", player.getName());
            
            // Execute as console command
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
            
            plugin.getLogger().fine("Executed reward command for " + player.getName() + ": " + processedCommand);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, 
                "Error executing reward command for player " + player.getName(), e);
        }
    }
    
    /**
     * Manually test a reward for a player.
     * Admin command: /chunklock reward test <player> <biome> <count>
     * 
     * @param player The player to test for
     * @param biomeName The biome name
     * @param purchaseCount The purchase count to simulate
     */
    public void testReward(Player player, String biomeName, int purchaseCount) {
        // Clear tracking for this player-biome to allow re-execution
        Map<String, Integer> playerRewards = playerBiomeRewards.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        playerRewards.put(biomeName, 0);
        
        // Check and execute rewards
        checkAndExecuteRewards(player, biomeName, purchaseCount);
    }
    
    /**
     * Get information about available rewards for a biome.
     * 
     * @param biomeName The biome name
     * @return List of milestone descriptions, or empty list if no rewards
     */
    public List<String> getRewardInfo(String biomeName) {
        Map<Integer, List<String>> biomeRewards = rewards.get(biomeName);
        if (biomeRewards == null || biomeRewards.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> info = new ArrayList<>();
        List<Integer> milestones = new ArrayList<>(biomeRewards.keySet());
        Collections.sort(milestones);
        
        for (int milestone : milestones) {
            List<String> commands = biomeRewards.get(milestone);
            info.add("Milestone " + milestone + ": " + commands.size() + " command(s)");
        }
        
        return info;
    }
    
    /**
     * Reload rewards configuration from config.yml.
     * Clears all previously loaded rewards and player tracking.
     */
    public void reload() {
        rewards.clear();
        playerBiomeRewards.clear();
        loadConfiguration();
        plugin.getLogger().info("✅ Rewards configuration reloaded");
    }
}
