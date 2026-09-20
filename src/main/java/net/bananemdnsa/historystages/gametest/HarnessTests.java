package net.bananemdnsa.historystages.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * Proves the GameTest harness works, before any test that could fail for a real reason.
 *
 * <p>The first GameTests in this project. When one of the others fails, this one passing is what
 * says the fault is in the mod rather than in the template path, the namespace property or the
 * annotations.
 *
 * <p>{@code @PrefixGameTestTemplate(false)} keeps the template name as written. Without it NeoForge
 * looks for {@code harnesstests.empty} — the class's simple name in front — and every test class
 * would need its own copy of the same empty structure.
 */
public class HarnessTests {

    public HarnessTests() {}

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void theHarnessRuns(GameTestHelper helper) {
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void aMockServerPlayerCanBeMade(GameTestHelper helper) {
        // The one capability every other test in this package rests on: a real ServerPlayer in a
        // real ServerLevel. It is what the dependency checker needs and what no unit test can
        // produce, because the unit test runtime has no Minecraft at all.
        //
        // Built by GameTestPlayers rather than by the GameTestHelper, for a reason worth reading
        // there before anyone switches it back.
        ServerPlayer player = GameTestPlayers.create(helper);
        if (player == null) {
            helper.fail("GameTestPlayers.create returned null");
            return;
        }
        if (player.level() == null) {
            helper.fail("the mock player has no level");
            return;
        }
        helper.succeed();
    }
}
