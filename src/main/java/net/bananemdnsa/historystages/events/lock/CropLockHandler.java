package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.lock.LockFeedbacks;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.lock.engine.LockResolution;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.HsRuntimeCategories;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps plants of a locked global stage from growing: random-tick growth, bonemeal, and a
 * sapling (or fungus, or mushroom) turning into its feature.
 *
 * <p>Global stages only. Growth happens in random ticks with nobody attached, so there is no
 * player whose individual stages could be asked.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public final class CropLockHandler {

    private CropLockHandler() {}

    /** The global stages keeping this plant from growing; empty when it may grow. */
    public static List<String> missingStagesFor(BlockState state) {
        // CropGrowEvent fires on every random tick of every plant; most packs lock none.
        if (!StageLocks.engine().anyCropLocks()) return List.of();

        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        List<String> tags = new ArrayList<>();
        state.getTags().forEach(tag -> tags.add(tag.location().toString()));

        List<String> gating = StageLocks.engine().gatingStagesForCrop(blockId, tags);
        if (gating.isEmpty()) return List.of();
        return LockResolution.missingStages(StageScope.GLOBAL, gating, StageLocks.serverGlobal());
    }

    @SubscribeEvent
    public static void onCropGrow(CropGrowEvent.Pre event) {
        if (isClient(event.getLevel())) return;
        if (missingStagesFor(event.getState()).isEmpty()) return;
        event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        DebugLogger.runtimeThrottled(HsRuntimeCategories.CROP_LOCK, "grow_" + blockKey(event.getState()),
                "Kept " + blockKey(event.getState()) + " from growing at " + event.getPos().toShortString());
    }

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        if (event.getLevel().isClientSide()) return;
        List<String> missing = missingStagesFor(event.getState());
        if (missing.isEmpty()) return;

        // Cancelled and not marked successful: the item code then neither grows the plant nor
        // uses the bonemeal up. Dispensers land here too, with no player.
        event.setCanceled(true);
        if (event.getPlayer() instanceof ServerPlayer player) {
            LockFeedbacks.actionbar(player, "crop_lock",
                    Component.translatable("message.historystages.crop_locked"), missing);
        }
        // Keyed by plant type, not position: the throttle map is never cleared, and a farm has
        // thousands of positions.
        DebugLogger.runtimeThrottled(HsRuntimeCategories.CROP_LOCK, "bonemeal_" + blockKey(event.getState()),
                "Refused bonemeal on " + blockKey(event.getState()) + " at " + event.getPos().toShortString()
                        + " — missing stages: " + missing);
    }

    @SubscribeEvent
    public static void onGrowFeature(BlockGrowFeatureEvent event) {
        LevelAccessor level = event.getLevel();
        if (isClient(level)) return;
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (missingStagesFor(state).isEmpty()) return;
        event.setCanceled(true);
        DebugLogger.runtimeThrottled(HsRuntimeCategories.CROP_LOCK, "feature_" + blockKey(state),
                "Kept " + blockKey(state) + " from growing into its feature at " + pos.toShortString());
    }

    private static boolean isClient(LevelAccessor level) {
        return level.isClientSide();
    }

    private static String blockKey(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }
}
