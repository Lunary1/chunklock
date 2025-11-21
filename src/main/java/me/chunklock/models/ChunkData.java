package me.chunklock.models;

import java.util.UUID;

public class ChunkData {
    private boolean locked;
    private Difficulty difficulty;
    private UUID ownerId;
    private double baseValue;

    public ChunkData(boolean locked, Difficulty difficulty) {
        this(locked, difficulty, null, 0.0);
    }

    public ChunkData(boolean locked, Difficulty difficulty, UUID ownerId) {
        this(locked, difficulty, ownerId, 0.0);
    }

    public ChunkData(boolean locked, Difficulty difficulty, UUID ownerId, double baseValue) {
        this.locked = locked;
        this.difficulty = difficulty;
        this.ownerId = ownerId;
        this.baseValue = baseValue;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public double getBaseValue() {
        return baseValue;
    }

    public void setBaseValue(double baseValue) {
        this.baseValue = baseValue;
    }
}