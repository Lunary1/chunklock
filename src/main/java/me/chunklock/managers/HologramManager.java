package me.chunklock.managers;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import me.chunklock.ChunklockPlugin;

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
    private final Map<String, HologramState> hologramStates = new ConcurrentHashMap<>();
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
    // View distance is now configurable

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
     * FIXED: No longer causes flashing by avoiding unnecessary hologram recreation
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
            
            // Verify chunk world is enabled
            if (!isWorldEnabled(playerChunk.getWorld())) {
                removeHologramsForPlayer(player);
                return;
            }
            
            // Track which chunks should have holograms
            Set<String> validChunkKeys = new HashSet<>();
            
            // Check all chunks in a 3x3 grid around the player
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    try {
                        Chunk checkChunk = player.getWorld().getChunkAt(
                            playerChunk.getX() + dx, 
                            playerChunk.getZ() + dz
                        );
                        
                        // Skip chunks in disabled worlds
                        if (!isWorldEnabled(checkChunk.getWorld())) {
                            continue;
                        }
                        
                        // Initialize chunk if needed
                        chunkLockManager.initializeChunk(checkChunk, player.getUniqueId());
                        
                        String chunkKey = getChunkKey(checkChunk);

                        if (chunkLockManager.isLocked(checkChunk)) {
                            Map<WallSide, Location> locs = getWallHologramLocations(player, checkChunk);
                            if (!locs.isEmpty()) {
                                var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), checkChunk);
                                updateHologramForChunk(player, checkChunk, evaluation, locs);
                                validChunkKeys.add(chunkKey);
                            } else {
                                removeHologramForChunk(player, checkChunk);
                            }
                        } else {
                            // Chunk is unlocked - remove any hologram
                            removeHologramForChunk(player, checkChunk);
                        }
                    } catch (Exception e) {
                        ChunklockPlugin.getInstance().getLogger().log(Level.FINE, 
                            "Error checking chunk at offset " + dx + "," + dz + " for player " + player.getName(), e);
                    }
                }
            }
            
            // Clean up holograms that should no longer exist
            cleanupInvalidHolograms(player, validChunkKeys);
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error updating holograms for " + player.getName(), e);
        }
    }

    /**
     * Clean up holograms that are no longer valid (separated for clarity)
     */
    private void cleanupInvalidHolograms(Player player, Set<String> validChunkKeys) {
        String playerPrefix = player.getUniqueId().toString() + "_";
        
        activeHolograms.entrySet().removeIf(entry -> {
            String hologramKey = entry.getKey();
            if (!hologramKey.startsWith(playerPrefix)) {
                return false; // Not this player's hologram
            }
            
            // Extract chunk coordinates from hologram key (format: playerUUID_world_x_z_wallside)
            String[] parts = hologramKey.split("_");
            if (parts.length >= 4) {
                try {
                    String worldName = parts[1];
                    int chunkX = Integer.parseInt(parts[2]);
                    int chunkZ = Integer.parseInt(parts[3]);
                    String chunkKey = worldName + ":" + chunkX + ":" + chunkZ;
                    
                    // Check if the hologram's world is still enabled
                    World hologramWorld = org.bukkit.Bukkit.getWorld(worldName);
                    if (hologramWorld == null || !isWorldEnabled(hologramWorld)) {
                        removeHologramObject(entry.getValue());
                        hologramStates.remove(hologramKey);
                        return true;
                    }
                    
                    // If this chunk should not have a hologram, remove it
                    if (!validChunkKeys.contains(chunkKey)) {
                        removeHologramObject(entry.getValue());
                        hologramStates.remove(hologramKey);
                        return true;
                    }
                } catch (NumberFormatException e) {
                    // Invalid hologram key format, remove it
                    removeHologramObject(entry.getValue());
                    hologramStates.remove(hologramKey);
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * Helper method to safely remove a hologram object
     * ENHANCED: More robust removal with verification
     */
    private void removeHologramObject(Object hologram) {
        if (hologram != null && fancyHologramsAvailable) {
            try {
                // First, try to set visibility to false to hide it immediately
                try {
                    Method setVisibilityMethod = hologramClass.getMethod("setVisibility", boolean.class);
                    setVisibilityMethod.invoke(hologram, false);
                } catch (Exception e) {
                    // Not critical if visibility setting fails
                }
                
                // Then remove the hologram from the manager
                removeHologramMethod.invoke(hologramManager, hologram);
                
                // Add a tiny delay to ensure FancyHolograms processes the removal
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.FINE, 
                    "Error removing FancyHologram: " + e.getMessage());
            }
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
                
                if (neighborLocked) {
                    continue; // neighbor also locked, no hologram needed
                }

                // Calculate hologram position centered on the wall - OUTSIDE chunk boundaries
                double x, z;
                double wallOffset = ChunklockPlugin.getInstance().getConfig().getDouble("holograms.positioning.wall-offset", 0.5);
                double centerOffset = ChunklockPlugin.getInstance().getConfig().getDouble("holograms.positioning.center-offset", 8.0);
                
                if (side == WallSide.NORTH) {
                    // North wall - center horizontally, position OUTSIDE the north edge
                    x = startX + centerOffset; // Center of chunk horizontally
                    z = startZ - wallOffset; // Just OUTSIDE the north edge (into neighbor chunk)
                } else if (side == WallSide.SOUTH) {
                    // South wall - center horizontally, position OUTSIDE the south edge  
                    x = startX + centerOffset; // Center of chunk horizontally
                    z = startZ + 16 + wallOffset; // Just OUTSIDE the south edge (into neighbor chunk)
                } else if (side == WallSide.EAST) {
                    // East wall - center vertically, position OUTSIDE the east edge
                    x = startX + 16 + wallOffset; // Just OUTSIDE the east edge (into neighbor chunk)
                    z = startZ + centerOffset; // Center of chunk vertically
                } else { // WEST
                    // West wall - center vertically, position OUTSIDE the west edge
                    x = startX - wallOffset; // Just OUTSIDE the west edge (into neighbor chunk)
                    z = startZ + centerOffset; // Center of chunk vertically
                }

                double groundClearance = ChunklockPlugin.getInstance().getConfig().getDouble("holograms.positioning.ground-clearance", 2.5);
                int y = getHighestSolidY(world, (int)x, (int)z) + (int)groundClearance;
                int minHeight = ChunklockPlugin.getInstance().getConfig().getInt("holograms.positioning.min-height", 64);
                if (y < minHeight) y = minHeight;

                // Create location with proper yaw rotation to face parallel to chunk wall
                Location hologramLoc = new Location(world, x, y, z);
                
                // Apply rotation based on wall direction to face parallel to the chunk wall
                float yaw = getWallFacingYaw(side);
                hologramLoc.setYaw(yaw);
                
                // Also set pitch to 0 for horizontal orientation
                hologramLoc.setPitch(0.0f);
                
                map.put(side, hologramLoc);
                
                ChunklockPlugin.getInstance().getLogger().fine("Added " + side + " wall hologram at " + 
                    String.format("%.1f,%.1f,%.1f", x, (double)y, z) + " with yaw=" + yaw + "°");
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
     * Updates hologram for a specific chunk (only if needed to prevent flashing)
     * FIXED: No longer recreates holograms unnecessarily
     */
    private void updateHologramForChunk(Player player, Chunk chunk, ChunkEvaluator.ChunkValueData evaluation, Map<WallSide, Location> locations) {
        if (locations == null || locations.isEmpty() || !fancyHologramsAvailable) {
            return;
        }

        // Double-check world status before creating/updating holograms
        if (!isWorldEnabled(chunk.getWorld())) {
            return;
        }

        try {
            var requirement = biomeUnlockRegistry.calculateRequirement(player, evaluation.biome, evaluation.score);
            boolean hasItems = biomeUnlockRegistry.hasRequiredItems(player, evaluation.biome, evaluation.score);
            int playerItemCount = countPlayerItems(player, requirement.material());
            String materialName = formatMaterialName(requirement.material());

            // Create hologram text
            String statusColor = hasItems ? "§a" : "§c";
            String statusSymbol = hasItems ? "✓" : "✗";
            
            // Create text lines with simplified format (no 3D items)
            java.util.List<String> lines = new java.util.ArrayList<>();
            lines.add("§c§l LOCKED CHUNK"); // Red and bold text for locked chunk
            lines.add(""); // empty line
            lines.add("§7" + materialName); // material as text only
            lines.add(""); // empty line
            lines.add(statusColor + statusSymbol + " §a§l" + playerItemCount + "§7/" + "§l" + requirement.amount()); // Bold green numbers
            lines.add("§a§l RIGHT-CLICK TO UNLOCK"); // Bold green text
            
            String hologramText = String.join("\n", lines);

            for (Map.Entry<WallSide, Location> entry : locations.entrySet()) {
                String hologramKey = getHologramKey(player, chunk, entry.getKey().name());
                Location hologramLocation = entry.getValue();
                
                // Check view distance
                double distance = player.getLocation().distance(hologramLocation);
                int viewDistance = ChunklockPlugin.getInstance().getConfig().getInt("holograms.view-distance", 64);
                if (distance > viewDistance) {
                    // Remove if out of range
                    removeHologramByKey(hologramKey);
                    continue;
                }

                // Check if hologram needs update
                HologramState currentState = hologramStates.get(hologramKey);
                if (currentState != null && !currentState.needsUpdate(hologramText, hologramLocation, materialName, playerItemCount, requirement.amount(), hasItems)) {
                    // No update needed, hologram is current
                    continue;
                }

                // Remove existing hologram if it exists - with enhanced cleanup
                boolean hadExistingHologram = activeHolograms.containsKey(hologramKey);
                if (hadExistingHologram) {
                    removeHologramByKey(hologramKey);
                    
                    // Give FancyHolograms a moment to fully process the removal
                    // This prevents overlapping holograms when updating quickly
                    try {
                        Thread.sleep(50); // 50ms delay to ensure clean removal
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                // Create new hologram
                createNewHologram(hologramKey, hologramLocation, lines, entry.getKey(), viewDistance, hologramText, materialName, playerItemCount, requirement.amount(), hasItems);
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING,
                "Error updating hologram for chunk " + chunk.getX() + "," + chunk.getZ() +
                " for player " + player.getName(), e);
        }
    }

    /**
     * Creates a new hologram with proper state tracking
     */
    private void createNewHologram(String hologramKey, Location hologramLocation, java.util.List<String> lines, WallSide wallSide, int viewDistance, String hologramText, String materialName, int itemCount, int requiredCount, boolean hasItems) {
        try {
            // Create text hologram data
            Object textHologramData = textHologramDataConstructor.newInstance(hologramKey, hologramLocation);
            
            // Set the text content
            Method setTextMethod = textHologramDataClass.getMethod("setText", java.util.List.class);
            setTextMethod.invoke(textHologramData, lines);
            
            // Apply hologram settings (rotation, view distance, etc.)
            applyHologramSettings(textHologramData, textHologramDataClass, hologramLocation, wallSide, viewDistance);
            
            // Create the hologram
            Object textHologram = createHologramMethod.invoke(hologramManager, textHologramData);
            
            // Set persistence to false
            try {
                Method setPersistentMethod = hologramClass.getMethod("setPersistent", boolean.class);
                setPersistentMethod.invoke(textHologram, false);
            } catch (Exception e) {
                // Not critical if this fails
            }
            
            // Add hologram to manager
            addHologramMethod.invoke(hologramManager, textHologram);

            // Store hologram in our tracking maps
            activeHolograms.put(hologramKey, textHologram);
            hologramStates.put(hologramKey, new HologramState(hologramText, hologramLocation, materialName, itemCount, requiredCount, hasItems));

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error creating hologram: " + hologramKey, e);
        }
    }

    /**
     * Remove hologram by key (helper method)
     * ENHANCED: More thorough cleanup and verification
     */
    private void removeHologramByKey(String hologramKey) {
        // Remove main hologram
        Object existingHologram = activeHolograms.remove(hologramKey);
        if (existingHologram != null) {
            removeHologramObject(existingHologram);
            ChunklockPlugin.getInstance().getLogger().fine("Removed hologram: " + hologramKey);
        }
        hologramStates.remove(hologramKey);
        
        // Also remove any legacy item holograms
        String itemKey = hologramKey + "_item";
        Object existingItemHologram = activeHolograms.remove(itemKey);
        if (existingItemHologram != null) {
            removeHologramObject(existingItemHologram);
            ChunklockPlugin.getInstance().getLogger().fine("Removed legacy item hologram: " + itemKey);
        }
        hologramStates.remove(itemKey);
        
        // Additional safety: Remove any holograms with similar keys (in case of key conflicts)
        activeHolograms.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            if (key.startsWith(hologramKey + "_") || key.equals(hologramKey)) {
                removeHologramObject(entry.getValue());
                hologramStates.remove(key);
                ChunklockPlugin.getInstance().getLogger().fine("Removed conflicting hologram: " + key);
                return true;
            }
            return false;
        });
    }



    /**
     * Removes hologram for a specific chunk
     * FIXED: Enhanced cleanup with better logging and verification
     */
    private void removeHologramForChunk(Player player, Chunk chunk) {
        if (!fancyHologramsAvailable) return;
        
        String prefix = getHologramKey(player, chunk);
        
        // Collect all keys to remove (to avoid concurrent modification)
        java.util.List<String> keysToRemove = new java.util.ArrayList<>();
        for (String key : activeHolograms.keySet()) {
            if (key.startsWith(prefix)) {
                keysToRemove.add(key);
            }
        }
        
        // Remove all collected holograms
        for (String key : keysToRemove) {
            removeHologramByKey(key);
        }
        
        // ENHANCED: Also force cleanup of any state tracking for this chunk
        String chunkKey = getChunkKey(chunk);
        String playerPrefix = player.getUniqueId().toString() + "_";
        
        // Additional cleanup - remove any remaining state entries for this chunk
        hologramStates.entrySet().removeIf(entry -> {
            String stateKey = entry.getKey();
            if (stateKey.startsWith(playerPrefix)) {
                // Extract chunk coordinates from state key
                String[] parts = stateKey.split("_");
                if (parts.length >= 4) {
                    try {
                        String worldName = parts[1];
                        int chunkX = Integer.parseInt(parts[2]);
                        int chunkZ = Integer.parseInt(parts[3]);
                        String extractedChunkKey = worldName + ":" + chunkX + ":" + chunkZ;
                        return extractedChunkKey.equals(chunkKey);
                    } catch (NumberFormatException e) {
                        // Invalid key format, remove it
                        return true;
                    }
                }
            }
            return false;
        });
        
        if (keysToRemove.size() > 0) {
            ChunklockPlugin.getInstance().getLogger().fine("Removed " + keysToRemove.size() + 
                " holograms for chunk " + chunk.getX() + "," + chunk.getZ() + " for player " + player.getName());
        }
    }

    /**
     * Removes all holograms for a player
     */
    private void removeHologramsForPlayer(Player player) {
        if (!fancyHologramsAvailable) return;
        
        String playerPrefix = player.getUniqueId().toString() + "_";
        
        // Collect all keys to remove (to avoid concurrent modification)
        java.util.List<String> keysToRemove = new java.util.ArrayList<>();
        for (String key : activeHolograms.keySet()) {
            if (key.startsWith(playerPrefix)) {
                keysToRemove.add(key);
            }
        }
        
        // Remove all collected holograms
        for (String key : keysToRemove) {
            removeHologramByKey(key);
        }
        
        ChunklockPlugin.getInstance().getLogger().fine("Removed " + keysToRemove.size() + " holograms for player " + player.getName());
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
                            removeHologramMethod.invoke(hologramManager, hologram);
                        } catch (Exception e) {
                            ChunklockPlugin.getInstance().getLogger().log(Level.FINE, 
                                "Error removing FancyHologram during cleanup: " + e.getMessage());
                        }
                    }
                }
            }
            activeHolograms.clear();
            hologramStates.clear(); // Clear state tracking
            
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
        stats.put("trackedStates", hologramStates.size());
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
        
        // Add world-related hologram statistics
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
     * Force immediate cleanup of holograms for a specific chunk (called when chunk is unlocked)
     */
    public void forceCleanupChunk(Player player, Chunk chunk) {
        if (player == null || chunk == null || !fancyHologramsAvailable) return;
        
        try {
            // Force immediate removal of holograms for this specific chunk
            removeHologramForChunk(player, chunk);
            
            ChunklockPlugin.getInstance().getLogger().fine("Force cleaned up holograms for chunk " + 
                chunk.getX() + "," + chunk.getZ() + " for player " + player.getName());
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error during force cleanup of chunk holograms", e);
        }
    }

    /**
     * Force refresh holograms for a specific player
     */
    public void refreshHologramsForPlayer(Player player) {
        if (player == null || !player.isOnline()) return;
        
        // Check if player is in enabled world before refreshing
        if (!isWorldEnabled(player)) {
            ChunklockPlugin.getInstance().getLogger().fine("Player " + player.getName() + 
                " is in disabled world " + player.getWorld().getName() + " - removing holograms instead of refreshing");
            removeHologramsForPlayer(player);
            return;
        }
        
        try {
            // Remove existing holograms and their state
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

    /**
     * Get rotation angle in radians for hologram to face toward the chunk center
     */
    private float getWallFacingYawRadians(WallSide wallSide) {
        return (float) Math.toRadians(getWallFacingYaw(wallSide));
    }

    /**
     * Count how many of a specific material the player has in their inventory
     */
    private int countPlayerItems(Player player, Material material) {
        int count = 0;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        // Also check off-hand
        org.bukkit.inventory.ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getType() == material) {
            count += offHand.getAmount();
        }
        return count;
    }

    /**
     * Apply common settings to hologram data (view distance, billboard, rotation)
     */
    private void applyHologramSettings(Object hologramData, Class<?> hologramDataClass, Location location, WallSide wallSide, int viewDistance) {
        try {
            // Set view distance
            try {
                Method setVisibilityDistanceMethod = hologramDataClass.getMethod("setVisibilityDistance", int.class);
                setVisibilityDistanceMethod.invoke(hologramData, viewDistance);
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().fine("Could not set view distance: " + e.getMessage());
            }
            
            // Try to update location with rotation if the API supports it
            try {
                Method setLocationMethod = hologramDataClass.getMethod("setLocation", org.bukkit.Location.class);
                setLocationMethod.invoke(hologramData, location);
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().fine("setLocation method not available, using initial location");
            }
            
            // Set billboard and rotation
            try {
                // Use FIXED billboard for custom rotation (no player following)
                Class<?> billboardClass = Class.forName("org.bukkit.entity.Display$Billboard");
                Object billboard = billboardClass.getField("FIXED").get(null);
                
                Method setBillboardMethod = hologramDataClass.getMethod("setBillboard", billboardClass);
                setBillboardMethod.invoke(hologramData, billboard);
                
                // Set rotation based on wall side to face chunk center
                boolean rotationSet = false;
                
                // Method 1: Try Quaternion rotation (most precise)
                if (!rotationSet) {
                    try {
                        Class<?> quaternionClass = Class.forName("org.joml.Quaternionf");
                        Constructor<?> quaternionConstructor = quaternionClass.getConstructor();
                        Method rotateYMethod = quaternionClass.getMethod("rotateY", float.class);
                        Method setRotationMethod = hologramDataClass.getMethod("setRotation", quaternionClass);
                        
                        Object quaternion = quaternionConstructor.newInstance();
                        float yRotation = getWallFacingYawRadians(wallSide);
                        rotateYMethod.invoke(quaternion, yRotation);
                        setRotationMethod.invoke(hologramData, quaternion);
                        
                        rotationSet = true;
                    } catch (Exception quaternionException) {
                        // Quaternion rotation failed, try next method
                    }
                }
                
                // Method 2: Try setTransformation with Bukkit's Transformation class
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
                        float yawRadians = getWallFacingYawRadians(wallSide);
                        rotateYMethod.invoke(leftRotation, yawRadians);
                        
                        Object rightRotation = quaternionfClass.getConstructor().newInstance();
                        
                        // Create transformation
                        Object transformation = transformationClass.getConstructor(vector3fClass, quaternionfClass, vector3fClass, quaternionfClass)
                            .newInstance(translation, leftRotation, scale, rightRotation);
                        
                        Method setTransformationMethod = hologramDataClass.getMethod("setTransformation", transformationClass);
                        setTransformationMethod.invoke(hologramData, transformation);
                        
                        rotationSet = true;
                    } catch (Exception transformException) {
                        // setTransformation failed, try next method
                    }
                }
                
                // Method 3: Try legacy setRotation method with yaw/pitch
                if (!rotationSet) {
                    try {
                        Method setRotationMethod = hologramDataClass.getMethod("setRotation", float.class, float.class);
                        float yaw = getWallFacingYaw(wallSide);
                        setRotationMethod.invoke(hologramData, yaw, 0.0f); // yaw, pitch
                        
                        rotationSet = true;
                    } catch (Exception rotationException) {
                        // setRotation method failed
                    }
                }
                
                // Method 4: Final fallback - rotation methods not available
                // Hologram will use default orientation
                
            } catch (Exception e) {
                // Billboard setting failed, continue without it
                ChunklockPlugin.getInstance().getLogger().fine("Could not set billboard orientation: " + e.getMessage());
            }
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().fine("Error applying hologram settings: " + e.getMessage());
        }
    }
    
    /**
     * Format material name for display in holograms
     */
    private String formatMaterialName(Material material) {
        if (material == null) return "Unknown Item";
        
        // Convert material name to a more readable format
        // e.g., OAK_LOG -> Oak Log, IRON_INGOT -> Iron Ingot
        String name = material.name().toLowerCase().replace('_', ' ');
        StringBuilder formatted = new StringBuilder();
        
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (capitalizeNext) {
                formatted.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else if (c == ' ') {
                formatted.append(c);
                capitalizeNext = true;
            } else {
                formatted.append(c);
            }
        }
        
        return formatted.toString();
    }

    /**
     * Helper class to track hologram state and prevent unnecessary recreations
     */
    private static class HologramState {
        final String text;
        final Location location;
        final String materialName;
        final int itemCount;
        final int requiredCount;
        final boolean hasItems;
        
        HologramState(String text, Location location, String materialName, int itemCount, int requiredCount, boolean hasItems) {
            this.text = text;
            this.location = location.clone();
            this.materialName = materialName;
            this.itemCount = itemCount;
            this.requiredCount = requiredCount;
            this.hasItems = hasItems;
        }
        
        boolean needsUpdate(String newText, Location newLocation, String newMaterialName, int newItemCount, int newRequiredCount, boolean newHasItems) {
            return !text.equals(newText) || 
                   !location.equals(newLocation) ||
                   !materialName.equals(newMaterialName) ||
                   itemCount != newItemCount ||
                   requiredCount != newRequiredCount ||
                   hasItems != newHasItems;
        }
    }
}