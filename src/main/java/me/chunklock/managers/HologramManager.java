package me.chunklock.managers;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import me.chunklock.ChunklockPlugin;
import me.chunklock.models.Difficulty;

import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages floating holograms above locked chunks with world-aware filtering.
 * Only displays holograms in worlds where ChunkLock is enabled.
 * Uses FancyHolograms API for modern hologram display with wall-facing orientation.
 */
public class HologramManager {

    private final ChunkLockManager chunkLockManager;
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    private final Map<String, Object> activeHolograms = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> playerHologramTasks = new HashMap<>();
    
    // FancyHolograms integration
    private final boolean fancyHologramsAvailable;
    private final Object hologramManager;
    
    // FancyHolograms reflection classes and methods
    private final Class<?> textHologramDataClass;
    private final Class<?> hologramClass;
    private final Method createHologramMethod;
    private final Method addHologramMethod;
    private final Method removeHologramMethod;
    private final Constructor<?> textHologramDataConstructor;

    private static final int HOLOGRAM_UPDATE_INTERVAL = 20; // 1 second
    private static final int HOLOGRAM_VIEW_DISTANCE = 48; // blocks (3 chunks)

    private enum WallSide {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

        final int dx;
        final int dz;

        WallSide(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }
    }

    public HologramManager(ChunkLockManager chunkLockManager, BiomeUnlockRegistry biomeUnlockRegistry) {
        this.chunkLockManager = chunkLockManager;
        this.biomeUnlockRegistry = biomeUnlockRegistry;
        
        // Check configuration to see if holograms are enabled
        boolean hologramsEnabled = ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.enabled", true);
        String configuredProvider = ChunklockPlugin.getInstance().getConfig().getString("holograms.provider", "auto").toLowerCase();
        
        if (!hologramsEnabled) {
            ChunklockPlugin.getInstance().getLogger().info("Holograms disabled in configuration - hologram functionality disabled");
            // Initialize all fields to null/false
            this.textHologramDataClass = null;
            this.hologramClass = null;
            this.createHologramMethod = null;
            this.addHologramMethod = null;
            this.removeHologramMethod = null;
            this.textHologramDataConstructor = null;
            this.hologramManager = null;
            this.fancyHologramsAvailable = false;
            return;
        }
        
        // Try FancyHolograms unless explicitly disabled
        boolean tryFancyHolograms = !"none".equals(configuredProvider) && 
                                   org.bukkit.Bukkit.getPluginManager().getPlugin("FancyHolograms") != null;
        
        ChunklockPlugin.getInstance().getLogger().info("🔍 Hologram Provider Analysis:");
        ChunklockPlugin.getInstance().getLogger().info("  ├─ Configured Provider: " + configuredProvider);
        ChunklockPlugin.getInstance().getLogger().info("  ├─ FancyHolograms Installed: " + (org.bukkit.Bukkit.getPluginManager().getPlugin("FancyHolograms") != null));
        ChunklockPlugin.getInstance().getLogger().info("  └─ Will Try FancyHolograms: " + tryFancyHolograms);
        
        if (tryFancyHolograms) {
            Object tempHologramManager = null;
            boolean tempFancyAvailable = false;
            Class<?> tempFancyPluginClass = null;
            Class<?> tempTextHologramDataClass = null;
            Class<?> tempHologramClass = null;
            Class<?> tempHologramManagerClass = null;
            Method tempGetPluginInstanceMethod = null;
            Method tempGetHologramManagerMethod = null;
            Method tempCreateHologramMethod = null;
            Method tempAddHologramMethod = null;
            Method tempRemoveHologramMethod = null;
            Constructor<?> tempTextHologramDataConstructor = null;
            
            try {
                // Use correct FancyHolograms v2 API package structure
                tempFancyPluginClass = Class.forName("de.oliver.fancyholograms.api.FancyHologramsPlugin");
                tempTextHologramDataClass = Class.forName("de.oliver.fancyholograms.api.data.TextHologramData");
                tempHologramClass = Class.forName("de.oliver.fancyholograms.api.hologram.Hologram");
                tempHologramManagerClass = Class.forName("de.oliver.fancyholograms.api.HologramManager");
                
                // Get plugin instance using the static get() method
                tempGetPluginInstanceMethod = tempFancyPluginClass.getMethod("get");
                
                // Get hologram manager method
                tempGetHologramManagerMethod = tempFancyPluginClass.getMethod("getHologramManager");
                
                // Get hologram manager methods - use correct signatures for v2.6.0
                Class<?> hologramDataClass = Class.forName("de.oliver.fancyholograms.api.data.HologramData");
                tempCreateHologramMethod = tempHologramManagerClass.getMethod("create", hologramDataClass);
                tempAddHologramMethod = tempHologramManagerClass.getMethod("addHologram", tempHologramClass);
                tempRemoveHologramMethod = tempHologramManagerClass.getMethod("removeHologram", tempHologramClass);
                
                // Get TextHologramData constructor
                tempTextHologramDataConstructor = tempTextHologramDataClass.getConstructor(String.class, Location.class);
                
                // Get plugin instance and hologram manager
                Object pluginInstance = tempGetPluginInstanceMethod.invoke(null);
                tempHologramManager = tempGetHologramManagerMethod.invoke(pluginInstance);
                tempFancyAvailable = true;
                ChunklockPlugin.getInstance().getLogger().info("FancyHolograms detected and initialized successfully - using FancyHolograms for enhanced hologram display (Priority system)");
            } catch (ClassNotFoundException e) {
                ChunklockPlugin.getInstance().getLogger().warning("FancyHolograms plugin detected but API classes not found. Please ensure you have FancyHolograms version 2.4.0+ installed.");
                ChunklockPlugin.getInstance().getLogger().fine("Missing class: " + e.getMessage());
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().warning("FancyHolograms found but failed to initialize API: " + e.getMessage());
                ChunklockPlugin.getInstance().getLogger().fine("Full error details: " + e.getClass().getName() + " - " + e.getMessage());
            }
            
            // Assign final fields
            this.textHologramDataClass = tempTextHologramDataClass;
            this.hologramClass = tempHologramClass;
            this.createHologramMethod = tempCreateHologramMethod;
            this.addHologramMethod = tempAddHologramMethod;
            this.removeHologramMethod = tempRemoveHologramMethod;
            this.textHologramDataConstructor = tempTextHologramDataConstructor;
            this.hologramManager = tempHologramManager;
            this.fancyHologramsAvailable = tempFancyAvailable;
            
            // Log successful initialization or fallback
            if (tempFancyAvailable) {
                ChunklockPlugin.getInstance().getLogger().info("✅ Hologram system active: FancyHolograms (Prioritized)");
            } else {
                ChunklockPlugin.getInstance().getLogger().warning("❌ FancyHolograms initialization failed - falling back to other providers or disabling");
            }
        } else {
            // FancyHolograms not available or explicitly disabled, try other providers
            ChunklockPlugin.getInstance().getLogger().info("FancyHolograms not available or disabled in config - checking for alternative providers");
            this.textHologramDataClass = null;
            this.hologramClass = null;
            this.createHologramMethod = null;
            this.addHologramMethod = null;
            this.removeHologramMethod = null;
            this.textHologramDataConstructor = null;
            this.hologramManager = null;
            this.fancyHologramsAvailable = false;
        }
        
        // Handle fallback providers only if FancyHolograms is not available
        if (!this.fancyHologramsAvailable) {
            if ("none".equals(configuredProvider)) {
                ChunklockPlugin.getInstance().getLogger().info("Hologram provider set to 'none' - hologram functionality disabled");
            } else {
                // Auto or unknown provider - inform about available options
                if ("auto".equals(configuredProvider)) {
                    ChunklockPlugin.getInstance().getLogger().warning("No supported hologram providers found. Install FancyHolograms for hologram functionality.");
                } else if (!"FancyHolograms".equalsIgnoreCase(configuredProvider)) {
                    ChunklockPlugin.getInstance().getLogger().warning("Unknown hologram provider '" + configuredProvider + "' configured. Supported providers: 'FancyHolograms', 'auto', 'none'. Install FancyHolograms for hologram functionality.");
                }
            }
            ChunklockPlugin.getInstance().getLogger().info("🔇 Hologram system: DISABLED (No supported provider available)");
        }
    }

    /**
     * Helper method to check if world is enabled for ChunkLock holograms
     */
    private boolean isWorldEnabled(Player player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        
        try {
            WorldManager worldManager = ChunklockPlugin.getInstance().getWorldManager();
            return worldManager.isWorldEnabled(player.getWorld());
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().fine("Could not check world status for holograms: " + e.getMessage());
            return false; // Err on the side of caution - no holograms if can't verify
        }
    }

    /**
     * Helper method to check if world is enabled by World object
     */
    private boolean isWorldEnabled(World world) {
        if (world == null) {
            return false;
        }
        
        try {
            WorldManager worldManager = ChunklockPlugin.getInstance().getWorldManager();
            return worldManager.isWorldEnabled(world);
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().fine("Could not check world status for holograms: " + e.getMessage());
            return false;
        }
    }

    /**
     * Starts hologram display task for a player
     */
    public void startHologramDisplay(Player player) {
        if (player == null || !player.isOnline() || !fancyHologramsAvailable) {
            return;
        }
        
        // Check if player is in an enabled world
        if (!isWorldEnabled(player)) {
            ChunklockPlugin.getInstance().getLogger().fine("Player " + player.getName() + 
                " is in disabled world " + player.getWorld().getName() + " - skipping hologram display");
            return;
        }
        
        stopHologramDisplay(player); // Clean up any existing task
        
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                
                // Re-check world status on each update
                if (!isWorldEnabled(player)) {
                    // Player moved to disabled world, remove holograms and stop task
                    removeHologramsForPlayer(player);
                    cancel();
                    return;
                }
                
                try {
                    if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                        ChunklockPlugin.getInstance().getLogger().info("🔄 Updating holograms for player " + player.getName());
                    }
                    updateHologramsForPlayer(player);
                } catch (Exception e) {
                    ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                        "Error updating holograms for " + player.getName(), e);
                }
            }
        }.runTaskTimer(ChunklockPlugin.getInstance(), 0L, HOLOGRAM_UPDATE_INTERVAL);
        
        playerHologramTasks.put(player.getUniqueId(), task);
        
        ChunklockPlugin.getInstance().getLogger().fine("Started FancyHolograms display for " + player.getName());
    }

    /**
     * Stops hologram display for a player
     */
    public void stopHologramDisplay(Player player) {
        if (player == null) return;
        
        BukkitTask task = playerHologramTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        
        // Remove holograms visible to this player
        removeHologramsForPlayer(player);
        
        ChunklockPlugin.getInstance().getLogger().fine("Stopped hologram display for " + player.getName());
    }

    /**
     * Updates holograms around a player - shows holograms for ALL locked adjacent chunks
     * Now includes world validation to ensure holograms only appear in enabled worlds
     */
    private void updateHologramsForPlayer(Player player) {
        try {
            // Double-check world status (defensive programming)
            if (!isWorldEnabled(player)) {
                removeHologramsForPlayer(player);
                return;
            }
            
            if (chunkLockManager.isBypassing(player)) {
                // Remove all holograms for bypassing players
                removeHologramsForPlayer(player);
                return;
            }

            Location playerLoc = player.getLocation();
            Chunk playerChunk = playerLoc.getChunk();
            
            // NEW: Verify chunk world is enabled
            if (!isWorldEnabled(playerChunk.getWorld())) {
                removeHologramsForPlayer(player);
                return;
            }
            
            // Track which chunks should have holograms
            Set<String> chunksWithHolograms = new HashSet<>();
            
            // Check all chunks in a 3x3 grid around the player
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    try {
                        Chunk checkChunk = player.getWorld().getChunkAt(
                            playerChunk.getX() + dx, 
                            playerChunk.getZ() + dz
                        );
                        
                        // NEW: Skip chunks in disabled worlds (shouldn't happen, but defensive)
                        if (!isWorldEnabled(checkChunk.getWorld())) {
                            continue;
                        }
                        
                        // Initialize chunk if needed
                        chunkLockManager.initializeChunk(checkChunk, player.getUniqueId());
                        
                        String chunkKey = getChunkKey(checkChunk);

                        if (chunkLockManager.isLocked(checkChunk)) {
                            if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                                ChunklockPlugin.getInstance().getLogger().info("📍 Found locked chunk " + checkChunk.getX() + "," + checkChunk.getZ() + " - checking for wall holograms");
                            }
                            
                            Map<WallSide, Location> locs = getWallHologramLocations(player, checkChunk);
                            if (!locs.isEmpty()) {
                                var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), checkChunk);
                                showHologramForChunk(player, checkChunk, evaluation, locs);
                                chunksWithHolograms.add(chunkKey);
                            } else {
                                removeHologramForChunk(player, checkChunk);
                            }
                        } else {
                            // Chunk is unlocked - definitely remove any hologram
                            removeHologramForChunk(player, checkChunk);
                            ChunklockPlugin.getInstance().getLogger().fine(
                                "Removed hologram for unlocked chunk " + checkChunk.getX() + "," + checkChunk.getZ() + " for player " + player.getName());
                        }
                    } catch (Exception e) {
                        ChunklockPlugin.getInstance().getLogger().log(Level.FINE, 
                            "Error checking chunk at offset " + dx + "," + dz + " for player " + player.getName(), e);
                    }
                }
            }
            
            // FIX: Additional cleanup - remove any remaining holograms that shouldn't exist
            String playerPrefix = player.getUniqueId().toString() + "_";
            activeHolograms.entrySet().removeIf(entry -> {
                if (entry.getKey().startsWith(playerPrefix)) {
                    // Extract chunk coordinates from hologram key (format: playerUUID_world_x_z)
                    String[] parts = entry.getKey().split("_");
                    if (parts.length >= 4) {
                        try {
                            String worldName = parts[1];
                            int chunkX = Integer.parseInt(parts[2]);
                            int chunkZ = Integer.parseInt(parts[3]);
                            String chunkKey = worldName + ":" + chunkX + ":" + chunkZ;
                            
                            // NEW: Also check if the hologram's world is still enabled
                            World hologramWorld = org.bukkit.Bukkit.getWorld(worldName);
                            if (hologramWorld == null || !isWorldEnabled(hologramWorld)) {
                                Object hologram = entry.getValue();
                                if (hologram != null && fancyHologramsAvailable) {
                                    try {
                                        // Remove using hologram object
                                        removeHologramMethod.invoke(hologramManager, hologram);
                                    } catch (Exception e) {
                                        ChunklockPlugin.getInstance().getLogger().log(Level.FINE, 
                                            "Error removing FancyHologram: " + e.getMessage());
                                    }
                                    ChunklockPlugin.getInstance().getLogger().fine(
                                        "Cleaned up hologram in disabled world " + worldName + " for player " + player.getName());
                                }
                                return true;
                            }
                            
                            // If this chunk should not have a hologram, remove it
                            if (!chunksWithHolograms.contains(chunkKey)) {
                                Object hologram = entry.getValue();
                                if (hologram != null && fancyHologramsAvailable) {
                                    try {
                                        // Remove using hologram object
                                        removeHologramMethod.invoke(hologramManager, hologram);
                                    } catch (Exception e) {
                                        ChunklockPlugin.getInstance().getLogger().log(Level.FINE, 
                                            "Error removing FancyHologram: " + e.getMessage());
                                    }
                                    ChunklockPlugin.getInstance().getLogger().fine(
                                        "Cleaned up stale hologram for chunk " + chunkX + "," + chunkZ + " for player " + player.getName());
                                }
                                return true;
                            }
                        } catch (NumberFormatException e) {
                            // Invalid hologram key format, remove it
                            Object hologram = entry.getValue();
                            if (hologram != null && fancyHologramsAvailable) {
                                try {
                                    // Remove using hologram object
                                    removeHologramMethod.invoke(hologramManager, hologram);
                                } catch (Exception ex) {
                                    ChunklockPlugin.getInstance().getLogger().log(Level.FINE, 
                                        "Error removing FancyHologram: " + ex.getMessage());
                                }
                            }
                            return true;
                        }
                    }
                }
                return false;
            });
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error updating holograms for " + player.getName(), e);
        }
    }

    /**
     * Helper method to get chunk key from chunk
     */
    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }



    /**
     * Calculates hologram spawn locations for each wall of the locked chunk that
     * borders an unlocked chunk.
     */
    private Map<WallSide, Location> getWallHologramLocations(Player player, Chunk chunk) {
        Map<WallSide, Location> map = new HashMap<>();
        World world = chunk.getWorld();
        
        // Skip if world is disabled
        if (!isWorldEnabled(world)) {
            return map; // Return empty map for disabled worlds
        }
        
        int startX = chunk.getX() * 16;
        int startZ = chunk.getZ() * 16;

        for (WallSide side : WallSide.values()) {
            try {
                Chunk neighbor = world.getChunkAt(chunk.getX() + side.dx, chunk.getZ() + side.dz);
                chunkLockManager.initializeChunk(neighbor, player.getUniqueId());
                
                boolean neighborLocked = chunkLockManager.isLocked(neighbor);
                if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                    ChunklockPlugin.getInstance().getLogger().info("🔍 Checking " + side + " wall: neighbor chunk " + 
                        neighbor.getX() + "," + neighbor.getZ() + " is " + (neighborLocked ? "LOCKED" : "UNLOCKED"));
                }
                
                if (neighborLocked) {
                    continue; // neighbor also locked, no hologram needed
                }

                // Calculate hologram position centered on the wall
                double x, z;
                
                if (side == WallSide.NORTH) {
                    // North wall - center horizontally, position at north edge
                    x = startX + 8.0; // Center of chunk horizontally
                    z = startZ + 0.5; // Just inside the north edge
                } else if (side == WallSide.SOUTH) {
                    // South wall - center horizontally, position at south edge  
                    x = startX + 8.0; // Center of chunk horizontally
                    z = startZ + 15.5; // Just inside the south edge
                } else if (side == WallSide.EAST) {
                    // East wall - center vertically, position at east edge
                    x = startX + 15.5; // Just inside the east edge
                    z = startZ + 8.0; // Center of chunk vertically
                } else { // WEST
                    // West wall - center vertically, position at west edge
                    x = startX + 0.5; // Just inside the west edge
                    z = startZ + 8.0; // Center of chunk vertically
                }

                int y = getHighestSolidY(world, (int)x, (int)z) + 2;
                if (y < 64) y = 64;

                Location hologramLoc = new Location(world, x, y, z);
                map.put(side, hologramLoc);
                
                ChunklockPlugin.getInstance().getLogger().fine("Added " + side + " wall hologram at " + 
                    String.format("%.1f,%.1f,%.1f", x, (double)y, z));
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().fine("Error checking " + side + " wall: " + e.getMessage());
            }
        }

        return map;
    }

    /**
     * Finds the highest solid block at the given coordinates, ignoring glass or barrier blocks.
     */
    private int getHighestSolidY(World world, int x, int z) {
        // NEW: Skip if world is disabled
        if (!isWorldEnabled(world)) {
            return world.getMinHeight();
        }
        
        int y = world.getHighestBlockYAt(x, z);
        if (y > world.getMaxHeight()) y = world.getMaxHeight();
        while (y > world.getMinHeight()) {
            Block block = world.getBlockAt(x, y, z);
            var type = block.getType();
            if (type != org.bukkit.Material.BARRIER && !type.name().contains("GLASS") && type.isSolid()) {
                return y;
            }
            y--;
        }
        return world.getMinHeight();
    }

    /**
     * Shows hologram for a specific chunk using FancyHolograms API
     */
    private void showHologramForChunk(Player player, Chunk chunk, ChunkEvaluator.ChunkValueData evaluation, Map<WallSide, Location> locations) {
        if (locations == null || locations.isEmpty() || !fancyHologramsAvailable) {
            return;
        }

        // Double-check world status before creating holograms
        if (!isWorldEnabled(chunk.getWorld())) {
            ChunklockPlugin.getInstance().getLogger().fine("Skipping hologram creation in disabled world " + chunk.getWorld().getName());
            return;
        }

        try {
            var requirement = biomeUnlockRegistry.calculateRequirement(player, evaluation.biome, evaluation.score);
            String biomeName = BiomeUnlockRegistry.getBiomeDisplayName(evaluation.biome);
            boolean hasItems = biomeUnlockRegistry.hasRequiredItems(player, evaluation.biome, evaluation.score);

            ChunklockPlugin.getInstance().getLogger().fine("Creating holograms for chunk " + chunk.getX() + "," + chunk.getZ() + 
                " with " + locations.size() + " wall locations");

            if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                ChunklockPlugin.getInstance().getLogger().info("🏗️ Creating holograms for chunk " + chunk.getX() + "," + chunk.getZ() + 
                    " with " + locations.size() + " wall locations");
            }

            // Create hologram text as clean separate lines
            java.util.List<String> textLines = new java.util.ArrayList<>();
            if (hasItems) {
                textLines.add("§a§l🔓 UNLOCKABLE");
                textLines.add("§e" + biomeName);
                textLines.add("§fRequired: " + requirement.amount() + "x");
                textLines.add("§b" + requirement.material().name().replace("_", " "));
                textLines.add("§a✓ Items available!");
            } else {
                textLines.add("§c§l🔒 LOCKED");
                textLines.add("§e" + biomeName);
                textLines.add("§" + getDifficultyColorCode(evaluation.difficulty) + "Difficulty: " + evaluation.difficulty);
                textLines.add("§7Need: " + requirement.amount() + "x");
                textLines.add("§7" + requirement.material().name().replace("_", " "));
            }

            for (Map.Entry<WallSide, Location> entry : locations.entrySet()) {
                String hologramKey = getHologramKey(player, chunk, entry.getKey().name());

                if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                    ChunklockPlugin.getInstance().getLogger().info("🔧 Processing " + entry.getKey() + " wall for chunk " + chunk.getX() + "," + chunk.getZ());
                }

                if (activeHolograms.containsKey(hologramKey)) {
                    if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                        ChunklockPlugin.getInstance().getLogger().info("⚠️ Skipping existing hologram for " + entry.getKey() + " wall (key: " + hologramKey + ")");
                    }
                    continue;
                }

                Location hologramLocation = entry.getValue();
                double distance = player.getLocation().distance(hologramLocation);
                if (distance > HOLOGRAM_VIEW_DISTANCE) {
                    if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                        ChunklockPlugin.getInstance().getLogger().info("📏 Skipping hologram on " + entry.getKey() + " wall - too far: " + distance);
                    }
                    continue;
                }

                if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                    ChunklockPlugin.getInstance().getLogger().info("✨ Creating hologram for " + entry.getKey() + " wall at " + 
                        hologramLocation.getX() + "," + hologramLocation.getY() + "," + hologramLocation.getZ() + " (distance: " + distance + ")");
                }

                // Create FancyHologram using the correct API
                Object hologramData;
                try {
                    hologramData = textHologramDataConstructor.newInstance(hologramKey, hologramLocation);
                } catch (Exception e) {
                    ChunklockPlugin.getInstance().getLogger().warning("Failed to create TextHologramData for " + entry.getKey() + " wall: " + e.getMessage());
                    continue;
                }
                
                // Set the text using List of lines directly
                try {
                    Method setTextMethod = textHologramDataClass.getMethod("setText", java.util.List.class);
                    setTextMethod.invoke(hologramData, textLines);
                    
                    ChunklockPlugin.getInstance().getLogger().fine("Set hologram text with " + textLines.size() + " lines for " + entry.getKey() + " wall");
                } catch (Exception e) {
                    ChunklockPlugin.getInstance().getLogger().warning("Could not set hologram text for " + entry.getKey() + " wall: " + e.getMessage());
                    continue;
                }
                
                // Set view distance - use setVisibilityDistance from the available methods
                try {
                    Method setVisibilityDistanceMethod = textHologramDataClass.getMethod("setVisibilityDistance", int.class);
                    setVisibilityDistanceMethod.invoke(hologramData, HOLOGRAM_VIEW_DISTANCE);
                } catch (Exception e) {
                    // View distance setting failed, continue without it
                    ChunklockPlugin.getInstance().getLogger().fine("Could not set view distance: " + e.getMessage());
                }
                
                // Set billboard and explore available rotation methods
                try {
                    // Use FIXED billboard for custom rotation (no player following)
                    Class<?> billboardClass = Class.forName("org.bukkit.entity.Display$Billboard");
                    Object billboard = billboardClass.getField("FIXED").get(null);
                    
                    Method setBillboardMethod = textHologramDataClass.getMethod("setBillboard", billboardClass);
                    setBillboardMethod.invoke(hologramData, billboard);
                    
                    if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                        ChunklockPlugin.getInstance().getLogger().info("📺 Set billboard to FIXED for " + entry.getKey() + " wall");
                    }
                    
                    // Set rotation based on wall side to face chunk center
                    WallSide wallSide = entry.getKey();
                    float yaw = getWallFacingYaw(wallSide);
                    
                    if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                        ChunklockPlugin.getInstance().getLogger().info("🧭 Setting rotation for " + wallSide + " wall: " + yaw + "° yaw");
                    }
                    
                    // Try various rotation approaches in order of preference
                    boolean rotationSet = false;
                    
                    // Method 1: Try setTransformation with Bukkit's Transformation class
                    if (!rotationSet) {
                        try {
                            Class<?> transformationClass = Class.forName("org.bukkit.util.Transformation");
                            Class<?> vector3fClass = Class.forName("org.joml.Vector3f");
                            Class<?> quaternionfClass = Class.forName("org.joml.Quaternionf");
                            
                            // Create transformation components
                            Object translation = vector3fClass.getConstructor(float.class, float.class, float.class).newInstance(0f, 0f, 0f);
                            Object scale = vector3fClass.getConstructor(float.class, float.class, float.class).newInstance(1f, 1f, 1f);
                            
                            // Create rotation quaternion from yaw
                            Object leftRotation = quaternionfClass.getConstructor().newInstance();
                            Method rotateYMethod = quaternionfClass.getMethod("rotateY", float.class);
                            rotateYMethod.invoke(leftRotation, (float) Math.toRadians(yaw));
                            
                            Object rightRotation = quaternionfClass.getConstructor().newInstance();
                            
                            // Create transformation
                            Object transformation = transformationClass.getConstructor(vector3fClass, quaternionfClass, vector3fClass, quaternionfClass)
                                .newInstance(translation, leftRotation, scale, rightRotation);
                            
                            Method setTransformationMethod = textHologramDataClass.getMethod("setTransformation", transformationClass);
                            setTransformationMethod.invoke(hologramData, transformation);
                            
                            rotationSet = true;
                            if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                                ChunklockPlugin.getInstance().getLogger().info("✅ Set rotation using setTransformation: " + yaw + "° for " + wallSide + " wall");
                            }
                        } catch (Exception transformException) {
                            if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                                ChunklockPlugin.getInstance().getLogger().info("❌ setTransformation failed: " + transformException.getMessage());
                            }
                        }
                    }
                    
                    // Method 2: Try setRotation method
                    if (!rotationSet) {
                        try {
                            Method setRotationMethod = textHologramDataClass.getMethod("setRotation", float.class, float.class);
                            setRotationMethod.invoke(hologramData, yaw, 0.0f); // yaw, pitch
                            
                            rotationSet = true;
                            if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                                ChunklockPlugin.getInstance().getLogger().info("✅ Set rotation using setRotation: " + yaw + "° for " + wallSide + " wall");
                            }
                        } catch (Exception rotationException) {
                            if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                                ChunklockPlugin.getInstance().getLogger().info("❌ setRotation method failed: " + rotationException.getMessage());
                            }
                        }
                    }
                    
                    // Method 3: Try alternative rotation methods
                    if (!rotationSet) {
                        try {
                            Method setYawMethod = textHologramDataClass.getMethod("setYaw", float.class);
                            setYawMethod.invoke(hologramData, yaw);
                            rotationSet = true;
                            if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                                ChunklockPlugin.getInstance().getLogger().info("✅ Set rotation using setYaw: " + yaw + "° for " + wallSide + " wall");
                            }
                        } catch (Exception yawException) {
                            if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                                ChunklockPlugin.getInstance().getLogger().info("❌ setYaw method failed: " + yawException.getMessage());
                            }
                        }
                    }
                    
                    // Method 4: List available methods for debugging
                    if (!rotationSet) {
                        if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                            ChunklockPlugin.getInstance().getLogger().info("🔍 Available methods in TextHologramData:");
                            Method[] methods = textHologramDataClass.getMethods();
                            for (Method method : methods) {
                                String methodName = method.getName();
                                if (methodName.toLowerCase().contains("rotat") || methodName.toLowerCase().contains("transform") || 
                                    methodName.toLowerCase().contains("yaw") || methodName.toLowerCase().contains("pitch")) {
                                    ChunklockPlugin.getInstance().getLogger().info("  - " + methodName + "(" + 
                                        java.util.Arrays.toString(method.getParameterTypes()) + ")");
                                }
                            }
                            ChunklockPlugin.getInstance().getLogger().info("⚠️ No rotation method worked - hologram will use default orientation");
                        }
                    }
                    
                } catch (Exception e) {
                    // Billboard setting failed, continue without it
                    if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                        ChunklockPlugin.getInstance().getLogger().info("❌ Could not set billboard orientation: " + e.getMessage());
                    }
                }
                
                // Create the hologram
                Object hologram = createHologramMethod.invoke(hologramManager, hologramData);
                
                if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                    ChunklockPlugin.getInstance().getLogger().info("🎯 Created hologram object for " + entry.getKey() + " wall");
                }
                
                // Set persistence to false (don't save to disk) - use setPersistent method
                try {
                    Method setPersistentMethod = hologramClass.getMethod("setPersistent", boolean.class);
                    setPersistentMethod.invoke(hologram, false);
                } catch (Exception e) {
                    // Persistence setting failed, continue without it
                    ChunklockPlugin.getInstance().getLogger().fine("Could not set persistence: " + e.getMessage());
                }
                
                // Add hologram to manager
                addHologramMethod.invoke(hologramManager, hologram);

                activeHolograms.put(hologramKey, hologram);

                if (ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.debug-logging", false)) {
                    ChunklockPlugin.getInstance().getLogger().info("✅ Successfully registered hologram for chunk " +
                        chunk.getX() + "," + chunk.getZ() + " (" + entry.getKey() + ") for player " + player.getName() + " (key: " + hologramKey + ")");
                }
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING,
                "Error creating FancyHologram for chunk " + chunk.getX() + "," + chunk.getZ() +
                " for player " + player.getName(), e);
        }
    }



    /**
     * Get color code for difficulty level
     */
    private String getDifficultyColorCode(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> "a";      // Green
            case NORMAL -> "e";    // Yellow
            case HARD -> "c";      // Red
            case IMPOSSIBLE -> "5"; // Dark Purple
        };
    }



    /**
     * Removes hologram for a specific chunk
     */
    private void removeHologramForChunk(Player player, Chunk chunk) {
        if (!fancyHologramsAvailable) return;
        
        String prefix = getHologramKey(player, chunk);
        activeHolograms.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix)) {
                Object hologram = entry.getValue();
                if (hologram != null) {
                    try {
                        // Remove using hologram object
                        removeHologramMethod.invoke(hologramManager, hologram);
                    } catch (Exception e) {
                        ChunklockPlugin.getInstance().getLogger().log(Level.FINE, 
                            "Error removing FancyHologram: " + e.getMessage());
                    }
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Removes all holograms for a player
     */
    private void removeHologramsForPlayer(Player player) {
        if (!fancyHologramsAvailable) return;
        
        String playerPrefix = player.getUniqueId().toString() + "_";
        
        activeHolograms.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(playerPrefix)) {
                Object hologram = entry.getValue();
                if (hologram != null) {
                    try {
                        // Remove using hologram object
                        removeHologramMethod.invoke(hologramManager, hologram);
                    } catch (Exception e) {
                        ChunklockPlugin.getInstance().getLogger().log(Level.FINE, 
                            "Error removing FancyHologram: " + e.getMessage());
                    }
                }
                return true;
            }
            return false;
        });
        
        ChunklockPlugin.getInstance().getLogger().fine("Removed all FancyHolograms for player " + player.getName());
    }



    /**
     * Generates unique key for player-chunk hologram
     */
    private String getHologramKey(Player player, Chunk chunk) {
        return getHologramKey(player, chunk, null);
    }

    private String getHologramKey(Player player, Chunk chunk, String suffix) {
        String base = player.getUniqueId() + "_" + chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
        if (suffix != null) {
            return base + "_" + suffix;
        }
        return base;
    }

    /**
     * Cleanup all holograms and tasks
     */
    public void cleanup() {
        try {
            ChunklockPlugin.getInstance().getLogger().info("Cleaning up HologramManager...");
            
            // Cancel all tasks
            for (BukkitTask task : playerHologramTasks.values()) {
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                }
            }
            playerHologramTasks.clear();
            
            // Remove all FancyHolograms
            if (fancyHologramsAvailable) {
                for (Object hologram : activeHolograms.values()) {
                    if (hologram != null) {
                        try {
                            // Remove using hologram object
                            removeHologramMethod.invoke(hologramManager, hologram);
                        } catch (Exception e) {
                            ChunklockPlugin.getInstance().getLogger().log(Level.FINE, 
                                "Error removing FancyHologram during cleanup: " + e.getMessage());
                        }
                    }
                }
            }
            activeHolograms.clear();
            
            ChunklockPlugin.getInstance().getLogger().info("HologramManager cleanup completed");
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error during hologram cleanup", e);
        }
    }

    /**
     * Get hologram statistics for debugging
     */
    public Map<String, Object> getHologramStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeHolograms", activeHolograms.size());
        stats.put("activeTasks", playerHologramTasks.size());
        stats.put("onlinePlayers", playerHologramTasks.keySet().size());
        stats.put("fancyHologramsAvailable", fancyHologramsAvailable);
        
        // Get configured provider and status
        boolean hologramsEnabled = ChunklockPlugin.getInstance().getConfig().getBoolean("holograms.enabled", true);
        String configuredProvider = ChunklockPlugin.getInstance().getConfig().getString("holograms.provider", "auto");
        stats.put("configuredProvider", configuredProvider);
        stats.put("hologramsEnabled", hologramsEnabled);
        
        // Determine actual provider being used
        if (!hologramsEnabled) {
            stats.put("hologramProvider", "Disabled in configuration");
        } else if (fancyHologramsAvailable) {
            stats.put("hologramProvider", "FancyHolograms (Active - Priority System)");
            stats.put("providerPriority", "FancyHolograms detected and prioritized");
        } else {
            // Check what's available vs configured
            boolean fancyHologramsInstalled = org.bukkit.Bukkit.getPluginManager().getPlugin("FancyHolograms") != null;
            if (fancyHologramsInstalled) {
                stats.put("hologramProvider", "FancyHolograms (Installed but failed to initialize)");
                stats.put("providerPriority", "FancyHolograms available but initialization failed");
            } else {
                stats.put("hologramProvider", "None (No supported providers available)");
                stats.put("providerPriority", "FancyHolograms not installed, no other providers supported");
            }
        }
        
        // NEW: Add world-related hologram statistics
        try {
            WorldManager worldManager = ChunklockPlugin.getInstance().getWorldManager();
            stats.put("enabledWorlds", worldManager.getEnabledWorlds());
            stats.put("worldCheckingEnabled", true);
            
            // Count holograms by world
            Map<String, Integer> hologramsByWorld = new HashMap<>();
            for (String hologramKey : activeHolograms.keySet()) {
                String[] parts = hologramKey.split("_");
                if (parts.length >= 2) {
                    String worldName = parts[1];
                    hologramsByWorld.put(worldName, hologramsByWorld.getOrDefault(worldName, 0) + 1);
                }
            }
            stats.put("hologramsByWorld", hologramsByWorld);
            
        } catch (Exception e) {
            stats.put("worldCheckingEnabled", false);
            stats.put("worldCheckError", e.getMessage());
        }
        
        return stats;
    }

    /**
     * Force refresh holograms for a specific player
     */
    public void refreshHologramsForPlayer(Player player) {
        if (player == null || !player.isOnline()) return;
        
        // NEW: Check if player is in enabled world before refreshing
        if (!isWorldEnabled(player)) {
            ChunklockPlugin.getInstance().getLogger().fine("Player " + player.getName() + 
                " is in disabled world " + player.getWorld().getName() + " - removing holograms instead of refreshing");
            removeHologramsForPlayer(player);
            return;
        }
        
        try {
            // Remove existing holograms
            removeHologramsForPlayer(player);
            
            // Update holograms immediately
            updateHologramsForPlayer(player);
            
            ChunklockPlugin.getInstance().getLogger().fine("Refreshed holograms for " + player.getName());
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error refreshing holograms for " + player.getName(), e);
        }
    }

    /**
     * Get rotation angle for hologram to face toward the chunk center
     * Returns the yaw rotation needed for hologram to face inward
     */
    private float getWallFacingYaw(WallSide wallSide) {
        return switch (wallSide) {
            case NORTH -> 180.0f;  // North wall: face south (toward chunk center)
            case SOUTH -> 0.0f;    // South wall: face north (toward chunk center)
            case EAST -> 270.0f;   // East wall: face west (toward chunk center)
            case WEST -> 90.0f;    // West wall: face east (toward chunk center)
        };
    }
}