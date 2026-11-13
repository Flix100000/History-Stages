package net.bananemdnsa.historystages.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Addons join on Fabric through the {@code historystages} entrypoint, as on the 1.21.1 Fabric line.
 * {@link TestAddonPlugin} is declared under that entrypoint in this mod's fabric.mod.json and stands in for
 * the demo addon: it has to be called during the register window, inside its mod's context, so the
 * debug report can name the owner.
 */
public class AddonRegistrationTests {

    @GameTest
    public void pluginReceivesRegisterEvent(GameTestHelper helper) {
        helper.assertTrue(TestAddonPlugin.received, "the historystages entrypoint never got RegisterLockCategoriesEvent");
        helper.assertTrue("historystages".equals(TestAddonPlugin.owner),
                "owner during the call was " + TestAddonPlugin.owner + ", expected the entrypoint's mod");
        helper.succeed();
    }
}
