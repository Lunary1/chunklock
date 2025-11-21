package me.chunklock.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.ChunklockPlugin;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * OpenAI ChatGPT integration for intelligent chunk cost calculation.
 * Uses GPT-4 to analyze player behavior and make sophisticated pricing decisions.
 * 
 * @author Chunklock Team
 * @version 2.0
 * @since 2.0
 */
public class OpenAIChunkCostAgent {
    
    private final ChunklockPlugin plugin;
    private final ChunkEvaluator chunkEvaluator;
    private final BiomeUnlockRegistry biomeRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // OpenAI Configuration
    private String apiKey;
    private String model = "gpt-4o-mini"; // Cost-effective model for game balancing
    private String apiUrl = "https://api.openai.com/v1/chat/completions";
    private boolean enabled = false;
    private int maxTokens = 300;
    private double temperature = 0.3; // Low temperature for consistent responses
    
    // Caching to reduce API calls
    private final Map<String, CachedResponse> responseCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 300000; // 5 minutes
    
    // Player context for better AI decisions
    private final Map<UUID, PlayerContext> playerContexts = new ConcurrentHashMap<>();
    
    public OpenAIChunkCostAgent(ChunklockPlugin plugin, ChunkEvaluator chunkEvaluator, 
                               BiomeUnlockRegistry biomeRegistry) {
        this.plugin = plugin;
        this.chunkEvaluator = chunkEvaluator;
        this.biomeRegistry = biomeRegistry;
        
        loadConfiguration();
        
        if (enabled && apiKey != null && !apiKey.isEmpty()) {
            plugin.getLogger().info("[OpenAI Agent] ChatGPT integration enabled with model: " + model);
        } else {
            plugin.getLogger().info("[OpenAI Agent] ChatGPT integration disabled - check configuration");
        }
    }
    
    /**
     * Calculate AI-optimized cost using ChatGPT analysis
     */
    public CompletableFuture<OpenAICostResult> calculateOptimizedCostAsync(Player player, Chunk chunk) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get base evaluation
                ChunkEvaluator.ChunkValueData baseEvaluation = chunkEvaluator.evaluateChunk(player.getUniqueId(), chunk);
                BiomeUnlockRegistry.UnlockRequirement baseRequirement = 
                    biomeRegistry.calculateRequirement(player, baseEvaluation.biome, baseEvaluation.score);
                
                if (!enabled || apiKey == null || apiKey.isEmpty()) {
                    return new OpenAICostResult(baseRequirement.material(), baseRequirement.amount(), 
                        baseEvaluation.score, "OpenAI disabled - using base calculation", 1.0, false);
                }
                
                // Check cache first
                String cacheKey = generateCacheKey(player, chunk, baseEvaluation);
                CachedResponse cached = responseCache.get(cacheKey);
                if (cached != null && !cached.isExpired()) {
                    return cached.result;
                }
                
                // Prepare context for ChatGPT
                PlayerContext context = getOrCreatePlayerContext(player);
                String prompt = buildPrompt(player, chunk, baseEvaluation, baseRequirement, context);
                
                // Query ChatGPT
                JsonNode response = queryChatGPT(prompt);
                OpenAICostResult result = parseResponse(response, baseRequirement, baseEvaluation);
                
                // Cache the result
                responseCache.put(cacheKey, new CachedResponse(result));
                
                // Update player context
                context.recordQuery(baseEvaluation.difficulty, result.getAiMultiplier());
                
                return result;
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "OpenAI cost calculation failed", e);
                
                // Fallback to base calculation
                ChunkEvaluator.ChunkValueData baseEvaluation = chunkEvaluator.evaluateChunk(player.getUniqueId(), chunk);
                BiomeUnlockRegistry.UnlockRequirement baseRequirement = 
                    biomeRegistry.calculateRequirement(player, baseEvaluation.biome, baseEvaluation.score);
                
                return new OpenAICostResult(baseRequirement.material(), baseRequirement.amount(), 
                    baseEvaluation.score, "OpenAI error: " + e.getMessage(), 1.0, false);
            }
        });
    }
    
    /**
     * Synchronous version for immediate results (uses cache when possible)
     */
    public OpenAICostResult calculateOptimizedCost(Player player, Chunk chunk) {
        try {
            return calculateOptimizedCostAsync(player, chunk).get();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Sync OpenAI call failed", e);
            
            // Fallback
            ChunkEvaluator.ChunkValueData baseEvaluation = chunkEvaluator.evaluateChunk(player.getUniqueId(), chunk);
            BiomeUnlockRegistry.UnlockRequirement baseRequirement = 
                biomeRegistry.calculateRequirement(player, baseEvaluation.biome, baseEvaluation.score);
            
            return new OpenAICostResult(baseRequirement.material(), baseRequirement.amount(), 
                baseEvaluation.score, "Sync call failed - using base calculation", 1.0, false);
        }
    }
    
    /**
     * Build the prompt for ChatGPT with game context
     */
    private String buildPrompt(Player player, Chunk chunk, ChunkEvaluator.ChunkValueData evaluation,
                              BiomeUnlockRegistry.UnlockRequirement baseRequirement, PlayerContext context) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert game balance AI for a Minecraft chunk progression plugin called Chunklock. ");
        prompt.append("Players unlock chunks by paying resources, and you need to determine fair pricing.\n\n");
        
        prompt.append("CONTEXT:\n");
        prompt.append("- Player: ").append(player.getName()).append("\n");
        prompt.append("- Chunk: ").append(chunk.getX()).append(",").append(chunk.getZ()).append("\n");
        prompt.append("- Biome: ").append(evaluation.biome.toString()).append("\n");
        prompt.append("- Difficulty: ").append(evaluation.difficulty.toString()).append("\n");
        prompt.append("- Base Score: ").append(evaluation.score).append(" points\n");
        prompt.append("- Base Cost: ").append(baseRequirement.amount()).append("x ").append(baseRequirement.material().toString()).append("\n");
        
        prompt.append("\nPLAYER HISTORY:\n");
        prompt.append("- Total Attempts: ").append(context.totalAttempts).append("\n");
        prompt.append("- Recent Success Rate: ").append(String.format("%.1f%%", context.getRecentSuccessRate() * 100)).append("\n");
        prompt.append("- Avg Difficulty Faced: ").append(context.getAverageDifficulty()).append("\n");
        prompt.append("- Recent AI Adjustments: ").append(String.format("%.2fx", context.getAverageMultiplier())).append("\n");
        
        prompt.append("\nTASK:\n");
        prompt.append("Analyze if this cost should be adjusted for this specific player and situation. ");
        prompt.append("Consider player skill, recent performance, chunk difficulty, and game balance.\n");
        prompt.append("Respond with ONLY a JSON object in this exact format:\n");
        prompt.append("{\n");
        prompt.append("  \"multiplier\": 1.2,\n");
        prompt.append("  \"reasoning\": \"Player shows high skill, slight increase for challenge\"\n");
        prompt.append("}\n");
        prompt.append("Multiplier range: 0.3 to 3.0 (0.3 = 70% discount, 3.0 = 200% increase)");
        
        return prompt.toString();
    }
    
    /**
     * Query ChatGPT API
     */
    private JsonNode queryChatGPT(String prompt) throws IOException {
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setDoOutput(true);
        
        // Build request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);
        
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        requestBody.put("messages", messages);
        
        // Send request
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(jsonBody);
        }
        
        // Read response
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                String errorResponse = reader.lines().reduce("", (a, b) -> a + b);
                throw new IOException("OpenAI API error " + responseCode + ": " + errorResponse);
            }
        }
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String responseText = reader.lines().reduce("", (a, b) -> a + b);
            return objectMapper.readTree(responseText);
        }
    }
    
    /**
     * Parse ChatGPT response and create result
     */
    private OpenAICostResult parseResponse(JsonNode response, BiomeUnlockRegistry.UnlockRequirement baseRequirement,
                                          ChunkEvaluator.ChunkValueData evaluation) {
        try {
            String content = response.path("choices").path(0).path("message").path("content").asText();
            
            // Parse the JSON response from ChatGPT
            JsonNode aiResponse = objectMapper.readTree(content);
            double multiplier = aiResponse.path("multiplier").asDouble(1.0);
            String reasoning = aiResponse.path("reasoning").asText("No reasoning provided");
            
            // Apply bounds checking
            multiplier = Math.max(0.3, Math.min(3.0, multiplier));
            
            // Calculate final amount
            int finalAmount = (int) Math.ceil(baseRequirement.amount() * multiplier);
            finalAmount = Math.max(1, finalAmount);
            
            return new OpenAICostResult(baseRequirement.material(), finalAmount, evaluation.score,
                "💡 ChatGPT: " + reasoning, multiplier, true);
                
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to parse OpenAI response", e);
            
            return new OpenAICostResult(baseRequirement.material(), baseRequirement.amount(), 
                evaluation.score, "Failed to parse AI response", 1.0, false);
        }
    }
    
    /**
     * Record player feedback for learning
     */
    public void recordUnlockAttempt(Player player, Chunk chunk, boolean successful, 
                                   int actualCost, double timeTaken, boolean abandoned) {
        if (!enabled) return;
        
        try {
            PlayerContext context = getOrCreatePlayerContext(player);
            context.recordAttempt(successful, timeTaken, abandoned);
            
            // Log for potential future model fine-tuning
            plugin.getLogger().fine(String.format("[OpenAI Learning] Player %s: %s unlock of chunk %d,%d (cost: %d, time: %.1fs)",
                player.getName(), successful ? "successful" : "failed", 
                chunk.getX(), chunk.getZ(), actualCost, timeTaken));
                
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Error recording OpenAI learning data", e);
        }
    }
    
    // Helper methods and data structures
    
    private void loadConfiguration() {
        // Use modular config system
        me.chunklock.config.modular.OpenAIConfig openAIConfig = null;
        if (plugin instanceof me.chunklock.ChunklockPlugin) {
            openAIConfig = ((me.chunklock.ChunklockPlugin) plugin).getConfigManager().getOpenAIConfig();
        } else {
            openAIConfig = new me.chunklock.config.modular.OpenAIConfig(plugin);
        }
        
        if (openAIConfig != null) {
            enabled = openAIConfig.isEnabled();
            apiKey = openAIConfig.getApiKey();
            model = openAIConfig.getModel();
            maxTokens = openAIConfig.getMaxTokens();
            temperature = openAIConfig.getTemperature();
        } else {
            enabled = false;
            apiKey = "";
            model = "gpt-4o-mini";
            maxTokens = 300;
            temperature = 0.3;
        }
        
        if (enabled && (apiKey == null || apiKey.isEmpty())) {
            plugin.getLogger().warning("[OpenAI Agent] Enabled but no API key provided - disabling");
            enabled = false;
        }
    }
    
    private String generateCacheKey(Player player, Chunk chunk, ChunkEvaluator.ChunkValueData evaluation) {
        PlayerContext context = getOrCreatePlayerContext(player);
        return String.format("%s_%d_%d_%s_%d_%.2f", 
            player.getUniqueId().toString(), chunk.getX(), chunk.getZ(), 
            evaluation.difficulty, context.totalAttempts / 5, context.getRecentSuccessRate()); // Cache changes every 5 attempts or success rate change
    }
    
    private PlayerContext getOrCreatePlayerContext(Player player) {
        return playerContexts.computeIfAbsent(player.getUniqueId(), k -> new PlayerContext());
    }
    
    // Data structures
    
    public static class OpenAICostResult {
        private final Material material;
        private final int amount;
        private final int baseScore;
        private final String explanation;
        private final double aiMultiplier;
        private final boolean aiProcessed;
        
        public OpenAICostResult(Material material, int amount, int baseScore, String explanation, 
                               double aiMultiplier, boolean aiProcessed) {
            this.material = material;
            this.amount = amount;
            this.baseScore = baseScore;
            this.explanation = explanation;
            this.aiMultiplier = aiMultiplier;
            this.aiProcessed = aiProcessed;
        }
        
        // Getters
        public Material getMaterial() { return material; }
        public int getAmount() { return amount; }
        public int getBaseScore() { return baseScore; }
        public String getExplanation() { return explanation; }
        public double getAiMultiplier() { return aiMultiplier; }
        public boolean isAiProcessed() { return aiProcessed; }
    }
    
    private static class CachedResponse {
        private final OpenAICostResult result;
        private final long timestamp;
        
        public CachedResponse(OpenAICostResult result) {
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION;
        }
    }
    
    private static class PlayerContext {
        private int totalAttempts = 0;
        private int successfulAttempts = 0;
        private final List<Boolean> recentResults = new ArrayList<>();
        private final List<String> recentDifficulties = new ArrayList<>();
        private final List<Double> recentMultipliers = new ArrayList<>();
        private static final int RECENT_LIMIT = 10;
        
        public void recordAttempt(boolean successful, double timeTaken, boolean abandoned) {
            totalAttempts++;
            if (successful) successfulAttempts++;
            
            // Track recent results
            recentResults.add(successful);
            if (recentResults.size() > RECENT_LIMIT) {
                recentResults.remove(0);
            }
        }
        
        public void recordQuery(me.chunklock.models.Difficulty difficulty, double multiplier) {
            recentDifficulties.add(difficulty.toString());
            recentMultipliers.add(multiplier);
            
            if (recentDifficulties.size() > RECENT_LIMIT) {
                recentDifficulties.remove(0);
                recentMultipliers.remove(0);
            }
        }
        
        public double getRecentSuccessRate() {
            if (recentResults.isEmpty()) return 0.5;
            return (double) recentResults.stream().mapToInt(b -> b ? 1 : 0).sum() / recentResults.size();
        }
        
        public String getAverageDifficulty() {
            if (recentDifficulties.isEmpty()) return "UNKNOWN";
            Map<String, Integer> counts = new HashMap<>();
            recentDifficulties.forEach(d -> counts.merge(d, 1, Integer::sum));
            return counts.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
        }
        
        public double getAverageMultiplier() {
            if (recentMultipliers.isEmpty()) return 1.0;
            return recentMultipliers.stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
        }
    }
}
