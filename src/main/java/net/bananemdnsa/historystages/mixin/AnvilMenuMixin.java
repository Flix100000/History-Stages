package net.bananemdnsa.historystages.mixin;

import java.util.UUID;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.bananemdnsa.historystages.util.lock.LockMessages;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the anvil from putting a locked enchantment on anything.
 *
 * <p>Checked on the result rather than on the inputs: the anvil merges levels (two Sharpness IV
 * become V), so only the result knows what would actually come out. An empty result slot is also
 * all {@code onTake} and shift-clicking need — they find nothing to take.
 *
 * <p>Asks for the {@code anvil} action, so an entry can allow the anvil and still refuse the table.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    @Shadow @Final private DataSlot cost;

    @Unique
    @Nullable
    private ServerPlayer historystages$player;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;"
            + "Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
    private void historystages$rememberPlayer(int containerId, Inventory inventory,
                                              ContainerLevelAccess access, CallbackInfo ci) {
        if (inventory.player instanceof ServerPlayer serverPlayer) this.historystages$player = serverPlayer;
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void historystages$refuseLockedEnchantments(CallbackInfo ci) {
        ServerPlayer player = this.historystages$player;
        if (player == null) return;
        if (!Config.GAMEPLAY.lockEnchanting.get() && !Config.GAMEPLAY.individualLockEnchanting.get()) return;

        AnvilMenu self = (AnvilMenu) (Object) this;
        ItemStack result = self.getSlot(self.getResultSlot()).getItem();
        if (result.isEmpty()) return;

        String locked = historystages$firstLocked(result.get(DataComponents.ENCHANTMENTS), player.getUUID());
        if (locked == null) {
            locked = historystages$firstLocked(result.get(DataComponents.STORED_ENCHANTMENTS), player.getUUID());
        }
        if (locked == null) return;

        self.getSlot(self.getResultSlot()).set(ItemStack.EMPTY);
        this.cost.set(0);
        DebugLogger.runtimeThrottled("Enchantment Lock", "anvil_" + player.getUUID(),
                "<" + player.getName().getString() + "> Anvil blocked: enchantment " + locked + " is locked");
        LockFeedback.sendActionbar(player, "anvil", LockMessages.enchantmentLocked());
    }

    @Unique
    @Nullable
    private static String historystages$firstLocked(@Nullable ItemEnchantments enchantments, UUID player) {
        if (enchantments == null || enchantments.isEmpty()) return null;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            String id = entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse(null);
            if (id == null) continue;
            if (StageLockHelper.isEnchantmentLockedForPlayer(id, entry.getIntValue(), "anvil", player)) {
                return id + " " + entry.getIntValue();
            }
        }
        return null;
    }
}
