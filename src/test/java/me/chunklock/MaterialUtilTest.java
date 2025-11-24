package me.chunklock;

import org.junit.jupiter.api.Test;
import org.bukkit.Material;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MaterialUtil to ensure material name formatting works correctly.
 */
public class MaterialUtilTest {
    
    @Test
    public void testGetMaterialName() {
        // Test that getMaterialName returns the enum name
        assertEquals("OAK_LOG", me.chunklock.util.item.MaterialUtil.getMaterialName(Material.OAK_LOG));
        assertEquals("DIAMOND_ORE", me.chunklock.util.item.MaterialUtil.getMaterialName(Material.DIAMOND_ORE));
        assertEquals("AIR", me.chunklock.util.item.MaterialUtil.getMaterialName(null));
    }
    
    @Test
    public void testFormatMaterialName() {
        // Test that formatMaterialName converts enum names to display format
        assertEquals("Oak Log", me.chunklock.util.item.MaterialUtil.formatMaterialName(Material.OAK_LOG));
        assertEquals("Diamond Ore", me.chunklock.util.item.MaterialUtil.formatMaterialName(Material.DIAMOND_ORE));
        assertEquals("Iron Ingot", me.chunklock.util.item.MaterialUtil.formatMaterialName(Material.IRON_INGOT));
        assertEquals("Unknown Item", me.chunklock.util.item.MaterialUtil.formatMaterialName(null));
    }
    
    @Test
    public void testFormatMaterialNameWithMultipleWords() {
        // Test materials with multiple words
        assertEquals("Oak Wood", me.chunklock.util.item.MaterialUtil.formatMaterialName(Material.OAK_WOOD));
        // REDSTONE has no underscore, so it formats as a single word
        assertEquals("Redstone", me.chunklock.util.item.MaterialUtil.formatMaterialName(Material.REDSTONE));
    }
    
    @Test
    public void testFormatMaterialNameConsistency() {
        // Test that formatting is consistent (same input = same output)
        String result1 = me.chunklock.util.item.MaterialUtil.formatMaterialName(Material.DIAMOND);
        String result2 = me.chunklock.util.item.MaterialUtil.formatMaterialName(Material.DIAMOND);
        assertEquals(result1, result2, "Formatting should be deterministic");
    }
}

