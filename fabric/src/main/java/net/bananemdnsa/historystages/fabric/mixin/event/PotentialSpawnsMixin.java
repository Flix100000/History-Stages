package net.bananemdnsa.historystages.fabric.mixin.event;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.level.LevelEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
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

/**
 * Raises the potential-spawns event with the candidate list open to change. A mob taken out here
 * was never in the running, so it does not use up spawn attempts the way a later refusal would.
 * Vanilla's weighted list is immutable: handlers get a copy and a new list is built if they
 * changed it.
 */
@Mixin(NaturalSpawner.class)
public abstract class PotentialSpawnsMixin {

    @Inject(method = "mobsAt", at = @At("RETURN"), cancellable = true)
    private static void historystages$raise(ServerLevel level, StructureManager structures, ChunkGenerator generator,
                                            MobCategory category, BlockPos pos, Holder<Biome> biome,
                                            CallbackInfoReturnable<WeightedList<MobSpawnSettings.SpawnerData>> cir) {
        if (!Phases.wanted(LevelEvent.PotentialSpawns.class)) {
            return;
        }
        List<Weighted<MobSpawnSettings.SpawnerData>> before = cir.getReturnValue().unwrap();
        List<Weighted<MobSpawnSettings.SpawnerData>> candidates = new ArrayList<>(before);
        EventBus.post(new LevelEvent.PotentialSpawns(level, category, pos, candidates));
        if (!candidates.equals(before)) {
            cir.setReturnValue(WeightedList.of(candidates));
        }
    }
}
