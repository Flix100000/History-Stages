package net.bananemdnsa.historystages.platform.event.level;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.jetbrains.annotations.Nullable;

/**
 * A sapling, mushroom or fungus about to turn into its feature. Cancelling leaves it as it is.
 *
 * <p>Raised where NeoForge raises it: {@code TreeGrower.growTree}, {@code MushroomBlock.growMushroom}
 * and {@code FungusBlock.performBonemeal}.
 */
public class BlockGrowFeatureEvent extends Event implements ICancellableEvent {

    private final LevelAccessor level;
    private final RandomSource random;
    private final BlockPos pos;
    @Nullable
    private final Holder<ConfiguredFeature<?, ?>> feature;

    public BlockGrowFeatureEvent(LevelAccessor level, RandomSource random, BlockPos pos,
                                 @Nullable Holder<ConfiguredFeature<?, ?>> feature) {
        this.level = level;
        this.random = random;
        this.pos = pos;
        this.feature = feature;
    }

    public LevelAccessor getLevel() {
        return level;
    }

    public RandomSource getRandom() {
        return random;
    }

    public BlockPos getPos() {
        return pos;
    }

    @Nullable
    public Holder<ConfiguredFeature<?, ?>> getFeature() {
        return feature;
    }
}
