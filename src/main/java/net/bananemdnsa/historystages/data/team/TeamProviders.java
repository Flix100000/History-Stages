package net.bananemdnsa.historystages.data.team;

import net.bananemdnsa.historystages.api.team.TeamProvider;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The team mods HistoryStages can ask. Filled once through {@code RegisterTeamProvidersEvent},
 * then frozen, the same way every other addon registry here works.
 */
public final class TeamProviders {

    private static final Object LOCK = new Object();
    private static final Map<String, TeamProvider> BY_ID = new LinkedHashMap<>();
    private static boolean frozen = false;
    /** Ids whose provider already threw once; logged a single time, then skipped quietly. */
    private static final Set<String> FAILED = new HashSet<>();
    @Nullable
    private static TeamProvider testOverride;

    private TeamProviders() {}

    public static void register(String id, TeamProvider provider) {
        synchronized (LOCK) {
            if (frozen) {
                throw new IllegalStateException(
                        "Team provider registration is closed; '" + id + "' tried to register after the freeze.");
            }
            if (BY_ID.containsKey(id)) {
                throw new IllegalArgumentException("A team provider is already registered under id '" + id + "'.");
            }
            BY_ID.put(id, provider);
        }
    }

    public static void freeze() {
        synchronized (LOCK) {
            frozen = true;
        }
    }

    public static List<String> ids() {
        synchronized (LOCK) {
            return List.copyOf(BY_ID.keySet());
        }
    }

    /**
     * Everyone who shares a team with {@code player} according to any provider, never the player
     * itself. A provider that throws is left out rather than taking the unlock down with it.
     */
    public static Set<UUID> teammates(MinecraftServer server, UUID player) {
        Map<String, TeamProvider> providers;
        TeamProvider override;
        synchronized (LOCK) {
            providers = Map.copyOf(BY_ID);
            override = testOverride;
        }
        Set<UUID> mates = new HashSet<>();
        if (override != null) {
            mates.addAll(override.teammates(server, player));
        } else {
            for (Map.Entry<String, TeamProvider> e : providers.entrySet()) {
                try {
                    mates.addAll(e.getValue().teammates(server, player));
                } catch (RuntimeException ex) {
                    boolean first;
                    synchronized (LOCK) {
                        first = FAILED.add(e.getKey());
                    }
                    if (first) {
                        org.slf4j.LoggerFactory.getLogger("HistoryStages").error(
                                "[HistoryStages] Team provider '{}' failed; later failures are not logged again.",
                                e.getKey(), ex);
                    }
                }
            }
        }
        mates.remove(player);
        return mates;
    }

    /**
     * Replaces every registered provider while a GameTest runs; null restores them. Fake players
     * are in no FTB team, so this is the only way to put two of them in one.
     */
    public static void overrideForTesting(@Nullable TeamProvider provider) {
        synchronized (LOCK) {
            testOverride = provider;
        }
    }
}
