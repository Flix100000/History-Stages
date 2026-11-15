package net.bananemdnsa.historystages.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;

/**
 * Day time and moon phase as plain numbers, read from the world clocks and environment
 * attributes.
 */
public final class LevelTime {

    private LevelTime() {}

    /** The overworld clock, the same in every dimension. */
    public static long dayTime(Level level) {
        return level.getOverworldClockTime();
    }

    /** The moon phase (0..7) at the given position. */
    public static int moonPhase(Level level, BlockPos pos) {
        return level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos).index();
    }
}
