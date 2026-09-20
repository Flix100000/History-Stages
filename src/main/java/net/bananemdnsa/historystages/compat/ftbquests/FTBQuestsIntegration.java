package net.bananemdnsa.historystages.compat.ftbquests;

import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.stage.StageEvent;
import net.bananemdnsa.historystages.init.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.util.ServerHolder;

public class FTBQuestsIntegration {

    public static TaskType HISTORY_STAGE_TASK;
    public static RewardType HISTORY_STAGE_REWARD;

    public static void init() {
        HISTORY_STAGE_TASK = TaskTypes.register(
                ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "history_stage"),
                HistoryStageTask::new,
                () -> ItemIcon.getItemIcon(new ItemStack(ModItems.RESEARCH_SCROLL.get()))
        ).setDisplayName(Component.translatable("ftbquests.task.historystages.history_stage"));

        HISTORY_STAGE_REWARD = RewardTypes.register(
                ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "history_stage"),
                HistoryStageReward::new,
                () -> ItemIcon.getItemIcon(new ItemStack(ModItems.RESEARCH_SCROLL.get()))
        ).setDisplayName(Component.translatable("ftbquests.reward.historystages.history_stage"));

        EventBus.addListener(StageEvent.Unlocked.class, event ->
                HistoryStageTask.onGlobalStageChanged(event.getStageId(), true)
        );

        EventBus.addListener(StageEvent.Locked.class, event ->
                HistoryStageTask.onGlobalStageChanged(event.getStageId(), false)
        );

        EventBus.addListener(StageEvent.IndividualUnlocked.class, event -> {
            ServerPlayer player = resolvePlayer(event.getPlayerUUID());
            if (player != null) {
                HistoryStageTask.onIndividualStageChanged(event.getStageId(), player, true);
            }
        });

        EventBus.addListener(StageEvent.IndividualLocked.class, event -> {
            ServerPlayer player = resolvePlayer(event.getPlayerUUID());
            if (player != null) {
                HistoryStageTask.onIndividualStageChanged(event.getStageId(), player, false);
            }
        });
    }

    private static ServerPlayer resolvePlayer(java.util.UUID uuid) {
        MinecraftServer server = ServerHolder.get();
        if (server == null) return null;
        return server.getPlayerList().getPlayer(uuid);
    }
}
