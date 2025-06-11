import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class ChunkValueRegistryTest {

    @Test
    void loadsWeightsFromConfig(@TempDir Path tempDir) throws Exception {
        File dataFolder = tempDir.toFile();
        File yaml = new File(dataFolder, "chunk_values.yml");
        try (FileWriter writer = new FileWriter(yaml)) {
            writer.write("thresholds:\n  easy: 10\n  normal: 20\n  hard: 30\n" +
                         "biomes:\n  PLAINS: 5\n" +
                         "blocks:\n  STONE: 2\n");
        }

        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        Mockito.when(plugin.getDataFolder()).thenReturn(dataFolder);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        ChunkValueRegistry reg = new ChunkValueRegistry(plugin);
        assertEquals(5, reg.getBiomeWeight(org.bukkit.block.Biome.PLAINS));
        assertEquals(2, reg.getBlockWeight(org.bukkit.Material.STONE));
        assertEquals(10, reg.getThreshold("easy"));
    }
}
