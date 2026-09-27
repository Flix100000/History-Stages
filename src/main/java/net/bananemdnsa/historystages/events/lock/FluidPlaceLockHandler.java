package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.lock.engine.CategoryLockIndexes;
import net.bananemdnsa.historystages.data.lock.engine.FluidContent;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.bananemdnsa.historystages.util.lock.LockMessages;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.CauldronFluidContent;
import org.jetbrains.annotations.Nullable;

/**
 * Denies putting a gated fluid into the world.
 *
 * <p>The other half of {@link FluidPickupLockHandler}, and the half that was missing: the editor
 * has offered a <em>place</em> action for fluids all along, and nothing ever asked about it. A
 * gated fluid could be poured out of its bucket and, more quietly, tipped into a cauldron.
 *
 * <p><strong>Why the block interaction is refused as well.</strong> Pouring a fluid onto the
 * ground is the held item acting, so refusing the item is enough. A cauldron is the other way
 * round: the <em>block</em> takes the bucket and empties it, on a path the item's own refusal
 * never reaches. Refusing the block half outright would be too much — it would also stop a chest
 * from opening while a gated bucket happens to be in hand — so it is refused only where the block
 * would actually take the fluid.
 *
 * <p>Answers the same on both sides, like the pickup half: the event is raised on the client too,
 * and a server-only refusal would let the client show the cauldron filling before snapping back.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public class FluidPlaceLockHandler {

    private static final String FEEDBACK_CATEGORY = "fluid";

    /** Emptying into a block, which is where a cauldron and every modded tank live. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        String fluidId = lockedFluidIn(event.getItemStack(), event.getLevel(), event.getEntity());
        if (fluidId == null) return;

        // The item lock may have refused the bucket already, since a gated fluid gates its
        // container too. That only covers the item half; the cauldron still needs refusing here.
        boolean alreadyRefused = event.getUseItem() == TriState.FALSE;
        event.setUseItem(TriState.FALSE);
        if (takesFluid(event.getLevel(), event.getPos())) {
            event.setUseBlock(TriState.FALSE);
        }
        if (!alreadyRefused) {
            report(event.getEntity(), fluidId, "into a block");
        }
    }

    /** Pouring into the open, where the bucket's own raytrace does the work. */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        String fluidId = lockedFluidIn(event.getItemStack(), event.getLevel(), event.getEntity());
        if (fluidId == null) return;

        event.setCanceled(true);
        report(event.getEntity(), fluidId, "into the world");
    }

    /** The gated fluid this stack is carrying, or null when there is nothing to refuse. */
    @Nullable
    private static String lockedFluidIn(ItemStack held, Level level, Player player) {
        if (held.isEmpty()) return null;
        // FluidContent asks the item's own capability, which is another mod's code. Not on every
        // right click in a pack that gates no fluid at all (#130).
        if (!CategoryLockIndexes.anyStageUses("historystages:fluids")) return null;

        // An empty container carries nothing to place. Taking a fluid out of the world is the
        // pickup handler's question, not this one's.
        String fluidId = FluidContent.of(held);
        if (fluidId == null) return null;

        boolean locked = level.isClientSide()
                ? StageLockHelper.isFluidActionLockedForClient(fluidId, "place")
                        || StageLockHelper.isFluidActionLockedByIndividualStageClient(fluidId, "place")
                : StageLockHelper.isFluidActionLockedForServer(fluidId, "place")
                        || StageLockHelper.isFluidActionLockedByIndividualStage(
                                fluidId, player.getUUID(), "place");
        return locked ? fluidId : null;
    }

    /**
     * Whether this block would take the fluid rather than ignore it.
     *
     * <p>A cauldron is answered from NeoForge's mapping, which costs nothing and is the same on
     * both sides. Anything else is asked for its fluid capability — and only here, once the fluid
     * is known to be gated, because asking a block for its tank before there is a reason to has
     * taken worlds down before (#130).
     */
    private static boolean takesFluid(Level level, BlockPos pos) {
        if (CauldronFluidContent.getForBlock(level.getBlockState(pos).getBlock()) != null) {
            return true;
        }
        try {
            return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null) != null;
        } catch (RuntimeException e) {
            // Somebody else's block, somebody else's exception. Refusing the item already stopped
            // the pour; losing the block half here is the smaller error.
            return false;
        }
    }

    private static void report(Player player, String fluidId, String where) {
        if (!(player instanceof ServerPlayer sp)) return;
        DebugLogger.runtimeThrottled("Fluid Lock", "place_" + sp.getUUID() + "_" + fluidId,
                "<" + sp.getName().getString() + "> placing '" + fluidId + "' " + where
                        + " blocked [action: place]");
        LockFeedback.sendActionbar(sp, FEEDBACK_CATEGORY, LockMessages.fluidLocked());
    }
}
