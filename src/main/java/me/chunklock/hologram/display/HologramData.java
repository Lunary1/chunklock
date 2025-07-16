package me.chunklock.hologram.display;

import org.bukkit.Location;
import java.util.List;
import java.util.Objects;

/**
 * Immutable data class representing hologram configuration and content.
 * Contains all information needed to create or update a hologram.
 */
public final class HologramData {

    private final String id;
    private final Location location;
    private final List<String> lines;
    private final int viewDistance;
    private final boolean persistent;
    private final float yaw;
    private final float pitch;

    private HologramData(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "Hologram ID cannot be null");
        this.location = Objects.requireNonNull(builder.location, "Location cannot be null").clone();
        this.lines = List.copyOf(Objects.requireNonNull(builder.lines, "Lines cannot be null"));
        this.viewDistance = builder.viewDistance;
        this.persistent = builder.persistent;
        this.yaw = builder.yaw;
        this.pitch = builder.pitch;
    }

    public String getId() { return id; }
    public Location getLocation() { return location.clone(); }
    public List<String> getLines() { return lines; }
    public int getViewDistance() { return viewDistance; }
    public boolean isPersistent() { return persistent; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    public static Builder builder(String id, Location location) {
        return new Builder(id, location);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof HologramData other)) return false;
        return Objects.equals(id, other.id) &&
               Objects.equals(location, other.location) &&
               Objects.equals(lines, other.lines) &&
               viewDistance == other.viewDistance &&
               persistent == other.persistent &&
               Float.compare(yaw, other.yaw) == 0 &&
               Float.compare(pitch, other.pitch) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, location, lines, viewDistance, persistent, yaw, pitch);
    }

    @Override
    public String toString() {
        return "HologramData{id='%s', location=%s, lines=%d, viewDistance=%d}"
            .formatted(id, location, lines.size(), viewDistance);
    }

    public static final class Builder {
        private String id;
        private Location location;
        private List<String> lines = List.of();
        private int viewDistance = 64;
        private boolean persistent = false;
        private float yaw = 0.0f;
        private float pitch = 0.0f;

        private Builder(String id, Location location) {
            this.id = id;
            this.location = location;
        }

        private Builder(HologramData existing) {
            this.id = existing.id;
            this.location = existing.location;
            this.lines = existing.lines;
            this.viewDistance = existing.viewDistance;
            this.persistent = existing.persistent;
            this.yaw = existing.yaw;
            this.pitch = existing.pitch;
        }

        public Builder lines(List<String> lines) {
            this.lines = lines;
            return this;
        }

        public Builder viewDistance(int viewDistance) {
            this.viewDistance = Math.max(1, viewDistance);
            return this;
        }

        public Builder persistent(boolean persistent) {
            this.persistent = persistent;
            return this;
        }

        public Builder rotation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
            return this;
        }

        public HologramData build() {
            return new HologramData(this);
        }
    }
}
