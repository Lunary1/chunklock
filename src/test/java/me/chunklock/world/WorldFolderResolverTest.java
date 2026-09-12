package me.chunklock.world;

import me.chunklock.util.world.WorldFolderResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins where Chunklock looks for its world on disk (issue #103).
 *
 * <p>Paper 26.1 moved dimensions from {@code <container>/<name>} to
 * {@code <container>/world/dimensions/<namespace>/<name>}. Chunklock only knew the old path, so on
 * 26.1 it reported its world missing on every boot and forced {@code /chunklock setup} to be
 * re-run, regenerating chunks over a world whose region files were already there.
 *
 * <p>These tests build both layouts on disk and assert the resolver finds each, because the failure
 * was entirely about which path was consulted.
 */
class WorldFolderResolverTest {

    private static final String WORLD = "chunklock_world";

    /** Builds a dimension folder with region data, the marker of a generated world. */
    private static File generated(Path root, String... segments) throws IOException {
        File dir = root.toFile();
        for (String segment : segments) {
            dir = new File(dir, segment);
        }
        assertTrue(new File(dir, "region").mkdirs(), "could not create test world folder");
        return dir;
    }

    private static void writeLevelDat(File worldDir) throws IOException {
        assertTrue(worldDir.exists() || worldDir.mkdirs());
        assertTrue(new File(worldDir, "level.dat").createNewFile());
    }

    @Test
    void findsAWorldInThePaper26Layout(@TempDir Path serverRoot) throws IOException {
        File primary = new File(serverRoot.toFile(), "world");
        writeLevelDat(primary);
        assertTrue(new File(primary, "dimensions").mkdirs());
        File expected = generated(serverRoot, "world", "dimensions", "minecraft", WORLD);

        File resolved = WorldFolderResolver.resolveExisting(serverRoot.toFile(), WORLD);

        assertNotNull(resolved, "the 26.1 nested layout must be found - this is issue #103");
        assertEquals(expected.getCanonicalPath(), resolved.getCanonicalPath());
    }

    @Test
    void stillFindsAWorldInThePre26Layout(@TempDir Path serverRoot) throws IOException {
        File expected = generated(serverRoot, WORLD);

        File resolved = WorldFolderResolver.resolveExisting(serverRoot.toFile(), WORLD);

        assertNotNull(resolved, "a world created before 26.1 must keep loading after the upgrade");
        assertEquals(expected.getCanonicalPath(), resolved.getCanonicalPath());
    }

    @Test
    void reportsMissingWhenTheWorldWasNeverCreated(@TempDir Path serverRoot) throws IOException {
        File primary = new File(serverRoot.toFile(), "world");
        writeLevelDat(primary);
        assertTrue(new File(primary, "dimensions/minecraft/overworld/region").mkdirs());

        assertNull(WorldFolderResolver.resolveExisting(serverRoot.toFile(), WORLD));
        assertFalse(WorldFolderResolver.exists(serverRoot.toFile(), WORLD));
    }

    /**
     * A negative control for the resolver itself. An empty directory left behind by an interrupted
     * setup must not read as a usable world - otherwise the plugin skips setup and starts against
     * a world with no region data.
     */
    @Test
    void anEmptyFolderIsNotMistakenForAGeneratedWorld(@TempDir Path serverRoot) {
        assertTrue(new File(serverRoot.toFile(), "world/dimensions/minecraft/" + WORLD).mkdirs());

        assertNull(WorldFolderResolver.resolveExisting(serverRoot.toFile(), WORLD),
            "an empty dimension folder carries no region data and is not a generated world");
    }

    @Test
    void prefersTheModernLayoutWhenBothExist(@TempDir Path serverRoot) throws IOException {
        File primary = new File(serverRoot.toFile(), "world");
        writeLevelDat(primary);
        generated(serverRoot, WORLD);
        File modern = generated(serverRoot, "world", "dimensions", "minecraft", WORLD);

        File resolved = WorldFolderResolver.resolveExisting(serverRoot.toFile(), WORLD);

        assertEquals(modern.getCanonicalPath(), resolved.getCanonicalPath(),
            "on 26.1 the nested copy is the live one");
    }

    @Test
    void handlesMissingInputsWithoutThrowing(@TempDir Path serverRoot) {
        assertNull(WorldFolderResolver.resolveExisting(null, WORLD));
        assertNull(WorldFolderResolver.resolveExisting(serverRoot.toFile(), null));
        assertNull(WorldFolderResolver.resolveExisting(serverRoot.toFile(), "  "));
    }
}
