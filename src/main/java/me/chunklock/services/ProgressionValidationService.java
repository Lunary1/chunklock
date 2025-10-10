package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkLockManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Level;

/**
 * Service to prevent players from getting trapped in "impossible chunk" scenarios.
 * Ensures progression is always possible by validating unlock requirements and providing fallback options.
 */
public class ProgressionValidationService {
    
    private final ChunklockPlugin plugin;
    private final ChunkLockManager chunkLockManager;
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    
    // Materials that can be obtained without needing to unlock chunks (basic survival)
    private static final Set<Material> BASIC_OBTAINABLE_MATERIALS = Set.of(
        // Basic blocks
        Material.DIRT, Material.GRASS_BLOCK, Material.STONE, Material.COBBLESTONE,
        Material.GRAVEL, Material.SAND, Material.CLAY_BALL,
        
        // Basic wood (most worlds have some trees)
        Material.OAK_LOG, Material.OAK_PLANKS, Material.STICK,
        
        // Food and farming
        Material.WHEAT, Material.WHEAT_SEEDS, Material.BREAD, Material.APPLE,
        Material.CARROT, Material.POTATO, Material.BEETROOT, Material.SUGAR_CANE,
        
        // Basic ores (surface level)
        Material.COAL, Material.IRON_INGOT, Material.COPPER_INGOT,
        
        // Crafted items
        Material.WOODEN_PICKAXE, Material.WOODEN_AXE, Material.WOODEN_SHOVEL,
        Material.STONE_PICKAXE, Material.STONE_AXE, Material.STONE_SHOVEL,
        
        // Common drops
        Material.STRING, Material.BONE, Material.FEATHER, Material.LEATHER,
        Material.WHITE_WOOL, Material.COBWEB
    );
    
    // Emergency fallback materials - always available as last resort
    private static final Map<Biome, Material> EMERGENCY_FALLBACKS = Map.of(
        Biome.OCEAN, Material.DIRT,
        Biome.DEEP_OCEAN, Material.DIRT,
        Biome.FROZEN_OCEAN, Material.DIRT,
        Biome.LUKEWARM_OCEAN, Material.DIRT,
        Biome.WARM_OCEAN, Material.DIRT,
        Biome.COLD_OCEAN, Material.DIRT,
        Biome.DEEP_COLD_OCEAN, Material.DIRT,
        Biome.DEEP_LUKEWARM_OCEAN, Material.DIRT,
        Biome.DEEP_FROZEN_OCEAN, Material.DIRT
    );
    
    public ProgressionValidationService(ChunklockPlugin plugin) {
        this.plugin = plugin;
        this.chunkLockManager = plugin.getChunkLockManager();
        this.biomeUnlockRegistry = plugin.getBiomeUnlockRegistry();
    }
    
    /**
     * Validates that a player can progress from their current position.
     * Returns true if progression is possible, false if intervention is needed.
     */
    public boolean validatePlayerProgression(Player player) {
        try {
            Location playerLocation = player.getLocation();
            Chunk currentChunk = playerLocation.getChunk();
            
            // Get surrounding chunks (3x3 area)
            List<Chunk> surroundingChunks = getSurroundingChunks(currentChunk, 1);
            
            // Check if player can unlock any surrounding chunk
            for (Chunk chunk : surroundingChunks) {
                if (!chunkLockManager.isLocked(chunk)) {
                    continue; // Already unlocked
                }
                
                if (canPlayerUnlockChunk(player, chunk)) {
                    return true; // Found at least one unlockable chunk
                }
            }
            
            // If no surrounding chunks can be unlocked, check broader area
            List<Chunk> extendedArea = getSurroundingChunks(currentChunk, 2);
            for (Chunk chunk : extendedArea) {
                if (!chunkLockManager.isLocked(chunk)) {
                    continue;
                }
                
                if (canPlayerUnlockChunk(player, chunk)) {
                    return true; // Found unlockable chunk in extended area
                }
            }
            
            // No progression possible - intervention needed
            plugin.getLogger().warning("Player " + player.getName() + " has no valid progression options at " + 
                playerLocation.getBlockX() + "," + playerLocation.getBlockZ());
            return false;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error validating progression for " + player.getName(), e);
            return true; // Assume progression is possible if validation fails
        }
    }
    
    /**
     * Checks if a player can unlock a specific chunk with their current resources or obtainable materials.
     */
    private boolean canPlayerUnlockChunk(Player player, Chunk chunk) {
        try {
            // Initialize and evaluate the chunk
            chunkLockManager.initializeChunk(chunk, player.getUniqueId());
            var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
            
            // Get unlock requirement
            var requirement = biomeUnlockRegistry.calculateRequirement(player, evaluation.biome, evaluation.score);
            
            // Check if player already has the materials
            if (hasEnoughMaterials(player, requirement)) {
                return true;
            }
            
            // Check if the required material is obtainable without unlocking more chunks
            if (BASIC_OBTAINABLE_MATERIALS.contains(requirement.material())) {
                return true;
            }
            
            // Check if there's an emergency fallback for this biome
            if (EMERGENCY_FALLBACKS.containsKey(evaluation.biome)) {
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Error checking chunk unlock for " + player.getName(), e);
            return false;
        }
    }
    
    /**
     * Provides emergency assistance to a player who cannot progress.
     */
    public void provideEmergencyAssistance(Player player) {
        try {
            plugin.getLogger().info("Providing emergency assistance to " + player.getName());
            
            Location playerLocation = player.getLocation();
            Chunk currentChunk = playerLocation.getChunk();
            
            // Find the closest chunk that can be made unlockable with emergency materials
            Chunk targetChunk = findEmergencyUnlockableChunk(player, currentChunk);
            
            if (targetChunk != null) {
                // Evaluate the target chunk
                var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), targetChunk);
                
                // Provide emergency materials or alternative unlock method
                Material emergencyMaterial = EMERGENCY_FALLBACKS.getOrDefault(evaluation.biome, Material.DIRT);
                int emergencyAmount = calculateEmergencyAmount(player, evaluation.biome, evaluation.score);
                
                // Give the player the emergency materials
                ItemStack emergencyItems = new ItemStack(emergencyMaterial, emergencyAmount);
                player.getInventory().addItem(emergencyItems);
                
                // Notify the player
                player.sendMessage("§6⚠ Emergency assistance provided!");
                player.sendMessage("§eYou have been given " + emergencyAmount + "x " + 
                    formatMaterialName(emergencyMaterial) + " to unlock nearby chunks.");
                player.sendMessage("§7This emergency system prevents you from getting stuck.");
                
                plugin.getLogger().info("Gave " + player.getName() + " emergency materials: " + 
                    emergencyAmount + "x " + emergencyMaterial);
                
            } else {
                // Last resort: force unlock a nearby chunk
                forceUnlockNearbyChunk(player, currentChunk);
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error providing emergency assistance to " + player.getName(), e);
        }
    }
    
    /**
     * Finds a chunk that can be unlocked with emergency materials.
     */
    private Chunk findEmergencyUnlockableChunk(Player player, Chunk centerChunk) {
        List<Chunk> nearbyChunks = getSurroundingChunks(centerChunk, 2);
        
        for (Chunk chunk : nearbyChunks) {
            if (!chunkLockManager.isLocked(chunk)) {
                continue;
            }
            
            try {
                var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
                
                // Prefer chunks that have emergency fallbacks or use basic materials
                var requirement = biomeUnlockRegistry.calculateRequirement(player, evaluation.biome, evaluation.score);
                
                if (BASIC_OBTAINABLE_MATERIALS.contains(requirement.material()) || 
                    EMERGENCY_FALLBACKS.containsKey(evaluation.biome)) {
                    return chunk;
                }
                
            } catch (Exception e) {
                continue;
            }
        }
        
        // If no suitable chunk found, return the first locked chunk
        for (Chunk chunk : nearbyChunks) {
            if (chunkLockManager.isLocked(chunk)) {
                return chunk;
            }
        }
        
        return null;
    }
    
    /**
     * Force unlocks a nearby chunk as last resort.
     */
    private void forceUnlockNearbyChunk(Player player, Chunk centerChunk) {
        List<Chunk> nearbyChunks = getSurroundingChunks(centerChunk, 1);
        
        for (Chunk chunk : nearbyChunks) {
            if (chunkLockManager.isLocked(chunk)) {
                UUID teamId = plugin.getTeamManager().getTeamLeader(player.getUniqueId());
                chunkLockManager.unlockChunk(chunk, teamId);
                
                player.sendMessage("§c⚠ EMERGENCY UNLOCK!");
                player.sendMessage("§eChunk " + chunk.getX() + "," + chunk.getZ() + " has been force-unlocked to prevent you from getting stuck.");
                
                plugin.getLogger().warning("Force-unlocked chunk " + chunk.getX() + "," + chunk.getZ() + 
                    " for " + player.getName() + " (emergency assistance)");
                return;
            }
        }
    }
    
    /**
     * Calculates emergency material amount (reduced cost).
     */
    private int calculateEmergencyAmount(Player player, Biome biome, int score) {
        var normalRequirement = biomeUnlockRegistry.calculateRequirement(player, biome, score);
        
        // Emergency assistance uses 50% of normal cost with minimum of 1
        return Math.max(1, normalRequirement.amount() / 2);
    }
    
    /**
     * Gets chunks in a square area around the center chunk.
     */
    private List<Chunk> getSurroundingChunks(Chunk centerChunk, int radius) {
        List<Chunk> chunks = new ArrayList<>();
        World world = centerChunk.getWorld();
        
        int centerX = centerChunk.getX();
        int centerZ = centerChunk.getZ();
        
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                if (x == centerX && z == centerZ) {
                    continue; // Skip center chunk
                }
                
                try {
                    Chunk chunk = world.getChunkAt(x, z);
                    chunks.add(chunk);
                } catch (Exception e) {
                    // Skip chunks that can't be loaded
                }
            }
        }
        
        return chunks;
    }
    
    /**
     * Checks if player has enough materials for a requirement.
     */
    private boolean hasEnoughMaterials(Player player, BiomeUnlockRegistry.UnlockRequirement requirement) {
        return player.getInventory().containsAtLeast(new ItemStack(requirement.material(), 1), requirement.amount());
    }
    
    /**
     * Formats material name for display.
     */
    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();
        
        for (String word : words) {
            if (formatted.length() > 0) formatted.append(" ");
            formatted.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }
        
        return formatted.toString();
    }
    
    /**
     * Checks if a player might be stuck and triggers assistance if needed.
     * Should be called periodically or when player attempts fail.
     */
    public void checkAndAssistPlayer(Player player) {
        if (!validatePlayerProgression(player)) {
            provideEmergencyAssistance(player);
        }
    }
    
    /**
     * Manual trigger for admins to help stuck players.
     */
    public void forceAssistPlayer(Player player) {
        plugin.getLogger().info("Manual emergency assistance triggered for " + player.getName());
        provideEmergencyAssistance(player);
    }
}
