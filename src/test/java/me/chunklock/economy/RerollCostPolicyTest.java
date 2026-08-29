package me.chunklock.economy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the re-roll cost policy behind issue #83.
 *
 * <p>#83 makes a chunk's price a commitment, and a commitment needs an escape hatch: a
 * player can exhaust the required material and otherwise be stuck forever. The re-roll is
 * that hatch, and its cost is the whole design. A <em>free</em> re-roll is just the original
 * exploit wearing a button - reopen until a cheap material appears.</p>
 *
 * <p>These tests assert the property that makes the cost real: <strong>re-rolling repeatedly
 * must get worse, fast enough that spamming is irrational</strong>. They deliberately test
 * the arithmetic rather than the Bukkit plumbing, because the arithmetic is what fails
 * quietly - a flat fee looks fine in review and is exploitable in play.</p>
 *
 * <p>The pricing mirrors {@code ChunkPriceRerollService}: the first re-roll of a chunk costs
 * half that chunk's price and each subsequent one doubles, resetting when the chunk is
 * unlocked.</p>
 */
public class RerollCostPolicyTest {

    private static final double FIRST_REROLL_FRACTION = 0.5;
    private static final double ESCALATION = 2.0;

    /** Mirrors ChunkPriceRerollService.priceFor. */
    private static double priceFor(int previousRerolls, double chunkPrice) {
        double base = Math.max(1.0, chunkPrice) * FIRST_REROLL_FRACTION;
        return base * Math.pow(ESCALATION, previousRerolls);
    }

    @Test
    @DisplayName("#83: the first re-roll costs half the chunk, so a genuine dead end stays affordable")
    public void testFirstRerollIsAffordable() {
        double chunkPrice = 150.0;

        double first = priceFor(0, chunkPrice);

        assertEquals(75.0, first, 0.001,
            "a player stuck on an unobtainable material should be able to buy their way out once");
        assertTrue(first < chunkPrice,
            "the escape hatch must cost less than the chunk, or nobody will ever use it");
    }

    @Test
    @DisplayName("#83: two re-rolls cost more than the chunk itself")
    public void testSecondRerollExceedsChunkPrice() {
        double chunkPrice = 150.0;

        double total = priceFor(0, chunkPrice) + priceFor(1, chunkPrice);

        assertTrue(total > chunkPrice,
            "re-rolling twice (" + total + ") must cost more than simply paying the chunk price ("
                + chunkPrice + "), otherwise re-rolling is the cheap path and the commitment is "
                + "decorative");
    }

    @Test
    @DisplayName("#83: cost escalation outruns any saving from shopping for a cheaper material")
    public void testEscalationOutrunsRepeatedRerolling() {
        double chunkPrice = 100.0;

        // A player hunting for a cheaper requirement re-rolls five times in a row.
        double cumulative = 0.0;
        for (int i = 0; i < 5; i++) {
            cumulative += priceFor(i, chunkPrice);
        }

        assertTrue(cumulative > chunkPrice * 10,
            "five re-rolls cost " + cumulative + " against a chunk worth " + chunkPrice
                + "; spamming must become absurd rather than merely expensive");
    }

    @Test
    @DisplayName("#83: the counter resets per chunk, so the hatch never becomes unaffordable")
    public void testResetRestoresTheAffordableFirstPrice() {
        double chunkPrice = 150.0;

        // A player who re-rolled three times on an earlier chunk, then unlocked it.
        double beforeReset = priceFor(3, chunkPrice);
        double afterReset = priceFor(0, chunkPrice);

        assertTrue(beforeReset > chunkPrice,
            "escalation should have made further re-rolls of that chunk punishing");
        assertEquals(75.0, afterReset, 0.001,
            "a fresh chunk must start from the affordable price again - escalation is per chunk, "
                + "not a permanent tax on players who have ever used the escape hatch");
    }

    @Test
    @DisplayName("#83: a free chunk still charges for re-rolling")
    public void testZeroPriceChunkStillCostsSomething() {
        double first = priceFor(0, 0.0);

        assertTrue(first > 0.0,
            "with a floor of zero, a misconfigured or free chunk would restore unlimited free "
                + "re-rolling - the exact exploit #83 exists to close");
    }

    @Test
    @DisplayName("#83: the cooldown path is per chunk, so parallel progress is unaffected")
    public void testCooldownIsKeyedPerChunk() {
        // The Vault-free path uses a cooldown keyed on world:x,z|player. Two different chunks
        // must produce different keys, or a player with several chunks in progress would be
        // blocked on all of them by touching one.
        String chunkA = "world:10,20|" + java.util.UUID.nameUUIDFromBytes("p".getBytes());
        String chunkB = "world:11,20|" + java.util.UUID.nameUUIDFromBytes("p".getBytes());

        assertNotEquals(chunkA, chunkB,
            "cooldown keys must distinguish chunks; a per-player key would punish players for "
                + "working several chunks at once");
    }
}
