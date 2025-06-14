package me.chunklock;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages chunk ownership and overclaiming functionality
 */
public class ChunkOwnershipManager {

    public static class ChunkOwnership {
        private final UUID ownerId;
        private final String ownerName;
        private final long unlockTime;
        private final boolean isOverclaimed;
        private final UUID originalOwnerId; // For tracking overclaim chains
        
        public ChunkOwnership(UUID ownerId, String ownerName, long unlockTime, boolean isOverclaimed, UUID originalOwnerId) {
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.unlockTime = unlockTime;
            this.isOverclaimed = isOverclaimed;
            this.originalOwnerId = originalOwnerId;
        }
        
        // Getters
        public UUID getOwnerId() { return ownerId; }
        public String getOwnerName() { return ownerName; }
        public long getUnlockTime() { return unlockTime; }
        public boolean isOverclaimed() { return isOverclaimed; }
        public UUID getOriginalOwnerId() { return originalOwnerId; }
        
        public boolean isProtected(long protectionDurationMs) {
            return (System.currentTimeMillis() - unlockTime) < protectionDurationMs;
        }
    }

    public static class OverclaimLimits {
        private final Map<UUID, Integer> dailyOverclaims = new ConcurrentHashMap<>();
        private final Map<UUID, Long> lastResetTime = new ConcurrentHashMap<>();
        private static final long DAY_MS = 24 * 60 * 60 * 1000L;
        
        public boolean canOverclaim(UUID playerId, int maxPerDay) {
            resetDailyLimitsIfNeeded(playerId);
            return dailyOverclaims.getOrDefault(playerId, 0) < maxPerDay;
        }
        
        public void recordOverclaim(UUID playerId) {
            resetDailyLimitsIfNeeded(playerId);
            dailyOverclaims.put(playerId, dailyOverclaims.getOrDefault(playerId, 0) + 1);
        }
        
        public int getRemainingOverclaims(UUID playerId, int maxPerDay) {
            resetDailyLimitsIfNeeded(playerId);
            return Math.max(0, maxPerDay - dailyOverclaims.getOrDefault(playerId, 0));
        }
        
        private void resetDailyLimitsIfNeeded(UUID playerId) {
            long now = System.currentTimeMillis();
            Long lastReset = lastResetTime.get(playerId);
            
            if (lastReset == null || (now - lastReset) >= DAY_MS) {
                dailyOverclaims.put(playerId, 0);
                lastResetTime.put(playerId, now);
            }
        }
    }

    private final JavaPlugin plugin;
    private final TeamManager teamManager;
    private final File ownershipFile;
    private final FileConfiguration ownershipConfig;
    private final Map<String, ChunkOwnership> chunkOwners = new ConcurrentHashMap<>();
    private final Map<String, Long> overclaimCooldowns = new ConcurrentHashMap<>();
    private final OverclaimLimits overclaimLimits = new OverclaimLimits();
    
    // Configuration values
    private boolean overclaimEnabled;
    private double overclaimMultiplier;
    private long ownerProtectionTime;
    private double teamProtectionMultiplier;
    private int maxOverclaimsPerDay;
    private long overclaimCooldownTime;

    public ChunkOwnershipManager(JavaPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.ownershipFile = new File(plugin.getDataFolder(), "chunk_ownership.yml");
        
        // Initialize ownership config file
        if (!ownershipFile.exists()) {
            ownershipFile.getParentFile().mkdirs();
            try {
                ownershipFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create chunk_ownership.yml");
            }
        }
        
        this.ownershipConfig = YamlConfiguration.loadConfiguration(ownershipFile);
        
        loadConfiguration();
        loadOwnershipData();
    }

    private void loadConfiguration() {
        FileConfiguration config = plugin.getConfig();
        
        // Load overclaim settings with defaults
        overclaimEnabled = config.getBoolean("overclaim.enabled", true);
        overclaimMultiplier = config.getDouble("overclaim.base_multiplier", 3.0);
        ownerProtectionTime = config.getLong("overclaim.owner_protection_hours", 24) * 60 * 60 * 1000L;
        teamProtectionMultiplier = config.getDouble("overclaim.team_protection_multiplier", 5.0);
        maxOverclaimsPerDay = config.getInt("overclaim.max_overclaims_per_day", 3);
        overclaimCooldownTime = config.getLong("overclaim.overclaim_cooldown_hours", 6) * 60 * 60 * 1000L;
        
        plugin.getLogger().info("Overclaiming system " + (overclaimEnabled ? "ENABLED" : "DISABLED"));
        if (overclaimEnabled) {
            plugin.getLogger().info("Overclaim settings: multiplier=" + overclaimMultiplier + 
                ", protection=" + (ownerProtectionTime / (60 * 60 * 1000L)) + "h" +
                ", max_per_day=" + maxOverclaimsPerDay);
        }
    }

    private void loadOwnershipData() {
        int loadedCount = 0;
        int errorCount = 0;
        
        for (String chunkKey : ownershipConfig.getKeys(false)) {
            try {
                String ownerIdStr = ownershipConfig.getString(chunkKey + ".owner_id");
                String ownerName = ownershipConfig.getString(chunkKey + ".owner_name", "Unknown");
                long unlockTime = ownershipConfig.getLong(chunkKey + ".unlock_time", System.currentTimeMillis());
                boolean isOverclaimed = ownershipConfig.getBoolean(chunkKey + ".is_overclaimed", false);
                String originalOwnerIdStr = ownershipConfig.getString(chunkKey + ".original_owner_id");
                
                if (ownerIdStr != null) {
                    UUID ownerId = UUID.fromString(ownerIdStr);
                    UUID originalOwnerId = originalOwnerIdStr != null ? UUID.fromString(originalOwnerIdStr) : ownerId;
                    
                    chunkOwners.put(chunkKey, new ChunkOwnership(ownerId, ownerName, unlockTime, isOverclaimed, originalOwnerId));
                    loadedCount++;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load ownership for chunk: " + chunkKey);
                errorCount++;
            }
        }
        
        plugin.getLogger().info("Loaded " + loadedCount + " chunk ownerships" + 
            (errorCount > 0 ? " (" + errorCount + " errors)" : ""));
    }

    public void saveOwnershipData() {
        try {
            // Clear existing data
            for (String key : ownershipConfig.getKeys(false)) {
                ownershipConfig.set(key, null);
            }
            
            // Save current ownership data
            for (Map.Entry<String, ChunkOwnership> entry : chunkOwners.entrySet()) {
                String chunkKey = entry.getKey();
                ChunkOwnership ownership = entry.getValue();
                
                ownershipConfig.set(chunkKey + ".owner_id", ownership.getOwnerId().toString());
                ownershipConfig.set(chunkKey + ".owner_name", ownership.getOwnerName());
                ownershipConfig.set(chunkKey + ".unlock_time", ownership.getUnlockTime());
                ownershipConfig.set(chunkKey + ".is_overclaimed", ownership.isOverclaimed());
                if (ownership.getOriginalOwnerId() != null) {
                    ownershipConfig.set(chunkKey + ".original_owner_id", ownership.getOriginalOwnerId().toString());
                }
            }
            
            ownershipConfig.save(ownershipFile);
            plugin.getLogger().info("Saved " + chunkOwners.size() + " chunk ownerships");
            
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save chunk_ownership.yml", e);
        }
    }

    /**
     * Sets the owner of a chunk when it's unlocked normally
     */
    public void setChunkOwner(Chunk chunk, UUID ownerId, String ownerName) {
        String chunkKey = getChunkKey(chunk);
        long now = System.currentTimeMillis();
        
        ChunkOwnership ownership = new ChunkOwnership(ownerId, ownerName, now, false, ownerId);
        chunkOwners.put(chunkKey, ownership);
        
        plugin.getLogger().fine("Set owner of chunk " + chunk.getX() + "," + chunk.getZ() + " to " + ownerName);
    }

    /**
     * Attempts to overclaim a chunk from another player
     */
    public OverclaimResult attemptOverclaim(Chunk chunk, Player newOwner) {
        if (!overclaimEnabled) {
            return new OverclaimResult(false, "Overclaiming is disabled on this server");
        }
        
        String chunkKey = getChunkKey(chunk);
        ChunkOwnership currentOwnership = chunkOwners.get(chunkKey);
        
        if (currentOwnership == null) {
            return new OverclaimResult(false, "This chunk has no owner to overclaim from");
        }
        
        UUID newOwnerId = newOwner.getUniqueId();
        
        // Check if already owns the chunk
        if (currentOwnership.getOwnerId().equals(newOwnerId)) {
            return new OverclaimResult(false, "You already own this chunk");
        }
        
        // Check if same team
        if (teamManager.sameTeam(currentOwnership.getOwnerId(), newOwnerId)) {
            return new OverclaimResult(false, "You cannot overclaim from your team member");
        }
        
        // Check protection time
        if (currentOwnership.isProtected(ownerProtectionTime)) {
            long remainingMs = ownerProtectionTime - (System.currentTimeMillis() - currentOwnership.getUnlockTime());
            long remainingHours = remainingMs / (60 * 60 * 1000L);
            return new OverclaimResult(false, "This chunk is protected for " + remainingHours + " more hours");
        }
        
        // Check overclaim cooldown
        Long lastOverclaim = overclaimCooldowns.get(chunkKey);
        if (lastOverclaim != null && (System.currentTimeMillis() - lastOverclaim) < overclaimCooldownTime) {
            long remainingMs = overclaimCooldownTime - (System.currentTimeMillis() - lastOverclaim);
            long remainingHours = remainingMs / (60 * 60 * 1000L);
            return new OverclaimResult(false, "This chunk was recently overclaimed. Cooldown: " + remainingHours + "h");
        }
        
        // Check daily overclaim limits
        if (!overclaimLimits.canOverclaim(newOwnerId, maxOverclaimsPerDay)) {
            return new OverclaimResult(false, "You have reached your daily overclaim limit (" + maxOverclaimsPerDay + ")");
        }
        
        return new OverclaimResult(true, "Overclaim allowed");
    }

    /**
     * Completes an overclaim after requirements are met
     */
    public void completeOverclaim(Chunk chunk, UUID newOwnerId, String newOwnerName) {
        String chunkKey = getChunkKey(chunk);
        ChunkOwnership oldOwnership = chunkOwners.get(chunkKey);
        
        if (oldOwnership != null) {
            // Notify old owner if online
            Player oldOwner = Bukkit.getPlayer(oldOwnership.getOwnerId());
            if (oldOwner != null && oldOwner.isOnline()) {
                oldOwner.sendMessage("§c⚠ Your chunk at " + chunk.getX() + "," + chunk.getZ() + 
                    " has been overclaimed by " + newOwnerName + "!");
            }
            
            // Create new ownership record
            UUID originalOwnerId = oldOwnership.getOriginalOwnerId();
            ChunkOwnership newOwnership = new ChunkOwnership(newOwnerId, newOwnerName, 
                System.currentTimeMillis(), true, originalOwnerId);
            
            chunkOwners.put(chunkKey, newOwnership);
            overclaimCooldowns.put(chunkKey, System.currentTimeMillis());
            overclaimLimits.recordOverclaim(newOwnerId);
            
            plugin.getLogger().info(newOwnerName + " overclaimed chunk " + chunk.getX() + "," + chunk.getZ() + 
                " from " + oldOwnership.getOwnerName());
        }
    }

    /**
     * Calculates the overclaim cost multiplier for a chunk
     */
    public double calculateOverclaimMultiplier(Chunk chunk, UUID newOwnerId) {
        ChunkOwnership ownership = getChunkOwnership(chunk);
        if (ownership == null) {
            return 1.0; // No overclaim multiplier if no owner
        }
        
        double multiplier = overclaimMultiplier;
        
        // Apply team protection multiplier
        if (teamManager.sameTeam(ownership.getOwnerId(), newOwnerId)) {
            multiplier *= teamProtectionMultiplier;
        }
        
        // Future: Could add biome-specific or difficulty-specific multipliers here
        
        return multiplier;
    }

    public ChunkOwnership getChunkOwnership(Chunk chunk) {
        return chunkOwners.get(getChunkKey(chunk));
    }

    public boolean isChunkOwned(Chunk chunk) {
        return chunkOwners.containsKey(getChunkKey(chunk));
    }

    public boolean isOwnedBy(Chunk chunk, UUID playerId) {
        ChunkOwnership ownership = getChunkOwnership(chunk);
        return ownership != null && ownership.getOwnerId().equals(playerId);
    }

    public boolean isOwnedByTeam(Chunk chunk, UUID playerId) {
        ChunkOwnership ownership = getChunkOwnership(chunk);
        return ownership != null && teamManager.sameTeam(ownership.getOwnerId(), playerId);
    }

    public int getRemainingOverclaims(UUID playerId) {
        return overclaimLimits.getRemainingOverclaims(playerId, maxOverclaimsPerDay);
    }

    public boolean isOverclaimEnabled() {
        return overclaimEnabled;
    }

    /**
     * Gets all chunks owned by a player or their team
     */
    public List<String> getOwnedChunks(UUID playerId, boolean includeTeam) {
        List<String> ownedChunks = new ArrayList<>();
        
        for (Map.Entry<String, ChunkOwnership> entry : chunkOwners.entrySet()) {
            ChunkOwnership ownership = entry.getValue();
            boolean owns = ownership.getOwnerId().equals(playerId);
            boolean teamOwns = includeTeam && teamManager.sameTeam(ownership.getOwnerId(), playerId);
            
            if (owns || teamOwns) {
                ownedChunks.add(entry.getKey());
            }
        }
        
        return ownedChunks;
    }

    /**
     * Removes ownership when a chunk is reset/locked
     */
    public void removeChunkOwnership(Chunk chunk) {
        String chunkKey = getChunkKey(chunk);
        ChunkOwnership removed = chunkOwners.remove(chunkKey);
        overclaimCooldowns.remove(chunkKey);
        
        if (removed != null) {
            plugin.getLogger().fine("Removed ownership of chunk " + chunk.getX() + "," + chunk.getZ() + 
                " from " + removed.getOwnerName());
        }
    }

    /**
     * Resets all ownership for a player (used in player reset)
     */
    public void resetPlayerOwnership(UUID playerId) {
        List<String> toRemove = new ArrayList<>();
        
        for (Map.Entry<String, ChunkOwnership> entry : chunkOwners.entrySet()) {
            if (entry.getValue().getOwnerId().equals(playerId)) {
                toRemove.add(entry.getKey());
            }
        }
        
        for (String chunkKey : toRemove) {
            chunkOwners.remove(chunkKey);
            overclaimCooldowns.remove(chunkKey);
        }
        
        plugin.getLogger().info("Reset ownership for " + toRemove.size() + " chunks owned by player " + playerId);
    }

    /**
     * Gets ownership statistics for debugging
     */
    public Map<String, Object> getOwnershipStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOwnedChunks", chunkOwners.size());
        stats.put("chunksCoolingDown", overclaimCooldowns.size());
        stats.put("overclaimEnabled", overclaimEnabled);
        stats.put("overclaimMultiplier", overclaimMultiplier);
        
        // Count overclaimed vs original ownership
        long overclaimedCount = chunkOwners.values().stream().mapToLong(o -> o.isOverclaimed() ? 1 : 0).sum();
        stats.put("overclaimedChunks", overclaimedCount);
        stats.put("originalOwnershipChunks", chunkOwners.size() - overclaimedCount);
        
        return stats;
    }

    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    public static class OverclaimResult {
        private final boolean allowed;
        private final String message;
        
        public OverclaimResult(boolean allowed, String message) {
            this.allowed = allowed;
            this.message = message;
        }
        
        public boolean isAllowed() { return allowed; }
        public String getMessage() { return message; }
    }
}