package net.bananemdnsa.historystages.client.slotlock;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotTargetGroupsTest {

    private static final Map<String, String> NAMES = Map.of(
            "minecraft", "Minecraft", "create", "Create", "sophisticatedstorage", "Sophisticated Storage",
            "ae2", "Applied Energistics 2");

    private static String name(String namespace) {
        return NAMES.getOrDefault(namespace, namespace);
    }

    @Test
    void minecraftFirstThenModsByDisplayName() {
        List<String> grouped = SlotTargetGroups.group(List.of(
                "sophisticatedstorage:chest", "create:depot", "minecraft:barrel", "ae2:inscriber", "minecraft:chest"),
                SlotTargetGroupsTest::name);

        assertEquals(List.of(
                SlotTargetGroups.header("minecraft"), "minecraft:barrel", "minecraft:chest",
                SlotTargetGroups.header("ae2"), "ae2:inscriber",
                SlotTargetGroups.header("create"), "create:depot",
                SlotTargetGroups.header("sophisticatedstorage"), "sophisticatedstorage:chest"), grouped);
    }

    @Test
    void headersAreRecognisedAndCarryTheirNamespace() {
        String header = SlotTargetGroups.header("create");
        assertTrue(SlotTargetGroups.isHeader(header));
        assertFalse(SlotTargetGroups.isHeader("create:depot"));
        assertEquals("create", SlotTargetGroups.namespaceOf(header));
    }

    /** The search filters entries first; a mod whose rows all fell away must not leave its caption behind. */
    @Test
    void emptyInputGivesNoHeaders() {
        assertEquals(List.of(), SlotTargetGroups.group(List.of(), SlotTargetGroupsTest::name));
    }
}
