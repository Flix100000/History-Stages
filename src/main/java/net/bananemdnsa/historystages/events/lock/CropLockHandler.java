package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.bananemdnsa.historystages.util.lock.LockMessages;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.lock.engine.LockResolution;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.HsRuntimeCategories;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.level.SaplingGrowTreeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.level.BlockEvent.CropGrowEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps plants of a locked global stage from growing: random-tick growth, bonemeal, and a
 * sapling (or fungus, or mushroom) turning into its feature.
 *
 * <p>Global stages only. Growth happens in random ticks with nobody attached, so there is no
 * player whose individual stages could be asked.
 */
@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID)
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
        event.setResult(Event.Result.DENY);
        DebugLogger.runtimeThrottled(HsRuntimeCategories.CROP_LOCK, "grow_" + blockKey(event.getState()),
                "Kept " + blockKey(event.getState()) + " from growing at " + event.getPos().toShortString());
    }

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        if (event.getLevel().isClientSide()) return;
        List<String> missing = missingStagesFor(event.getBlock());
        if (missing.isEmpty()) return;

        // Cancelled and not marked successful: the item code then neither grows the plant nor
        // uses the bonemeal up. Dispensers land here too, with no player.
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) {
            LockFeedback.sendActionbar(player, "crop_lock",
                    LockMessages.cropLocked(player, event.getBlock(), missing));
        }
        // Keyed by plant type, not position: the throttle map is never cleared, and a farm has
        // thousands of positions.
        DebugLogger.runtimeThrottled(HsRuntimeCategories.CROP_LOCK, "bonemeal_" + blockKey(event.getBlock()),
                "Refused bonemeal on " + blockKey(event.getBlock()) + " at " + event.getPos().toShortString()
                        + " — missing stages: " + missing);
    }

    @SubscribeEvent
    public static void onGrowFeature(SaplingGrowTreeEvent event) {
        LevelAccessor level = event.getLevel();
        if (isClient(level)) return;
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (missingStagesFor(state).isEmpty()) return;
        // Forge 1.20.1 asks by result, not by cancelling: DENY keeps the sapling a sapling.
        event.setResult(Event.Result.DENY);
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
