package me.chunklock.hologram.tracking;

import org.bukkit.Location;
import java.util.Objects;

/**
 * Immutable state tracking for holograms with progress tracking capabilities.
 * Used by the display service to track material requirements and player progress.
 */
public final class HologramState {

    private final String text;
    private final Location location;
    private final String materialName;
    private final int itemCount;
    private final int requiredCount;
    private final boolean hasItems;
    private final long lastUpdate;

    public HologramState(String text, Location location, String materialName, 
                        int itemCount, int requiredCount, boolean hasItems) {
        this.text = Objects.requireNonNull(text);
        this.location = Objects.requireNonNull(location).clone();
        this.materialName = Objects.requireNonNull(materialName);
        this.itemCount = itemCount;
        this.requiredCount = requiredCount;
        this.hasItems = hasItems;
        this.lastUpdate = System.currentTimeMillis();
    }

    /**
     * Checks if this hologram state needs updating based on new values.
     */
    public boolean needsUpdate(String newText, Location newLocation, String newMaterialName,
                              int newItemCount, int newRequiredCount, boolean newHasItems) {
        return !text.equals(newText) ||
               !location.equals(newLocation) ||
               !materialName.equals(newMaterialName) ||
               itemCount != newItemCount ||
               requiredCount != newRequiredCount ||
               hasItems != newHasItems;
    }

    /**
     * Creates a new state with updated values.
     */
    public HologramState withUpdates(String newText, Location newLocation, String newMaterialName,
                                   int newItemCount, int newRequiredCount, boolean newHasItems) {
        return new HologramState(newText, newLocation, newMaterialName, 
                               newItemCount, newRequiredCount, newHasItems);
    }

    // Getters
    public String getText() { return text; }
    public Location getLocation() { return location.clone(); }
    public String getMaterialName() { return materialName; }
    public int getItemCount() { return itemCount; }
    public int getRequiredCount() { return requiredCount; }
    public boolean hasItems() { return hasItems; }
    public long getLastUpdate() { return lastUpdate; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof HologramState other)) return false;
        return Objects.equals(text, other.text) &&
               Objects.equals(location, other.location) &&
               Objects.equals(materialName, other.materialName) &&
               itemCount == other.itemCount &&
               requiredCount == other.requiredCount &&
               hasItems == other.hasItems;
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, location, materialName, itemCount, requiredCount, hasItems);
    }

    @Override
    public String toString() {
        return "HologramState{material='%s', items=%d/%d, hasItems=%s}"
            .formatted(materialName, itemCount, requiredCount, hasItems);
    }
}
