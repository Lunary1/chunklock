package me.chunklock.debug;

import me.chunklock.ChunklockPlugin;
import me.chunklock.hologram.HologramService;
import me.chunklock.managers.ChunkLockManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Debug utility to demonstrate the hologram regression fixes in action.
 * Shows debug logs for both Issue A (eligibility) and Issue B (inventory updates).
 */
public final class HologramDebugDemo {
    
    private final Logger logger;
    private final HologramService hologramService;
    private final ChunkLockManager chunkLockManager;
    
    public HologramDebugDemo(ChunklockPlugin plugin) {
        this.logger = plugin.getLogger();
        this.hologramService = plugin.getHologramService();
        this.chunkLockManager = plugin.getChunkLockManager();
    }
    
    /**
     * Demonstrate Issue A fix: eligibility-based hologram display
     */
    public void demonstrateEligibilityFix(Player player) {
        logger.info("=== ISSUE A DEBUG: Eligibility-Based Hologram Display ===");
        
        World world = player.getWorld();
        Location playerLoc = player.getLocation();
        Chunk currentChunk = world.getChunkAt(playerLoc);
        
        logger.info("Player: " + player.getName());
        logger.info("Current Chunk: " + currentChunk.getX() + ", " + currentChunk.getZ());
        logger.info("World: " + world.getName());
        
        // Check ownership status
        UUID playerId = player.getUniqueId();
        boolean isLocked = chunkLockManager.isLocked(currentChunk);
        UUID owner = chunkLockManager.getChunkOwner(currentChunk);
        boolean isUnlocked = !isLocked && playerId.equals(owner);
        logger.info("Current chunk locked: " + isLocked + ", owner: " + owner + ", unlocked for player: " + isUnlocked);
        
        // Simulate the eligibility calculation that happens in findActiveHologramCandidates()
        logger.info("--- Calculating eligible chunks (unlocked + frontier only) ---");
        
        // This would be called from HologramService.findEligibleChunks()
        int unlockCount = 0;
        int frontierCount = 0;
        
        // Check surrounding chunks in a 5x5 grid
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Chunk chunk = world.getChunkAt(currentChunk.getX() + dx, currentChunk.getZ() + dz);
                boolean isChunkLocked = chunkLockManager.isLocked(chunk);
                UUID chunkOwner = chunkLockManager.getChunkOwner(chunk);
                boolean chunkUnlocked = !isChunkLocked && playerId.equals(chunkOwner);
                
                if (chunkUnlocked) {
                    unlockCount++;
                    logger.fine("Unlocked chunk found: " + chunk.getX() + ", " + chunk.getZ());
                } else {
                    // Check if it's a frontier chunk (adjacent to an unlocked chunk)
                    boolean isFrontier = isFrontierChunk(chunk, playerId);
                    if (isFrontier) {
                        frontierCount++;
                        logger.fine("Frontier chunk found: " + chunk.getX() + ", " + chunk.getZ());
                    }
                }
            }
        }
        
        logger.info("Total unlocked chunks in range: " + unlockCount);
        logger.info("Total frontier chunks in range: " + frontierCount);
        logger.info("Total eligible chunks: " + (unlockCount + frontierCount));
        logger.info("ACCEPTANCE CRITERIA: Player should only see holograms for " + (unlockCount + frontierCount) + " chunks");
        
        // Trigger actual hologram update to see the real eligibility calculation
        logger.info("--- Triggering hologram update (see debug logs above) ---");
        hologramService.updateActiveHologramsForPlayer(player);
        
        logger.info("=== ISSUE A DEBUG COMPLETE ===");
    }
    
    /**
     * Demonstrate Issue B fix: inventory change detection and hologram updates
     */
    public void demonstrateInventoryUpdateFix(Player player) {
        logger.info("=== ISSUE B DEBUG: Inventory Change Detection ===");
        
        logger.info("Player: " + player.getName());
        logger.info("Current inventory contents:");
        
        // Show current inventory state
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() != Material.AIR) {
                logger.info("  Slot " + i + ": " + item.getAmount() + "x " + item.getType());
            }
        }
        
        // Simulate what happens when inventory changes
        logger.info("--- Simulating inventory change event ---");
        
        // This would be triggered by InventoryChangeListener events
        logger.info("InventoryChangeListener detected change for " + player.getName());
        logger.info("Scheduling debounced hologram update in 3 ticks...");
        
        // Schedule the same update that InventoryChangeListener would schedule
        Bukkit.getScheduler().runTaskLater(ChunklockPlugin.getInstance(), () -> {
            logger.info("Executing debounced hologram update for " + player.getName());
            logger.info("This will recompute progress for all visible holograms");
            
            // Trigger hologram update (which includes progress recomputation)
            hologramService.updateActiveHologramsForPlayer(player);
            
            logger.info("ACCEPTANCE CRITERIA: Hologram 'have/need' counts should update within 2 ticks");
            logger.info("=== ISSUE B DEBUG COMPLETE ===");
            
        }, 3L); // Same 3-tick delay as InventoryChangeListener
    }
    
    /**
     * Check if a chunk is a frontier chunk (locked but adjacent to unlocked)
     */
    private boolean isFrontierChunk(Chunk chunk, UUID playerId) {
        boolean isChunkLocked = chunkLockManager.isLocked(chunk);
        UUID chunkOwner = chunkLockManager.getChunkOwner(chunk);
        boolean isUnlocked = !isChunkLocked && playerId.equals(chunkOwner);
        
        if (isUnlocked) {
            return false; // Already unlocked, not frontier
        }
        
        World world = chunk.getWorld();
        int x = chunk.getX();
        int z = chunk.getZ();
        
        // Check the four cardinal directions
        Chunk[] neighbors = {
            world.getChunkAt(x - 1, z),     // West
            world.getChunkAt(x + 1, z),     // East
            world.getChunkAt(x, z - 1),     // North
            world.getChunkAt(x, z + 1)      // South
        };
        
        for (Chunk neighbor : neighbors) {
            boolean neighborLocked = chunkLockManager.isLocked(neighbor);
            UUID neighborOwner = chunkLockManager.getChunkOwner(neighbor);
            boolean neighborUnlocked = !neighborLocked && playerId.equals(neighborOwner);
            
            if (neighborUnlocked) {
                return true; // Adjacent to unlocked chunk = frontier
            }
        }
        
        return false; // No unlocked neighbors = not frontier
    }
    
    /**
     * Run both demonstrations
     */
    public void runFullDemo(Player player) {
        logger.info("========================================");
        logger.info("HOLOGRAM REGRESSION FIX DEMONSTRATION");
        logger.info("========================================");
        
        demonstrateEligibilityFix(player);
        
        // Wait 2 seconds between demos
        Bukkit.getScheduler().runTaskLater(ChunklockPlugin.getInstance(), () -> {
            demonstrateInventoryUpdateFix(player);
        }, 40L);
    }
}
