package net.bananemdnsa.historystages.mixin;

import java.util.UUID;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.bananemdnsa.historystages.util.lock.RecipeCraftContext;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the recipe filter who opened the stonecutter.
 *
 * <p>The resolution happens in {@code setupRecipeList}, not in {@code setupResultSlot} — the
 * latter only assembles from the list that was already resolved. Gating at the resolution means a
 * locked recipe never becomes a button in the first place, so preview and taking are both covered
 * without a second hook.
 *
 * <p>The menu carries no player field, so the crafter is remembered at construction. Both public
 * constructors run through the one injected here.
 *
 * <p>Runs on both sides — the menu exists on the client too and resolves its own button list. That
 * symmetry is the point: one side flipping alone would show a button whose result the other side
 * refuses.
 *
 * <p>The method is wrapped rather than the lookup inside it, for the reason spelled out on
 * {@link CraftingMenuMixin}.
 */
@Mixin(StonecutterMenu.class)
public class StonecutterMenuMixin {

    @Unique
    @Nullable
    private UUID historystages$crafter;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;"
            + "Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
    private void historystages$rememberCrafter(int containerId, Inventory playerInventory,
                                               ContainerLevelAccess access, CallbackInfo ci) {
        this.historystages$crafter = playerInventory.player.getUUID();
    }

    @WrapMethod(method = "setupRecipeList")
    private void historystages$resolveForCrafter(Container container, ItemStack stack,
                                                 Operation<Void> original) {
        RecipeCraftContext.with(this.historystages$crafter,
                () -> original.call(container, stack));
    }
}
