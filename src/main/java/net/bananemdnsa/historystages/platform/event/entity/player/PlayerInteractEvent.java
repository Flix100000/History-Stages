package net.bananemdnsa.historystages.platform.event.entity.player;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A player interacting with the world, in the five shapes this mod gates.
 *
 * <p>The three-way answer is the part that matters and the part Fabric has no equivalent for.
 * Cancelling stops the interaction outright, but {@link RightClickBlock#setUseBlock} alone means
 * "do not open the block, but still let the held item be used" — which is how a locked chest can
 * be closed to the player while placing a torch on it still works. Whoever fires these has to
 * reproduce all three states; collapsing them into a single cancel would over-block.
 */
public abstract class PlayerInteractEvent extends Event implements ICancellableEvent {

    private final Player player;
    private final InteractionHand hand;
    private final ItemStack stack;
    private final BlockPos pos;
    private final Direction face;
    private InteractionResult cancellationResult = InteractionResult.PASS;

    protected PlayerInteractEvent(Player player, InteractionHand hand, ItemStack stack,
                                  BlockPos pos, Direction face) {
        this.player = player;
        this.hand = hand;
        this.stack = stack;
        this.pos = pos;
        this.face = face;
    }

    public Player getEntity() {
        return player;
    }

    public InteractionHand getHand() {
        return hand;
    }

    public ItemStack getItemStack() {
        return stack;
    }

    public BlockPos getPos() {
        return pos;
    }

    public Direction getFace() {
        return face;
    }

    public Level getLevel() {
        return player.level();
    }

    public InteractionResult getCancellationResult() {
        return cancellationResult;
    }

    public void setCancellationResult(InteractionResult result) {
        this.cancellationResult = result;
    }

    /** Right-clicking a block: the block's own use and the held item's use answer separately. */
    public static class RightClickBlock extends PlayerInteractEvent {

        private TriState useBlock = TriState.DEFAULT;
        private TriState useItem = TriState.DEFAULT;

        public RightClickBlock(Player player, InteractionHand hand, BlockPos pos, Direction face) {
            super(player, hand, player.getItemInHand(hand), pos, face);
        }

        public TriState getUseBlock() {
            return useBlock;
        }

        public void setUseBlock(TriState value) {
            this.useBlock = value;
        }

        public TriState getUseItem() {
            return useItem;
        }

        public void setUseItem(TriState value) {
            this.useItem = value;
        }
    }

    /** Left-clicking a block, which is the start of breaking it. */
    public static class LeftClickBlock extends PlayerInteractEvent {

        private TriState useBlock = TriState.DEFAULT;
        private TriState useItem = TriState.DEFAULT;

        public LeftClickBlock(Player player, BlockPos pos, Direction face) {
            super(player, InteractionHand.MAIN_HAND, player.getMainHandItem(), pos, face);
        }

        public TriState getUseBlock() {
            return useBlock;
        }

        public void setUseBlock(TriState value) {
            this.useBlock = value;
        }

        public TriState getUseItem() {
            return useItem;
        }

        public void setUseItem(TriState value) {
            this.useItem = value;
        }
    }

    /** Right-clicking with an item in hand, pointed at nothing in particular. */
    public static class RightClickItem extends PlayerInteractEvent {
        public RightClickItem(Player player, InteractionHand hand) {
            super(player, hand, player.getItemInHand(hand), player.blockPosition(), null);
        }
    }

    /** Right-clicking an entity. */
    public static class EntityInteract extends PlayerInteractEvent {

        private final Entity target;

        public EntityInteract(Player player, InteractionHand hand, Entity target) {
            super(player, hand, player.getItemInHand(hand), target.blockPosition(), null);
            this.target = target;
        }

        public Entity getTarget() {
            return target;
        }
    }

    /**
     * Right-clicking an entity at a particular spot on it, which vanilla asks about first and
     * which some entities answer differently — an armour stand, for instance.
     */
    public static class EntityInteractSpecific extends PlayerInteractEvent {

        private final Entity target;
        private final Vec3 localPos;

        public EntityInteractSpecific(Player player, InteractionHand hand, Entity target, Vec3 localPos) {
            super(player, hand, player.getItemInHand(hand), target.blockPosition(), null);
            this.target = target;
            this.localPos = localPos;
        }

        public Entity getTarget() {
            return target;
        }

        public Vec3 getLocalPos() {
            return localPos;
        }
    }
}
