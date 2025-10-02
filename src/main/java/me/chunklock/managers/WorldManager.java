package me.chunklock.managers;

import me.chunklock.ChunklockPlugin;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Enhanced WorldManager that handles both world-specific settings and per-player private worlds.
 * Manages world creation, loading, unloading, and player world ownership for Chunklock.
 */
public class WorldManager {
    
    private final ChunklockPlugin plugin;
    private final FileConfiguration config;
    
    // Per-player world management
    private final Map<UUID, String> playerWorlds = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> worldOwners = new ConcurrentHashMap<>();
    private final Map<String, Long> worldLastAccess = new ConcurrentHashMap<>();
    private final Map<String, Long> worldCreationTime = new ConcurrentHashMap<>();
    private final Set<String> pendingCreation = Collections.synchronizedSet(new HashSet<>());
    
    // Configuration
    private File playerWorldsFile;
    private FileConfiguration playerWorldsConfig;
    private long worldUnloadDelay = 300000L; // 5 minutes in milliseconds
    private int maxPlayerWorlds = 1000;
    private boolean enablePerPlayerWorlds = true;
    private boolean enableSymbolicLinks = true;
    
    public WorldManager(ChunklockPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        initializeConfig();
        initializePlayerWorldsConfig();
        loadPlayerWorldData();
        startWorldUnloadTask();
    }
    
    private void initializeConfig() {
        // Simple per-player world configuration
        if (!config.contains("worlds.per-player.enabled")) {
            config.set("worlds.per-player.enabled", true);
        }
        if (!config.contains("worlds.per-player.unload-delay-minutes")) {
            config.set("worlds.per-player.unload-delay-minutes", 5);
        }
        if (!config.contains("worlds.per-player.max-worlds")) {
            config.set("worlds.per-player.max-worlds", 100);
        }
        if (!config.contains("worlds.per-player.cleanup-inactive-days")) {
            config.set("worlds.per-player.cleanup-inactive-days", 30);
        }
        if (!config.contains("worlds.per-player.enable-symbolic-links")) {
            config.set("worlds.per-player.enable-symbolic-links", true);
        }
        
        // Load configuration values
        enablePerPlayerWorlds = config.getBoolean("worlds.per-player.enabled", true);
        worldUnloadDelay = config.getLong("worlds.per-player.unload-delay-minutes", 5) * 60000L;
        maxPlayerWorlds = config.getInt("worlds.per-player.max-worlds", 100);
        enableSymbolicLinks = config.getBoolean("worlds.per-player.enable-symbolic-links", true);
        
        plugin.saveConfig();
    }
    
    private void initializePlayerWorldsConfig() {
        playerWorldsFile = new File(plugin.getDataFolder(), "player_worlds.yml");
        if (!playerWorldsFile.exists()) {
            try {
                playerWorldsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create player_worlds.yml: " + e.getMessage());
            }
        }
        playerWorldsConfig = YamlConfiguration.loadConfiguration(playerWorldsFile);
    }
    
    private void loadPlayerWorldData() {
        ConfigurationSection worlds = playerWorldsConfig.getConfigurationSection("worlds");
        if (worlds == null) return;
        
        for (String worldName : worlds.getKeys(false)) {
            ConfigurationSection worldSection = worlds.getConfigurationSection(worldName);
            if (worldSection == null) continue;
            
            List<String> ownerStrings = worldSection.getStringList("owners");
            Set<UUID> owners = new HashSet<>();
            
            for (String ownerString : ownerStrings) {
                try {
                    UUID owner = UUID.fromString(ownerString);
                    owners.add(owner);
                    playerWorlds.put(owner, worldName);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in player_worlds.yml: " + ownerString);
                }
            }
            
            if (!owners.isEmpty()) {
                worldOwners.put(worldName, owners);
                worldLastAccess.put(worldName, System.currentTimeMillis());
            }
        }
        
        plugin.getLogger().info("Loaded " + playerWorlds.size() + " player world mappings");
        
        // Create symbolic links for existing worlds if enabled
        if (enableSymbolicLinks) {
            createSymbolicLinksForExistingWorlds();
        }
    }
    
    /**
     * Create symbolic links for all existing player worlds
     */
    private void createSymbolicLinksForExistingWorlds() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int linksCreated = 0;
            for (String worldName : worldOwners.keySet()) {
                File worldDir = new File(Bukkit.getWorldContainer(), worldName);
                if (worldDir.exists() && worldDir.isDirectory()) {
                    try {
                        createSymbolicLinkForExistingWorld(worldName, worldDir);
                        linksCreated++;
                    } catch (Exception e) {
                        plugin.getLogger().warning("Could not create symbolic link for existing world " + worldName + ": " + e.getMessage());
                    }
                }
            }
            if (linksCreated > 0) {
                plugin.getLogger().info("Created " + linksCreated + " symbolic links for existing worlds");
            }
        });
    }
    
    /**
     * Create symbolic link for an existing world directory
     */
    private void createSymbolicLinkForExistingWorld(String worldName, File worldDir) {
        try {
            File organizedDir = new File(Bukkit.getWorldContainer(), "chunklock_worlds");
            if (!organizedDir.exists()) {
                organizedDir.mkdirs();
            }
            
            Path actualWorldPath = worldDir.toPath().toAbsolutePath();
            Path symlinkPath = new File(organizedDir, worldName).toPath();
            
            if (!Files.exists(symlinkPath)) {
                Files.createSymbolicLink(symlinkPath, actualWorldPath);
                plugin.getLogger().fine("Created symbolic link for existing world: " + worldName);
            }
        } catch (Exception e) {
            // Silently handle errors for existing worlds to avoid spam
            plugin.getLogger().fine("Could not create symbolic link for existing world " + worldName + ": " + e.getMessage());
        }
    }
    
    private void savePlayerWorldData() {
        try {
            playerWorldsConfig.set("worlds", null); // Clear existing data
            
            ConfigurationSection worldsSection = playerWorldsConfig.createSection("worlds");
            
            for (Map.Entry<String, Set<UUID>> entry : worldOwners.entrySet()) {
                String worldName = entry.getKey();
                Set<UUID> owners = entry.getValue();
                
                ConfigurationSection worldSection = worldsSection.createSection(worldName);
                List<String> ownerStrings = new ArrayList<>();
                
                for (UUID owner : owners) {
                    ownerStrings.add(owner.toString());
                }
                
                worldSection.set("owners", ownerStrings);
                worldSection.set("created", System.currentTimeMillis());
            }
            
            playerWorldsConfig.save(playerWorldsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player_worlds.yml: " + e.getMessage());
        }
    }
    
    /**
     * Check if ChunkLock is enabled in this world
     */
    public boolean isWorldEnabled(World world) {
        if (world == null) return false;
        return isWorldEnabled(world.getName());
    }
    
    /**
     * Check if ChunkLock is enabled in this world by name
     * NEW: Only player worlds have Chunklock enabled, all default worlds are disabled
     */
    public boolean isWorldEnabled(String worldName) {
        if (worldName == null) return false;
        
        // Only enable Chunklock in player worlds
        return enablePerPlayerWorlds && isPlayerWorld(worldName);
    }
    
    /**
     * Check if auto-assignment on world change is enabled
     * NEW: Always disabled since we only use player worlds now
     */
    public boolean isAutoAssignOnWorldChangeEnabled() {
        return false; // Disabled for per-player world system
    }
    
    /**
     * Get list of enabled worlds (only player worlds)
     */
    public List<String> getEnabledWorlds() {
        List<String> enabled = new ArrayList<>();
        
        // Only include player worlds if the feature is enabled
        if (enablePerPlayerWorlds) {
            enabled.addAll(worldOwners.keySet());
        }
        
        return enabled;
    }
    
    /**
     * Check if a world name represents a player world
     */
    public boolean isPlayerWorld(String worldName) {
        if (worldName == null) return false;
        // Use simple naming: chunklock_playername_shortid
        return worldName.startsWith("chunklock_") && worldOwners.containsKey(worldName);
    }
    
    /**
     * Check if a world was recently created (within the last 5 minutes)
     */
    public boolean isRecentlyCreatedWorld(String worldName) {
        if (worldName == null || !worldCreationTime.containsKey(worldName)) {
            return false;
        }
        
        long creationTime = worldCreationTime.get(worldName);
        long currentTime = System.currentTimeMillis();
        return (currentTime - creationTime) < 300000L; // 5 minutes
    }
    
    /**
     * Get or create a player's private world - SIMPLIFIED VERSION
     */
    public CompletableFuture<World> getOrCreatePlayerWorld(Player player) {
        if (!enablePerPlayerWorlds) {
            return CompletableFuture.completedFuture(null);
        }

        UUID playerId = player.getUniqueId();
        String worldName = playerWorlds.get(playerId);
        
        // Check if player already has a world
        if (worldName != null) {
            World existingWorld = Bukkit.getWorld(worldName);
            if (existingWorld != null) {
                updateWorldAccess(worldName);
                return CompletableFuture.completedFuture(existingWorld);
            }
            
            // World exists but isn't loaded, try to load it
            return loadPlayerWorld(worldName);
        }
        
        // Create new world for player
        return createPlayerWorld(player);
    }

    /**
     * Create a new player world - SIMPLIFIED CLEAN VERSION
     */
    private CompletableFuture<World> createPlayerWorld(Player player) {
        CompletableFuture<World> future = new CompletableFuture<>();
        UUID playerId = player.getUniqueId();
        
        // Clean, short world naming: chunklock_playername_shortid
        String playerName = player.getName().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        String shortId = playerId.toString().substring(0, 8); // First 8 chars of UUID
        String worldName = "chunklock_" + playerName + "_" + shortId;
        
        // Check if we're already creating this world
        if (pendingCreation.contains(worldName)) {
            future.completeExceptionally(new IllegalStateException("World creation already in progress"));
            return future;
        }
        
        // Check world limit
        if (worldOwners.size() >= maxPlayerWorlds) {
            future.completeExceptionally(new IllegalStateException("Maximum player worlds limit reached"));
            return future;
        }
        
        pendingCreation.add(worldName);
        
        // Create world on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                WorldCreator creator = new WorldCreator(worldName)
                    .type(WorldType.NORMAL)
                    .environment(World.Environment.NORMAL)
                    .generateStructures(true);
                
                World world = creator.createWorld();
                
                if (world != null) {
                    // Configure world settings
                    configurePlayerWorld(world);
                    
                    // Create symbolic link for organization
                    createSymbolicLinkForWorld(world, worldName);
                    
                    // Register world ownership
                    Set<UUID> owners = new HashSet<>();
                    owners.add(playerId);
                    worldOwners.put(worldName, owners);
                    playerWorlds.put(playerId, worldName);
                    updateWorldAccess(worldName);
                    worldCreationTime.put(worldName, System.currentTimeMillis());
                    
                    // Save data
                    savePlayerWorldData();
                    
                    plugin.getLogger().info("Created player world: " + worldName + " for " + player.getName());
                    future.complete(world);
                } else {
                    plugin.getLogger().severe("Failed to create world: " + worldName);
                    future.completeExceptionally(new RuntimeException("World creation failed"));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error creating player world: " + worldName, e);
                future.completeExceptionally(e);
            } finally {
                pendingCreation.remove(worldName);
            }
        });
        
        return future;
    }
    
    /**
     * Create symbolic link for world organization
     * This creates a symlink in chunklock_worlds/ that points to the actual world in root
     */
    private void createSymbolicLinkForWorld(World world, String worldName) {
        // Check if symbolic links are enabled
        if (!enableSymbolicLinks) {
            return;
        }
        
        try {
            // Create organized directory structure
            File organizedDir = new File(Bukkit.getWorldContainer(), "chunklock_worlds");
            if (!organizedDir.exists()) {
                organizedDir.mkdirs();
                plugin.getLogger().info("Created chunklock_worlds directory for organization");
            }
            
            // Get paths
            Path actualWorldPath = world.getWorldFolder().toPath().toAbsolutePath();
            Path symlinkPath = new File(organizedDir, worldName).toPath();
            
            // Create symbolic link if it doesn't exist
            if (!Files.exists(symlinkPath)) {
                try {
                    Files.createSymbolicLink(symlinkPath, actualWorldPath);
                    plugin.getLogger().info("Created symbolic link: " + symlinkPath + " -> " + actualWorldPath);
                } catch (UnsupportedOperationException e) {
                    // Symbolic links not supported (older Windows versions)
                    plugin.getLogger().warning("Symbolic links not supported on this system. World organization unavailable.");
                    createFallbackOrganization(organizedDir, worldName, actualWorldPath);
                } catch (IOException e) {
                    // Permission issues or other IO errors
                    plugin.getLogger().warning("Could not create symbolic link for " + worldName + ": " + e.getMessage());
                    createFallbackOrganization(organizedDir, worldName, actualWorldPath);
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error creating symbolic link for world " + worldName, e);
        }
    }
    
    /**
     * Fallback organization when symbolic links are not available
     */
    private void createFallbackOrganization(File organizedDir, String worldName, Path actualWorldPath) {
        try {
            // Create a text file with the actual path for reference
            File referenceFile = new File(organizedDir, worldName + "_location.txt");
            try (java.io.FileWriter writer = new java.io.FileWriter(referenceFile)) {
                writer.write("World Location: " + actualWorldPath.toString() + "\n");
                writer.write("Created: " + new java.util.Date() + "\n");
                writer.write("Note: This is a reference file. The actual world is in the server root directory.\n");
            }
            plugin.getLogger().info("Created reference file: " + referenceFile.getName());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not create reference file for " + worldName + ": " + e.getMessage());
        }
    }

    /**
     * Load an existing player world
     */
    private CompletableFuture<World> loadPlayerWorld(String worldName) {
        CompletableFuture<World> future = new CompletableFuture<>();
        
        // Check if world is already being created
        if (pendingCreation.contains(worldName)) {
            future.completeExceptionally(new IllegalStateException("World creation already in progress"));
            return future;
        }
        
        pendingCreation.add(worldName);
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                WorldCreator creator = new WorldCreator(worldName);
                World world = creator.createWorld();
                
                if (world != null) {
                    configurePlayerWorld(world);
                    updateWorldAccess(worldName);
                    
                    // Create symbolic link if it doesn't exist (for existing worlds)
                    createSymbolicLinkForWorld(world, worldName);
                    
                    plugin.getLogger().info("Loaded player world: " + worldName);
                    future.complete(world);
                } else {
                    plugin.getLogger().warning("Failed to load player world: " + worldName);
                    future.completeExceptionally(new RuntimeException("World loading failed"));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error loading player world: " + worldName, e);
                future.completeExceptionally(e);
            } finally {
                pendingCreation.remove(worldName);
            }
        });
        
        return future;
    }
    
    /**
     * Configure a newly created or loaded player world
     */
    private void configurePlayerWorld(World world) {
        world.setDifficulty(Difficulty.NORMAL);
        world.setSpawnFlags(true, true);
        world.setPVP(false);
        world.setKeepSpawnInMemory(false);
        
        // Set world border (optional - for performance)
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(2000); // 1000 blocks radius
        border.setWarningDistance(50);
        
        // Pre-generate some chunks around spawn to improve starting chunk assignment
        preGenerateSpawnChunks(world);
    }
    
    /**
     * Pre-generate a small area around spawn for better performance
     */
    private void preGenerateSpawnChunks(World world) {
        try {
            // Generate a 3x3 area around spawn (9 chunks total)
            Location spawn = world.getSpawnLocation();
            int spawnChunkX = spawn.getChunk().getX();
            int spawnChunkZ = spawn.getChunk().getZ();
            
            for (int x = spawnChunkX - 1; x <= spawnChunkX + 1; x++) {
                for (int z = spawnChunkZ - 1; z <= spawnChunkZ + 1; z++) {
                    Chunk chunk = world.getChunkAt(x, z);
                    if (!chunk.isLoaded()) {
                        chunk.load(true);
                    }
                }
            }
            
            plugin.getLogger().fine("Pre-generated spawn chunks for world: " + world.getName());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to pre-generate spawn chunks for " + world.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Teleport player to their private world
     */
    public CompletableFuture<Boolean> teleportToPlayerWorld(Player player) {
        return getOrCreatePlayerWorld(player).thenApply(world -> {
            if (world == null) {
                return false;
            }
            
            // Teleport on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                Location spawnLocation = getPlayerWorldSpawn(world);
                player.teleport(spawnLocation);
                
                // For player worlds, assign spawn chunk directly (no evaluation needed)
                if (!plugin.getPlayerDataManager().hasChunk(player.getUniqueId())) {
                    // Delay service initialization to ensure player is properly in the new world
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        assignPlayerWorldSpawnChunk(player, world);
                    }, 2L); // 2 ticks delay (100ms) to ensure teleport is fully processed
                } else {
                    // Player already has a chunk - just start services for returning player
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Chunk playerChunk = spawnLocation.getChunk();
                        startPlayerServices(player, playerChunk);
                        plugin.getLogger().info("Started services for returning player " + player.getName() + " in world " + world.getName());
                    }, 2L); // 2 ticks delay (100ms) to ensure teleport is fully processed
                }
            });
            
            return true;
        }).exceptionally(throwable -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to teleport player to private world", throwable);
            return false;
        });
    }
    
    /**
     * Assign spawn chunk directly for player worlds (no evaluation needed)
     */
    private void assignPlayerWorldSpawnChunk(Player player, World world) {
        try {
            // In player worlds, simply assign the spawn chunk directly
            Chunk spawnChunk = world.getSpawnLocation().getChunk();
            
            plugin.getLogger().info("DEBUG: Assigning spawn chunk (" + spawnChunk.getX() + "," + spawnChunk.getZ() + ") in world " + world.getName() + " for " + player.getName());
            
            // Ensure chunk is loaded
            if (!spawnChunk.isLoaded()) {
                spawnChunk.load(true);
            }
            
            // Get team ID for ownership
            UUID teamId = plugin.getTeamManager().getTeamLeader(player.getUniqueId());
            
            // Add chunk to player's unlocked chunks
            plugin.getChunkLockManager().unlockChunk(spawnChunk, teamId);
            
            // Initialize surrounding chunks as locked to create visible borders for holograms
            // This recreates the original Chunklock experience in the private world
            int chunkX = spawnChunk.getX();
            int chunkZ = spawnChunk.getZ();
            
            // Initialize a 3x3 area around spawn (excluding spawn itself) as locked
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue; // Skip spawn chunk itself
                    
                    Chunk borderChunk = world.getChunkAt(chunkX + dx, chunkZ + dz);
                    // This will create the chunk as locked if it doesn't exist
                    plugin.getChunkLockManager().initializeChunk(borderChunk, player.getUniqueId());
                }
            }
            
            // Set player's spawn location to the center of the chunk
            Location centerSpawnLocation = getPlayerWorldSpawn(world);
            plugin.getPlayerDataManager().setChunk(player.getUniqueId(), centerSpawnLocation);
            
            // Start visual border and hologram services for the player
            startPlayerServices(player, spawnChunk);
            
            // IMPORTANT: Force immediate hologram update after chunk setup with longer delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    plugin.getLogger().info("DEBUG: Starting delayed hologram refresh for " + player.getName());
                    var hologramService = plugin.getHologramService();
                    if (hologramService != null) {
                        // Force immediate refresh to ensure holograms appear
                        hologramService.updateActiveHologramsForPlayer(player);
                        plugin.getLogger().info("Forced hologram refresh for " + player.getName() + " after world setup");
                        
                        // Additional refresh after another short delay
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            try {
                                hologramService.updateActiveHologramsForPlayer(player);
                                plugin.getLogger().info("Secondary hologram refresh for " + player.getName());
                            } catch (Exception e) {
                                plugin.getLogger().log(Level.WARNING, "Failed secondary hologram refresh for " + player.getName(), e);
                            }
                        }, 20L); // 1 second later
                    } else {
                        plugin.getLogger().warning("HologramService is null during delayed refresh for " + player.getName());
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to force hologram refresh for " + player.getName(), e);
                }
            }, 40L); // 2 seconds delay to ensure everything is properly set up
            
            plugin.getLogger().info("Assigned spawn chunk (" + spawnChunk.getX() + "," + spawnChunk.getZ() + ") to " + player.getName() + " in world " + world.getName());
            plugin.getLogger().info("DEBUG: Player will spawn at location (" + centerSpawnLocation.getX() + ", " + centerSpawnLocation.getY() + ", " + centerSpawnLocation.getZ() + ")");
            
            // Send success message to player with chunk coordinates  
            player.sendMessage("§a✓ Welcome to your private world!");
            player.sendMessage("§7You have been assigned chunk §e(" + spawnChunk.getX() + ", " + spawnChunk.getZ() + ")§7 at the center.");
            player.sendMessage("§7Visual borders and holograms are now active to help you explore.");
            player.sendMessage("§e💡 Move to the edge of your chunk to see locked chunks and unlock them!");
            player.sendMessage("§7§oIf you don't see holograms, try walking around or use /chunklock debug");
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to assign spawn chunk for player world", e);
            player.sendMessage("§cError setting up your world. Please try again or contact an administrator.");
        }
    }
    
    /**
     * Start all player services (borders, holograms, etc.) after world assignment
     */
    private void startPlayerServices(Player player, Chunk assignedChunk) {
        try {
            World playerWorld = player.getWorld();
            String worldName = playerWorld.getName();
            
            // Verify world is properly registered as a player world
            boolean isWorldEnabledCheck = isWorldEnabled(worldName);
            
            // Start glass border display
            try {
                ChunkBorderManager borderManager = plugin.getChunkBorderManager();
                if (borderManager != null && borderManager.isEnabled()) {
                    // Schedule border update for the player
                    borderManager.scheduleBorderUpdate(player);
                    plugin.getLogger().info("Started border services for " + player.getName());
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not start border services for " + player.getName(), e);
            }
            
            // Start hologram display (using modular system)
            try {
                plugin.getLogger().info("DEBUG: Attempting to get HologramService for " + player.getName());
                var hologramService = plugin.getHologramService();
                plugin.getLogger().info("DEBUG: HologramService retrieved: " + (hologramService != null ? "SUCCESS" : "NULL"));
                
                if (hologramService != null) {
                    plugin.getLogger().info("DEBUG: World enabled check for " + worldName + ": " + isWorldEnabledCheck);
                    plugin.getLogger().info("DEBUG: Per-player worlds enabled: " + enablePerPlayerWorlds);
                    plugin.getLogger().info("DEBUG: Is player world: " + isPlayerWorld(worldName));
                    plugin.getLogger().info("DEBUG: World owners contains key: " + worldOwners.containsKey(worldName));
                    
                    // Ensure world is recognized as enabled before starting holograms
                    if (isWorldEnabledCheck) {
                        plugin.getLogger().info("DEBUG: Starting hologram services for " + player.getName() + " in world " + worldName);
                        // ISSUE A FIX: Use delayed refresh to ensure proper eligibility calculation
                        plugin.getLogger().info("DEBUG: Scheduling delayed hologram refresh for " + player.getName());
                        hologramService.scheduleDelayedRefreshForPlayer(player);
                        plugin.getLogger().info("Scheduled delayed hologram refresh for " + player.getName() + " in world " + worldName);
                        
                    } else {
                        plugin.getLogger().warning("Cannot start hologram services - world " + worldName + " not enabled for " + player.getName());
                        plugin.getLogger().warning("- Per-player worlds enabled: " + enablePerPlayerWorlds);
                        plugin.getLogger().warning("- World registered as player world: " + isPlayerWorld(worldName));
                        
                        // Retry hologram startup after a longer delay in case of timing issues
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            boolean retryCheck = isWorldEnabled(worldName);
                            plugin.getLogger().info("DEBUG: Retry check for world " + worldName + ": " + retryCheck);
                            if (retryCheck) {
                                plugin.getLogger().info("Retrying hologram startup for " + player.getName() + " - world now enabled");
                                try {
                                    hologramService.updateActiveHologramsForPlayer(player);
                                    plugin.getLogger().info("Successfully started hologram services on retry for " + player.getName());
                                } catch (Exception retryException) {
                                    plugin.getLogger().log(Level.SEVERE, "DEBUG: Retry hologram startup failed for " + player.getName(), retryException);
                                }
                            } else {
                                plugin.getLogger().warning("DEBUG: Retry check failed - world still not enabled: " + worldName);
                            }
                        }, 20L); // 20 ticks (1 second) retry delay
                    }
                } else {
                    plugin.getLogger().warning("DEBUG: Hologram services not available - HologramService is null for " + player.getName());
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "DEBUG: Exception in hologram services startup for " + player.getName(), e);
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error starting player services for " + player.getName(), e);
        }
    }
    
    /**
     * Get spawn location for a player world - centers player in their assigned chunk
     */
    private Location getPlayerWorldSpawn(World world) {
        Location spawn = world.getSpawnLocation();
        
        // Get the spawn chunk coordinates
        Chunk spawnChunk = spawn.getChunk();
        
        // Calculate the center of the chunk (8, 8 in chunk coordinates)
        double centerX = (spawnChunk.getX() * 16) + 8.0;
        double centerZ = (spawnChunk.getZ() * 16) + 8.0;
        
        // Find a safe Y coordinate at the center
        int safeY = findSafeYCoordinate(world, (int) centerX, (int) centerZ);
        
        // Create the centered spawn location
        Location centeredSpawn = new Location(world, centerX, safeY, centerZ);
        centeredSpawn.setYaw(0.0f);  // Face north for consistency
        centeredSpawn.setPitch(0.0f);
        
        return centeredSpawn;
    }
    
    /**
     * Find a safe Y coordinate at the given X,Z position
     */
    private int findSafeYCoordinate(World world, int x, int z) {
        // Start from a reasonable height and work down to find ground level
        int maxY = Math.min(world.getMaxHeight() - 10, 120); // Don't start too high
        int minY = Math.max(world.getMinHeight() + 5, 60);   // Don't go too low
        
        plugin.getLogger().info("DEBUG: Finding safe Y coordinate at (" + x + "," + z + ") between Y" + minY + " and Y" + maxY);
        
        // First, find the highest solid ground (not leaves/air)
        for (int y = maxY; y >= minY; y--) {
            Material blockType = world.getBlockAt(x, y, z).getType();
            Material aboveType = world.getBlockAt(x, y + 1, z).getType();
            Material above2Type = world.getBlockAt(x, y + 2, z).getType();
            
            // Check if this is solid ground with air above
            if (isSafeBlock(blockType) && aboveType.isAir() && above2Type.isAir()) {
                int spawnY = y + 1;
                plugin.getLogger().info("DEBUG: Found safe spawn Y coordinate: " + spawnY + " (standing on " + blockType + ")");
                return spawnY;
            }
        }
        
        // Fallback: use a safe default height
        int fallbackY = Math.max(world.getSeaLevel() + 5, 70);
        plugin.getLogger().warning("No safe spawn found, using fallback Y coordinate: " + fallbackY);
        return fallbackY;
    }
    
    /**
     * Check if a block is safe to spawn on
     */
    private boolean isSafeBlock(Material material) {
        // Safe blocks are solid ground blocks, not dangerous or temporary
        if (!material.isSolid() || material == Material.AIR) {
            return false;
        }
        
        // Exclude dangerous blocks
        if (material.name().contains("LAVA") || 
            material.name().contains("FIRE") || 
            material.name().contains("CACTUS") || 
            material.name().contains("MAGMA")) {
            return false;
        }
        
        // Exclude leaves and other non-ground blocks
        if (material.name().contains("LEAVES") || 
            material.name().contains("LOG") ||
            material.name().contains("WOOD") ||
            material == Material.SNOW ||
            material == Material.ICE) {
            return false;
        }
        
        // Good ground blocks
        return material == Material.GRASS_BLOCK ||
               material == Material.DIRT ||
               material == Material.STONE ||
               material == Material.COBBLESTONE ||
               material == Material.SAND ||
               material == Material.GRAVEL ||
               material.name().contains("TERRACOTTA") ||
               material.name().contains("CONCRETE") ||
               material.isSolid(); // Fallback for other solid blocks
    }
    
    /**
     * Add a team member to a player's world
     */
    public boolean addTeamMemberToWorld(UUID ownerId, UUID memberId) {
        if (!enablePerPlayerWorlds) return false;
        
        String worldName = playerWorlds.get(ownerId);
        if (worldName == null) return false;
        
        Set<UUID> owners = worldOwners.get(worldName);
        if (owners == null) return false;
        
        owners.add(memberId);
        playerWorlds.put(memberId, worldName);
        updateWorldAccess(worldName);
        savePlayerWorldData();
        
        return true;
    }
    
    /**
     * Remove a team member from a player's world
     */
    public boolean removeTeamMemberFromWorld(UUID ownerId, UUID memberId) {
        if (!enablePerPlayerWorlds) return false;
        
        String worldName = playerWorlds.get(ownerId);
        if (worldName == null) return false;
        
        Set<UUID> owners = worldOwners.get(worldName);
        if (owners == null) return false;
        
        owners.remove(memberId);
        playerWorlds.remove(memberId);
        updateWorldAccess(worldName);
        savePlayerWorldData();
        
        return true;
    }
    
    /**
     * Update world access time
     */
    private void updateWorldAccess(String worldName) {
        worldLastAccess.put(worldName, System.currentTimeMillis());
    }
    
    /**
     * Start the world unload task
     */
    private void startWorldUnloadTask() {
        if (!enablePerPlayerWorlds) return;
        
        new BukkitRunnable() {
            @Override
            public void run() {
                checkAndUnloadWorlds();
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 60L, 20L * 60L); // Check every minute
    }
    
    /**
     * Check and unload unused worlds
     */
    private void checkAndUnloadWorlds() {
        long currentTime = System.currentTimeMillis();
        List<String> worldsToUnload = new ArrayList<>();
        
        for (Map.Entry<String, Long> entry : worldLastAccess.entrySet()) {
            String worldName = entry.getKey();
            long lastAccess = entry.getValue();
            
            if (currentTime - lastAccess > worldUnloadDelay) {
                World world = Bukkit.getWorld(worldName);
                if (world != null && world.getPlayers().isEmpty()) {
                    worldsToUnload.add(worldName);
                }
            }
        }
        
        // Unload worlds on main thread
        if (!worldsToUnload.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (String worldName : worldsToUnload) {
                    unloadWorld(worldName);
                }
            });
        }
    }
    
    /**
     * Unload a world safely
     */
    private boolean unloadWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return true;
        
        // Don't unload if players are still in the world
        if (!world.getPlayers().isEmpty()) return false;
        
        try {
            boolean success = Bukkit.unloadWorld(world, true);
            if (success) {
                plugin.getLogger().info("Unloaded inactive player world: " + worldName);
            } else {
                plugin.getLogger().warning("Failed to unload world: " + worldName);
            }
            return success;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error unloading world: " + worldName, e);
            return false;
        }
    }
    
    /**
     * Get the player world for a specific player
     */
    public String getPlayerWorldName(UUID playerId) {
        return playerWorlds.get(playerId);
    }
    
    /**
     * Check if a player has access to a world
     */
    public boolean hasWorldAccess(UUID playerId, String worldName) {
        if (!enablePerPlayerWorlds || !isPlayerWorld(worldName)) {
            return isWorldEnabled(worldName);
        }
        
        Set<UUID> owners = worldOwners.get(worldName);
        return owners != null && owners.contains(playerId);
    }
    
    /**
     * Get statistics about player worlds
     */
    public Map<String, Object> getPlayerWorldStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", enablePerPlayerWorlds);
        stats.put("totalPlayerWorlds", worldOwners.size());
        stats.put("loadedPlayerWorlds", worldOwners.keySet().stream()
            .mapToLong(worldName -> Bukkit.getWorld(worldName) != null ? 1 : 0)
            .sum());
        stats.put("maxPlayerWorlds", maxPlayerWorlds);
        stats.put("unloadDelayMinutes", worldUnloadDelay / 60000L);
        return stats;
    }
    
    /**
     * Handle player leaving - simplified cleanup
     */
    public void onPlayerQuit(Player player) {
        if (!enablePerPlayerWorlds) return;
        
        UUID playerId = player.getUniqueId();
        String worldName = playerWorlds.get(playerId);
        
        if (worldName != null) {
            updateWorldAccess(worldName);
            plugin.getLogger().fine("Updated access time for " + worldName + " after " + player.getName() + " disconnected");
        }
    }
    
    /**
     * Check if per-player worlds are enabled
     */
    public boolean isPerPlayerWorldsEnabled() {
        return enablePerPlayerWorlds;
    }

    /**
     * Cleanup method for plugin disable
     */
    public void cleanup() {
        savePlayerWorldData();
        
        // Unload all player worlds
        for (String worldName : new ArrayList<>(worldOwners.keySet())) {
            unloadWorld(worldName);
        }
    }
}