package me.chunklock.util.world;

import java.io.File;

/**
 * Resolves where a world's data actually lives on disk (issue #103).
 *
 * <p><b>Paper 26.1 reorganised world storage.</b> Before it, a world called {@code chunklock_world}
 * got its own top-level folder in the world container:
 *
 * <pre>
 *   server/chunklock_world/{region,entities,poi,data}
 * </pre>
 *
 * <p>From 26.1 the dimensions of a server share one container, keyed by namespace:
 *
 * <pre>
 *   server/world/level.dat
 *   server/world/dimensions/minecraft/chunklock_world/{region,entities,poi,data}
 *   server/world/dimensions/minecraft/{overworld,the_nether,the_end}/...
 * </pre>
 *
 * <p>Chunklock decided whether its world had ever been created by testing the legacy path for
 * existence. On 26.1 that test is always false, so the plugin reported the world missing on every
 * boot and forced an admin to re-run {@code /chunklock setup}, regenerating chunks over a world
 * whose region files were sitting on disk the whole time.
 *
 * <p><b>Note the new layout has no {@code level.dat} of its own per dimension</b> - that file lives
 * once at the container root and describes all of them. So "does this world exist" has to be
 * answered by looking for the dimension's own data, not for a level file.
 *
 * <p>This resolver checks the 26.1 location first and falls back to the legacy one, so a server
 * upgraded in place keeps loading a world created under the old layout.
 */
public final class WorldFolderResolver {

    /** Default namespace for worlds created through the Bukkit API. */
    public static final String DEFAULT_NAMESPACE = "minecraft";

    /** The container-relative directory 26.1+ nests dimensions under. */
    private static final String DIMENSIONS = "dimensions";

    /**
     * Subdirectories that mark a real dimension folder. A world that has been generated has at
     * least one of these; an empty directory left behind by a failed run has none.
     */
    private static final String[] DATA_MARKERS = { "region", "entities", "poi", "data" };

    private WorldFolderResolver() {
    }

    /**
     * Returns the folder holding this world's data, or {@code null} if it has not been created.
     *
     * <p>Checks the Paper 26.1+ nested layout first, then the pre-26.1 top-level layout.
     *
     * @param worldContainer the server's world container, from {@code Bukkit.getWorldContainer()}
     * @param worldName      the world's name, for example {@code chunklock_world}
     */
    public static File resolveExisting(File worldContainer, String worldName) {
        if (worldContainer == null || worldName == null || worldName.isBlank()) {
            return null;
        }

        File modern = modernLocation(worldContainer, worldName);
        if (isGeneratedWorldFolder(modern)) {
            return modern;
        }

        File legacy = legacyLocation(worldContainer, worldName);
        if (isGeneratedWorldFolder(legacy)) {
            return legacy;
        }

        return null;
    }

    /**
     * True when this world already exists on disk under either layout.
     */
    public static boolean exists(File worldContainer, String worldName) {
        return resolveExisting(worldContainer, worldName) != null;
    }

    /**
     * The Paper 26.1+ location: {@code <container>/world/dimensions/minecraft/<name>}.
     *
     * <p>{@code Bukkit.getWorldContainer()} returns the server root, and the top-level world
     * directory sits inside it, so the dimensions tree is addressed from there.
     */
    public static File modernLocation(File worldContainer, String worldName) {
        return modernLocation(worldContainer, DEFAULT_NAMESPACE, worldName);
    }

    /**
     * The Paper 26.1+ location for an explicit namespace.
     */
    public static File modernLocation(File worldContainer, String namespace, String worldName) {
        File dimensionsRoot = new File(worldContainer, DIMENSIONS);
        if (dimensionsRoot.isDirectory()) {
            // The container already points at the top-level world directory.
            return new File(new File(dimensionsRoot, namespace), worldName);
        }
        // The container is the server root, so descend through the primary world directory.
        File primary = new File(worldContainer, primaryWorldName(worldContainer));
        return new File(new File(new File(primary, DIMENSIONS), namespace), worldName);
    }

    /**
     * The pre-26.1 location: {@code <container>/<name>}.
     */
    public static File legacyLocation(File worldContainer, String worldName) {
        return new File(worldContainer, worldName);
    }

    /**
     * Finds the top-level world directory inside the server root - the one carrying
     * {@code level.dat}. Falls back to {@code world}, the server default.
     */
    private static String primaryWorldName(File serverRoot) {
        File[] candidates = serverRoot.listFiles(File::isDirectory);
        if (candidates != null) {
            for (File candidate : candidates) {
                if (new File(candidate, "level.dat").isFile()
                    && new File(candidate, DIMENSIONS).isDirectory()) {
                    return candidate.getName();
                }
            }
        }
        return "world";
    }

    /**
     * A directory counts as a generated world only if it carries dimension data. An empty folder
     * left behind by an interrupted setup must not be mistaken for a usable world.
     */
    private static boolean isGeneratedWorldFolder(File folder) {
        if (folder == null || !folder.isDirectory()) {
            return false;
        }
        for (String marker : DATA_MARKERS) {
            if (new File(folder, marker).isDirectory()) {
                return true;
            }
        }
        return false;
    }
}
