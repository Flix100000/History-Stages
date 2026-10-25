package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.disguise.Disguises;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Breaking a block whose disguise has {@code drops: disguise}: it takes as long, needs the same
 * tool and drops the same as the block it pretends to be. Otherwise the first swing of a pickaxe
 * tells the player the "stone" is something else.
 *
 * <p>Speed and tool run on both sides, with each side's own view of the player's stages, so the
 * crack animation and the server agree. Drops are the server's alone.
 *
 * <p>Forge 1.20.1 has no {@code BlockDropsEvent}: the drop list is swapped by
 * {@code BlockDropsMixin}, the experience here on {@link BlockEvent.BreakEvent}, which is where
 * Forge works out a player break's experience.</p>
 */
@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID)
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

    /**
     * The disguise's drops for a player's break, or null when the real ones stand. Popping them
     * stays with vanilla, which also honours the block-drops game rule.
     */
    @Nullable
    public static List<ItemStack> disguisedDrops(BlockState real, ServerLevel level, BlockPos pos,
                                                 @Nullable Entity breaker, ItemStack tool) {
        if (!(breaker instanceof Player player)) return null;
        Disguises.Resolved r = Disguises.breakingDisguise(player, real);
        if (r == null) return null;
        BlockState fake = Disguises.mapState(real, r.targetBlock());
        return Block.getDrops(fake, level, pos, null, player, tool);
    }

    /** Same sum Forge makes for the real block in the event's constructor, made for the disguise. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        BlockState real = event.getState();
        Disguises.Resolved r = Disguises.breakingDisguise(player, real);
        if (r == null) return;

        BlockState fake = Disguises.mapState(real, r.targetBlock());
        // Goes through HarvestCheck above, so this already asks about the disguise's tool.
        if (!ForgeHooks.isCorrectToolForDrops(fake, player)) {
            event.setExpToDrop(0);
            return;
        }
        ItemStack tool = player.getMainHandItem();
        event.setExpToDrop(fake.getExpDrop(event.getLevel(), player.getRandom(), event.getPos(),
                tool.getEnchantmentLevel(Enchantments.BLOCK_FORTUNE),
                tool.getEnchantmentLevel(Enchantments.SILK_TOUCH)));
    }
}
