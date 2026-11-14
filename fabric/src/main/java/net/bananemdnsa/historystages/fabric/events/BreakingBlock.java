package net.bananemdnsa.historystages.fabric.events;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

/**
 * The block a player is breaking right now, on this thread. Vanilla asks the player for break
 * speed and tool fitness with the state alone; NeoForge's events carry level and position, so
 * the two callers that know them (destroy progress, the server's break) open a scope here.
 */
public final class BreakingBlock {

    private record Context(BlockGetter level, BlockPos pos) {}

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private BreakingBlock() {}

    public static <T> T around(BlockGetter level, BlockPos pos, Supplier<T> body) {
        Context previous = CURRENT.get();
        CURRENT.set(new Context(level, pos));
        try {
            return body.get();
        } finally {
            CURRENT.set(previous);
        }
    }

    public static @Nullable BlockGetter level() {
        Context context = CURRENT.get();
        return context == null ? null : context.level();
    }

    public static @Nullable BlockPos pos() {
        Context context = CURRENT.get();
        return context == null ? null : context.pos();
    }
}
