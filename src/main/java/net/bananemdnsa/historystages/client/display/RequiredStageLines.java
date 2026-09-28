package net.bananemdnsa.historystages.client.display;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;
import net.bananemdnsa.historystages.data.lock.engine.InterchangeableStages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The bullet lines under a "Required Progress" header, shared by the item tooltip and Jade.
 *
 * <p>Interchangeable stages are why this exists: they go under a "One of:" line of their own,
 * and vanish once one of them is unlocked, because at that point none of them is still
 * required. Everything else is listed the way both callers always listed it.
 */
public final class RequiredStageLines {

    private RequiredStageLines() {}

    /**
     * @param gating  every stage gating the subject in this scope, locked or not, in engine order
     * @param showAll list unlocked stages too, with their status, while more than one gates it
     * @param grouped put interchangeable stages under a "One of:" line; off, they are listed in
     *                place like any other stage, and still dropped once the group is met
     * @param name    how a stage id is rendered; the tooltip wraps it for search hiding, Jade doesn't
     */
    public static List<Component> lines(StageScope scope, List<String> gating, StageStateView state,
                                        boolean showAll, boolean grouped, ChatFormatting bulletColor,
                                        Function<String, MutableComponent> name) {
        Set<String> flagged = InterchangeableStages.of(scope);
        boolean withStatus = showAll && gating.size() > 1;

        List<Component> out = new ArrayList<>();
        if (!grouped) {
            boolean groupMet = anyUnlocked(gating, flagged, state);
            for (String stage : gating) {
                if (groupMet && !withStatus && flagged.contains(stage)) continue;
                addBullet(out, " • ", stage, state, withStatus, bulletColor, name);
            }
            return out;
        }

        List<String> group = new ArrayList<>();
        for (String stage : gating) {
            if (flagged.contains(stage)) {
                group.add(stage);
                continue;
            }
            addBullet(out, " • ", stage, state, withStatus, bulletColor, name);
        }
        if (group.isEmpty()) return out;

        if (anyUnlocked(group, flagged, state) && !withStatus) return out;

        if (group.size() == 1) {
            addBullet(out, " • ", group.get(0), state, withStatus, bulletColor, name);
            return out;
        }
        out.add(Component.literal(" ")
                .append(Component.translatable("tooltip.historystages.any_of"))
                .withStyle(ChatFormatting.GRAY));
        for (String stage : group) {
            addBullet(out, "   • ", stage, state, withStatus, bulletColor, name);
        }
        return out;
    }

    private static boolean anyUnlocked(List<String> stages, Set<String> flagged, StageStateView state) {
        for (String stage : stages) {
            if (flagged.contains(stage) && state.isUnlocked(stage)) return true;
        }
        return false;
    }

    private static void addBullet(List<Component> out, String prefix, String stage, StageStateView state,
                                  boolean withStatus, ChatFormatting bulletColor,
                                  Function<String, MutableComponent> name) {
        boolean unlocked = state.isUnlocked(stage);
        if (withStatus) {
            ChatFormatting statusColor = unlocked ? ChatFormatting.GREEN : ChatFormatting.RED;
            String statusKey = unlocked
                    ? "tooltip.historystages.status.unlocked"
                    : "tooltip.historystages.status.locked";
            out.add(Component.literal(prefix)
                    .append(name.apply(stage).withStyle(bulletColor))
                    .append(Component.translatable(statusKey).withStyle(statusColor)));
        } else if (!unlocked) {
            out.add(Component.literal(prefix).append(name.apply(stage).withStyle(bulletColor)));
        }
    }
}
