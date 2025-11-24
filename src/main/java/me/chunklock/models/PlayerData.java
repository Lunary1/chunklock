package me.chunklock.models;

import java.io.Serializable;

public class PlayerData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String spawnWorld;
    private int spawnX;
    private int spawnY;
    private int spawnZ;
    private int unlockedChunks;
    private long updatedAt;

    public PlayerData() {
        this.updatedAt = System.currentTimeMillis();
    }

    public PlayerData(String spawnWorld, int spawnX, int spawnY, int spawnZ, int unlockedChunks) {
        this.spawnWorld = spawnWorld;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.unlockedChunks = unlockedChunks;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getSpawnWorld() {
        return spawnWorld;
    }

    public void setSpawnWorld(String spawnWorld) {
        this.spawnWorld = spawnWorld;
        this.updatedAt = System.currentTimeMillis();
    }

    public int getSpawnX() {
        return spawnX;
    }

    public void setSpawnX(int spawnX) {
        this.spawnX = spawnX;
        this.updatedAt = System.currentTimeMillis();
    }

    public int getSpawnY() {
        return spawnY;
    }

    public void setSpawnY(int spawnY) {
        this.spawnY = spawnY;
        this.updatedAt = System.currentTimeMillis();
    }

    public int getSpawnZ() {
        return spawnZ;
    }

    public void setSpawnZ(int spawnZ) {
        this.spawnZ = spawnZ;
        this.updatedAt = System.currentTimeMillis();
    }

    public int getUnlockedChunks() {
        return unlockedChunks;
    }

    public void setUnlockedChunks(int unlockedChunks) {
        this.unlockedChunks = unlockedChunks;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String spawnWorld;
        private int spawnX;
        private int spawnY;
        private int spawnZ;
        private int unlockedChunks = 0;

        public Builder spawnWorld(String spawnWorld) {
            this.spawnWorld = spawnWorld;
            return this;
        }

        public Builder spawnX(int spawnX) {
            this.spawnX = spawnX;
            return this;
        }

        public Builder spawnY(int spawnY) {
            this.spawnY = spawnY;
            return this;
        }

        public Builder spawnZ(int spawnZ) {
            this.spawnZ = spawnZ;
            return this;
        }

        public Builder unlockedChunks(int unlockedChunks) {
            this.unlockedChunks = unlockedChunks;
            return this;
        }

        public PlayerData build() {
            return new PlayerData(spawnWorld, spawnX, spawnY, spawnZ, unlockedChunks);
        }
    }
}



