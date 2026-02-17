package me.chunklock.economy.calculation;

import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.TeamManager;
import me.chunklock.services.ChunkStore;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Scans blocks in a player's owned chunks to build a resource inventory.
 * Used by ResourceBasedMaterialStrategy to determine what materials the player
 * can actually gather, solving the issue where biome-based costs require
 * materials that don't exist in the player's territory.
 *
 * <p>Results are cached per-player with a configurable TTL to avoid expensive
 * repeated chunk scans.</p>
 */
public class OwnedChunkScanner {

    /** Materials considered valid for payment requirements. */
    private static final Set<Material> HARVESTABLE_MATERIALS = buildHarvestableMaterials();

    /**
     * Resource tier mapping — higher tier = more valuable, lower required amount.
     * <ul>
     *   <li>Tier 1 (Common): dirt, cobble, sand — cheap, need lots</li>
     *   <li>Tier 2 (Wood): logs — moderate</li>
     *   <li>Tier 3 (Crops): wheat, sugarcane — moderate</li>
     *   <li>Tier 4 (Stone/mineral): stone, deepslate, copper — moderate-high</li>
     *   <li>Tier 5 (Precious): iron, gold, lapis, redstone — high</li>
     *   <li>Tier 6 (Rare): diamond, emerald — very high</li>
     * </ul>
     */
    private static final Map<Material, Integer> MATERIAL_TIERS = buildMaterialTiers();

    private final ChunklockPlugin plugin;
    private final ChunkStore chunkStore;
    private final TeamManager teamManager;

    // Cache: playerId -> (timestamp, resourceMap)
    private final Map<UUID, CachedScan> scanCache = new ConcurrentHashMap<>();
    private long cacheTtlMs = 60_000L; // Default 60 seconds
    private int minAbundance = 10; // Minimum block count to consider

    public OwnedChunkScanner(ChunklockPlugin plugin, ChunkStore chunkStore, TeamManager teamManager) {
        this.plugin = plugin;
        this.chunkStore = chunkStore;
        this.teamManager = teamManager;
    }

    public void setCacheTtlMs(long ttlMs) {
        this.cacheTtlMs = ttlMs;
    }

    public void setMinAbundance(int minAbundance) {
        this.minAbundance = minAbundance;
    }

    /**
     * Scan all owned chunks for the given player (resolves team leader for shared chunks).
     *
     * @return sorted list of ResourceEntry (most abundant first within each tier)
     */
    public List<ResourceEntry> scanPlayerResources(Player player) {
        UUID teamId = teamManager.getTeamLeader(player.getUniqueId());

        // Check cache
        CachedScan cached = scanCache.get(teamId);
        if (cached != null && !cached.isExpired(cacheTtlMs)) {
            return cached.resources;
        }

        // Perform scan
        Map<Material, Integer> resourceCounts = new HashMap<>();
        Set<String> ownedChunkKeys = chunkStore.getChunksByOwner(teamId);

        if (ownedChunkKeys.isEmpty()) {
            plugin.getLogger().fine("No owned chunks found for " + player.getName() + " (team: " + teamId + ")");
            return Collections.emptyList();
        }

        for (String chunkKey : ownedChunkKeys) {
            try {
                scanChunk(chunkKey, resourceCounts);
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "Failed to scan chunk " + chunkKey, e);
            }
        }

        // Convert to sorted entries, filter by minimum abundance
        List<ResourceEntry> entries = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : resourceCounts.entrySet()) {
            if (entry.getValue() >= minAbundance) {
                int tier = MATERIAL_TIERS.getOrDefault(entry.getKey(), 1);
                entries.add(new ResourceEntry(entry.getKey(), entry.getValue(), tier));
            }
        }

        // Sort: highest tier first, then by count descending within same tier
        entries.sort((a, b) -> {
            int tierCmp = Integer.compare(b.tier, a.tier);
            return tierCmp != 0 ? tierCmp : Integer.compare(b.count, a.count);
        });

        // Cache result
        scanCache.put(teamId, new CachedScan(entries));

        return entries;
    }

    /**
     * Invalidate cache for a player/team (e.g., after unlocking a new chunk).
     */
    public void invalidateCache(UUID playerId) {
        scanCache.remove(playerId);
        // Also remove the team leader key
        UUID teamId = teamManager.getTeamLeader(playerId);
        if (!teamId.equals(playerId)) {
            scanCache.remove(teamId);
        }
    }

    /**
     * Clear entire cache.
     */
    public void clearCache() {
        scanCache.clear();
    }

    /**
     * Parse a chunk key and scan the chunk's blocks.
     * Chunk key format: "worldName:chunkX:chunkZ"
     */
    private void scanChunk(String chunkKey, Map<Material, Integer> resourceCounts) {
        String[] parts = chunkKey.split(":");
        if (parts.length < 3) return;

        String worldName = parts[0];
        int chunkX, chunkZ;
        try {
            chunkX = Integer.parseInt(parts[1]);
            chunkZ = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        // Only scan if chunk is loaded (don't force-load for performance)
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }

        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        scanChunkBlocks(chunk, resourceCounts);
    }

    /**
     * Scan blocks in a chunk. Samples every 2 blocks horizontally from surface down
     * to limit performance impact while still getting accurate counts.
     */
    private void scanChunkBlocks(Chunk chunk, Map<Material, Integer> resourceCounts) {
        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        // Sample every 2 blocks horizontally for performance
        for (int x = 0; x < 16; x += 2) {
            for (int z = 0; z < 16; z += 2) {
                int worldX = chunk.getX() * 16 + x;
                int worldZ = chunk.getZ() * 16 + z;

                // Scan from top of world downward
                for (int y = maxY - 1; y >= minY; y--) {
                    Block block = world.getBlockAt(worldX, y, worldZ);
                    Material mat = block.getType();

                    if (mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR) {
                        continue;
                    }

                    if (HARVESTABLE_MATERIALS.contains(mat)) {
                        // Multiply by 4 to compensate for 2×2 sampling grid
                        resourceCounts.merge(mat, 4, Integer::sum);
                    }
                }
            }
        }
    }

    /**
     * Get the tier of a material.
     */
    public static int getMaterialTier(Material material) {
        return MATERIAL_TIERS.getOrDefault(material, 1);
    }

    /**
     * Get amount multiplier for a tier. Higher tier = smaller multiplier = fewer items needed.
     */
    public static double getTierCostMultiplier(int tier) {
        return switch (tier) {
            case 1 -> 1.0;    // Common: full cost
            case 2 -> 0.5;    // Wood: half cost
            case 3 -> 0.4;    // Crops: slightly less
            case 4 -> 0.25;   // Stone/mineral: quarter cost
            case 5 -> 0.1;    // Precious: 10%
            case 6 -> 0.05;   // Rare: 5%
            default -> 1.0;
        };
    }

    // ---- Static data builders ----

    private static Set<Material> buildHarvestableMaterials() {
        Set<Material> materials = new HashSet<>();

        // Tier 1: Common
        materials.add(Material.DIRT);
        materials.add(Material.COBBLESTONE);
        materials.add(Material.SAND);
        materials.add(Material.GRAVEL);
        materials.add(Material.CLAY_BALL);
        materials.add(Material.CLAY);
        materials.add(Material.RED_SAND);
        materials.add(Material.NETHERRACK);
        materials.add(Material.MUD);

        // Tier 2: Wood
        materials.add(Material.OAK_LOG);
        materials.add(Material.BIRCH_LOG);
        materials.add(Material.SPRUCE_LOG);
        materials.add(Material.JUNGLE_LOG);
        materials.add(Material.ACACIA_LOG);
        materials.add(Material.DARK_OAK_LOG);
        materials.add(Material.CHERRY_LOG);
        materials.add(Material.MANGROVE_LOG);
        materials.add(Material.PALE_OAK_LOG);
        materials.add(Material.CRIMSON_STEM);
        materials.add(Material.WARPED_STEM);

        // Tier 3: Crops & other renewables
        materials.add(Material.WHEAT);
        materials.add(Material.SUGAR_CANE);
        materials.add(Material.BAMBOO);
        materials.add(Material.CACTUS);
        materials.add(Material.KELP);
        materials.add(Material.HAY_BLOCK);
        materials.add(Material.MELON);
        materials.add(Material.PUMPKIN);

        // Tier 4: Stone & mineral
        materials.add(Material.STONE);
        materials.add(Material.DEEPSLATE);
        materials.add(Material.GRANITE);
        materials.add(Material.DIORITE);
        materials.add(Material.ANDESITE);
        materials.add(Material.TUFF);
        materials.add(Material.BASALT);
        materials.add(Material.BLACKSTONE);
        materials.add(Material.COPPER_ORE);
        materials.add(Material.RAW_COPPER_BLOCK);
        materials.add(Material.COAL_ORE);
        materials.add(Material.DEEPSLATE_COAL_ORE);

        // Tier 5: Precious
        materials.add(Material.IRON_ORE);
        materials.add(Material.DEEPSLATE_IRON_ORE);
        materials.add(Material.GOLD_ORE);
        materials.add(Material.DEEPSLATE_GOLD_ORE);
        materials.add(Material.LAPIS_ORE);
        materials.add(Material.DEEPSLATE_LAPIS_ORE);
        materials.add(Material.REDSTONE_ORE);
        materials.add(Material.DEEPSLATE_REDSTONE_ORE);

        // Tier 6: Rare
        materials.add(Material.DIAMOND_ORE);
        materials.add(Material.DEEPSLATE_DIAMOND_ORE);
        materials.add(Material.EMERALD_ORE);
        materials.add(Material.DEEPSLATE_EMERALD_ORE);
        materials.add(Material.ANCIENT_DEBRIS);

        return Collections.unmodifiableSet(materials);
    }

    private static Map<Material, Integer> buildMaterialTiers() {
        Map<Material, Integer> tiers = new HashMap<>();

        // Tier 1: Common
        for (Material m : List.of(Material.DIRT, Material.COBBLESTONE, Material.SAND,
                Material.GRAVEL, Material.CLAY_BALL, Material.CLAY, Material.RED_SAND,
                Material.NETHERRACK, Material.MUD)) {
            tiers.put(m, 1);
        }

        // Tier 2: Wood
        for (Material m : List.of(Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG,
                Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
                Material.CHERRY_LOG, Material.MANGROVE_LOG, Material.PALE_OAK_LOG,
                Material.CRIMSON_STEM, Material.WARPED_STEM)) {
            tiers.put(m, 2);
        }

        // Tier 3: Crops
        for (Material m : List.of(Material.WHEAT, Material.SUGAR_CANE, Material.BAMBOO,
                Material.CACTUS, Material.KELP, Material.HAY_BLOCK, Material.MELON,
                Material.PUMPKIN)) {
            tiers.put(m, 3);
        }

        // Tier 4: Stone
        for (Material m : List.of(Material.STONE, Material.DEEPSLATE, Material.GRANITE,
                Material.DIORITE, Material.ANDESITE, Material.TUFF, Material.BASALT,
                Material.BLACKSTONE, Material.COPPER_ORE, Material.RAW_COPPER_BLOCK,
                Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE)) {
            tiers.put(m, 4);
        }

        // Tier 5: Precious
        for (Material m : List.of(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
                Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
                Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
                Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE)) {
            tiers.put(m, 5);
        }

        // Tier 6: Rare
        for (Material m : List.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
                Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
                Material.ANCIENT_DEBRIS)) {
            tiers.put(m, 6);
        }

        return Collections.unmodifiableMap(tiers);
    }

    // ---- Inner classes ----

    /**
     * A single resource entry from a chunk scan.
     */
    public record ResourceEntry(Material material, int count, int tier) {}

    private record CachedScan(List<ResourceEntry> resources, long timestamp) {
        CachedScan(List<ResourceEntry> resources) {
            this(resources, System.currentTimeMillis());
        }

        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
    }
}
