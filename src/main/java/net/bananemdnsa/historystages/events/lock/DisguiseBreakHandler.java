package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.data.disguise.Disguises;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Breaking a block whose disguise has {@code drops: disguise}: it takes as long, needs the same
 * tool and drops the same as the block it pretends to be. Otherwise the first swing of a pickaxe
 * tells the player the "stone" is something else.
 *
 * <p>Speed and tool run on both sides, with each side's own view of the player's stages, so the
 * crack animation and the server agree. Drops are the server's alone.
 *
 * <p>NeoForge splits this over three events (break speed, harvest check, block drops). Vanilla
 * has none of them, so the mixins go to the two places that decide it: {@code DisguiseProgressMixin}
 * answers the block's destroy progress with the disguise's, which covers speed, tool and
 * unbreakable in one, and {@code DisguiseDropsMixin} has the server's break hand the disguise to
 * the tool check and to {@code playerDestroy}, so drops and experience come from the disguise.
 */
public final class DisguiseBreakHandler {

    private DisguiseBreakHandler() {}

    /** The state this player breaks the block as, or null when it breaks as itself. */
    @Nullable
    public static BlockState breaksAs(Player player, BlockState real) {
        Disguises.Resolved r = Disguises.breakingDisguise(player, real);
        return r == null ? null : Disguises.mapState(real, r.targetBlock());
    }
}
