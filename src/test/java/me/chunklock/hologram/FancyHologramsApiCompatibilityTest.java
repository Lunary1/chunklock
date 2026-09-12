package me.chunklock.hologram;

import org.bukkit.Color;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the FancyHolograms API surface Chunklock reaches by reflection (issue #91).
 *
 * <p>The provider looks its methods up by name and caches a null on failure, so a wrong name or
 * signature does not throw - the feature just silently does nothing. Three lookups were in that
 * state, and had been since before the 26.1.2 upgrade rather than being broken by it:
 * {@code setShadow(boolean)} (the real name is {@code setTextShadow}),
 * {@code setBackground(int)} (it takes an {@code org.bukkit.Color}), and
 * {@code setRotation(float, float)}, which does not exist on this API at all - orientation is
 * carried on the hologram's own Location.
 *
 * <p>These tests resolve the names against the FancyHolograms jar actually on the compile
 * classpath, so the next rename fails here rather than in production. They assert the
 * <em>lookup</em>, which is the thing that broke - a test that called the new method directly
 * would pass against the old code too.
 */
public class FancyHologramsApiCompatibilityTest {

    private static final String TEXT_HOLOGRAM_DATA =
        "de.oliver.fancyholograms.api.data.TextHologramData";

    private static Class<?> textHologramData() {
        try {
            return Class.forName(TEXT_HOLOGRAM_DATA);
        } catch (ClassNotFoundException e) {
            return fail("FancyHolograms TextHologramData is not on the classpath: " + e.getMessage());
        }
    }

    /**
     * At least one of the two shadow spellings must resolve, and the provider tries the
     * current name first. If both disappear the toggle silently does nothing and nothing warns -
     * which now means text loses its shadow against a transparent background and turns
     * unreadable over a stained-glass border, rather than merely keeping one it did not want.
     */
    @Test
    void aShadowToggleResolvesUnderOneOfItsTwoNames() {
        Class<?> type = textHologramData();

        Method resolved = findMethod(type, "setTextShadow", boolean.class);
        if (resolved == null) {
            resolved = findMethod(type, "setShadow", boolean.class);
        }

        assertNotNull(resolved,
            "Neither setTextShadow(boolean) nor setShadow(boolean) exists on TextHologramData. "
                + "FancyHolograms renamed the shadow toggle again - update the fallback chain in "
                + "FancyHologramsProvider.cacheMethods().");
    }

    /**
     * Asserts the real spelling specifically, so the provider is exercising the working path
     * rather than quietly falling through to the legacy branch.
     */
    @Test
    void theShadowToggleIsSpelledSetTextShadow() {
        assertNotNull(findMethod(textHologramData(), "setTextShadow", boolean.class),
            "Expected setTextShadow(boolean) on TextHologramData.");
    }

    /**
     * Rotation has no direct replacement, so the provider falls back to re-applying a rotated
     * Location. That fallback is only reachable if both Location accessors resolve - if they
     * are ever renamed too, holograms lose their orientation with no error.
     */
    @Test
    void theLocationBasedRotationFallbackIsReachable() {
        Class<?> type = textHologramData();

        assertNotNull(findMethod(type, "getLocation"),
            "TextHologramData.getLocation() is missing - the rotation fallback in "
                + "FancyHologramsProvider.setRotation(...) cannot work without it.");
        assertNotNull(findMethod(type, "setLocation", Location.class),
            "TextHologramData.setLocation(Location) is missing - the rotation fallback in "
                + "FancyHologramsProvider.setRotation(...) cannot work without it.");
    }

    /**
     * Documents why the fallback exists. If a future FancyHolograms restores
     * setRotation(float, float), the direct path becomes live again and this test is the
     * prompt to re-check which branch the provider takes.
     */
    @Test
    void rotationIsAbsentOnTheCurrentDependencySoTheFallbackIsTheLivePath() {
        assertNull(findMethod(textHologramData(), "setRotation", float.class, float.class),
            "setRotation(float, float) is back on TextHologramData. The direct path in "
                + "FancyHologramsProvider.setRotation(...) is live again - confirm it is still "
                + "the behaviour you want before removing the Location fallback.");
    }

    /** The remaining names the provider caches, which all survived the 2.10 bump. */
    @Test
    void theStableTextHologramDataMethodsStillResolve() {
        Class<?> type = textHologramData();

        assertNotNull(findMethod(type, "setPersistent", boolean.class),
            "TextHologramData.setPersistent(boolean) is missing.");
        assertNotNull(findMethod(type, "setBackground", Color.class),
            "TextHologramData.setBackground(Color) is missing.");
    }

    /**
     * The background setter takes a Color and always has. The provider looked it up as
     * setBackground(int) for years, which silently resolved to nothing - holograms kept their
     * default background. Pins the parameter type so the int spelling cannot come back.
     */
    @Test
    void theBackgroundSetterTakesAColorNotAnInt() {
        Class<?> type = textHologramData();

        assertNotNull(findMethod(type, "setBackground", Color.class),
            "Expected setBackground(org.bukkit.Color) on TextHologramData.");
        assertNull(findMethod(type, "setBackground", int.class),
            "setBackground(int) resolved - the provider's original lookup would work after all. "
                + "Re-check which overload FancyHolograms expects.");
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
