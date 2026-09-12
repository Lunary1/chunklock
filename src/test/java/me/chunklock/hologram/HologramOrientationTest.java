package me.chunklock.hologram;

import me.chunklock.hologram.core.HologramData;
import me.chunklock.hologram.core.HologramId;
import me.chunklock.hologram.util.HologramLocationUtils;
import me.chunklock.hologram.util.HologramLocationUtils.WallSide;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins that a border hologram's facing survives the trip into the provider (issue #104).
 *
 * <p>Border holograms rendered sideways on three of the four chunk walls. The per-wall angle was
 * never wrong - {@code HologramLocationUtils} computes it correctly and stamps it onto the Location
 * it returns. It was dropped one step later: {@code HologramData.Builder} defaulted yaw and pitch
 * to zero and no caller ever set them, so the provider received zero for every wall. Because the
 * provider pins holograms to a FIXED billboard, that yaw is the only thing deciding which way the
 * text points, and every hologram ended up facing south.
 *
 * <p>These tests assert the <em>hand-off</em> rather than the arithmetic. A test that only checked
 * {@code getWallFacingYaw} passed for the whole time the bug was live, because that function was
 * always right - nothing carried its answer forward.
 */
class HologramOrientationTest {

    /** Built by parsing, which needs no live world. */
    private static HologramId idFor(WallSide side) {
        return HologramId.parse(
            "chunklock:00000000-0000-0000-0000-000000000001:chunklock_world:4:-7:" + side.name());
    }

    private static final HologramId ID = idFor(WallSide.NORTH);

    /** A Location needs no live server, so the builder contract is testable directly. */
    private static Location locationFacing(float yaw, float pitch) {
        Location location = new Location(null, 100.5, 70.0, -40.5);
        location.setYaw(yaw);
        location.setPitch(pitch);
        return location;
    }

    @Test
    void eachWallGetsItsOwnFacingAngle() {
        float north = HologramLocationUtils.getWallFacingYaw(WallSide.NORTH);
        float south = HologramLocationUtils.getWallFacingYaw(WallSide.SOUTH);
        float east = HologramLocationUtils.getWallFacingYaw(WallSide.EAST);
        float west = HologramLocationUtils.getWallFacingYaw(WallSide.WEST);

        assertEquals(180.0f, north);
        assertEquals(0.0f, south);
        assertEquals(270.0f, east);
        assertEquals(90.0f, west);

        assertEquals(4, java.util.Set.of(north, south, east, west).size(),
            "four walls must face four different ways, or some render edge-on");
    }

    /**
     * The regression itself. Build hologram data the way the service does - without touching
     * yaw - and the location's facing must still reach the finished object.
     */
    @Test
    void theBuilderCarriesTheLocationsFacingWhenNoCallerSetsIt() {
        for (WallSide side : WallSide.getOrderedSides()) {
            float expected = HologramLocationUtils.getWallFacingYaw(side);

            HologramData data = HologramData.builder(idFor(side), locationFacing(expected, 0.0f))
                .lines(List.of("locked"))
                .viewDistance(32.0)
                .persistent(false)
                .build();

            assertEquals(expected, data.getYaw(),
                "the " + side + " wall's facing was dropped between the location and the provider");
        }
    }

    /**
     * The negative control. Before the fix every wall produced zero, so a test that happened to
     * check only the south wall would have passed. This pins that the three non-zero walls are
     * genuinely non-zero.
     */
    @Test
    void threeOfTheFourWallsFaceAwayFromZero() {
        long nonZero = java.util.Arrays.stream(WallSide.getOrderedSides())
            .map(side -> HologramData.builder(idFor(side), locationFacing(
                    HologramLocationUtils.getWallFacingYaw(side), 0.0f))
                .lines(List.of("locked"))
                .build())
            .filter(data -> data.getYaw() != 0.0f)
            .count();

        assertEquals(3, nonZero,
            "only the south wall faces zero; if every wall reads zero the facing is being dropped");
    }

    @Test
    void anExplicitAngleStillOverridesTheLocation() {
        HologramData data = HologramData.builder(ID, locationFacing(90.0f, 0.0f))
            .lines(List.of("locked"))
            .yaw(270.0f)
            .pitch(15.0f)
            .build();

        assertEquals(270.0f, data.getYaw(), "an explicit yaw must still win");
        assertEquals(15.0f, data.getPitch(), "an explicit pitch must still win");
    }

    @Test
    void pitchAlsoSurvivesTheBuilder() {
        HologramData data = HologramData.builder(ID, locationFacing(180.0f, -12.5f))
            .lines(List.of("locked"))
            .build();

        assertEquals(-12.5f, data.getPitch());
    }
}
