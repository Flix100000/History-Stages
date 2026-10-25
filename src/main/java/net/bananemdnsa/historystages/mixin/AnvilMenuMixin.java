package net.bananemdnsa.historystages.mixin;

import java.util.UUID;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.lock.engine.StackContents;
import net.bananemdnsa.historystages.data.lock.engine.StackContentsReader;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.bananemdnsa.historystages.util.lock.LockMessages;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
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

        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
        String locked = historystages$firstLocked(StackContentsReader.of(result).allEnchantments(), itemId,
                player.getUUID());
        if (locked == null) return;

        self.getSlot(self.getResultSlot()).set(ItemStack.EMPTY);
        this.cost.set(0);
        DebugLogger.runtimeThrottled("Enchantment Lock", "anvil_" + player.getUUID(),
                "<" + player.getName().getString() + "> Anvil blocked: enchantment " + locked + " is locked");
        LockFeedback.sendActionbar(player, "anvil", LockMessages.enchantmentLocked());
    }

    @Unique
    @Nullable
    private static String historystages$firstLocked(java.util.List<StackContents.Levelled> enchantments,
                                                    String itemId, UUID player) {
        for (StackContents.Levelled entry : enchantments) {
            if (StageLockHelper.isEnchantmentLockedForPlayer(entry.id(), entry.level(), "anvil", itemId, player)) {
                return entry.id() + " " + entry.level();
            }
        }
        return null;
    }
}
