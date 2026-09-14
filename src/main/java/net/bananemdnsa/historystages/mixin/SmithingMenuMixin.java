package net.bananemdnsa.historystages.mixin;

import java.util.UUID;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.bananemdnsa.historystages.util.lock.RecipeCraftContext;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the recipe filter who opened the smithing table.
 *
 * <p>The crafter is remembered at construction rather than shadowed off
 * {@code ItemCombinerMenu.player}, so this reads exactly like the stonecutter next to it — same
 * decision, same shape.
 *
 * <p>{@code createResult} is the one place the result comes from, and it writes into the result
 * container. A locked recipe leaves that container empty, so {@code onTake} and
 * {@code quickMoveStack} need no hook of their own: they find nothing.
 *
 * <p>The method is wrapped rather than the lookup inside it, for the reason spelled out on
 * {@link CraftingMenuMixin} — Polymorph reads the recipe list here with {@code @ModifyVariable}
 * and needs the lookup to still be where vanilla left it.
 */
@Mixin(SmithingMenu.class)
public class SmithingMenuMixin {

    @Unique
    @Nullable
    private UUID historystages$crafter;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;"
            + "Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
    private void historystages$rememberCrafter(int containerId, Inventory playerInventory,
                                               ContainerLevelAccess access, CallbackInfo ci) {
        this.historystages$crafter = playerInventory.player.getUUID();
    }

    @WrapMethod(method = "createResult")
    private void historystages$resolveForCrafter(Operation<Void> original) {
        RecipeCraftContext.with(this.historystages$crafter, () -> original.call());
    }
}
