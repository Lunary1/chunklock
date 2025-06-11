package me.chunklock;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ChunkEvaluatorTest {

    @Test
    void evaluatesDifficultyBasedOnDistance() {
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        Mockito.when(plugin.getDataFolder()).thenReturn(new java.io.File("."));
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        ChunkValueRegistry reg = Mockito.mock(ChunkValueRegistry.class);
        Mockito.when(reg.getBiomeWeight(Mockito.any())).thenReturn(0);
        Mockito.when(reg.getBlockWeight(Mockito.any())).thenReturn(0);
        Mockito.when(reg.getThreshold("easy")).thenReturn(10);
        Mockito.when(reg.getThreshold("normal")).thenReturn(20);
        Mockito.when(reg.getThreshold("hard")).thenReturn(30);

        PlayerDataManager pdm = Mockito.mock(PlayerDataManager.class);
        World world = Mockito.mock(World.class);
        Location spawn = new Location(world, 0, 0, 0);
        Mockito.when(pdm.getChunkSpawn(Mockito.any())).thenReturn(spawn);

        Chunk chunk = Mockito.mock(Chunk.class);
        Mockito.when(chunk.getX()).thenReturn(3);
        Mockito.when(chunk.getZ()).thenReturn(0);
        Mockito.when(chunk.getWorld()).thenReturn(world);
        Block block = Mockito.mock(Block.class);
        Mockito.when(block.getType()).thenReturn(org.bukkit.Material.AIR);
        Mockito.when(block.getBiome()).thenReturn(Biome.PLAINS);
        Mockito.when(chunk.getBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(block);
        Mockito.when(world.getHighestBlockYAt(Mockito.any(Location.class))).thenReturn(64);

        ChunkEvaluator eval = new ChunkEvaluator(pdm, reg);
        var result = eval.evaluateChunk(UUID.randomUUID(), chunk);
        assertEquals(Biome.PLAINS, result.biome); // default from mock
        assertEquals(Difficulty.EASY, result.difficulty);
    }

    @Test
    void handlesMinHeightSample() {
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        Mockito.when(plugin.getDataFolder()).thenReturn(new java.io.File("."));
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        ChunkValueRegistry reg = Mockito.mock(ChunkValueRegistry.class);
        Mockito.when(reg.getBiomeWeight(Mockito.any())).thenReturn(0);
        Mockito.when(reg.getBlockWeight(Mockito.any())).thenReturn(0);
        Mockito.when(reg.getThreshold("easy")).thenReturn(10);
        Mockito.when(reg.getThreshold("normal")).thenReturn(20);
        Mockito.when(reg.getThreshold("hard")).thenReturn(30);

        PlayerDataManager pdm = Mockito.mock(PlayerDataManager.class);
        World world = Mockito.mock(World.class);
        Mockito.when(world.getMinHeight()).thenReturn(0);
        Location spawn = new Location(world, 0, 0, 0);
        Mockito.when(pdm.getChunkSpawn(Mockito.any())).thenReturn(spawn);

        Chunk chunk = Mockito.mock(Chunk.class);
        Mockito.when(chunk.getX()).thenReturn(0);
        Mockito.when(chunk.getZ()).thenReturn(0);
        Mockito.when(chunk.getWorld()).thenReturn(world);
        Block block = Mockito.mock(Block.class);
        Mockito.when(block.getType()).thenReturn(org.bukkit.Material.AIR);
        Mockito.when(block.getBiome()).thenReturn(Biome.PLAINS);
        Mockito.when(chunk.getBlock(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(block);
        Mockito.when(world.getHighestBlockYAt(Mockito.any(Location.class))).thenReturn(0);

        ChunkEvaluator eval = new ChunkEvaluator(pdm, reg);
        assertDoesNotThrow(() -> eval.evaluateChunk(UUID.randomUUID(), chunk));
    }
}
