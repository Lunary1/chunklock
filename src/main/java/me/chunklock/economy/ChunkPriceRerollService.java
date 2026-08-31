package me.chunklock.economy;

import me.chunklock.ChunklockPlugin;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Governs deliberate re-rolls of a committed chunk price (issue #83).
 *
 * <p>Once a player has seen a price for a chunk, that price is a commitment the plugin
 * honors. That creates one genuine dead end: a player can exhaust the required resource and
 * be unable to buy the chunk at all. The re-roll is the escape hatch - but a <em>free</em>
 * escape hatch is just the original exploit again, where a player reopens until the cheapest
 * material appears.</p>
 *
 * <p>So a re-roll always costs something, and what it costs depends on what the server has:</p>
 *
 * <table>
 *   <caption>Re-roll cost by server capability</caption>
 *   <tr><th>Server</th><th>Cost</th></tr>
 *   <tr><td>No Vault</td><td>A per-chunk cooldown ({@value #DEFAULT_COOLDOWN_MINUTES} min)</td></tr>
 *   <tr><td>Vault present</td><td>Currency, escalating per re-roll on the same chunk</td></tr>
 * </table>
 *
 * <h3>Why the Vault price escalates</h3>
 *
 * <p>A flat fee with no cooldown is the slot machine with a price tag: a wealthy player
 * re-rolls repeatedly and still pays less than the grind they avoided. Doubling each time
 * means the second re-roll on a chunk costs more than the chunk itself, so spamming stops
 * being rational while a single genuine escape stays affordable. The counter resets when the
 * chunk is unlocked, so the cost never carries across chunks.</p>
 *
 * <p>The cooldown is <strong>per chunk</strong>, not per player: someone with several chunks
 * in progress can work all of them, and rotating between chunks does not dodge the limit
 * because each chunk tracks its own timer.</p>
 *
 * <p>Owner's decision, August 29, 2026. See the brain note {@code Price-Commitment-Design}.</p>
 */
public class ChunkPriceRerollService {

    /** Cooldown applied on servers without Vault. */
    public static final int DEFAULT_COOLDOWN_MINUTES = 60;

    /** First re-roll on a chunk costs this fraction of the chunk's own vault price. */
    private static final double FIRST_REROLL_PRICE_FRACTION = 0.5;

    /** Each further re-roll on the same chunk multiplies the previous price by this. */
    private static final double ESCALATION_FACTOR = 2.0;

    private final ChunklockPlugin plugin;
    private final VaultEconomyService vaultService;

    /** chunk key -> epoch millis when a re-roll becomes allowed again. */
    private final Map<String, Long> cooldownUntil = new ConcurrentHashMap<>();

    /** chunk key -> how many times this chunk has been re-rolled since it was last unlocked. */
    private final Map<String, Integer> rerollCount = new ConcurrentHashMap<>();

    private long cooldownMillis = DEFAULT_COOLDOWN_MINUTES * 60L * 1000L;

    public ChunkPriceRerollService(ChunklockPlugin plugin, VaultEconomyService vaultService) {
        this.plugin = plugin;
        this.vaultService = vaultService;
    }

    /** Override the cooldown length, in minutes. Config-driven. */
    public void setCooldownMinutes(int minutes) {
        this.cooldownMillis = Math.max(0L, minutes * 60L * 1000L);
    }

    /**
     * Whether currency is the cost on this server. Checked live rather than cached because
     * Vault can load after this service is constructed.
     */
    public boolean usesCurrency() {
        return vaultService != null && vaultService.isVaultAvailable();
    }

    /**
     * What the next re-roll of this chunk would cost the player, for display in the GUI.
     *
     * @param alternativesExist whether the player has more than one material to be asked
     *                          for. False greys the button out: with a single candidate a
     *                          re-roll cannot change anything, so offering it would take
     *                          payment for an impossibility.
     */
    public RerollQuote quote(Player player, Chunk chunk, double chunkVaultPrice,
                             boolean alternativesExist) {
        String key = key(player, chunk);

        if (usesCurrency()) {
            double price = priceFor(key, chunkVaultPrice);
            boolean affordable = vaultService.hasEnoughMoney(player, price);
            return RerollQuote.currency(price, affordable, alternativesExist);
        }

        long remaining = cooldownRemainingMillis(key);
        return RerollQuote.cooldown(remaining, alternativesExist);
    }

    /**
     * Attempt to pay for a re-roll.
     *
     * <p>On success the caller should clear the stored requirement so the next calculation
     * commits a fresh one. This method only handles the cost; it does not touch stored
     * prices, because the caller owns that decision.</p>
     *
     * @return true if the player paid and the re-roll may proceed
     */
    public boolean tryConsumeRerollCost(Player player, Chunk chunk, double chunkVaultPrice) {
        String key = key(player, chunk);

        if (usesCurrency()) {
            double price = priceFor(key, chunkVaultPrice);
            if (!vaultService.withdrawMoney(player, price)) {
                return false;
            }
            rerollCount.merge(key, 1, Integer::sum);
            plugin.getLogger().fine("Re-roll purchased by " + player.getName()
                + " for " + price + " (chunk " + key + ")");
            return true;
        }

        if (cooldownRemainingMillis(key) > 0L) {
            return false;
        }
        cooldownUntil.put(key, System.currentTimeMillis() + cooldownMillis);
        rerollCount.merge(key, 1, Integer::sum);
        plugin.getLogger().fine("Re-roll granted to " + player.getName()
            + " on cooldown (chunk " + key + ")");
        return true;
    }

    /**
     * Forget the escalation and cooldown for a chunk the player has now unlocked.
     *
     * <p>Without this the price of re-rolling would keep climbing across a player's whole
     * history rather than resetting per chunk, which would make a late-game escape hatch
     * unaffordable for reasons unrelated to the chunk in front of them.</p>
     */
    public void clearForUnlockedChunk(Player player, Chunk chunk) {
        String key = key(player, chunk);
        cooldownUntil.remove(key);
        rerollCount.remove(key);
    }

    /** Drop all state for a player who has logged out. */
    public void clearPlayer(UUID playerId) {
        String suffix = "|" + playerId;
        cooldownUntil.keySet().removeIf(k -> k.endsWith(suffix));
        rerollCount.keySet().removeIf(k -> k.endsWith(suffix));
    }

    private double priceFor(String key, double chunkVaultPrice) {
        int previous = rerollCount.getOrDefault(key, 0);
        double base = Math.max(1.0, chunkVaultPrice) * FIRST_REROLL_PRICE_FRACTION;
        return base * Math.pow(ESCALATION_FACTOR, previous);
    }

    private long cooldownRemainingMillis(String key) {
        Long until = cooldownUntil.get(key);
        if (until == null) {
            return 0L;
        }
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0L) {
            cooldownUntil.remove(key);
            return 0L;
        }
        return remaining;
    }

    private String key(Player player, Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + "," + chunk.getZ()
            + "|" + player.getUniqueId();
    }

    /**
     * What a re-roll would cost right now, in whichever currency this server uses.
     */
    /**
     * @param available          whether the button should be clickable at all
     * @param alternativesExist  whether a re-roll could produce a different material. False
     *                           forces {@code available} false and is reported separately so
     *                           the GUI can explain <em>why</em> - "nothing else to ask for"
     *                           is a different message from "you cannot afford it".
     */
    public record RerollQuote(boolean currencyBased, double price, long cooldownRemainingMillis,
                              boolean available, boolean alternativesExist) {

        static RerollQuote currency(double price, boolean affordable, boolean alternativesExist) {
            return new RerollQuote(true, price, 0L, affordable && alternativesExist, alternativesExist);
        }

        static RerollQuote cooldown(long remainingMillis, boolean alternativesExist) {
            return new RerollQuote(false, 0.0, remainingMillis,
                remainingMillis <= 0L && alternativesExist, alternativesExist);
        }

        /** Minutes remaining, rounded up, for display. Zero when currency-based. */
        public long cooldownRemainingMinutes() {
            return (cooldownRemainingMillis + 59_999L) / 60_000L;
        }
    }
}
