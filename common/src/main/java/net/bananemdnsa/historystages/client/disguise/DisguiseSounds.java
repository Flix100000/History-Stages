package net.bananemdnsa.historystages.client.disguise;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared body of the loaders' sound mixins: a disguised block steps, falls and breaks with its
 * disguise's sounds, for the local player only. Placing keeps the real sound, as in 1.21.1.
 *
 * <p>The client-side check matters in singleplayer, where the integrated server runs the same
 * entity code on its own thread: sounds the server sends to other players stay the real ones.
 *
 * <p>The call sites differ per loader in 26.2 (vanilla asks {@code getSoundType()}, NeoForge its
 * three-argument extension and {@code playStepSound}/{@code playFallSound}), so each loader
 * module has its own mixins; this is what they share.
 */
public final class DisguiseSounds {

    private DisguiseSounds() {}

    /** The state whose sound plays: the disguise on the client, the real one elsewhere. */
    public static BlockState heard(BlockState state, LevelReader level) {
        return level instanceof Level l && l.isClientSide() ? ClientDisguises.view(state) : state;
    }
}
