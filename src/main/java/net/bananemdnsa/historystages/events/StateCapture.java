package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.api.trigger.StateView;
import net.bananemdnsa.historystages.events.lock.StructureLockHandler;
import net.bananemdnsa.historystages.structure.ClusterBuilder;
import net.bananemdnsa.historystages.structure.StructureCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Builds the per-poll {@link StateView}s. Every value is lazy, see StateView. */
public final class StateCapture {

    private StateCapture() {}

    public static StateView forPlayer(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        BlockPos pos = p.blockPosition();
        return StateView.builder()
                .dimension(() -> sl.dimension().location().toString())
                .biome(() -> {
                    ResourceLocation key = sl.registryAccess().registryOrThrow(Registries.BIOME)
                            .getKey(sl.getBiome(pos).value());
                    return key == null ? "" : key.toString();
                })
                .structures(() -> structuresAt(sl, pos))
                .effects(() -> effectsOf(p))
                .items(() -> itemsOf(p))
                .xpLevel(() -> p.experienceLevel)
                .weather(sl::isRaining, sl::isThundering)
                .dayTime(sl::getDayTime)
                .handles(p, sl)
                .build();
    }

    /** World states only, for global stages. Read from the overworld, like every global stage. */
    public static StateView forWorld(ServerLevel overworld) {
        return StateView.builder()
                .weather(overworld::isRaining, overworld::isThundering)
                .dayTime(overworld::getDayTime)
                .handles(null, overworld)
                .build();
    }

    // Same cluster/shape detection as the structure lock, so a state flips exactly where the lock
    // zone starts.
    static StateView.Structures structuresAt(ServerLevel sl, BlockPos pos) {
        int padding = Config.GAMEPLAY.structureLockPadding.get();
        int clusterDistance = Config.GAMEPLAY.structureClusterDistance.get();
        List<StructureCluster> clusters = ClusterBuilder.collectClustersNear(
                sl, pos, StructureLockHandler.CHUNK_SCAN_RADIUS, padding, clusterDistance);
        if (clusters.isEmpty()) return StateView.Structures.NONE;
        Set<String> ids = new HashSet<>();
        Set<String> tags = new HashSet<>();
        for (StructureCluster c : clusters) {
            if (!c.contains(pos)) continue;
            var h = c.structure();
            h.unwrapKey().ifPresent(k -> ids.add(k.location().toString()));
            h.tags().forEach(tag -> tags.add(tag.location().toString()));
        }
        return new StateView.Structures(ids, tags);
    }

    private static Set<String> effectsOf(ServerPlayer p) {
        Set<String> out = new HashSet<>();
        for (MobEffectInstance inst : p.getActiveEffects()) {
            ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(inst.getEffect().value());
            if (key != null) out.add(key.toString());
        }
        return out;
    }

    private static Set<String> itemsOf(ServerPlayer p) {
        Set<String> out = new HashSet<>();
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(s.getItem());
            if (key != null) out.add(key.toString());
        }
        return out;
    }
}
