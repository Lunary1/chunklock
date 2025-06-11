package me.chunklock;

import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class PlayerDataManagerTest {

    @Test
    void savesAndLoadsSpawn(@TempDir Path tempDir) throws Exception {
        File dataFolder = tempDir.toFile();
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        Mockito.when(plugin.getDataFolder()).thenReturn(dataFolder);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        // Create world mock
        World world = Mockito.mock(World.class);
        Mockito.when(world.getName()).thenReturn("world");

        UUID uuid = UUID.randomUUID();
        Location loc = new Location(world, 1, 2, 3);

        try (MockedStatic<Bukkit> mocked = Mockito.mockStatic(Bukkit.class)) {
            mocked.when(() -> Bukkit.getWorld("world")).thenReturn(world);

            PlayerDataManager mgr = new PlayerDataManager(plugin);
            mgr.setChunk(uuid, loc);
            mgr.saveAll();

            PlayerDataManager mgr2 = new PlayerDataManager(plugin);
            assertEquals(loc.getBlockX(), mgr2.getChunkSpawn(uuid).getBlockX());
            assertEquals(loc.getBlockY(), mgr2.getChunkSpawn(uuid).getBlockY());
            assertEquals(loc.getBlockZ(), mgr2.getChunkSpawn(uuid).getBlockZ());
        }
    }
}
