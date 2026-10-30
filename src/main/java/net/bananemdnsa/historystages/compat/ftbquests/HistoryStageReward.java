package net.bananemdnsa.historystages.compat.ftbquests;

import net.bananemdnsa.historystages.data.StageCauses;
import net.bananemdnsa.historystages.api.stage.StageChangeCause;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.HsRuntimeCategories;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public class HistoryStageReward extends Reward {
    private String stage = "";
    private boolean remove = false;
    private boolean individual = false;

    public HistoryStageReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return FTBQuestsIntegration.HISTORY_STAGE_REWARD;
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putString("stage", stage);
        nbt.putBoolean("remove", remove);
        nbt.putBoolean("individual", individual);
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        stage = nbt.getString("stage");
        remove = nbt.getBoolean("remove");
        individual = nbt.getBoolean("individual");
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buf) {
        super.writeNetData(buf);
        buf.writeUtf(stage);
        buf.writeBoolean(remove);
        buf.writeBoolean(individual);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buf) {
        super.readNetData(buf);
        stage = buf.readUtf(Short.MAX_VALUE);
        remove = buf.readBoolean();
        individual = buf.readBoolean();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.add("stage", new StagePickerConfig(),
                        StagePickerConfig.toPrefixed(stage, individual),
                        v -> {
                            this.stage = StagePickerConfig.stripPrefix(v);
                            this.individual = StagePickerConfig.isIndividual(v);
                        }, "")
                .setNameKey("ftbquests.reward.historystages.history_stage.stage");
        config.addBool("remove", remove, v -> remove = v, false)
                .setNameKey("ftbquests.reward.historystages.history_stage.remove");
    }

    @Override
    public void claim(ServerPlayer player, boolean notify) {
        if (stage.isEmpty()) return;

        if (individual) {
            if (!StageManager.getIndividualStages().containsKey(stage)) return;
            claimIndividual(player);
        } else {
            if (!StageManager.getStages().containsKey(stage)) return;
            claimGlobal(player);
        }
    }

    /**
     * Through {@link StageStates}, like every other unlock. This used to be a hand copy of it,
     * and the copy drifted: it reloaded every datapack on each claim instead of only when hidden
     * recipes changed, left the structure and biome caches stale, and was the only relock that
     * honoured {@code dropOnRevoke}. A quest reward is pack logic, not an admin action, so the
     * normal unlock is the right one: a logic block or a permanent loss stops it.
     */
    private void claimGlobal(ServerPlayer player) {
        boolean changed = StageCauses.with(StageChangeCause.QUEST, () -> remove
                ? StageStates.relockGlobal(stage, player.serverLevel())
                : StageStates.unlockGlobal(stage, player.serverLevel()));
        if (changed) {
            DebugLogger.runtime(HsRuntimeCategories.STAGE, player.getName().getString(),
                    "Global stage '" + stage + "' " + (remove ? "locked" : "unlocked") + " by an FTB Quests reward");
        }
    }

    private void claimIndividual(ServerPlayer player) {
        boolean changed = StageCauses.with(StageChangeCause.QUEST, () -> remove
                ? StageStates.relockIndividual(stage, player)
                : StageStates.unlockIndividual(stage, player));
        if (changed) {
            DebugLogger.runtime(HsRuntimeCategories.STAGE, player.getName().getString(),
                    "Individual stage '" + stage + "' " + (remove ? "locked" : "unlocked") + " by an FTB Quests reward");
        }
    }

    @Override
    public MutableComponent getAltTitle() {
        String displayName = resolveDisplayName();
        if (remove) {
            return Component.translatable("ftbquests.reward.historystages.history_stage.title.lock", displayName);
        }
        return Component.translatable("ftbquests.reward.historystages.history_stage.title.unlock", displayName);
    }

    @Override
    public Icon getAltIcon() {
        java.util.Map<String, StageEntry> source = individual
                ? StageManager.getIndividualStages()
                : StageManager.getStages();
        StageEntry entry = source.get(stage);
        String iconId = (entry != null && !entry.getIcon().isEmpty())
                ? entry.getIcon()
                : Config.VISUAL.defaultStageIcon.get();
        if (iconId == null || iconId.isEmpty()) return super.getAltIcon();
        return ItemIcon.getItemIcon(iconId);
    }

    private String resolveDisplayName() {
        if (stage.isEmpty()) return stage;
        java.util.Map<String, StageEntry> source = individual
                ? StageManager.getIndividualStages()
                : StageManager.getStages();
        StageEntry entry = source.get(stage);
        return entry != null ? entry.getDisplayName() : stage;
    }

    @Override
    public boolean ignoreRewardBlocking() {
        return true;
    }

    @Override
    protected boolean isIgnoreRewardBlockingHardcoded() {
        return true;
    }
}
