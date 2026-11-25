package net.bananemdnsa.historystages.platform;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.player.BonemealEvent;
import net.bananemdnsa.historystages.platform.event.level.CropGrowEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The questions NeoForge asks through {@code CommonHooks} before a plant grows. */
public final class PlantHooks {

    private PlantHooks() {}

    /** Whether a plant may take its random-tick growth. */
    public static boolean canCropGrow(LevelAccessor level, BlockPos pos, BlockState state) {
        return EventBus.post(new CropGrowEvent.Pre(level, pos, state)).getResult()
                != CropGrowEvent.Pre.Result.DO_NOT_GROW;
    }

    /** Whether bonemeal may be applied here. False means: no growth, and the bonemeal is kept. */
    public static boolean mayBonemeal(@Nullable Player player, Level level, BlockPos pos, ItemStack stack) {
        return !EventBus.post(new BonemealEvent(player, level, pos, level.getBlockState(pos), stack)).isCanceled();
    }
}
