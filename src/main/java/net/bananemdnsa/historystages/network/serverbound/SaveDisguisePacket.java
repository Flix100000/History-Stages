package net.bananemdnsa.historystages.network.serverbound;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.disguise.DisguiseData;
import net.bananemdnsa.historystages.data.disguise.DisguiseRule;
import net.bananemdnsa.historystages.data.disguise.DisguiseRuleSet;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sets or removes one rule in {@code disguises.json}.
 *
 * <p>{@code ruleJson} is the rule's object exactly as the file holds it, or empty to remove the
 * key — one format for file and wire, read by the same parser, so the server applies the same
 * checks a hand-edited file gets. {@code previousKey} lets the dialog rename a rule (pick another
 * locked item) without leaving the old one behind; empty when it is the same key.
 */
public record SaveDisguisePacket(String key, String previousKey, String ruleJson) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaveDisguisePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "save_disguise"));

    public static final StreamCodec<FriendlyByteBuf, SaveDisguisePacket> STREAM_CODEC =
            StreamCodec.of(SaveDisguisePacket::encode, SaveDisguisePacket::decode);

    private static final int MAX_KEY = 512;
    private static final int MAX_RULE = 16 * 1024;

    private static void encode(FriendlyByteBuf buffer, SaveDisguisePacket msg) {
        buffer.writeUtf(msg.key, MAX_KEY);
        buffer.writeUtf(msg.previousKey != null ? msg.previousKey : "", MAX_KEY);
        buffer.writeUtf(msg.ruleJson != null ? msg.ruleJson : "", MAX_RULE);
    }

    private static SaveDisguisePacket decode(FriendlyByteBuf buffer) {
        return new SaveDisguisePacket(buffer.readUtf(MAX_KEY), buffer.readUtf(MAX_KEY), buffer.readUtf(MAX_RULE));
    }

    public static void handle(SaveDisguisePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            DisguiseRuleSet set = DisguiseData.get();
            if (!msg.previousKey.isEmpty() && !msg.previousKey.equals(msg.key)) {
                set = set.without(msg.previousKey);
            }

            if (msg.ruleJson.isEmpty()) {
                set = set.without(msg.key);
            } else {
                DisguiseRule rule = parse(msg.key, msg.ruleJson);
                if (rule == null || set.wouldCycle(rule.key(), rule.as())) {
                    PacketHandler.sendEditorFeedback(EditorFeedbackPacket.error(
                            "editor.historystages.disguise.toast.rejected.title",
                            "editor.historystages.disguise.toast.rejected.message", msg.key), player);
                    return;
                }
                set = set.withRule(rule);
            }

            DisguiseData.set(set);
            DisguiseData.save();
            PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
            PacketHandler.sendEditorFeedback(EditorFeedbackPacket.success(
                    "editor.historystages.disguise.toast.saved.title",
                    "editor.historystages.disguise.toast.saved.message", msg.key), player);
        });
    }

    /** Null when the file parser would not accept this rule either. */
    private static DisguiseRule parse(String key, String ruleJson) {
        try {
            JsonObject wrapper = new JsonObject();
            wrapper.add(key, JsonParser.parseString(ruleJson));
            DisguiseRuleSet.Parsed parsed = DisguiseRuleSet.fromJson(wrapper.toString());
            return parsed.problems().isEmpty() ? parsed.set().get(key) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** The wire form of a rule: its object as {@code disguises.json} would hold it. */
    public static String ruleJson(DisguiseRule rule) {
        JsonObject root = JsonParser.parseString(
                DisguiseRuleSet.toJson(DisguiseRuleSet.empty().withRule(rule))).getAsJsonObject();
        return root.get(rule.key()).toString();
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
