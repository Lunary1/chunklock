package me.chunklock.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
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
import me.chunklock.ChunklockPlugin;
import me.chunklock.config.LanguageKeys;
import me.chunklock.managers.ChunkBorderManager;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.WorldManager;
import me.chunklock.ui.UnlockGui;
import me.chunklock.util.message.MessageUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Comprehensive protection system for locked chunks.
 * Prevents all forms of block interaction, building, and destruction in locked chunks.
 * Now includes world-specific filtering - only protects blocks in enabled worlds.
 */
public class BlockProtectionListener implements Listener {

    private final ChunkLockManager chunkLockManager;
    @SuppressWarnings("unused") // Reserved for future GUI integration
    private final UnlockGui unlockGui;
    private final ChunkBorderManager chunkBorderManager;
    
    // Rate limiting for messages
    private final Map<UUID, Long> lastProtectionWarning = new ConcurrentHashMap<>();
    private static final long WARNING_COOLDOWN_MS = 3000L; // 3 seconds between warnings
    
    public BlockProtectionListener(ChunkLockManager chunkLockManager, UnlockGui unlockGui, ChunkBorderManager chunkBorderManager) {
        this.chunkLockManager = chunkLockManager;
        this.unlockGui = unlockGui;
        this.chunkBorderManager = chunkBorderManager;
    }

    /**
     * Helper method to check if world is enabled for ChunkLock
     */
    private boolean isWorldEnabled(Player player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        
        try {
            WorldManager worldManager = ChunklockPlugin.getInstance().getWorldManager();
            return worldManager.isWorldEnabled(player.getWorld());
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().fine("Could not check world status for protection: " + e.getMessage());
            return false; // Err on the side of caution - no protection if can't verify
        }
    }

    /**
     * Prevents block breaking in locked chunks
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        
        Player player = event.getPlayer();
        
        // NEW: Early world check - skip protection in disabled worlds
        if (!isWorldEnabled(player)) {
            return; // Allow block breaking in disabled worlds
        }
        
        Block block = event.getBlock();

        if (chunkBorderManager.isBorderBlock(block)) {
            event.setCancelled(true);
            Chunk target = chunkBorderManager.getBorderChunk(block);
            if (target == null) target = block.getChunk();
            handleProtectionViolation(player, target, "break border");
            return;
        }

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
        
        // NEW: Early world check - skip protection in disabled worlds
        if (!isWorldEnabled(player)) {
            return; // Allow block placement in disabled worlds
        }
        
        Block block = event.getBlock();

        if (chunkBorderManager.isBorderBlock(block)) {
            event.setCancelled(true);
            Chunk target = chunkBorderManager.getBorderChunk(block);
            if (target == null) target = block.getChunk();
            handleProtectionViolation(player, target, "place blocks");
            return;
        }

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
        if (event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) return;
        
        Player player = event.getPlayer();
        
        // NEW: Early world check - skip protection in disabled worlds
        if (!isWorldEnabled(player)) {
            return; // Allow interactions in disabled worlds
        }
        
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
        
        // NEW: Early world check - skip protection in disabled worlds
        if (!isWorldEnabled(player)) {
            return; // Allow bucket usage in disabled worlds
        }
        
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
        
        // NEW: Early world check - skip protection in disabled worlds
        if (!isWorldEnabled(player)) {
            return; // Allow bucket usage in disabled worlds
        }
        
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
        
        // NEW: Early world check - skip protection in disabled worlds
        if (!isWorldEnabled(player)) {
            return; // Allow hanging entity placement in disabled worlds
        }
        
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
        
        // NEW: Early world check - skip protection in disabled worlds
        if (!isWorldEnabled(player)) {
            return; // Allow hanging entity breaking in disabled worlds
        }
        
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
        
        // NEW: Early world check - skip protection in disabled worlds
        if (!isWorldEnabled(player)) {
            return; // Allow entity damage in disabled worlds
        }
        
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
        
        // NEW: Early world check - skip protection in disabled worlds
        if (!isWorldEnabled(player)) {
            return; // Allow vehicle destruction in disabled worlds
        }
        
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
        
        // NEW: World check for explosion location
        if (event.getLocation() != null && event.getLocation().getWorld() != null) {
            try {
                WorldManager worldManager = ChunklockPlugin.getInstance().getWorldManager();
                if (!worldManager.isWorldEnabled(event.getLocation().getWorld())) {
                    return; // Allow explosions in disabled worlds
                }
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().fine("Could not check world status for explosion: " + e.getMessage());
                return; // Skip protection if can't verify world status
            }
        }
        
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
        
        // NEW: World check for explosion location
        if (event.getBlock() != null && event.getBlock().getWorld() != null) {
            try {
                WorldManager worldManager = ChunklockPlugin.getInstance().getWorldManager();
                if (!worldManager.isWorldEnabled(event.getBlock().getWorld())) {
                    return; // Allow explosions in disabled worlds
                }
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().fine("Could not check world status for block explosion: " + e.getMessage());
                return; // Skip protection if can't verify world status
            }
        }
        
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
            
            if (chunkBorderManager.isBorderBlock(block)) {
                Chunk protectedChunk = chunkBorderManager.getBorderChunk(block);
                if (protectedChunk != null) {
                    return isChunkProtected(player, protectedChunk);
                }
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
        EntityType entityType = entity.getType();
        
        // Check common protected entity types
        switch (entityType) {
            case ARMOR_STAND:
            case ITEM_FRAME:
            case GLOW_ITEM_FRAME:
            case PAINTING:
                return true;
            case MINECART:
                return true;
            default:
                // Check if it's any type of minecart by name
                String typeName = entityType.name();
                if (typeName.contains("MINECART")) {
                    return true;
                }
                return false;
        }
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
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("action", action);
            String message = MessageUtil.getMessage(LanguageKeys.PROTECTION_CHUNK_LOCKED, placeholders);
            player.sendMessage(Component.text("✋ ", NamedTextColor.RED)
                .append(Component.text(message, NamedTextColor.RED)));
            
            lastProtectionWarning.put(playerId, now);
        }
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
        
        // NEW: Add world-related protection statistics
        try {
            WorldManager worldManager = ChunklockPlugin.getInstance().getWorldManager();
            stats.put("enabledWorlds", worldManager.getEnabledWorlds());
            stats.put("worldCheckingEnabled", true);
        } catch (Exception e) {
            stats.put("worldCheckingEnabled", false);
            stats.put("worldCheckError", e.getMessage());
        }
        
        return stats;
    }
}