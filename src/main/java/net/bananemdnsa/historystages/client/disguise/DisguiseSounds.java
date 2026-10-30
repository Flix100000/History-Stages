package net.bananemdnsa.historystages.client.disguise;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared body of the sound mixins: a disguised block sounds like its disguise, for the local
 * player only.
 *
 * <p>The client-side check matters in singleplayer, where the integrated server runs the same
 * entity code on its own thread: sounds the server sends to other players stay the real ones.
 *
 * <p>Vanilla asks the state alone for its sound. NeoForge's overload also passes the level, which
 * is where the side came from there; here each mixin says which side it is on.
 */
public final class DisguiseSounds {

    private DisguiseSounds() {}

    public static SoundType soundType(BlockState state, boolean clientSide, Operation<SoundType> original) {
        if (clientSide) {
            state = ClientDisguises.view(state);
        }
        return original.call(state);
    }
}
