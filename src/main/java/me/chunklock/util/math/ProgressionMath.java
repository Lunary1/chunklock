package me.chunklock.util.math;

/**
 * Utility class for mathematical calculations related to chunk unlocking and progression.
 * 
 * @author Chunklock Team
 * @version 1.3.0
 * @since 1.3.0
 */
public final class ProgressionMath {
    
    private ProgressionMath() {
        // Utility class
    }
    
    /**
     * Calculates the progress percentage based on unlocked chunks and total possible chunks.
     * 
     * @param unlockedChunks Number of chunks unlocked
     * @param totalChunks Total number of possible chunks
     * @return Progress percentage (0.0 to 100.0)
     */
    public static double calculateProgressPercentage(int unlockedChunks, int totalChunks) {
        if (totalChunks <= 0) {
            return 0.0;
        }
        return Math.min(100.0, (double) unlockedChunks / totalChunks * 100.0);
    }
    
    /**
     * Calculates the progressive cost multiplier based on the number of unlocked chunks.
     * 
     * @param unlockedChunks Number of chunks already unlocked
     * @param baseMultiplier Base multiplier for progression
     * @param maxMultiplier Maximum multiplier cap
     * @return The calculated multiplier
     */
    public static double calculateProgressiveMultiplier(int unlockedChunks, double baseMultiplier, double maxMultiplier) {
        if (baseMultiplier <= 1.0) {
            return 1.0;
        }
        
        double multiplier = Math.pow(baseMultiplier, unlockedChunks);
        return Math.min(multiplier, maxMultiplier);
    }
    
    /**
     * Calculates the distance between two chunk coordinates.
     * 
     * @param x1 First chunk X coordinate
     * @param z1 First chunk Z coordinate
     * @param x2 Second chunk X coordinate
     * @param z2 Second chunk Z coordinate
     * @return The distance between the chunks
     */
    public static double calculateChunkDistance(int x1, int z1, int x2, int z2) {
        int deltaX = x2 - x1;
        int deltaZ = z2 - z1;
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }
    
    /**
     * Calculates the Manhattan distance between two chunk coordinates.
     * 
     * @param x1 First chunk X coordinate
     * @param z1 First chunk Z coordinate
     * @param x2 Second chunk X coordinate
     * @param z2 Second chunk Z coordinate
     * @return The Manhattan distance between the chunks
     */
    public static int calculateManhattanDistance(int x1, int z1, int x2, int z2) {
        return Math.abs(x2 - x1) + Math.abs(z2 - z1);
    }
    
    /**
     * Calculates the cost with multiple multipliers applied.
     * 
     * @param baseCost The base cost
     * @param multipliers Array of multipliers to apply
     * @return The final calculated cost
     */
    public static double calculateCostWithMultipliers(double baseCost, double... multipliers) {
        double result = baseCost;
        for (double multiplier : multipliers) {
            result *= multiplier;
        }
        return result;
    }
    
    /**
     * Rounds a value to a specified number of decimal places.
     * 
     * @param value The value to round
     * @param decimalPlaces Number of decimal places
     * @return The rounded value
     */
    public static double roundToDecimalPlaces(double value, int decimalPlaces) {
        if (decimalPlaces < 0) {
            throw new IllegalArgumentException("Decimal places must be non-negative");
        }
        
        double multiplier = Math.pow(10, decimalPlaces);
        return Math.round(value * multiplier) / multiplier;
    }
    
    /**
     * Calculates a smooth interpolation between two values.
     * 
     * @param start Starting value
     * @param end Ending value
     * @param factor Interpolation factor (0.0 to 1.0)
     * @return The interpolated value
     */
    public static double lerp(double start, double end, double factor) {
        factor = Math.max(0.0, Math.min(1.0, factor)); // Clamp factor
        return start + (end - start) * factor;
    }
    
    /**
     * Calculates the area of a circular region in chunks.
     * 
     * @param radius The radius in chunks
     * @return The approximate number of chunks in the circular area
     */
    public static int calculateCircularChunkArea(double radius) {
        return (int) Math.ceil(Math.PI * radius * radius);
    }
    
    /**
     * Calculates the area of a square region in chunks.
     * 
     * @param sideLength The side length in chunks
     * @return The number of chunks in the square area
     */
    public static int calculateSquareChunkArea(int sideLength) {
        return sideLength * sideLength;
    }
}