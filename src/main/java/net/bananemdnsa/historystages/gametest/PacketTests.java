package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import io.netty.buffer.Unpooled;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.network.clientbound.EditorSyncPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStagesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncVisualConfigPacket;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Packets survive the wire.
 *
 * <p>Half of the sync path, and the half that fails quietly. What the client does with a packet
 * cannot be tested here — on a gametest server there is no client — but a codec that drops a field
 * hands the client a perfectly valid packet with the wrong contents, and nothing anywhere throws.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PacketTests {

    private PacketTests() {}

    @GameTest(template = "empty")
    public static void syncStagesSurvivesItsCodec(GameTestHelper helper) {
        SyncStagesPacket original = new SyncStagesPacket(
                List.of("gametest:one", "gametest:two", "gametest:three"));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        SyncStagesPacket.STREAM_CODEC.encode(buffer, original);
        SyncStagesPacket restored = SyncStagesPacket.STREAM_CODEC.decode(buffer);

        if (!original.unlockedStages().equals(restored.unlockedStages())) {
            // Both printed, because the realistic fault is one entry lost out of several, and a
            // bare "not equal" leaves somebody comparing two lists by hand.
            helper.fail("the packet did not survive its codec"
                    + "\n  sent:     " + original.unlockedStages()
                    + "\n  received: " + restored.unlockedStages());
            return;
        }
        if (buffer.readableBytes() != 0) {
            helper.fail("the decoder left " + buffer.readableBytes()
                    + " bytes unread, so it reads less than the encoder writes");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anEmptySyncStagesSurvivesItsCodec(GameTestHelper helper) {
        // The empty case on its own: a codec that forgets a length prefix passes the case above and
        // fails here, and "nothing unlocked yet" is what every fresh world starts as.
        SyncStagesPacket original = new SyncStagesPacket(List.of());

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        SyncStagesPacket.STREAM_CODEC.encode(buffer, original);
        SyncStagesPacket restored = SyncStagesPacket.STREAM_CODEC.decode(buffer);

        if (!restored.unlockedStages().isEmpty()) {
            helper.fail("an empty packet came back holding " + restored.unlockedStages());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void syncVisualConfigCarriesTheEditedValue(GameTestHelper helper) {
        // The visual settings were local-only until they got a packet, so the thing worth proving
        // is that one actually leaves the server: a changed value, under its dotted toml path, in
        // the payload. Pointed at the wrong spec the payload is still perfectly well-formed and
        // full of plausible values — it just never mentions this key.
        boolean original = Config.VISUAL.showLockIcons.get();
        try {
            Config.VISUAL.showLockIcons.set(!original);

            Map<String, String> sent = SyncVisualConfigPacket.fromServerConfig().values();
            if (!sent.containsKey("visuals.showLockIcons")) {
                helper.fail("the payload has no visuals.showLockIcons at all — it carries "
                        + sent.size() + " keys, and the first few are "
                        + sent.keySet().stream().sorted().limit(5).toList());
                return;
            }
            if (!sent.get("visuals.showLockIcons").equals(String.valueOf(!original))) {
                helper.fail("visuals.showLockIcons went out as '" + sent.get("visuals.showLockIcons")
                        + "' instead of '" + !original + "'");
                return;
            }
            helper.succeed();
        } finally {
            Config.VISUAL.showLockIcons.set(original);
        }
    }

    /** A stage set of {@code stages} stages holding {@code itemsEach} items apiece. */
    private static Map<String, StageEntry> stageSet(int stages, int itemsEach) {
        Map<String, StageEntry> map = new LinkedHashMap<>();
        for (int s = 0; s < stages; s++) {
            StageEntry stage = new StageEntry();
            stage.setDisplayName("Gametest Stage " + s);
            List<ItemEntry> items = new ArrayList<>();
            for (int i = 0; i < itemsEach; i++) {
                items.add(new ItemEntry("gametest:item_" + s + "_" + i));
            }
            stage.setItemEntries(items);
            map.put("gametest:stage_" + s, stage);
        }
        return map;
    }

    private static SyncStageDefinitionsPacket definitionsOf(Map<String, StageEntry> stages) {
        return new SyncStageDefinitionsPacket(stages, Map.of(),
                Map.of("gametest:stage_0", "early/ores"), Map.of(),
                Set.of("early", "early/ores"), Set.of(),
                "{\"global\":{}}", "{\"descriptions\":{}}", true, false);
    }

    @GameTest(template = "empty")
    public static void stageDefinitionsSurviveTheirCodec(GameTestHelper helper) {
        SyncStageDefinitionsPacket original = definitionsOf(stageSet(3, 4));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        SyncStageDefinitionsPacket.STREAM_CODEC.encode(buffer, original);
        SyncStageDefinitionsPacket restored = SyncStageDefinitionsPacket.STREAM_CODEC.decode(buffer);

        if (!original.stages().keySet().equals(restored.stages().keySet())) {
            helper.fail("the stage set did not survive its codec"
                    + "\n  sent:     " + original.stages().keySet()
                    + "\n  received: " + restored.stages().keySet());
            return;
        }
        // The fields after the stage maps are the ones a misread length prefix eats first, so they
        // are worth naming individually rather than trusting the keys above.
        if (!original.stagePaths().equals(restored.stagePaths())
                || !original.folders().equals(restored.folders())) {
            helper.fail("the folder tree did not survive: paths " + restored.stagePaths()
                    + ", folders " + restored.folders());
            return;
        }
        if (!original.graphLayout().equals(restored.graphLayout())
                || !original.graphStages().equals(restored.graphStages())
                || restored.graphGlobalFrozen() != original.graphGlobalFrozen()
                || restored.graphIndividualFrozen() != original.graphIndividualFrozen()) {
            helper.fail("the graph settings did not survive: layout " + restored.graphLayout()
                    + ", stages " + restored.graphStages()
                    + ", frozen " + restored.graphGlobalFrozen() + "/" + restored.graphIndividualFrozen());
            return;
        }
        if (buffer.readableBytes() != 0) {
            helper.fail("the decoder left " + buffer.readableBytes()
                    + " bytes unread, so it reads less than the encoder writes");
            return;
        }
        helper.succeed();
    }

    /**
     * The bug this whole compression round exists for: a pack whose stage JSON went past the old
     * quarter-million character cap could not log its players in at all.
     */
    @GameTest(template = "empty")
    public static void aStageSetPastTheOldStringCapStillEncodes(GameTestHelper helper) {
        Map<String, StageEntry> stages = stageSet(60, 250);

        int rawChars = new Gson().toJson(stages).length();
        if (rawChars <= 262144) {
            helper.fail("the fixture is only " + rawChars + " characters, which the old cap "
                    + "would have accepted - it proves nothing");
            return;
        }

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        SyncStageDefinitionsPacket.STREAM_CODEC.encode(buffer, definitionsOf(stages));
        SyncStageDefinitionsPacket restored = SyncStageDefinitionsPacket.STREAM_CODEC.decode(buffer);

        if (restored.stages().size() != stages.size()) {
            helper.fail("sent " + stages.size() + " stages, got back " + restored.stages().size());
            return;
        }
        StageEntry first = restored.stages().get("gametest:stage_0");
        if (first == null || first.getItemEntries().size() != 250) {
            helper.fail("the first stage came back with "
                    + (first == null ? "nothing" : first.getItemEntries().size() + " items"));
            return;
        }
        helper.succeed();
    }

    /** Same map, second door: opening the editor on that pack has to work too. */
    @GameTest(template = "empty")
    public static void aStageSetPastTheOldStringCapReachesTheEditor(GameTestHelper helper) {
        Map<String, StageEntry> stages = stageSet(60, 250);

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        EditorSyncPacket.STREAM_CODEC.encode(buffer, new EditorSyncPacket(stages));
        EditorSyncPacket restored = EditorSyncPacket.STREAM_CODEC.decode(buffer);

        if (restored.stages().size() != stages.size()) {
            helper.fail("sent " + stages.size() + " stages, got back " + restored.stages().size());
            return;
        }
        if (buffer.readableBytes() != 0) {
            helper.fail("the decoder left " + buffer.readableBytes() + " bytes unread");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void syncStagesKeepsItsOrder(GameTestHelper helper) {
        // Order matters to nothing in the mod today, and that is exactly why a codec could quietly
        // start reversing it. Cheaper to notice here than to wonder about a shuffled stage list.
        List<String> sent = List.of("gametest:alpha", "gametest:beta", "gametest:gamma");
        SyncStagesPacket original = new SyncStagesPacket(sent);

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        SyncStagesPacket.STREAM_CODEC.encode(buffer, original);
        SyncStagesPacket restored = SyncStagesPacket.STREAM_CODEC.decode(buffer);

        for (int i = 0; i < sent.size(); i++) {
            if (!sent.get(i).equals(restored.unlockedStages().get(i))) {
                helper.fail("entry " + i + " came back as " + restored.unlockedStages().get(i)
                        + " instead of " + sent.get(i));
                return;
            }
        }
        helper.succeed();
    }
}
