package net.bananemdnsa.historystages.api.dependency;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.dependency.DependencyProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Everything the requirement views need to evaluate a group, assembled once per group.
 *
 * <p>Carries exactly what the views read and nothing more. A view that turns out to need
 * something else widens this record — deliberately, and visibly to every other view.
 *
 * @param player        the player, or null for global-only checks
 * @param level         the server level, or null when there is none
 * @param depositedData the tracking NBT from the scroll, if applicable
 * @param groupKey      this group's identity, which stored progress is keyed by. Not its
 *                      position: the editor can delete and reorder groups, and progress filed by
 *                      position moves to whichever requirement lands on that number next
 * @param costReduction reduction shrinking item requirements: the booster's, combined with any
 *                      "cheaper/costlier while" block — negative means costlier, 1.0 free
 * @param scope         whether this check is about a global stage or an individual one; decides
 *                      which requirement kinds are asked at all
 * @param xpFactor      multiplier on XP level requirements from "cheaper/costlier while" blocks;
 *                      1.0 unchanged, 0 free
 */
public record RequirementContext(ServerPlayer player, Level level, CompoundTag depositedData,
        String groupKey, double costReduction, StageScope scope, double xpFactor) {

    /**
     * Without an XP factor, i.e. XP costs unchanged. Kept so views and addons built against the
     * six-field form keep compiling.
     */
    public RequirementContext(ServerPlayer player, Level level, CompoundTag depositedData,
                              String groupKey, double costReduction, StageScope scope) {
        this(player, level, depositedData, groupKey, costReduction, scope, 1.0);
    }

    /**
     * The NBT key this requirement's stored progress belongs under, inside {@link #depositedData}.
     *
     * <p>{@code suffix} is the requirement's own part of the key — the built-ins use
     * {@code "Item_<item id>"} and {@code "XP"}. Include something of your requirement's id in
     * it: the group is all that keeps two requirements' keys apart otherwise.
     */
    public String progressKey(String suffix) {
        return DependencyProgress.key(groupKey, suffix);
    }
}
