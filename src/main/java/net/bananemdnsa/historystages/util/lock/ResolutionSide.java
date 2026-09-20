package net.bananemdnsa.historystages.util.lock;

import javax.annotation.Nullable;

import net.minecraft.world.level.Level;
import net.neoforged.fml.util.thread.EffectiveSide;

/**
 * Which side a recipe lookup is running on, for lookups that arrive without a level.
 *
 * <p>Vanilla always passes one, but the level is only there for the recipe to look at, and a recipe
 * that never does lets its mod pass {@code null} instead. TFC does that for alloys: the crucible
 * asks the recipe manager with no level at all, vanilla walks its recipes and never notices, and
 * the gate reading {@code isClientSide()} off it took the game down with a NullPointerException
 * before the player ever saw an alloy. Reported against the 1.20.1 build; the lookups are the same
 * ones here.
 *
 * <p>With no level to ask, the thread answers. A dedicated server has no client side to confuse it
 * with, and in single player the client and the integrated server keep threads of their own, so the
 * crucible ticking and a screen drawing still come out on different sides.
 */
public final class ResolutionSide {

    private ResolutionSide() {
    }

    /**
     * Whether this lookup belongs to the client, with {@code level} allowed to be absent.
     *
     * @param level the level the lookup came in with, or {@code null} if the caller had none
     */
    public static boolean isClient(@Nullable Level level) {
        return level != null ? level.isClientSide() : EffectiveSide.get().isClient();
    }
}
