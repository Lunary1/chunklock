package me.chunklock;

public class ChunkData {
    private boolean locked;
    private Difficulty difficulty;

    public ChunkData(boolean locked, Difficulty difficulty) {
        this.locked = locked;
        this.difficulty = difficulty;
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
}