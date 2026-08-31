package me.chunklock.economy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the "nothing to re-roll into" guard (#83, step 3.11).
 *
 * <p>A player whose territory yields exactly one obtainable material has nothing to re-roll
 * into. Selection is deterministic - that <em>is</em> the #82 fix - so recalculating returns
 * the material it just returned, however much the player pays. Before this guard the button
 * was still offered and still charged, taking payment for an impossibility.</p>
 *
 * <p>These tests assert the quote's <strong>availability logic</strong> rather than the
 * Bukkit plumbing, matching {@link RerollCostPolicyTest}. The interesting property is that
 * {@code alternativesExist} vetoes availability <em>independently</em> of cost: a player who
 * can easily afford a re-roll must still be refused when it cannot change anything. A guard
 * that only applied when the player was also broke would look correct in review and fail in
 * exactly the case that matters.</p>
 */
public class RerollAlternativesTest {

    @Test
    @DisplayName("#83: a rich player with one candidate is still refused")
    public void testAffordableButNoAlternatives() {
        var quote = ChunkPriceRerollService.RerollQuote.currency(50.0, true, false);

        assertFalse(quote.available(),
            "affording a re-roll is irrelevant when there is no other material to roll into - "
                + "charging here takes payment for something that cannot happen");
        assertFalse(quote.alternativesExist());
    }

    @Test
    @DisplayName("#83: an expired cooldown with one candidate is still refused")
    public void testCooldownReadyButNoAlternatives() {
        var quote = ChunkPriceRerollService.RerollQuote.cooldown(0L, false);

        assertFalse(quote.available(),
            "a ready cooldown must not re-open a re-roll that cannot change the material");
    }

    @Test
    @DisplayName("#83: alternatives alone do not make an unaffordable re-roll available")
    public void testAlternativesDoNotOverrideCost() {
        var quote = ChunkPriceRerollService.RerollQuote.currency(50.0, false, true);

        assertFalse(quote.available(),
            "having somewhere to roll to does not waive the price");
        assertTrue(quote.alternativesExist(),
            "the GUI needs to distinguish 'cannot afford' from 'nothing to roll into' - "
                + "telling a player to earn money they cannot use is the wrong instruction");
    }

    @Test
    @DisplayName("#83: alternatives alone do not bypass an active cooldown")
    public void testAlternativesDoNotOverrideCooldown() {
        var quote = ChunkPriceRerollService.RerollQuote.cooldown(30 * 60_000L, true);

        assertFalse(quote.available(), "the cooldown still has to elapse");
        assertTrue(quote.alternativesExist());
    }

    @Test
    @DisplayName("#83: the normal case stays available")
    public void testAffordableWithAlternativesIsAvailable() {
        var currency = ChunkPriceRerollService.RerollQuote.currency(50.0, true, true);
        var cooldown = ChunkPriceRerollService.RerollQuote.cooldown(0L, true);

        assertTrue(currency.available(),
            "a player who can pay and has somewhere to roll to must still be offered the button");
        assertTrue(cooldown.available(),
            "an elapsed cooldown with alternatives must still offer the button");
    }
}
