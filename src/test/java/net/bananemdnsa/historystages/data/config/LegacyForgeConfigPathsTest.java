package net.bananemdnsa.historystages.data.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The 5.x Forge client file did not match the NeoForge one: the structure border and overlay
 * settings sat in {@code [visuals]}. The coverage test cannot notice a missing old path, only a
 * missing new one, so an updating Forge pack would lose these four without anything going red.
 */
class LegacyForgeConfigPathsTest {

    @Test
    void theForgeStructureOverlayKeysReachTheirNewBlock() {
        for (String key : new String[] {"structureBorderEnabled", "structureBorderDistance",
                "structureLockOverlayEnabled", "structureLockOverlayOpacity"}) {
            assertEquals(
                    new LegacyConfigMap.Destination(LegacyConfigMap.Target.VISUAL, "structure_overlay." + key),
                    LegacyConfigMap.lookup(LegacyConfigMap.CLIENT, "visuals." + key),
                    "visuals." + key + " from a 5.x Forge client file has no destination");
        }
    }
}
