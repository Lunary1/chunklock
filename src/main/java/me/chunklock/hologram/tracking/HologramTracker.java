package me.chunklock.hologram.tracking;

import me.chunklock.hologram.api.Hologram;
import org.bukkit.entity.Player;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages tracking of hologram states and active holograms per player.
 * Thread-safe implementation for concurrent access.
 */
public final class HologramTracker {

    private final Map<String, Hologram> activeHolograms = new ConcurrentHashMap<>();
    private final Map<String, HologramState> hologramStates = new ConcurrentHashMap<>();

    /**
     * Adds a hologram to tracking.
     */
    public void trackHologram(String key, Hologram hologram, HologramState state) {
        activeHolograms.put(key, hologram);
        hologramStates.put(key, state);
    }

    /**
     * Removes a hologram from tracking.
     */
    public Hologram untrackHologram(String key) {
        hologramStates.remove(key);
        return activeHolograms.remove(key);
    }

    /**
     * Gets the current state of a hologram.
     */
    public HologramState getState(String key) {
        return hologramStates.get(key);
    }

    /**
     * Gets an active hologram by key.
     */
    public Hologram getHologram(String key) {
        return activeHolograms.get(key);
    }

    /**
     * Checks if a hologram is being tracked.
     */
    public boolean isTracked(String key) {
        return activeHolograms.containsKey(key);
    }

    /**
     * Gets all hologram keys for a specific player.
     */
    public Set<String> getPlayerHologramKeys(Player player) {
        String playerPrefix = player.getUniqueId().toString() + "_";
        return activeHolograms.keySet().stream()
            .filter(key -> key.startsWith(playerPrefix))
            .collect(Collectors.toSet());
    }

    /**
     * Removes all holograms for a specific player.
     */
    public Map<String, Hologram> removePlayerHolograms(Player player) {
        Set<String> playerKeys = getPlayerHologramKeys(player);
        Map<String, Hologram> removed = new ConcurrentHashMap<>();
        
        for (String key : playerKeys) {
            Hologram hologram = untrackHologram(key);
            if (hologram != null) {
                removed.put(key, hologram);
            }
        }
        
        return removed;
    }

    /**
     * Gets all hologram keys that start with the given prefix.
     */
    public Set<String> getHologramKeysWithPrefix(String prefix) {
        return activeHolograms.keySet().stream()
            .filter(key -> key.startsWith(prefix))
            .collect(Collectors.toSet());
    }

    /**
     * Removes all tracked holograms and returns them.
     */
    public Map<String, Hologram> clear() {
        Map<String, Hologram> all = Map.copyOf(activeHolograms);
        activeHolograms.clear();
        hologramStates.clear();
        return all;
    }

    /**
     * Gets statistics about tracked holograms.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("activeHolograms", activeHolograms.size());
        stats.put("trackedStates", hologramStates.size());
        
        // Count by player
        Map<UUID, Long> playerCounts = activeHolograms.keySet().stream()
            .filter(key -> key.contains("_"))
            .collect(Collectors.groupingBy(
                key -> UUID.fromString(key.split("_")[0]),
                Collectors.counting()
            ));
        stats.put("hologramsByPlayer", playerCounts);
        
        return stats;
    }
}
