package me.chunklock.hologram.core;

import org.bukkit.Location;
import java.util.List;
import java.util.Objects;

/**
 * Immutable hologram state used for change detection and caching.
 * Includes content hash for efficient update detection.
 */
public final class HologramState {
    
    private final Location location;
    private final List<String> lines;
    private final int contentHash;
    private final boolean spawned;
    private final boolean active;
    private final long lastUpdateTick;
    
    public HologramState(Location location, List<String> lines, boolean spawned, boolean active, long currentTick) {
        this.location = Objects.requireNonNull(location).clone();
        this.lines = List.copyOf(Objects.requireNonNull(lines));
        this.contentHash = computeContentHash(location, lines);
        this.spawned = spawned;
        this.active = active;
        this.lastUpdateTick = currentTick;
    }
    
    private static int computeContentHash(Location location, List<String> lines) {
        return Objects.hash(
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(), 
            location.getBlockZ(),
            String.join("\n", lines)
        );
    }
    
    public boolean needsUpdate(Location newLocation, List<String> newLines) {
        int newHash = computeContentHash(newLocation, newLines);
        return contentHash != newHash;
    }
    
    public HologramState withSpawnState(boolean spawned, boolean active, long currentTick) {
        return new HologramState(location, lines, spawned, active, currentTick);
    }
    
    public HologramState withContent(Location newLocation, List<String> newLines, long currentTick) {
        return new HologramState(newLocation, newLines, spawned, active, currentTick);
    }
    
    // Getters
    public Location getLocation() { return location.clone(); }
    public List<String> getLines() { return lines; }
    public int getContentHash() { return contentHash; }
    public boolean isSpawned() { return spawned; }
    public boolean isActive() { return active; }
    public long getLastUpdateTick() { return lastUpdateTick; }
    
    @Override
    public String toString() {
        return "HologramState{spawned=" + spawned + ", active=" + active + ", lines=" + lines.size() + "}";
    }
}
