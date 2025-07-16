package me.chunklock.hologram.api;

import org.bukkit.Location;

/**
 * Represents a hologram instance that can be displayed, hidden, or updated.
 * This is a wrapper around the underlying provider's hologram object.
 */
public interface Hologram {

    /**
     * Gets the unique identifier for this hologram.
     *
     * @return the hologram ID
     */
    String getId();

    /**
     * Gets the current location of this hologram.
     *
     * @return the hologram location
     */
    Location getLocation();

    /**
     * Sets the visibility of this hologram.
     *
     * @param visible true to show, false to hide
     */
    void setVisible(boolean visible);

    /**
     * Checks if this hologram is currently visible.
     *
     * @return true if visible
     */
    boolean isVisible();

    /**
     * Updates the text lines of this hologram.
     *
     * @param lines the new text lines
     */
    void updateText(java.util.List<String> lines);

    /**
     * Gets the underlying provider-specific hologram object.
     *
     * @return the wrapped hologram object
     */
    Object getWrappedHologram();

    /**
     * Checks if this hologram is still valid and exists.
     *
     * @return true if valid
     */
    boolean isValid();
}
