package net.bananemdnsa.historystages.platform.event.level;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.util.List;

public abstract class LevelEvent extends Event {

    private final LevelAccessor level;

    protected LevelEvent(LevelAccessor level) {
        this.level = level;
    }

    public LevelAccessor getLevel() {
        return level;
    }

    /** A level finished loading. */
    public static class Load extends LevelEvent {
        public Load(LevelAccessor level) {
            super(level);
        }
    }

    /**
     * The candidates natural spawning is about to choose from, while the list can still be
     * changed. Taking an entry out here is cheaper than refusing each mob afterwards, and it also
     * stops the spawn attempt counting against the cap.
     *
     * <p>Entries carry their weight in a {@link Weighted} wrapper since 1.21.5, the shape vanilla
     * and NeoForge 26.2 use; {@code SpawnerData} itself no longer has a weight.
     */
    public static class PotentialSpawns extends LevelEvent {

        private final List<Weighted<MobSpawnSettings.SpawnerData>> spawnerDataList;
        private final MobCategory category;
        private final BlockPos pos;

        public PotentialSpawns(LevelAccessor level, MobCategory category, BlockPos pos,
                               List<Weighted<MobSpawnSettings.SpawnerData>> spawnerDataList) {
            super(level);
            this.category = category;
            this.pos = pos;
            this.spawnerDataList = spawnerDataList;
        }

        /** Which group is being rolled for, so a rule for monsters does not answer about fish. */
        public MobCategory getMobCategory() {
            return category;
        }

        /** Where the roll is happening, which is what a zone or biome rule is asked about. */
        public BlockPos getPos() {
            return pos;
        }

        /** The live list, so handlers remove from it in place. */
        public List<Weighted<MobSpawnSettings.SpawnerData>> getSpawnerDataList() {
            return spawnerDataList;
        }

        public void addSpawnerData(Weighted<MobSpawnSettings.SpawnerData> data) {
            spawnerDataList.add(data);
        }
    }
}
