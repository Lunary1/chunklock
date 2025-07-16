package me.chunklock.hologram.api;

import org.bukkit.Location;
import me.chunklock.hologram.display.HologramData;

import java.util.List;
import java.util.Optional;

/**
 * Interface for hologram providers (FancyHolograms, HolographicDisplays, etc.)
 * Abstracts the underlying hologram implementation.
 */
public interface HologramProvider {

    /**
     * Gets the provider name for identification.
     *
     * @return the provider name
     */
    String getProviderName();

    /**
     * Checks if this provider is available and properly initialized.
     *
     * @return true if the provider is ready to use
     */
    boolean isAvailable();

    /**
     * Creates a new hologram with the given data.
     *
     * @param hologramData the hologram configuration
     * @return the created hologram instance, or empty if creation failed
     */
    Optional<Hologram> createHologram(HologramData hologramData);

    /**
     * Removes the specified hologram.
     *
     * @param hologram the hologram to remove
     * @return true if removal was successful
     */
    boolean removeHologram(Hologram hologram);

    /**
     * Updates an existing hologram with new data.
     *
     * @param hologram the hologram to update
     * @param newData the new hologram data
     * @return true if update was successful
     */
    boolean updateHologram(Hologram hologram, HologramData newData);

    /**
     * Performs cleanup when the provider is being shut down.
     */
    void cleanup();

    /**
     * Gets provider-specific statistics.
     *
     * @return statistics map
     */
    java.util.Map<String, Object> getStatistics();
}
