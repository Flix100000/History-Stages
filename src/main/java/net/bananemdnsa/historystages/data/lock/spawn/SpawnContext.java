package net.bananemdnsa.historystages.data.lock.spawn;

import java.util.List;

/** What the conditions look at for one spawn, already read out of the level. */
public record SpawnContext(String dimension, String biomeId, List<String> biomeTags, int y, int light,
                           boolean skyVisible, long dayTime, boolean raining, boolean thundering,
                           int moonPhase) {

    public SpawnContext {
        biomeTags = biomeTags == null ? List.of() : List.copyOf(biomeTags);
    }

    public boolean isDay() {
        return Math.floorMod(dayTime, 24000L) < 13000L;
    }
}
