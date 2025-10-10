package me.chunklock.hologram.core;

import org.bukkit.Location;
import java.util.List;

/**
 * Represents the data needed to create or update a hologram.
 * This includes the location, text lines, and any formatting.
 */
public class HologramData {
    private final Location location;
    private final List<String> lines;
    private final HologramId id;
    private final double viewDistance;
    private final float yaw;
    private final float pitch;
    private final boolean persistent;

    public HologramData(HologramId id, Location location, List<String> lines) {
        this(id, location, lines, 32.0, 0.0f, 0.0f, false);
    }

    public HologramData(HologramId id, Location location, List<String> lines, double viewDistance, float yaw, float pitch, boolean persistent) {
        this.id = id;
        this.location = location;
        this.lines = lines;
        this.viewDistance = viewDistance;
        this.yaw = yaw;
        this.pitch = pitch;
        this.persistent = persistent;
    }

    public HologramId getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public List<String> getLines() {
        return lines;
    }

    public double getViewDistance() {
        return viewDistance;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public static Builder builder(HologramId id, Location location) {
        return new Builder(id, location);
    }

    public static class Builder {
        private final HologramId id;
        private final Location location;
        private List<String> lines;
        private double viewDistance = 32.0;
        private float yaw = 0.0f;
        private float pitch = 0.0f;
        private boolean persistent = false;

        public Builder(HologramId id, Location location) {
            this.id = id;
            this.location = location;
        }

        public Builder lines(List<String> lines) {
            this.lines = lines;
            return this;
        }

        public Builder viewDistance(double viewDistance) {
            this.viewDistance = viewDistance;
            return this;
        }

        public Builder yaw(float yaw) {
            this.yaw = yaw;
            return this;
        }

        public Builder pitch(float pitch) {
            this.pitch = pitch;
            return this;
        }

        public Builder persistent(boolean persistent) {
            this.persistent = persistent;
            return this;
        }

        public HologramData build() {
            return new HologramData(id, location, lines, viewDistance, yaw, pitch, persistent);
        }
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        HologramData that = (HologramData) obj;
        return id.equals(that.id);
    }

    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "HologramData{" +
                "id=" + id +
                ", location=" + location +
                ", lines=" + lines +
                ", viewDistance=" + viewDistance +
                ", yaw=" + yaw +
                ", pitch=" + pitch +
                ", persistent=" + persistent +
                '}';
    }
}
