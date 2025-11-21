package me.chunklock.managers;

import me.chunklock.ChunklockPlugin;
import me.chunklock.util.chunk.ChunkUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Manages the single dedicated Chunklock world.
 * Handles world creation, chunk pre-generation, player claims, and teleportation.
 */
public class SingleWorldManager {
    
    private final ChunklockPlugin plugin;
    private final FileConfiguration config;
    private final Random random = new Random();
    
    // World configuration
    private String chunklockWorldName = "chunklock_world";
    private int worldDiameter = 30000;
    private int minDistanceBetweenClaims = 2;
    private boolean worldSetup = false;
    
    // Player claims tracking
    private final Map<UUID, Chunk> playerClaims = new ConcurrentHashMap<>();
    private final Set<Chunk> claimedChunks = ConcurrentHashMap.newKeySet();
    
    // Progress tracking for setup
    private final Map<UUID, Integer> setupProgress = new ConcurrentHashMap<>();
    
    public SingleWorldManager(ChunklockPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        loadConfiguration();
    }
    
    /**
     * Load configuration from worlds.yml
     */
    private void loadConfiguration() {
        // Use modular config
        me.chunklock.config.modular.WorldsConfig worldsConfig = plugin.getConfigManager().getWorldsConfig();
        
        if (worldsConfig != null) {
            chunklockWorldName = worldsConfig.getWorldName();
            worldDiameter = worldsConfig.getWorldDiameter();
            minDistanceBetweenClaims = worldsConfig.getMinDistanceBetweenClaims();
        } else {
            // Fallback defaults
            chunklockWorldName = "chunklock_world";
            worldDiameter = 30000;
            minDistanceBetweenClaims = 2;
        }
        
        // Check if world is already setup (either loaded in memory or exists on disk)
        World world = Bukkit.getWorld(chunklockWorldName);
        if (world != null) {
            // World is already loaded
            worldSetup = true;
            plugin.getLogger().info("ChunkLock world '" + chunklockWorldName + "' is already loaded");
            
            // Start pre-allocation service for existing world
            startPreAllocationService(world);
        } else {
            // Check if world exists on disk
            java.io.File worldFolder = new java.io.File(Bukkit.getWorldContainer(), chunklockWorldName);
            if (worldFolder.exists() && worldFolder.isDirectory()) {
                // World exists on disk, try to load it
                plugin.getLogger().info("Found existing ChunkLock world '" + chunklockWorldName + "' on disk, loading...");
                try {
                    WorldCreator creator = new WorldCreator(chunklockWorldName);
                    world = creator.createWorld();
                    if (world != null) {
                        worldSetup = true;
                        plugin.getLogger().info("Successfully loaded existing ChunkLock world '" + chunklockWorldName + "'");
                        
                        // Start pre-allocation service for loaded world
                        startPreAllocationService(world);
                    } else {
                        worldSetup = false;
                        plugin.getLogger().warning("Failed to load existing ChunkLock world '" + chunklockWorldName + "'");
                    }
                } catch (Exception e) {
                    worldSetup = false;
                    plugin.getLogger().severe("Error loading existing ChunkLock world '" + chunklockWorldName + "': " + e.getMessage());
                }
            } else {
                // World doesn't exist on disk
                worldSetup = false;
                plugin.getLogger().info("ChunkLock world '" + chunklockWorldName + "' not found - needs to be set up");
            }
        }
        
        plugin.getLogger().info("SingleWorldManager loaded - World: " + chunklockWorldName + 
                               " (setup: " + worldSetup + "), Diameter: " + worldDiameter);
    }
    
    /**
     * Helper method to start the chunk pre-allocation service
     */
    private void startPreAllocationService(World world) {
        try {
            me.chunklock.services.ChunkPreAllocationService preAllocationService = 
                ChunklockPlugin.getInstance().getChunkPreAllocationService();
            if (preAllocationService != null) {
                preAllocationService.start(world);
                plugin.getLogger().info("✅ Chunk pre-allocation service started for world: " + world.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, 
                "Failed to start chunk pre-allocation service for world " + world.getName(), e);
        }
    }
    
    /**
     * Setup the Chunklock world with specified diameter
     */
    public CompletableFuture<Boolean> setupChunklockWorld(int diameter, Player admin) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // Update configuration
        this.worldDiameter = diameter;
        config.set("world.name", chunklockWorldName);
        config.set("world.diameter", diameter);
        if (!config.contains("claims.min-distance-between-claims")) {
            config.set("claims.min-distance-between-claims", minDistanceBetweenClaims);
        }
        plugin.saveConfig();
        
        // Run world creation asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Create world on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        createChunklockWorld(admin, future);
                    } catch (Exception e) {
                        plugin.getLogger().severe("Failed to create Chunklock world: " + e.getMessage());
                        e.printStackTrace();
                        future.complete(false);
                    }
                });
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error in world setup task: " + e.getMessage());
                e.printStackTrace();
                future.complete(false);
            }
        });
        
        return future;
    }
    
    /**
     * Create the Chunklock world (main thread only)
     */
    private void createChunklockWorld(Player admin, CompletableFuture<Boolean> future) {
        try {
            plugin.getLogger().info("Creating Chunklock world: " + chunklockWorldName);
            
            // Check if world already exists
            World existingWorld = Bukkit.getWorld(chunklockWorldName);
            if (existingWorld != null) {
                plugin.getLogger().warning("World " + chunklockWorldName + " already exists!");
                worldSetup = true;
                future.complete(true);
                return;
            }
            
            // Create world creator
            WorldCreator creator = new WorldCreator(chunklockWorldName);
            creator.type(WorldType.NORMAL);
            creator.generateStructures(true);
            creator.environment(World.Environment.NORMAL);
            
            // Create the world
            World world = creator.createWorld();
            if (world == null) {
                plugin.getLogger().severe("Failed to create world: " + chunklockWorldName);
                future.complete(false);
                return;
            }
            
            // Configure world settings
            world.setDifficulty(Difficulty.NORMAL);
            world.setPVP(false);
            world.setSpawnFlags(true, false); // Spawn animals but not monsters at spawn
            world.setKeepSpawnInMemory(true);
            
            // Set world border
            WorldBorder border = world.getWorldBorder();
            border.setCenter(0, 0);
            border.setSize(worldDiameter);
            border.setWarningDistance(100);
            border.setWarningTime(10);
            
            plugin.getLogger().info("World created successfully: " + chunklockWorldName);
            admin.sendMessage(Component.text("🌍 World created: " + chunklockWorldName)
                .color(NamedTextColor.GREEN));
            
            worldSetup = true;
            
            // Start chunk pre-generation
            preGenerateChunks(world, admin, future);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error creating Chunklock world: " + e.getMessage());
            e.printStackTrace();
            future.complete(false);
        }
    }
    
    /**
     * Pre-generate chunks within the world border asynchronously
     */
    private void preGenerateChunks(World world, Player admin, CompletableFuture<Boolean> future) {
        try {
            int radius = worldDiameter / 2;
            int chunkRadius = (radius / 16) + 1; // Convert to chunk coordinates
            
            // Calculate total chunks to generate
            int totalChunks = (chunkRadius * 2) * (chunkRadius * 2);
            
            admin.sendMessage(Component.text("🔄 Starting chunk pre-generation...")
                .color(NamedTextColor.YELLOW));
            admin.sendMessage(Component.text("Estimated chunks: " + totalChunks + " (this may take a while)")
                .color(NamedTextColor.GRAY));
            
            AtomicInteger generated = new AtomicInteger(0);
            AtomicInteger progress = new AtomicInteger(0);
            
            // Store progress for this admin
            setupProgress.put(admin.getUniqueId(), 0);
            
            // Generate chunks in batches to prevent server lag
            new BukkitRunnable() {
                private int currentX = -chunkRadius;
                private int currentZ = -chunkRadius;
                private final int batchSize = 10; // Generate 10 chunks per tick
                private int lastReportedPercent = 0;
                
                @Override
                public void run() {
                    try {
                        // Generate a batch of chunks
                        for (int i = 0; i < batchSize && currentX <= chunkRadius; i++) {
                            if (currentZ > chunkRadius) {
                                currentX++;
                                currentZ = -chunkRadius;
                                if (currentX > chunkRadius) {
                                    break; // Done
                                }
                            }
                            
                            // Check if chunk is within circular boundary (for better performance)
                            double distance = Math.sqrt(currentX * currentX + currentZ * currentZ);
                            if (distance <= chunkRadius) {
                                // Use Paper's async chunk generation if available
                                if (world.getClass().getMethod("getChunkAtAsync", int.class, int.class) != null) {
                                    world.getChunkAtAsync(currentX, currentZ);
                                } else {
                                    // Fallback to synchronous generation
                                    world.getChunkAt(currentX, currentZ);
                                }
                                generated.incrementAndGet();
                            }
                            
                            currentZ++;
                        }
                        
                        // Update progress
                        int currentProgress = (generated.get() * 100) / totalChunks;
                        progress.set(currentProgress);
                        setupProgress.put(admin.getUniqueId(), currentProgress);
                        
                        // Report progress every 10%
                        if (currentProgress >= lastReportedPercent + 10) {
                            lastReportedPercent = currentProgress;
                            admin.sendMessage(Component.text("⏳ Pre-generation progress: " + currentProgress + "% (" + generated.get() + "/" + totalChunks + " chunks)")
                                .color(NamedTextColor.YELLOW));
                        }
                        
                        // Check if done
                        if (currentX > chunkRadius) {
                            // Completed
                            setupProgress.remove(admin.getUniqueId());
                            admin.sendMessage(Component.text("✅ Chunk pre-generation completed!")
                                .color(NamedTextColor.GREEN));
                            admin.sendMessage(Component.text("Generated " + generated.get() + " chunks")
                                .color(NamedTextColor.GRAY));
                            
                            plugin.getLogger().info("Chunk pre-generation completed for world " + chunklockWorldName + 
                                                   " - Generated " + generated.get() + " chunks");
                            
                            // NEW: Start chunk pre-allocation service for instant chunk assignment
                            startPreAllocationService(world);
                            
                            future.complete(true);
                            this.cancel();
                        }
                        
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Error during chunk pre-generation", e);
                        admin.sendMessage(Component.text("⚠️ Error during chunk generation: " + e.getMessage())
                            .color(NamedTextColor.RED));
                        setupProgress.remove(admin.getUniqueId());
                        future.complete(false);
                        this.cancel();
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L); // Run every tick
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error starting chunk pre-generation: " + e.getMessage());
            e.printStackTrace();
            future.complete(false);
        }
    }
    
    /**
     * Teleport player to the Chunklock world and assign them a starting chunk
     */
    public CompletableFuture<Boolean> teleportPlayerToChunklockWorld(Player player) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        if (!worldSetup) {
            future.complete(false);
            return future;
        }
        
        World world = Bukkit.getWorld(chunklockWorldName);
        if (world == null) {
            future.complete(false);
            return future;
        }
        
        // Check if player already has a claim
        Chunk existingClaim = playerClaims.get(player.getUniqueId());
        if (existingClaim != null) {
            // Teleport to existing claim
            Location teleportLoc = ChunkUtils.getChunkCenter(existingClaim);
            player.teleport(teleportLoc);
            
            // Update chunk borders after teleportation (with delay to ensure teleport is complete)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
                    if (borderManager != null && borderManager.isEnabled()) {
                        // Ensure the chunk is unlocked and trigger border update
                        ChunkLockManager chunkLockManager = ChunklockPlugin.getInstance().getChunkLockManager();
                        chunkLockManager.initializeChunk(existingClaim, player.getUniqueId());
                        chunkLockManager.unlockChunk(existingClaim, player.getUniqueId());
                        
                        // Schedule border update for the player
                        borderManager.scheduleBorderUpdate(player);
                        
                        plugin.getLogger().fine("Triggered border update for player " + player.getName() + 
                            " after teleport to existing claim " + existingClaim.getX() + "," + existingClaim.getZ());
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(java.util.logging.Level.WARNING, 
                        "Error updating borders after teleport to existing claim for " + player.getName(), e);
                }
            }, 20L); // 1 second delay to ensure teleport is complete
            
            future.complete(true);
            return future;
        }
        
        // Find and assign a new starting chunk
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Chunk startingChunk = findValidStartingChunk(world);
                if (startingChunk != null) {
                    // Assign the chunk to the player
                    playerClaims.put(player.getUniqueId(), startingChunk);
                    claimedChunks.add(startingChunk);
                    
                    // Teleport on main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Location teleportLoc = ChunkUtils.getChunkCenter(startingChunk);
                        player.teleport(teleportLoc);
                        player.setRespawnLocation(teleportLoc, true);
                        
                        // Update chunk borders after teleportation (with delay to ensure teleport is complete)
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            try {
                                ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
                                if (borderManager != null && borderManager.isEnabled()) {
                                    // Initialize the chunk and trigger border update
                                    ChunkLockManager chunkLockManager = ChunklockPlugin.getInstance().getChunkLockManager();
                                    chunkLockManager.initializeChunk(startingChunk, player.getUniqueId());
                                    chunkLockManager.unlockChunk(startingChunk, player.getUniqueId());
                                    
                                    // Schedule border update for the player
                                    borderManager.scheduleBorderUpdate(player);
                                    
                                    plugin.getLogger().fine("Triggered border update for player " + player.getName() + 
                                        " after teleport to chunk " + startingChunk.getX() + "," + startingChunk.getZ());
                                }
                            } catch (Exception e) {
                                plugin.getLogger().log(java.util.logging.Level.WARNING, 
                                    "Error updating borders after teleport for " + player.getName(), e);
                            }
                        }, 20L); // 1 second delay to ensure teleport is complete
                        
                        future.complete(true);
                    });
                } else {
                    future.complete(false);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error assigning starting chunk to " + player.getName(), e);
                future.complete(false);
            }
        });
        
        return future;
    }
    
    /**
     * Find a valid starting chunk that meets distance requirements
     */
    private Chunk findValidStartingChunk(World world) {
        int radius = worldDiameter / 2;
        int chunkRadius = (radius / 16) - 5; // Leave some buffer from world border
        int maxAttempts = 1000;
        
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Generate random chunk coordinates within bounds
            int chunkX = random.nextInt(chunkRadius * 2) - chunkRadius;
            int chunkZ = random.nextInt(chunkRadius * 2) - chunkRadius;
            
            Chunk candidate = world.getChunkAt(chunkX, chunkZ);
            
            // Check if this chunk meets distance requirements
            if (isValidStartingChunk(candidate)) {
                return candidate;
            }
        }
        
        plugin.getLogger().warning("Could not find valid starting chunk after " + maxAttempts + " attempts");
        return null;
    }
    
    /**
     * Check if a chunk is valid for player claims (distance requirements)
     */
    private boolean isValidStartingChunk(Chunk candidate) {
        // Check if chunk is suitable for spawning (not water-dominated)
        try {
            if (!ChunklockPlugin.getInstance().getChunkEvaluator().isChunkSuitableForSpawning(candidate)) {
                return false; // Skip water-dominated chunks
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.FINE, "Error checking spawn suitability for chunk", e);
            return false; // If error checking, assume unsafe
        }
        
        // Check distance from all existing claims
        for (Chunk claimedChunk : claimedChunks) {
            int deltaX = Math.abs(candidate.getX() - claimedChunk.getX());
            int deltaZ = Math.abs(candidate.getZ() - claimedChunk.getZ());
            
            // Use Chebyshev distance (max of deltaX and deltaZ) for chunk distance
            int distance = Math.max(deltaX, deltaZ);
            
            if (distance < minDistanceBetweenClaims) {
                return false; // Too close to existing claim
            }
        }
        
        return true;
    }
    
    // Getters and utility methods
    
    public boolean isChunklockWorldSetup() {
        // Check if world is currently loaded (more reliable than cached flag)
        World world = Bukkit.getWorld(chunklockWorldName);
        if (world != null) {
            worldSetup = true; // Update cached flag
            return true;
        }
        
        // If not loaded, check if it exists on disk and try to load it
        java.io.File worldFolder = new java.io.File(Bukkit.getWorldContainer(), chunklockWorldName);
        if (worldFolder.exists() && worldFolder.isDirectory()) {
            plugin.getLogger().info("ChunkLock world exists on disk but not loaded, attempting to load...");
            try {
                WorldCreator creator = new WorldCreator(chunklockWorldName);
                world = creator.createWorld();
                if (world != null) {
                    worldSetup = true;
                    plugin.getLogger().info("Successfully loaded ChunkLock world '" + chunklockWorldName + "'");
                    return true;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load existing ChunkLock world: " + e.getMessage());
            }
        }
        
        worldSetup = false;
        return false;
    }
    
    public String getChunklockWorldName() {
        return chunklockWorldName;
    }
    
    public int getWorldDiameter() {
        return worldDiameter;
    }
    
    public World getChunklockWorld() {
        return Bukkit.getWorld(chunklockWorldName);
    }
    
    public Chunk getPlayerClaim(UUID playerId) {
        return playerClaims.get(playerId);
    }
    
    public boolean hasPlayerClaim(UUID playerId) {
        return playerClaims.containsKey(playerId);
    }
    
    public int getMinDistanceBetweenClaims() {
        return minDistanceBetweenClaims;
    }
    
    public int getSetupProgress(UUID adminId) {
        return setupProgress.getOrDefault(adminId, -1);
    }
    
    public boolean isSetupInProgress() {
        return !setupProgress.isEmpty();
    }
    
    public int getTotalClaims() {
        return claimedChunks.size();
    }
    
    /**
     * Clear a player's claim (for testing or admin commands)
     */
    public void clearPlayerClaim(UUID playerId) {
        Chunk claim = playerClaims.remove(playerId);
        if (claim != null) {
            claimedChunks.remove(claim);
        }
    }
    
    /**
     * Get all claimed chunks (for admin/debug purposes)
     */
    public Set<Chunk> getClaimedChunks() {
        return new HashSet<>(claimedChunks);
    }
}