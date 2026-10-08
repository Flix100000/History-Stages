package net.bananemdnsa.historystages.api.trigger;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The live objects behind a {@link StateView}, for addon state triggers that need more than the
 * view offers. Kept out of StateView itself so the view stays loadable without Minecraft.
 */
public final class StateViews {

    private StateViews() {}

    /** The player the view was taken for; null for a world-only view. */
    @Nullable
    public static ServerPlayer player(StateView view) {
        return (ServerPlayer) view.playerHandle;
    }

    /** The level the view was taken in. Null only for views built in tests. */
    @Nullable
    public static ServerLevel level(StateView view) {
        return (ServerLevel) view.levelHandle;
    }
}
