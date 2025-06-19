package me.chunklock;

import java.util.UUID;

public class ChunkData {
    private boolean locked;
    private Difficulty difficulty;
    private UUID ownerId;

    public ChunkData(boolean locked, Difficulty difficulty) {
        this(locked, difficulty, null);
    }

    public ChunkData(boolean locked, Difficulty difficulty, UUID ownerId) {
        this.locked = locked;
        this.difficulty = difficulty;
        this.ownerId = ownerId;
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
}