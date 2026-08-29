package me.chunklock.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for message keys rendering as raw keys in game.
 *
 * <p>Language files are written to the server's data folder only when absent, because admins
 * are expected to edit them. The consequence went unnoticed for a long time: a server that
 * has run <em>any</em> earlier build keeps its original {@code lang/en.yml} forever, so every
 * key added by a later update is missing from it. {@code getMessage} then falls through to
 * returning the key itself, and players see {@code gui.builder.reroll-title} printed verbatim
 * in an item's lore.</p>
 *
 * <p>This was found in play-testing the #83 re-roll button, where all nine of its lore lines
 * rendered as their keys. It is not specific to that feature - it affects every message the
 * plugin has ever added after a server's first start, and it will affect every message added
 * in future unless the bundled copy backs the on-disk one.</p>
 *
 * <p>These tests exercise the Bukkit configuration semantics the fix depends on, rather than
 * the plugin bootstrap, so they run without a live server.</p>
 */
public class LanguageDefaultsFallbackTest {

    /** What a server that first started before the re-roll feature has on disk. */
    private static final String OUTDATED_ON_DISK = """
        gui:
          builder:
            unlock-button-ready: "CLICK TO UNLOCK!"
            help-title: "How to Unlock Chunks"
        """;

    /** What the current jar bundles. */
    private static final String BUNDLED_IN_JAR = """
        gui:
          builder:
            unlock-button-ready: "CLICK TO UNLOCK!"
            reroll-title: "Change Required Material"
            reroll-explain: "This chunk's price is locked in."
            help-title: "How to Unlock Chunks"
        """;

    private static YamlConfiguration load(String yaml) {
        return YamlConfiguration.loadConfiguration(new StringReader(yaml));
    }

    @Test
    @DisplayName("without the fix, a key added after the server's first start is missing")
    public void testOutdatedFileMissesNewKeys() {
        YamlConfiguration onDisk = load(OUTDATED_ON_DISK);

        assertFalse(onDisk.contains("gui.builder.reroll-title"),
            "this is the defect: the admin's file predates the key, so lookup fails and the "
                + "raw key is shown to players");
    }

    @Test
    @DisplayName("bundled defaults supply keys the on-disk file lacks")
    public void testBundledDefaultsSupplyMissingKeys() {
        YamlConfiguration onDisk = load(OUTDATED_ON_DISK);
        onDisk.setDefaults(load(BUNDLED_IN_JAR));

        assertTrue(onDisk.contains("gui.builder.reroll-title"),
            "contains() must see the bundled value, since getMessage guards its lookup with it");
        assertEquals("Change Required Material", onDisk.getString("gui.builder.reroll-title"),
            "the message must resolve to real text rather than the key");
    }

    @Test
    @DisplayName("an admin's edits still win over the bundled copy")
    public void testAdminEditsTakePriority() {
        YamlConfiguration onDisk = load("""
            gui:
              builder:
                unlock-button-ready: "Click here to claim this land!"
            """);
        onDisk.setDefaults(load(BUNDLED_IN_JAR));

        assertEquals("Click here to claim this land!",
            onDisk.getString("gui.builder.unlock-button-ready"),
            "falling back for missing keys must never overwrite a customised message - that "
                + "would silently revert every admin's translations on update");
    }

    @Test
    @DisplayName("get() falls through to defaults, which is what getMessage actually calls")
    public void testGetFallsThroughToDefaults() {
        YamlConfiguration onDisk = load(OUTDATED_ON_DISK);
        onDisk.setDefaults(load(BUNDLED_IN_JAR));

        // getMessage reads via contains(key) then get(key); both must see defaults or the
        // fallback is only half-wired.
        Object value = onDisk.get("gui.builder.reroll-explain");

        assertNotNull(value, "get() must resolve from defaults too, not only getString()");
        assertEquals("This chunk's price is locked in.", value.toString());
    }

    @Test
    @DisplayName("copyDefaults stays off, so the admin's file is not rewritten on disk")
    public void testDefaultsAreNotCopiedIntoTheSavedFile() {
        YamlConfiguration onDisk = load(OUTDATED_ON_DISK);
        onDisk.setDefaults(load(BUNDLED_IN_JAR));
        onDisk.options().copyDefaults(false);

        assertFalse(onDisk.saveToString().contains("reroll-title"),
            "the bundled values back the lookup at runtime; writing them into the admin's file "
                + "would rewrite a file they own");
    }

    @Test
    @DisplayName("a key absent from both file and jar still reports missing")
    public void testGenuinelyMissingKeyIsStillMissing() {
        YamlConfiguration onDisk = load(OUTDATED_ON_DISK);
        onDisk.setDefaults(load(BUNDLED_IN_JAR));

        assertFalse(onDisk.contains("gui.builder.does-not-exist"),
            "the fallback must not mask a genuine typo in a key name - that warning is how "
                + "such mistakes get noticed");
    }
}
