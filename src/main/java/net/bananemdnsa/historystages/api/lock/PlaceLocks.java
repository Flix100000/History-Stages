package net.bananemdnsa.historystages.api.lock;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.bananemdnsa.historystages.events.lock.BiomeLockHandler;
import net.bananemdnsa.historystages.events.lock.StructureLockHandler;
import net.bananemdnsa.historystages.events.lock.ZoneLockHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side questions about places: may this player be at, or reach into, this position?
 *
 * <p>A place is gated four ways: its dimension, its biome, a structure covering it, and a zone
 * drawn around it. This asks all four with the same rules the mod's own handlers apply, for any
 * position — not only where the player happens to stand. That is what a teleport, a waypoint or
 * a portal mod needs before it moves someone.
 *
 * <p>Structure checks look at the structures near the position, so they cost more than the
 * others. Call it when a player is about to go somewhere, not every tick for every block.
 */
public final class PlaceLocks {

    private PlaceLocks() {}

    /** Is {@code pos} in {@code level} locked for this player, by any of the four? */
    public static boolean isLocked(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return !missingStages(player.getUUID(), level, pos).isEmpty();
    }

    /** {@link #isLocked(ServerPlayer, ServerLevel, BlockPos)} by UUID, for a player who may be offline. */
    public static boolean isLocked(UUID playerUuid, ServerLevel level, BlockPos pos) {
        return !missingStages(playerUuid, level, pos).isEmpty();
    }

    /**
     * The stages this player still needs before {@code pos} is open: dimension first, then biome,
     * structure and zone, each stage named once. Empty when the place is free.
     */
    public static List<String> missingStages(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return missingStages(player.getUUID(), level, pos);
    }

    /** {@link #missingStages(ServerPlayer, ServerLevel, BlockPos)} by UUID. */
    public static List<String> missingStages(UUID playerUuid, ServerLevel level, BlockPos pos) {
        Set<String> out = new LinkedHashSet<>(CategoryLocks.missingStagesForPlayer(
                "historystages:dimensions", level.dimension().location().toString(), playerUuid));
        out.addAll(BiomeLockHandler.lockingStagesAt(level, pos, playerUuid));
        out.addAll(StructureLockHandler.lockingStagesAt(level, pos, playerUuid));
        out.addAll(ZoneLockHandler.lockingStagesAt(level, pos, playerUuid));
        return List.copyOf(out);
    }

    /** Is the dimension itself locked for this player? Cheap; no position involved. */
    public static boolean isDimensionLocked(ServerPlayer player, ServerLevel level) {
        return CategoryLocks.isLockedForPlayer("historystages:dimensions",
                level.dimension().location().toString(), player.getUUID());
    }
}
