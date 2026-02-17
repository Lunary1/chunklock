package me.chunklock.services;

import me.chunklock.models.PlayerData;
import org.bukkit.Location;

import java.util.Set;
import java.util.UUID;

public interface PlayerStore {

    boolean initialize();

    PlayerData getPlayerData(UUID playerId);

    PlayerData getPlayerData(String playerIdStr);

    void savePlayerData(UUID playerId, PlayerData data);

    void savePlayerData(String playerIdStr, PlayerData data);

    void deletePlayerData(UUID playerId);

    void deletePlayerData(String playerIdStr);

    Set<String> getAllPlayerIds();

    int getTotalPlayers();

    Location getSpawnLocation(UUID playerId);

    void setSpawnLocation(UUID playerId, Location location);

    int getUnlockedChunks(UUID playerId);

    void setUnlockedChunks(UUID playerId, int count);

    void incrementUnlockedChunks(UUID playerId);

    void close();

    void clearCache();
}
