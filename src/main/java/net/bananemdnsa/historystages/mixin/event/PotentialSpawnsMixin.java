package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.level.LevelEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Raises the potential-spawns event with the candidate list open to change.
 *
 * <p>Taking a mob out here is worth more than refusing it later: the spawner picks one candidate
 * per attempt, so a mob that is only refused afterwards still uses up the attempt and makes
 * everything else spawn less often. Removed here, it was simply never in the running.
 *
 * <p>Vanilla hands back an immutable weighted list, so the handlers get a mutable copy and a new
 * list is built from whatever survives.
 */
@Mixin(NaturalSpawner.class)
public abstract class PotentialSpawnsMixin {

    @Inject(method = "mobsAt", at = @At("RETURN"), cancellable = true)
    private static void historystages$raise(
            ServerLevel level, StructureManager structures, ChunkGenerator generator,
            MobCategory category, BlockPos pos, Holder<Biome> biome,
            CallbackInfoReturnable<WeightedRandomList<MobSpawnSettings.SpawnerData>> cir) {
        List<MobSpawnSettings.SpawnerData> candidates = new ArrayList<>(cir.getReturnValue().unwrap());
        LevelEvent.PotentialSpawns event = EventBus.post(new LevelEvent.PotentialSpawns(level, candidates));
        if (!candidates.equals(cir.getReturnValue().unwrap())) {
            cir.setReturnValue(WeightedRandomList.create(event.getSpawnerDataList()));
        }
    }
}
