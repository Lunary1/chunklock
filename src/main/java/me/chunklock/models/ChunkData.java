package me.chunklock.models;

import java.io.Serializable;
import java.util.UUID;

public class ChunkData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private boolean locked;
    private Difficulty difficulty;
    private UUID ownerId;
    private int baseValue;
    private String biome;
    private int score;
    private Long unlockedAt;

    public ChunkData(boolean locked, Difficulty difficulty) {
        this(locked, difficulty, null);
    }

    public ChunkData(boolean locked, Difficulty difficulty, UUID ownerId) {
        this(locked, difficulty, ownerId, 0, null, 0, null);
    }

    public ChunkData(boolean locked, Difficulty difficulty, UUID ownerId, int baseValue, String biome, int score, Long unlockedAt) {
        this.locked = locked;
        this.difficulty = difficulty;
        this.ownerId = ownerId;
        this.baseValue = baseValue;
        this.biome = biome;
        this.score = score;
        this.unlockedAt = unlockedAt;
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

    public int getBaseValue() {
        return baseValue;
    }

    public void setBaseValue(int baseValue) {
        this.baseValue = baseValue;
    }

    public String getBiome() {
        return biome;
    }

    public void setBiome(String biome) {
        this.biome = biome;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public Long getUnlockedAt() {
        return unlockedAt;
    }

    public void setUnlockedAt(Long unlockedAt) {
        this.unlockedAt = unlockedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean locked = true;
        private Difficulty difficulty = Difficulty.NORMAL;
        private UUID ownerId;
        private int baseValue = 0;
        private String biome;
        private int score = 0;
        private Long unlockedAt;

        public Builder locked(boolean locked) {
            this.locked = locked;
            return this;
        }

        public Builder difficulty(Difficulty difficulty) {
            this.difficulty = difficulty;
            return this;
        }

        public Builder ownerId(UUID ownerId) {
            this.ownerId = ownerId;
            return this;
        }

        public Builder baseValue(int baseValue) {
            this.baseValue = baseValue;
            return this;
        }

        public Builder biome(String biome) {
            this.biome = biome;
            return this;
        }

        public Builder score(int score) {
            this.score = score;
            return this;
        }

        public Builder unlockedAt(Long unlockedAt) {
            this.unlockedAt = unlockedAt;
            return this;
        }

        public ChunkData build() {
            return new ChunkData(locked, difficulty, ownerId, baseValue, biome, score, unlockedAt);
        }
    }
}