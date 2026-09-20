package net.bananemdnsa.historystages.platform.event.level;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.core.BlockPos;
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
     */
    public static class PotentialSpawns extends LevelEvent {

        private final List<MobSpawnSettings.SpawnerData> spawnerDataList;
        private final MobCategory category;
        private final BlockPos pos;

        public PotentialSpawns(LevelAccessor level, MobCategory category, BlockPos pos,
                               List<MobSpawnSettings.SpawnerData> spawnerDataList) {
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
        public List<MobSpawnSettings.SpawnerData> getSpawnerDataList() {
            return spawnerDataList;
        }

        public void addSpawnerData(MobSpawnSettings.SpawnerData data) {
            spawnerDataList.add(data);
        }
    }
}
