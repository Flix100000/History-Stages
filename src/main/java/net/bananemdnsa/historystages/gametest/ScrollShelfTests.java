package net.bananemdnsa.historystages.gametest;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.block.ScrollShelfBlock;
import net.bananemdnsa.historystages.block.ScrollShelfWood;
import net.bananemdnsa.historystages.block.entity.ScrollShelfBlockEntity;
import net.bananemdnsa.historystages.compat.ScrollVariants;
import net.bananemdnsa.historystages.init.ModBlocks;
import net.bananemdnsa.historystages.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Putting scrolls into a scroll shelf and taking them out, through the same click path a player
 * uses. The hit point decides the slot, so every test clicks a slot other than the first one a
 * broken mapping would fall back to.
 */
public class ScrollShelfTests {

    private static final BlockPos SHELF = new BlockPos(1, 2, 1);

    public ScrollShelfTests() {}

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void aScrollGoesIntoTheClickedSlot(GameTestHelper helper) {
        placeShelf(helper, Direction.NORTH);
        ServerPlayer player = GameTestPlayers.create(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, ScrollVariants.createScroll("bronze_age"));

        // North front: the viewer's left is +x, so 0.8 across and 0.75 up is the top-left slot.
        helper.useBlock(SHELF, player, frontHit(helper, Direction.NORTH, 0.8, 0.75));

        ScrollShelfBlockEntity shelf = shelf(helper);
        if (!shelf.getItem(0).is(ModItems.RESEARCH_SCROLL.get())) {
            helper.fail("the scroll did not land in the top-left slot");
            return;
        }
        if (!helper.getBlockState(SHELF).getValue(ScrollShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(0))) {
            helper.fail("slot 0 holds a scroll, but the block state still shows it empty");
            return;
        }
        if (!player.getMainHandItem().isEmpty()) {
            helper.fail("the scroll was placed but stayed in the player's hand as well");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void theSlotFollowsTheShelfsFacing(GameTestHelper helper) {
        placeShelf(helper, Direction.EAST);
        ServerPlayer player = GameTestPlayers.create(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CREATIVE_SCROLL.get()));

        // East front: the viewer's right is -z, so 0.2 along z and 0.25 up is the bottom-right slot.
        helper.useBlock(SHELF, player, frontHit(helper, Direction.EAST, 0.2, 0.25));

        ScrollShelfBlockEntity shelf = shelf(helper);
        for (int slot = 0; slot < ScrollShelfBlock.SLOTS; slot++) {
            boolean filled = !shelf.getItem(slot).isEmpty();
            if (filled != (slot == 5)) {
                helper.fail("a click on the bottom-right of an east-facing shelf filled slot " + slot + " instead of 5");
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void anOpenScrollIsTurnedAway(GameTestHelper helper) {
        placeShelf(helper, Direction.NORTH);
        ServerPlayer player = GameTestPlayers.create(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RESEARCH_SCROLL_OPEN.get()));

        helper.useBlock(SHELF, player, frontHit(helper, Direction.NORTH, 0.8, 0.75));

        if (!shelf(helper).isEmpty() || !player.getMainHandItem().is(ModItems.RESEARCH_SCROLL_OPEN.get())) {
            helper.fail("an open scroll went into the shelf");
            return;
        }
        if (shelf(helper).canPlaceItem(0, new ItemStack(ModItems.RESEARCH_SCROLL_OPEN.get()))) {
            helper.fail("a hopper may push an open scroll into the shelf");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void anEmptyHandTakesTheScrollBackUnchanged(GameTestHelper helper) {
        placeShelf(helper, Direction.NORTH);
        ItemStack scroll = ScrollVariants.createScroll("bronze_age");
        shelf(helper).setItem(4, scroll.copy());
        ServerPlayer player = GameTestPlayers.create(helper);

        // Bottom-middle: 0.5 across, 0.25 up.
        helper.useBlock(SHELF, player, frontHit(helper, Direction.NORTH, 0.5, 0.25));

        if (!ItemStack.isSameItemSameComponents(player.getInventory().getItem(0), scroll)) {
            helper.fail("the scroll taken out is not the scroll put in, stage tag and all");
            return;
        }
        if (!shelf(helper).isEmpty()
                || helper.getBlockState(SHELF).getValue(ScrollShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(4))) {
            helper.fail("the slot still counts as filled after its scroll was taken");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void breakingTheShelfDropsItsScrolls(GameTestHelper helper) {
        placeShelf(helper, Direction.NORTH);
        shelf(helper).setItem(2, new ItemStack(ModItems.CREATIVE_SCROLL.get()));

        helper.destroyBlock(SHELF);

        helper.assertItemEntityPresent(ModItems.CREATIVE_SCROLL.get(), SHELF, 2.0);
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void aShelfPlacedWithContentsShowsThem(GameTestHelper helper) {
        // What ctrl+middle-click in creative hands the player: the shelf item carrying its scrolls.
        ItemStack item = new ItemStack(ModItems.SCROLL_SHELF_ITEMS.get(ScrollShelfWood.OAK).get());
        NonNullList<ItemStack> contents = NonNullList.withSize(ScrollShelfBlock.SLOTS, ItemStack.EMPTY);
        contents.set(1, new ItemStack(ModItems.CREATIVE_SCROLL.get()));
        item.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));

        BlockPos floor = SHELF.below();
        helper.setBlock(floor, Blocks.STONE);
        ServerPlayer player = GameTestPlayers.create(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, item);
        BlockPos absFloor = helper.absolutePos(floor);
        item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absFloor).add(0, 0.5, 0), Direction.UP, absFloor, false)));

        if (shelf(helper).getItem(1).isEmpty()) {
            helper.fail("the placed shelf lost the scroll its item carried");
            return;
        }
        if (!helper.getBlockState(SHELF).getValue(ScrollShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(1))) {
            helper.fail("the placed shelf holds a scroll in slot 1 but shows the slot empty");
            return;
        }
        helper.succeed();
    }

    /**
     * Glowstone right behind the shelf. Through the back wall it would light the shelf's own cell
     * to 14; the long way round, through the open front, leaves it at about 10. Zero means the cell
     * is sealed and the recessed slots render black.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void lightReachesTheSlotsOnlyThroughTheFront(GameTestHelper helper) {
        placeShelf(helper, Direction.NORTH);
        helper.setBlock(SHELF.south(), Blocks.GLOWSTONE);

        helper.runAfterDelay(10, () -> {
            int light = helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(SHELF));
            if (light == 0) {
                helper.fail("no light gets into the shelf, so its slots would render black");
            } else if (light > 12) {
                helper.fail("light " + light + " in the shelf: it shines through the back wall");
            } else {
                helper.succeed();
            }
        });
    }

    /**
     * The renderer treats every face of a block with a full-cube collision shape as an outer face
     * and lights it from the neighbour it points at: the ceiling of a top slot took its light from
     * the dirt below the shelf and rendered black. The shelf must still act like a full block
     * everywhere else that used to follow from that cube.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void theSlotsAreLitFromInsideButTheShelfStaysSolid(GameTestHelper helper) {
        placeShelf(helper, Direction.EAST);
        BlockPos abs = helper.absolutePos(SHELF);
        BlockState state = helper.getLevel().getBlockState(abs);

        if (state.isCollisionShapeFullBlock(helper.getLevel(), abs)) {
            helper.fail("the collision shape is a full cube, so the slot ceilings render black over dark ground");
        } else if (!state.isRedstoneConductor(helper.getLevel(), abs)) {
            helper.fail("the shelf no longer passes on redstone power like a full block");
        } else if (!state.isSuffocating(helper.getLevel(), abs)) {
            helper.fail("a player inside the shelf no longer suffocates");
        } else if (!state.isFaceSturdy(helper.getLevel(), abs, Direction.EAST)
                || !state.isFaceSturdy(helper.getLevel(), abs, Direction.UP)) {
            helper.fail("the front or top no longer holds torches and the like");
        } else {
            helper.succeed();
        }
    }

    private static void placeShelf(GameTestHelper helper, Direction facing) {
        BlockState state = ModBlocks.SCROLL_SHELVES.get(ScrollShelfWood.OAK).get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing);
        helper.setBlock(SHELF, state);
    }

    private static ScrollShelfBlockEntity shelf(GameTestHelper helper) {
        return (ScrollShelfBlockEntity) helper.getBlockEntity(SHELF);
    }

    /**
     * A hit on the front face. {@code across} runs along the face's x or z axis in world terms,
     * {@code up} from the bottom of the block.
     */
    private static BlockHitResult frontHit(GameTestHelper helper, Direction front, double across, double up) {
        BlockPos abs = helper.absolutePos(SHELF);
        Vec3 point = switch (front) {
            case NORTH -> new Vec3(abs.getX() + across, abs.getY() + up, abs.getZ());
            case EAST -> new Vec3(abs.getX() + 1.0, abs.getY() + up, abs.getZ() + across);
            default -> throw new IllegalArgumentException("no test clicks a " + front + " front");
        };
        return new BlockHitResult(point, front, abs, false);
    }
}
