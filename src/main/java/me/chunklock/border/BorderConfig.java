package me.chunklock.border;

import org.bukkit.Material;

public class BorderConfig {
    public boolean enabled;
    public boolean useFullHeight;
    public int borderHeight;
    public int minYOffset;
    public int maxYOffset;
    public int scanRange;
    public long updateDelay;
    public long updateCooldown;
    public boolean showForBypassPlayers;
    public boolean autoUpdateOnMovement;
    public boolean restoreOriginalBlocks;
    public boolean debugLogging;
    public Material borderMaterial;
    public boolean skipValuableOres;
    public boolean skipFluids;
    public boolean skipImportantBlocks;
    public int borderUpdateDelayTicks;
    public int maxBorderUpdatesPerTick;
}
