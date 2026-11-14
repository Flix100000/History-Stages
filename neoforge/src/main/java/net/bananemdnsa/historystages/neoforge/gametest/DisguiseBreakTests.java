package net.bananemdnsa.historystages.neoforge.gametest;

import java.util.Map;
import java.util.function.Consumer;

import net.bananemdnsa.historystages.gametest.DisguiseBreakScenarios;
import net.minecraft.gametest.framework.GameTestHelper;

/** Disguised locked blocks break, harvest and drop as their disguise (via the NeoForge events). */
public final class DisguiseBreakTests {

    private DisguiseBreakTests() {}

    static void collect(Map<String, Consumer<GameTestHelper>> tests) {
        tests.put("disguise/ore_drops_the_disguise",
                helper -> DisguiseBreakScenarios.dropsTheDisguise(helper, TestPlayers::createConnected));
        tests.put("disguise/ore_harvests_like_the_disguise",
                helper -> DisguiseBreakScenarios.harvestFollowsTheDisguise(helper, TestPlayers::createConnected));
        tests.put("disguise/ore_breaks_at_the_disguises_speed",
                helper -> DisguiseBreakScenarios.breaksAtTheDisguisesSpeed(helper, TestPlayers::createConnected));
    }
}
