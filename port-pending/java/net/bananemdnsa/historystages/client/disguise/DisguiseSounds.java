package net.bananemdnsa.historystages.client.disguise;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared body of the sound mixins: a disguised block sounds like its disguise, for the local
 * player only.
 *
 * <p>The client-side check matters in singleplayer, where the integrated server runs the same
 * entity code on its own thread: sounds the server sends to other players stay the real ones.
 */
public final class DisguiseSounds {

    private DisguiseSounds() {}

    public static SoundType soundType(BlockState state, LevelReader level, BlockPos pos, Entity entity,
                                      Operation<SoundType> original) {
        if (level instanceof Level l && l.isClientSide()) {
            state = ClientDisguises.view(state);
        }
        return original.call(state, level, pos, entity);
    }
}
