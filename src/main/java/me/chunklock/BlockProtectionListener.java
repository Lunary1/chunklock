package me.chunklock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Comprehensive protection system for locked chunks.
 * Prevents all forms of block interaction, building, and destruction in locked chunks.
 */
public class BlockProtectionListener implements Listener {

    private final ChunkLockManager chunkLockManager;
    private final UnlockGui unlockGui;
    
    // Rate limiting for messages
    private final Map<UUID, Long> lastProtectionWarning = new ConcurrentHashMap<>();
    private static final long WARNING_COOLDOWN_MS = 3000L; // 3 seconds between warnings
    
    public BlockProtectionListener(ChunkLockManager chunkLockManager, UnlockGui unlockGui) {
        this.chunkLockManager = chunkLockManager;
        this.unlockGui = unlockGui;
    }

    /**
     * Prevents block breaking in locked chunks
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        if (isBlockProtected(player, block)) {
            event.setCancelled(true);
            handleProtectionViolation(player, block.getChunk(), "break blocks");
        }
    }

    /**
     * Prevents block placement in locked chunks
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        if (isBlockProtected(player, block)) {
            event.setCancelled(true);
            handleProtectionViolation(player, block.getChunk(), "place blocks");
        }
    }

    /**
     * Prevents various block interactions (buttons, levers, chests, etc.)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.isCancelled()) return;
        
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        
        if (block == null) return;
        
        // Only prevent interaction with certain block types (containers, redstone, etc.)
        if (shouldPreventInteraction(block) && isBlockProtected(player, block)) {
            event.setCancelled(true);
            handleProtectionViolation(player, block.getChunk(), "interact with blocks");
        }
    }

    /**
     * Prevents bucket usage in locked chunks
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.isCancelled()) return;
        
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        if (isBlockProtected(player, block)) {
            event.setCancelled(true);
            handleProtectionViolation(player, block.getChunk(), "use buckets");
        }
    }

    /**
     * Prevents bucket filling in locked chunks
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (event.isCancelled()) return;
        
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        if (isBlockProtected(player, block)) {
            event.setCancelled(true);
            handleProtectionViolation(player, block.getChunk(), "use buckets");
        }
    }

    /**
     * Prevents placing item frames, paintings, etc. in locked chunks
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.isCancelled()) return;
        
        Player player = event.getPlayer();
        if (player == null) return;
        
        Chunk chunk = event.getEntity().getLocation().getChunk();
        
        if (isChunkProtected(player, chunk)) {
            event.setCancelled(true);
            handleProtectionViolation(player, chunk, "place decorations");
        }
    }

    /**
     * Prevents breaking item frames, paintings, etc. in locked chunks
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.isCancelled()) return;
        
        if (!(event.getRemover() instanceof Player player)) return;
        
        Chunk chunk = event.getEntity().getLocation().getChunk();
        
        if (isChunkProtected(player, chunk)) {
            event.setCancelled(true);
            handleProtectionViolation(player, chunk, "break decorations");
        }
    }

    /**
     * Prevents entity damage (like armor stands) in locked chunks
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        
        if (!(event.getDamager() instanceof Player player)) return;
        
        Chunk chunk = event.getEntity().getLocation().getChunk();
        
        // Only protect certain entities (armor stands, item frames, etc.)
        if (shouldProtectEntity(event.getEntity()) && isChunkProtected(player, chunk)) {
            event.setCancelled(true);
            handleProtectionViolation(player, chunk, "damage entities");
        }
    }

    /**
     * Prevents vehicle destruction in locked chunks
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (event.isCancelled()) return;
        
        if (!(event.getAttacker() instanceof Player player)) return;
        
        Chunk chunk = event.getVehicle().getLocation().getChunk();
        
        if (isChunkProtected(player, chunk)) {
            event.setCancelled(true);
            handleProtectionViolation(player, chunk, "destroy vehicles");
        }
    }

    /**
     * Prevents explosions from affecting locked chunks
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) return;
        
        event.blockList().removeIf(block -> {
            Chunk chunk = block.getChunk();
            try {
                chunkLockManager.initializeChunk(chunk);
                return chunkLockManager.isLocked(chunk);
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                    "Error checking chunk lock status during explosion", e);
                return false;
            }
        });
    }

    /**
     * Prevents block explosions from affecting locked chunks
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.isCancelled()) return;
        
        event.blockList().removeIf(block -> {
            Chunk chunk = block.getChunk();
            try {
                chunkLockManager.initializeChunk(chunk);
                return chunkLockManager.isLocked(chunk);
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                    "Error checking chunk lock status during block explosion", e);
                return false;
            }
        });
    }

    /**
     * Core protection check for blocks
     */
    private boolean isBlockProtected(Player player, Block block) {
        if (player == null || block == null) return false;
        
        try {
            // Admin bypass
            if (chunkLockManager.isBypassing(player)) {
                return false;
            }
            
            Chunk chunk = block.getChunk();
            return isChunkProtected(player, chunk);
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error in block protection check", e);
            return false;
        }
    }

    /**
     * Core protection check for chunks
     */
    private boolean isChunkProtected(Player player, Chunk chunk) {
        try {
            // Initialize chunk if needed
            chunkLockManager.initializeChunk(chunk, player.getUniqueId());
            
            // Check if chunk is locked
            return chunkLockManager.isLocked(chunk);
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error in chunk protection check", e);
            return false;
        }
    }

    /**
     * Determines which block interactions should be prevented
     */
    private boolean shouldPreventInteraction(Block block) {
        return switch (block.getType()) {
            // Containers
            case CHEST, TRAPPED_CHEST, BARREL, SHULKER_BOX -> true;
            case WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX, MAGENTA_SHULKER_BOX,
                 LIGHT_BLUE_SHULKER_BOX, YELLOW_SHULKER_BOX, LIME_SHULKER_BOX,
                 PINK_SHULKER_BOX, GRAY_SHULKER_BOX, LIGHT_GRAY_SHULKER_BOX,
                 CYAN_SHULKER_BOX, PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX,
                 BROWN_SHULKER_BOX, GREEN_SHULKER_BOX, RED_SHULKER_BOX,
                 BLACK_SHULKER_BOX -> true;
            
            // Functional blocks
            case FURNACE, BLAST_FURNACE, SMOKER, BREWING_STAND, ENCHANTING_TABLE,
                 ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL, GRINDSTONE, STONECUTTER,
                 CARTOGRAPHY_TABLE, FLETCHING_TABLE, SMITHING_TABLE,
                 CRAFTING_TABLE, LOOM, COMPOSTER -> true;
            
            // Redstone components
            case LEVER, STONE_BUTTON, OAK_BUTTON, SPRUCE_BUTTON, BIRCH_BUTTON,
                 JUNGLE_BUTTON, ACACIA_BUTTON, DARK_OAK_BUTTON, MANGROVE_BUTTON,
                 CHERRY_BUTTON, BAMBOO_BUTTON, CRIMSON_BUTTON, WARPED_BUTTON,
                 STONE_PRESSURE_PLATE, OAK_PRESSURE_PLATE, SPRUCE_PRESSURE_PLATE,
                 BIRCH_PRESSURE_PLATE, JUNGLE_PRESSURE_PLATE, ACACIA_PRESSURE_PLATE,
                 DARK_OAK_PRESSURE_PLATE, MANGROVE_PRESSURE_PLATE, CHERRY_PRESSURE_PLATE,
                 BAMBOO_PRESSURE_PLATE, CRIMSON_PRESSURE_PLATE, WARPED_PRESSURE_PLATE,
                 HEAVY_WEIGHTED_PRESSURE_PLATE, LIGHT_WEIGHTED_PRESSURE_PLATE,
                 REPEATER, COMPARATOR, REDSTONE_WIRE -> true;
            
            // Doors and gates
            case OAK_DOOR, SPRUCE_DOOR, BIRCH_DOOR, JUNGLE_DOOR, ACACIA_DOOR,
                 DARK_OAK_DOOR, MANGROVE_DOOR, CHERRY_DOOR, BAMBOO_DOOR,
                 CRIMSON_DOOR, WARPED_DOOR, IRON_DOOR,
                 OAK_FENCE_GATE, SPRUCE_FENCE_GATE, BIRCH_FENCE_GATE,
                 JUNGLE_FENCE_GATE, ACACIA_FENCE_GATE, DARK_OAK_FENCE_GATE,
                 MANGROVE_FENCE_GATE, CHERRY_FENCE_GATE, BAMBOO_FENCE_GATE,
                 CRIMSON_FENCE_GATE, WARPED_FENCE_GATE -> true;
            
            // Trapdoors
            case OAK_TRAPDOOR, SPRUCE_TRAPDOOR, BIRCH_TRAPDOOR, JUNGLE_TRAPDOOR,
                 ACACIA_TRAPDOOR, DARK_OAK_TRAPDOOR, MANGROVE_TRAPDOOR,
                 CHERRY_TRAPDOOR, BAMBOO_TRAPDOOR, CRIMSON_TRAPDOOR,
                 WARPED_TRAPDOOR, IRON_TRAPDOOR -> true;
            
            // Beds
            case WHITE_BED, ORANGE_BED, MAGENTA_BED, LIGHT_BLUE_BED, YELLOW_BED,
                 LIME_BED, PINK_BED, GRAY_BED, LIGHT_GRAY_BED, CYAN_BED,
                 PURPLE_BED, BLUE_BED, BROWN_BED, GREEN_BED, RED_BED, BLACK_BED -> true;
            
            // Other important blocks
            case RESPAWN_ANCHOR, BEACON, CONDUIT, END_PORTAL_FRAME,
                 FLOWER_POT, JUKEBOX, NOTE_BLOCK, LECTERN -> true;
            
            default -> false;
        };
    }

    /**
     * Determines which entities should be protected
     */
    private boolean shouldProtectEntity(org.bukkit.entity.Entity entity) {
        return switch (entity.getType()) {
            case ARMOR_STAND, ITEM_FRAME, GLOW_ITEM_FRAME, PAINTING -> true;
            case MINECART, CHEST_MINECART, FURNACE_MINECART, TNT_MINECART,
                 HOPPER_MINECART, SPAWNER_MINECART, COMMAND_BLOCK_MINECART -> true;
            //case BOAT, CHEST_BOAT -> true;
            default -> false;
        };
    }

    /**
     * Handles protection violations with rate-limited messages and unlock GUI
     */
    private void handleProtectionViolation(Player player, Chunk chunk, String action) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        
        // Rate limit warning messages
        Long lastWarning = lastProtectionWarning.get(playerId);
        if (lastWarning == null || (now - lastWarning) >= WARNING_COOLDOWN_MS) {
            // Play error sound
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            
            // Send warning message
            player.sendMessage(Component.text("✋ ", NamedTextColor.RED)
                .append(Component.text("You cannot " + action + " in locked chunks!", NamedTextColor.RED)));
            
            try {
                // Show chunk info
                var evaluation = chunkLockManager.evaluateChunk(playerId, chunk);
                String biomeName = BiomeUnlockRegistry.getBiomeDisplayName(evaluation.biome);
                
                player.sendMessage(Component.text("📍 ", NamedTextColor.GRAY)
                    .append(Component.text("Chunk " + chunk.getX() + ", " + chunk.getZ(), NamedTextColor.WHITE))
                    .append(Component.text(" • " + biomeName, NamedTextColor.YELLOW))
                    .append(Component.text(" • " + evaluation.difficulty, getDifficultyColor(evaluation.difficulty))));
                
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                    "Error showing chunk info to player", e);
                player.sendMessage(Component.text("💡 ", NamedTextColor.YELLOW)
                    .append(Component.text("Right-click to see unlock requirements!", NamedTextColor.YELLOW)));
            }
            
            lastProtectionWarning.put(playerId, now);
        }
    }

    /**
     * Get color for difficulty level
     */
    private NamedTextColor getDifficultyColor(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> NamedTextColor.GREEN;
            case NORMAL -> NamedTextColor.YELLOW;
            case HARD -> NamedTextColor.RED;
            case IMPOSSIBLE -> NamedTextColor.DARK_PURPLE;
        };
    }

    /**
     * Cleanup method for player logout
     */
    public void cleanupPlayer(UUID playerId) {
        lastProtectionWarning.remove(playerId);
    }

    /**
     * Get statistics for debugging
     */
    public Map<String, Object> getProtectionStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("playersWithWarningCooldown", lastProtectionWarning.size());
        return stats;
    }
}