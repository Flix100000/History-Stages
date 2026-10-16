package net.bananemdnsa.historystages.block;

import com.mojang.serialization.MapCodec;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.block.entity.ScrollShelfBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * A chiseled bookshelf for scrolls: six slots, filled and emptied by clicking the front, with the
 * same comparator and hopper behaviour as the vanilla block. Vanilla's block can't simply be reused
 * because it hard-codes both its block entity type and the book tag.
 */
public class ScrollShelfBlock extends BaseEntityBlock {
    public static final MapCodec<ScrollShelfBlock> CODEC = simpleCodec(ScrollShelfBlock::new);

    /** Items a scroll shelf accepts. A tag rather than a fixed list so packs can add their own. */
    public static final TagKey<Item> SCROLLS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "scroll_shelf_scrolls"));

    public static final int SLOTS = 6;
    public static final List<BooleanProperty> SLOT_OCCUPIED_PROPERTIES = ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES;

    /**
     * The shelf's real form: frame, back wall and middle board, with the slots open at the front.
     *
     * <p>Used for light: fully opaque, the shelf would sit at light 0 and its recessed slots would
     * render black; fully see-through, light would pass straight through a wall of shelves. This
     * way light only gets in where the slots open, like into a box.
     *
     * <p>Used for collision too: with a full cube there, the renderer treats every face as an
     * outer one and lights the slot ceilings from whatever is below the shelf. The slots are too
     * low to step into, and what else a full cube brought is set back in the block properties.
     */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
            Direction.NORTH, frame(Block.box(1, 1, 0, 15, 15, 15), Block.box(1, 7, 0, 15, 9, 15)),
            Direction.SOUTH, frame(Block.box(1, 1, 1, 15, 15, 16), Block.box(1, 7, 1, 15, 9, 16)),
            Direction.WEST, frame(Block.box(0, 1, 1, 15, 15, 15), Block.box(0, 7, 1, 15, 9, 15)),
            Direction.EAST, frame(Block.box(1, 1, 1, 16, 15, 15), Block.box(1, 7, 1, 16, 9, 15))));

    private static VoxelShape frame(VoxelShape slots, VoxelShape board) {
        return Shapes.or(Shapes.join(Shapes.block(), slots, BooleanOp.ONLY_FIRST), board);
    }

    public ScrollShelfBlock(Properties properties) {
        super(properties);
        BlockState state = this.stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        for (BooleanProperty slot : SLOT_OCCUPIED_PROPERTIES) {
            state = state.setValue(slot, false);
        }
        this.registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPES.get(state.getValue(HorizontalDirectionalBlock.FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(HorizontalDirectionalBlock.FACING));
    }

    // Torches, buttons and the like attach as to any full block, front included.
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ScrollShelfBlockEntity shelf)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!stack.is(SCROLLS)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        OptionalInt slot = getHitSlot(hit, state);
        if (slot.isEmpty()) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        // Falls through to useWithoutItem: clicking a full slot with a scroll in hand takes the
        // one already there out, as vanilla does with books.
        if (state.getValue(SLOT_OCCUPIED_PROPERTIES.get(slot.getAsInt()))) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            shelf.setItem(slot.getAsInt(), stack.consumeAndReturn(1, player));
            level.playSound(null, pos, SoundEvents.CHISELED_BOOKSHELF_INSERT, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ScrollShelfBlockEntity shelf)) return InteractionResult.PASS;
        OptionalInt slot = getHitSlot(hit, state);
        if (slot.isEmpty()) return InteractionResult.PASS;
        if (!state.getValue(SLOT_OCCUPIED_PROPERTIES.get(slot.getAsInt()))) return InteractionResult.CONSUME;
        if (!level.isClientSide) {
            ItemStack taken = shelf.removeItem(slot.getAsInt(), 1);
            level.playSound(null, pos, SoundEvents.CHISELED_BOOKSHELF_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!player.getInventory().add(taken)) {
                player.drop(taken, false);
            }
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * The slot under the hit point, numbered like vanilla: 0-2 top row, 3-5 bottom row, each row
     * left to right as seen from the front. Empty when the hit isn't on the front face.
     */
    public static OptionalInt getHitSlot(BlockHitResult hit, BlockState state) {
        Direction front = state.getValue(HorizontalDirectionalBlock.FACING);
        if (hit.getDirection() != front) return OptionalInt.empty();
        BlockPos outside = hit.getBlockPos().relative(front);
        Vec3 local = hit.getLocation().subtract(outside.getX(), outside.getY(), outside.getZ());
        double x = switch (front) {
            case NORTH -> 1.0 - local.x();
            case SOUTH -> local.x();
            case WEST -> local.z();
            case EAST -> 1.0 - local.z();
            default -> -1;
        };
        if (x < 0) return OptionalInt.empty();
        int row = local.y() >= 0.5 ? 0 : 1;
        int column = x < 0.375 ? 0 : x < 0.6875 ? 1 : 2;
        return OptionalInt.of(column + row * 3);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScrollShelfBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING);
        SLOT_OCCUPIED_PROPERTIES.forEach(builder::add);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.is(newState.getBlock())) return;
        boolean hadScrolls = false;
        if (level.getBlockEntity(pos) instanceof ScrollShelfBlockEntity shelf && !shelf.isEmpty()) {
            for (int i = 0; i < SLOTS; i++) {
                ItemStack stack = shelf.getItem(i);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
            }
            shelf.clearContent();
            hadScrolls = true;
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (hadScrolls) {
            level.updateNeighbourForOutputSignal(pos, this);
        }
    }

    // Runs after the item's stored scrolls were copied in, which happens when a filled shelf is
    // picked with ctrl+middle-click and placed again.
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ScrollShelfBlockEntity shelf) {
            shelf.syncSlotStates();
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide()) return 0;
        return level.getBlockEntity(pos) instanceof ScrollShelfBlockEntity shelf ? shelf.getLastInteractedSlot() + 1 : 0;
    }

    // Same fire behaviour as the chiseled bookshelf; nether wood shelves are built without
    // ignitedByLava and so never catch.
    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.ignitedByLava() ? 30 : 0;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.ignitedByLava() ? 20 : 0;
    }
}
