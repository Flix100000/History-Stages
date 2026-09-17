package net.bananemdnsa.historystages.data.lock.spawn;

import java.util.List;

/**
 * Biomes an entity additionally spawns in.
 *
 * @param weight null means "as in the entity's own biomes" — resolved on the server, because biome
 *               spawn lists never reach the client
 */
public record ExtraBiomeSpawns(List<String> ids, SpawnWeight weight, boolean ignoreSpawnRules) {

    public ExtraBiomeSpawns {
        ids = ids == null ? List.of() : List.copyOf(ids);
    }

    public boolean matchesBiome(String biomeId, List<String> biomeTags) {
        return IdMatch.hits(ids, biomeId, biomeTags);
    }
}
