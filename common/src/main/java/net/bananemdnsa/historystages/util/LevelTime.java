package net.bananemdnsa.historystages.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;

/**
 * Day time and moon phase the way 1.21.1 asked for them. 26.2 moved both into world clocks and
 * environment attributes.
 */
public final class LevelTime {

    private LevelTime() {}

    /** 1.21.1's {@code getDayTime()}: the overworld clock, which every dimension shared. */
    public static long dayTime(Level level) {
        return level.getOverworldClockTime();
    }

    /** 1.21.1's {@code getMoonPhase()} (0..7), read at the given position. */
    public static int moonPhase(Level level, BlockPos pos) {
        return level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos).index();
    }
}
