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
        private float yaw;
        private float pitch;
        private boolean persistent = false;

        /**
         * Orientation defaults to the location's own yaw and pitch (issue #104).
         *
         * <p>It used to default to zero, and no caller ever set it. Border holograms are placed by
         * {@code HologramLocationUtils}, which stamps the correct per-wall facing onto the Location
         * it returns - and the provider renders them with a FIXED billboard, so that yaw is the
         * only thing deciding which way the text points. Dropping it here made every hologram face
         * south, leaving three of the four chunk walls edge-on and unreadable.
         *
         * <p>Taking the default from the Location keeps the two in step, so a new call site cannot
         * reintroduce the bug by forgetting to copy the angle across.
         */
        public Builder(HologramId id, Location location) {
            this.id = id;
            this.location = location;
            this.yaw = location != null ? location.getYaw() : 0.0f;
            this.pitch = location != null ? location.getPitch() : 0.0f;
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
