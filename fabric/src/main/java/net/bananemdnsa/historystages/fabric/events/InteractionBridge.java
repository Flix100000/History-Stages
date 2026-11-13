package net.bananemdnsa.historystages.fabric.events;

import net.bananemdnsa.historystages.platform.bus.EventPriority;
import net.bananemdnsa.historystages.platform.event.entity.player.AttackEntityEvent;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent;
import net.bananemdnsa.historystages.platform.event.level.BlockEvent;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;

/**
 * Player interaction, raised from Fabric's interaction callbacks with one listener per priority.
 *
 * <p>A cancelled platform event answers the callback with its cancellation result, or FAIL when
 * that is PASS: Fabric reads PASS as "carry on", so a cancel has to answer something else to stop
 * the interaction. These callbacks fire on both sides (except the block break, which is server
 * only), as the NeoForge events do.
 */
public final class InteractionBridge {

    private InteractionBridge() {}

    public static void register() {
        Phases.listen(UseBlockCallback.EVENT, priority -> (player, level, hand, hit) -> {
            if (!Phases.wanted(PlayerInteractEvent.RightClickBlock.class, priority)) {
                return InteractionResult.PASS;
            }
            RightClickBlockState state = RightClickBlockState.current();
            var event = new PlayerInteractEvent.RightClickBlock(player, hand, hit.getBlockPos(), hit.getDirection());
            if (state != null) {
                event.setUseBlock(state.useBlock);
                event.setUseItem(state.useItem);
            }
            if (Phases.postCancellable(event, priority)) {
                return refusal(event.getCancellationResult());
            }
            if (state != null) {
                state.useBlock = event.getUseBlock();
                state.useItem = event.getUseItem();
            }
            return InteractionResult.PASS;
        });

        // useBlock/useItem have no gate here: Fabric's attack path has no seam between the block
        // and the item, so only a cancel stops a left click (as on fabric-1.21.X).
        Phases.listen(AttackBlockCallback.EVENT, priority -> (player, level, hand, pos, direction) -> {
            if (!Phases.wanted(PlayerInteractEvent.LeftClickBlock.class, priority)) {
                return InteractionResult.PASS;
            }
            var event = new PlayerInteractEvent.LeftClickBlock(player, pos, direction);
            return Phases.postCancellable(event, priority) ? refusal(event.getCancellationResult()) : InteractionResult.PASS;
        });

        Phases.listen(UseItemCallback.EVENT, priority -> (player, level, hand) -> {
            if (!Phases.wanted(PlayerInteractEvent.RightClickItem.class, priority)) {
                return InteractionResult.PASS;
            }
            var event = new PlayerInteractEvent.RightClickItem(player, hand);
            return Phases.postCancellable(event, priority) ? refusal(event.getCancellationResult()) : InteractionResult.PASS;
        });

        Phases.listen(UseEntityCallback.EVENT, priority -> (player, level, hand, entity, hit) ->
                entityInteract(player, hand, entity, hit, priority));

        Phases.listen(AttackEntityCallback.EVENT, priority -> (player, level, hand, entity, hit) -> {
            if (!Phases.wanted(AttackEntityEvent.class, priority)) {
                return InteractionResult.PASS;
            }
            return Phases.postCancellable(new AttackEntityEvent(player, entity), priority)
                    ? InteractionResult.FAIL : InteractionResult.PASS;
        });

        // Fabric raises this from ServerPlayerGameMode only, so the platform event stays server
        // only, as decided for the NeoForge bridge. On a refusal Fabric resends the block itself.
        Phases.listen(PlayerBlockBreakEvents.BEFORE, priority -> (level, player, pos, state, blockEntity) -> {
            if (!Phases.wanted(BlockEvent.BreakEvent.class, priority)) {
                return true;
            }
            return !Phases.postCancellable(new BlockEvent.BreakEvent(level, pos, state, player), priority);
        });
    }

    /**
     * Fabric has one entity callback per interaction and it carries the hit position, so both
     * platform events come from it: the specific one first, as vanilla used to ask {@code
     * interactAt} before {@code interact}, and the plain one only when the specific one was not
     * cancelled. Same order and rule as the NeoForge bridge.
     */
    private static InteractionResult entityInteract(net.minecraft.world.entity.player.Player player,
                                                    net.minecraft.world.InteractionHand hand,
                                                    net.minecraft.world.entity.Entity entity,
                                                    net.minecraft.world.phys.EntityHitResult hit,
                                                    EventPriority priority) {
        if (Phases.wanted(PlayerInteractEvent.EntityInteractSpecific.class, priority)) {
            Vec3 local = hit == null ? Vec3.ZERO : hit.getLocation().subtract(entity.position());
            var specific = new PlayerInteractEvent.EntityInteractSpecific(player, hand, entity, local);
            if (Phases.postCancellable(specific, priority)) {
                return refusal(specific.getCancellationResult());
            }
        }
        if (Phases.wanted(PlayerInteractEvent.EntityInteract.class, priority)) {
            var plain = new PlayerInteractEvent.EntityInteract(player, hand, entity);
            if (Phases.postCancellable(plain, priority)) {
                return refusal(plain.getCancellationResult());
            }
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult refusal(InteractionResult cancellationResult) {
        return cancellationResult == InteractionResult.PASS ? InteractionResult.FAIL : cancellationResult;
    }
}
