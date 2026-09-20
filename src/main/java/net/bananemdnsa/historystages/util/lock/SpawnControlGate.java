package net.bananemdnsa.historystages.util.lock;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.spawn.SpawnContext;
import net.bananemdnsa.historystages.data.lock.spawn.SpawnWeight;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.bananemdnsa.historystages.util.ServerHolder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minecraft side of SpawnControl: keeps the current {@link SpawnRuleSet}, reads a spawn position
 * into a {@link SpawnContext}, and holds the extra spawns ready-made per mob category.
 *
 * <p>Only global stages count. A spawn belongs to the world, not to a player.
 */
public final class SpawnControlGate {

    private static volatile SpawnRuleSet rules = SpawnRuleSet.EMPTY;
    private static volatile Map<MobCategory, List<ResolvedExtra>> extrasByCategory = Map.of();

    /**
     * Dimension ids as strings, kept per level key. {@code location().toString()} builds a new
     * string every call, and EntityJoinLevel asks per arrow, per item, per XP orb.
     */
    private static final Map<ResourceKey<Level>, String> DIMENSION_IDS = new ConcurrentHashMap<>();

    private record ResolvedExtra(EntityType<?> type, MobSpawnSettings.SpawnerData data, SpawnRuleSet.ActiveExtra rule) {}

    private SpawnControlGate() {}

    /** Call after anything that changes stage definitions or unlock state. */
    public static void rebuild() {
        Map<String, List<EntitySpawnLockEntry>> byStage = new HashMap<>();
        for (Map.Entry<String, StageEntry> e : StageManager.getStages().entrySet()) {
            byStage.put(e.getKey(), e.getValue().getEntities().getSpawnlock());
        }
        SpawnRuleSet built = SpawnRuleSet.build(byStage, Set.copyOf(StageData.SERVER_CACHE));
        extrasByCategory = resolve(built);
        rules = built;
    }

    public static boolean isActive() {
        return rules.isActive();
    }

    public static boolean isAllowed(String entityId, String source, ServerLevelAccessor level, BlockPos pos) {
        SpawnRuleSet snapshot = rules;
        if (!snapshot.hasRulesFor(entityId)) return true;
        return snapshot.isAllowed(entityId, source, context(level, pos));
    }

    public static boolean forcesPlacement(String entityId, ServerLevelAccessor level, BlockPos pos) {
        SpawnRuleSet snapshot = rules;
        if (!snapshot.hasPlacementOverrides(entityId)) return false;
        return snapshot.forcesPlacement(entityId, context(level, pos));
    }

    /** Offers every active extra spawn that fits this position to vanilla's weighted pick. */
    public static void addExtras(LevelEvent.PotentialSpawns event) {
        List<ResolvedExtra> candidates = extrasByCategory.get(event.getMobCategory());
        if (candidates == null || candidates.isEmpty()) return;
        if (!(event.getLevel() instanceof ServerLevelAccessor level)) return;

        SpawnContext ctx = context(level, event.getPos());
        for (ResolvedExtra extra : candidates) {
            if (!extra.rule().spawns().matchesBiome(ctx.biomeId(), ctx.biomeTags())) continue;
            if (!extra.rule().conditions().matches(ctx, true)) continue;
            // The biome's own entry wins, and so does a heavier rule offered earlier: a second
            // entry for the same type would silently double its share.
            if (listed(event.getSpawnerDataList(), extra.type())) continue;
            event.addSpawnerData(extra.data());
        }
    }

    private static boolean listed(List<MobSpawnSettings.SpawnerData> list, EntityType<?> type) {
        for (MobSpawnSettings.SpawnerData data : list) {
            if (data.type == type) return true;
        }
        return false;
    }

    private static Map<MobCategory, List<ResolvedExtra>> resolve(SpawnRuleSet set) {
        if (set.extras().isEmpty()) return Map.of();
        MinecraftServer server = ServerHolder.get();
        Registry<Biome> biomes = server != null ? server.registryAccess().registryOrThrow(Registries.BIOME) : null;

        Map<MobCategory, List<ResolvedExtra>> out = new EnumMap<>(MobCategory.class);
        for (SpawnRuleSet.ActiveExtra extra : set.extras()) {
            ResourceLocation key = ResourceLocation.tryParse(extra.entityId());
            if (key == null) continue;
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(key);
            if (type.isEmpty()) continue;

            SpawnWeight weight = extra.spawns().weight() != null
                    ? extra.spawns().weight()
                    : defaultWeight(type.get(), biomes);
            MobSpawnSettings.SpawnerData data = new MobSpawnSettings.SpawnerData(
                    type.get(), weight.weight(), weight.minGroup(), weight.maxGroup());
            out.computeIfAbsent(type.get().getCategory(), k -> new ArrayList<>())
                    .add(new ResolvedExtra(type.get(), data, extra));
        }

        Map<MobCategory, List<ResolvedExtra>> frozen = new EnumMap<>(MobCategory.class);
        out.forEach((category, list) -> {
            list.sort(Comparator.comparingInt((ResolvedExtra r) -> r.data().getWeight().asInt()).reversed());
            frozen.put(category, List.copyOf(list));
        });
        return Collections.unmodifiableMap(frozen);
    }

    /**
     * The numbers the entity has in the first biome that lists it. Without a server there is no
     * registry to look in; the next rebuild, at the latest on world load, tries again.
     */
    private static SpawnWeight defaultWeight(EntityType<?> type, Registry<Biome> biomes) {
        if (biomes != null) {
            for (Biome biome : biomes) {
                for (MobSpawnSettings.SpawnerData data : biome.getMobSettings().getMobs(type.getCategory()).unwrap()) {
                    if (data.type == type) return new SpawnWeight(data.getWeight().asInt(), data.minCount, data.maxCount);
                }
            }
        }
        return SpawnWeight.FALLBACK;
    }

    private static SpawnContext context(ServerLevelAccessor accessor, BlockPos pos) {
        ServerLevel level = accessor.getLevel();
        Holder<Biome> biome = accessor.getBiome(pos);
        String biomeId = biome.unwrapKey().map(k -> k.location().toString()).orElse("");
        List<String> tags = biome.tags().map(t -> t.location().toString()).toList();
        return new SpawnContext(dimensionId(level), biomeId, tags, pos.getY(),
                accessor.getMaxLocalRawBrightness(pos), accessor.canSeeSky(pos),
                level.getDayTime(), level.isRaining(), level.isThundering(), level.getMoonPhase());
    }

    public static String dimensionId(Level level) {
        return DIMENSION_IDS.computeIfAbsent(level.dimension(), key -> key.location().toString());
    }
}
