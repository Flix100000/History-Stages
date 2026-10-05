package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.disguise.Disguises;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.List;

/**
 * Breaking a block whose disguise has {@code drops: disguise}: it takes as long, needs the same
 * tool and drops the same as the block it pretends to be. Otherwise the first swing of a pickaxe
 * tells the player the "stone" is something else.
 *
 * <p>Speed and tool run on both sides, with each side's own view of the player's stages, so the
 * crack animation and the server agree. Drops are the server's alone.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public final class DisguiseBreakHandler {

    private DisguiseBreakHandler() {}

    /**
     * Lowest priority: other mods' speed changes for the real block are replaced by the speed the
     * disguise would have, which already went through them once via {@code getDigSpeed}.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        BlockState real = event.getState();
        Disguises.Resolved r = Disguises.breakingDisguise(player, real);
        if (r == null) return;
        BlockPos pos = event.getPosition().orElse(null);
        if (pos == null) return;

        BlockState fake = Disguises.mapState(real, r.targetBlock());
        float realHardness = real.getDestroySpeed(player.level(), pos);
        float fakeHardness = fake.getDestroySpeed(player.level(), pos);
        if (fakeHardness < 0) {
            // Disguised as something unbreakable, so it is unbreakable.
            event.setCanceled(true);
            return;
        }
        float fakeSpeed = player.getDigSpeed(fake, pos);
        if (fakeHardness == 0 || realHardness <= 0) {
            event.setNewSpeed(Float.MAX_VALUE);
            return;
        }
        // Vanilla divides by the real hardness; scaling by real/fake leaves the disguise's progress.
        event.setNewSpeed(fakeSpeed * realHardness / fakeHardness);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        Player player = event.getEntity();
        BlockState real = event.getTargetBlock();
        Disguises.Resolved r = Disguises.breakingDisguise(player, real);
        if (r == null) return;
        BlockState fake = Disguises.mapState(real, r.targetBlock());
        event.setCanHarvest(!fake.requiresCorrectToolForDrops()
                || player.getMainHandItem().isCorrectToolForDrops(fake));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player)) return;
        BlockState real = event.getState();
        Disguises.Resolved r = Disguises.breakingDisguise(player, real);
        if (r == null) return;

        ServerLevel level = event.getLevel();
        BlockPos pos = event.getPos();
        ItemStack tool = event.getTool();
        BlockState fake = Disguises.mapState(real, r.targetBlock());

        List<ItemEntity> drops = event.getDrops();
        drops.clear();
        if (level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            for (ItemStack stack : Block.getDrops(fake, level, pos, null, player, tool)) {
                if (!stack.isEmpty()) drops.add(itemEntity(level, pos, stack));
            }
        }
        event.setDroppedExperience(fake.getExpDrop(level, pos, null, player, tool));
    }

    /** Placed the way {@code Block.popResource} places its drops. */
    private static ItemEntity itemEntity(ServerLevel level, BlockPos pos, ItemStack stack) {
        double half = EntityType.ITEM.getHeight() / 2.0;
        double x = pos.getX() + 0.5 + Mth.nextDouble(level.random, -0.25, 0.25);
        double y = pos.getY() + 0.5 + Mth.nextDouble(level.random, -0.25, 0.25) - half;
        double z = pos.getZ() + 0.5 + Mth.nextDouble(level.random, -0.25, 0.25);
        ItemEntity entity = new ItemEntity(level, x, y, z, stack);
        entity.setDefaultPickUpDelay();
        return entity;
    }
}
