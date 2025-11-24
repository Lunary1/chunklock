package me.chunklock.debug;

import me.chunklock.commands.SubCommand;
import me.chunklock.ChunklockPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * Debug command to test OpenAI ChatGPT integration
 * Usage: /chunklock test-openai
 */
public class OpenAITestCommand extends SubCommand {
    
    public OpenAITestCommand() {
        super("test-openai", "chunklock.admin.test", true);
    }
    
    @Override
    public String getDescription() {
        return "Test the OpenAI ChatGPT integration";
    }
    
    @Override
    public String getUsage() {
        return "/chunklock test-openai";
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            sender.sendMessage("┬ºcThis command can only be used by players!");
            return true;
        }
        
        // Try to get the OpenAI agent from the plugin
        ChunklockPlugin plugin = ChunklockPlugin.getInstance();
        if (plugin == null) {
            sender.sendMessage("┬ºcPlugin instance not available!");
            return true;
        }
        
        sender.sendMessage("┬º6Testing OpenAI ChatGPT integration...");
        
        try {
            // Test economy manager initialization
            me.chunklock.economy.EconomyManager economyManager = plugin.getEconomyManager();
            sender.sendMessage("§a✅ Economy Manager: Initialized");
            
            // Test current chunk analysis
            org.bukkit.Chunk currentChunk = player.getLocation().getChunk();
            me.chunklock.managers.ChunkEvaluator.ChunkValueData evaluation = 
                plugin.getChunkEvaluator().evaluateChunk(player.getUniqueId(), currentChunk);
            
            sender.sendMessage("┬º7Current chunk analysis:");
            sender.sendMessage("§7  • Biome: " + me.chunklock.util.world.BiomeUtil.getBiomeName(evaluation.biome));
            sender.sendMessage("§7  • Difficulty: " + evaluation.difficulty.name());
            sender.sendMessage("§7  • Score: " + evaluation.score);
            
            // Test cost calculation
            me.chunklock.economy.EconomyManager.PaymentRequirement requirement = 
                economyManager.calculateRequirement(player, currentChunk, evaluation.biome, evaluation);
            
            if (requirement.getType() == me.chunklock.economy.EconomyManager.EconomyType.VAULT) {
                sender.sendMessage("§a✅ Vault cost calculation: $" + requirement.getVaultCost());
            } else {
                sender.sendMessage("§a✅ Material cost calculation: " + requirement.getMaterialAmount() + "x " + me.chunklock.util.item.MaterialUtil.getMaterialName(requirement.getMaterial()));
            }
            
            sender.sendMessage("§a✅ OpenAI integration test completed successfully!");
            sender.sendMessage("┬º7Check console logs for detailed AI processing information.");
            
        } catch (IllegalStateException e) {
            sender.sendMessage("§c❌ Economy Manager: " + e.getMessage());
            sender.sendMessage("§7The economy manager failed to initialize properly.");
            sender.sendMessage("§7Check server console for errors during plugin startup.");
        } catch (Exception e) {
            sender.sendMessage("§c❌ Test failed: " + e.getMessage());
            plugin.getLogger().warning("OpenAI test command failed for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return true;
    }
}
