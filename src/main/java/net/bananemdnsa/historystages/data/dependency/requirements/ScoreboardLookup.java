package net.bananemdnsa.historystages.data.dependency.requirements;

import net.bananemdnsa.historystages.data.dependency.ScoreboardDep;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

/**
 * Reads one scoreboard value. Split out of {@link ScoreboardRequirement} so that class can be
 * loaded without Minecraft.
 *
 * <p>On 1.21 this kept a ServerPlayer-to-ScoreHolder assignment out of the requirement class, where
 * the bytecode verifier would have loaded both types the moment a unit test touched it. 1.20.1
 * looks scores up by name, but the split keeps the requirement class as free of Minecraft as it
 * is on the other branch.
 */
final class ScoreboardLookup {

    private ScoreboardLookup() {}

    static int valueOf(Level level, ServerPlayer player, ScoreboardDep dep) {
        if (level == null || dep.getObjective() == null || dep.getObjective().isEmpty()) return 0;
        Scoreboard scoreboard = level.getScoreboard();
        Objective objective = scoreboard.getObjective(dep.getObjective());
        if (objective == null) return 0;
        String holderName = dep.isPlayerSelf()
                ? (player != null ? player.getScoreboardName() : null)
                : dep.getScoreHolder();
        if (holderName == null) return 0;
        if (!scoreboard.hasPlayerScore(holderName, objective)) return 0;
        return scoreboard.getOrCreatePlayerScore(holderName, objective).getScore();
    }
}
