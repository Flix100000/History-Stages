package net.bananemdnsa.historystages.api.lock;

import java.util.List;
import java.util.UUID;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;
import net.bananemdnsa.historystages.data.lock.category.CategoryLockResolver;
import net.bananemdnsa.historystages.data.lock.engine.LockResolution;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side questions about items, answered the way the mod answers them for itself.
 *
 * <p>An item is gated by more than its own entry: by its tags, its mod, the fluid it carries and
 * the enchantments and effects on it, each possibly narrowed to certain actions. This class asks
 * all of that at once, through the same indexed path the mod's own handlers use, so it is cheap
 * enough to call per tick.
 *
 * <p><strong>To block something, ask {@link #isActionLocked}.</strong> An entry can be narrowed to
 * some actions, say only {@code recipe}; {@link #isGated} ignores that and answers whether any
 * locked stage mentions the item at all. That is the right question for a tooltip or a report,
 * and the wrong one for a gate: it is how a mod entry meant only for recipes once blocked
 * container clicks, item frames and the anvil (Issue #117).
 *
 * <p>Every method that takes a player asks both scopes: the world's global stages and that
 * player's individual ones. The {@code …Globally} forms ask the global stages only, for paths
 * with no player, such as a machine working on its own.
 *
 * <p>The actions are the ones in {@link LockActions#ITEM}: {@code equip}, {@code attack},
 * {@code place}, {@code break}, {@code pickup}, {@code trade}, {@code use}, {@code loot},
 * {@code recipe}, {@code gui} and {@code icon}. An item entry without actions locks all of them.
 */
public final class ItemLocks {

    private ItemLocks() {}

    /** Does a stage this player is missing gate this stack for any action? For display, not for blocking. */
    public static boolean isGated(ItemStack stack, ServerPlayer player) {
        return StageLockHelper.isItemLockedForPlayer(stack, player.getUUID());
    }

    /** {@link #isGated(ItemStack, ServerPlayer)} by UUID, for a player who may be offline. */
    public static boolean isGated(ItemStack stack, UUID playerUuid) {
        return StageLockHelper.isItemLockedForPlayer(stack, playerUuid);
    }

    /** Does a global stage the world is missing gate this stack for any action? For display. */
    public static boolean isGatedGlobally(ItemStack stack) {
        return StageLockHelper.isItemLockedForServer(stack);
    }

    /** May this player not do {@code action} with this stack? */
    public static boolean isActionLocked(ItemStack stack, String action, ServerPlayer player) {
        return isActionLocked(stack, action, player.getUUID());
    }

    /** {@link #isActionLocked(ItemStack, String, ServerPlayer)} by UUID. */
    public static boolean isActionLocked(ItemStack stack, String action, UUID playerUuid) {
        return StageLockHelper.isActionLockedForServer(stack, action)
                || StageLockHelper.isActionLockedByIndividualStage(stack, playerUuid, action);
    }

    /** May nobody do {@code action} with this stack, because of a global stage? */
    public static boolean isActionLockedGlobally(ItemStack stack, String action) {
        return StageLockHelper.isActionLockedForServer(stack, action);
    }

    /**
     * The stages this player still needs before the stack is free, global ones first. Empty when
     * it is not locked. Pass the ids to {@code StageStates.displayNameFor} before showing them.
     */
    public static List<String> missingStages(ItemStack stack, ServerPlayer player) {
        return missingStages(stack, player.getUUID());
    }

    /** {@link #missingStages(ItemStack, ServerPlayer)} by UUID. */
    public static List<String> missingStages(ItemStack stack, UUID playerUuid) {
        if (stack == null || stack.isEmpty()) return List.of();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return CategoryLockResolver.join(
                missing(id, stack, StageScope.GLOBAL, StageLocks.serverGlobal()),
                missing(id, stack, StageScope.INDIVIDUAL, StageLocks.serverIndividual(playerUuid)));
    }

    /**
     * May this player not do {@code action} with a fluid that is not in a container — scooping
     * from a pool, say? The actions are the ones in {@link LockActions#FLUID}.
     */
    public static boolean isFluidActionLocked(String fluidId, String action, ServerPlayer player) {
        return StageLockHelper.isFluidActionLockedForServer(fluidId, action)
                || StageLockHelper.isFluidActionLockedByIndividualStage(fluidId, player.getUUID(), action);
    }

    /**
     * May this player not get this enchantment at this level from a station?
     *
     * @param station {@code enchanting_table} or {@code anvil}
     * @param itemId  the item it would go on, or null if that is not known yet. An entry that
     *                spares an item type lets the enchantment through for that item.
     */
    public static boolean isEnchantmentLocked(String enchantmentId, int level, String station,
                                              String itemId, ServerPlayer player) {
        return StageLockHelper.isEnchantmentLockedForPlayer(enchantmentId, level, station, itemId,
                player.getUUID());
    }

    private static List<String> missing(ResourceLocation id, ItemStack stack, StageScope scope,
                                        StageStateView state) {
        List<String> gating = StageLocks.engine().gatingStagesForItem(
                id.toString(), id.getNamespace(), stack, scope);
        return LockResolution.missingStages(scope, gating, state);
    }
}
