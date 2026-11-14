package net.bananemdnsa.historystages.client.disguise;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Body of the sound mixin: a disguised block sounds like its disguise, for the local player only.
 *
 * <p>The thread check matters in singleplayer, where the integrated server runs the same code on
 * its own thread: sounds the server sends to other players stay the real ones.
 */
public final class DisguiseSounds {

    private DisguiseSounds() {}

    public static SoundType soundType(BlockState state, SoundType original) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !mc.isSameThread()) return original;
        BlockState shown = ClientDisguises.view(state);
        return shown == state ? original : shown.getSoundType();
    }
}
