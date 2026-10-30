package net.bananemdnsa.historystages.api.lock;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Tells a player that something is locked, the way the mod's own locks do: dark red italics on
 * the actionbar, at most once every two seconds per kind of lock.
 *
 * <p>Use it so an addon's lock feels like part of the mod rather than a second one. The cooldown
 * matters more than it looks: a lock checked per tick or per click would otherwise flood the
 * actionbar.
 *
 * <p>{@code bucket} names the kind of lock and keeps its own cooldown, so a recent item message
 * does not swallow a block message. Prefix it with your mod id, e.g. {@code "mymod:commands"}.
 */
public final class LockFeedbacks {

    private LockFeedbacks() {}

    /** Shows {@code message} on the actionbar unless this bucket spoke to this player in the last two seconds. */
    public static void actionbar(ServerPlayer player, String bucket, Component message) {
        LockFeedback.sendActionbar(player, bucket, message.copy());
    }

    /**
     * The same, with " (Required: …)" after the message naming the missing stages. The names are
     * the ones this player may see ({@code "???"} for hidden ones), and the whole suffix is left
     * out when a stage asks for its lock hints to be hidden or the list is empty.
     */
    public static void actionbar(ServerPlayer player, String bucket, Component message,
                                 List<String> missingStageIds) {
        MutableComponent full = message.copy();
        Component suffix = requiredSuffix(player, missingStageIds);
        if (suffix != null) full.append(suffix);
        LockFeedback.sendActionbar(player, bucket, full);
    }

    /**
     * Just the " (Required: …)" part, for a message of your own: chat, a tooltip, a screen. Null
     * when there is nothing to name or a stage hides its hints.
     */
    @Nullable
    public static Component requiredSuffix(ServerPlayer player, List<String> missingStageIds) {
        if (missingStageIds == null || missingStageIds.isEmpty()) return null;
        List<String> names = new ArrayList<>(missingStageIds.size());
        for (String stageId : missingStageIds) {
            if (!StageStates.showsLockHints(stageId)) return null;
            names.add(StageStates.displayNameFor(stageId, player));
        }
        return Component.translatable("message.historystages.locked_stage", String.join(", ", names));
    }
}
