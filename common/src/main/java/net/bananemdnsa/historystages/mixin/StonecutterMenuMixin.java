package net.bananemdnsa.historystages.mixin;

import java.util.Optional;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.bananemdnsa.historystages.events.RecipeHandler;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.bananemdnsa.historystages.util.lock.LockMessages;
import net.bananemdnsa.historystages.util.lock.RecipeCraftContext;
import net.bananemdnsa.historystages.util.lock.StonecutterGate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SelectableRecipe;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The stonecutter's recipe gate.
 *
 * <p>The stonecutter takes its button list from a precomputed set
 * ({@code stonecutterRecipes().selectByInput}) and the result straight from the chosen entry, so
 * no lookup the recipe filter gates is ever asked. The gate sits where the chosen entry hands
 * over its recipe: a locked one yields no result, both for the preview and for every refill
 * after a take.
 *
 * <p>The buttons are filtered too, per crafter: on the server here in {@code setupRecipeList},
 * on the client through the list each player is sent ({@code RecipePacketMixin}), with the same
 * rule ({@link StonecutterGate}) so the button indices of both sides match. The result-slot gate
 * stays as the backstop (a list sent before a lock, a modified client). Server only: the
 * client's entries carry no recipe at all.
 */
@Mixin(StonecutterMenu.class)
public class StonecutterMenuMixin {

    @Unique
    private static final String FEEDBACK_CATEGORY = "stonecutter";

    @Unique
    @Nullable
    private Player historystages$crafter;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;"
            + "Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
    private void historystages$rememberCrafter(int containerId, Inventory playerInventory,
                                               ContainerLevelAccess access, CallbackInfo ci) {
        this.historystages$crafter = playerInventory.player;
    }

    @WrapOperation(method = "setupRecipeList", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/SelectableRecipe$SingleInputSet;selectByInput(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/crafting/SelectableRecipe$SingleInputSet;"))
    private SelectableRecipe.SingleInputSet<StonecutterRecipe> historystages$buttonsForCrafter(
            SelectableRecipe.SingleInputSet<StonecutterRecipe> all, ItemStack input,
            Operation<SelectableRecipe.SingleInputSet<StonecutterRecipe>> original) {
        SelectableRecipe.SingleInputSet<StonecutterRecipe> matching = original.call(all, input);
        return this.historystages$crafter instanceof ServerPlayer player
                ? StonecutterGate.forPlayer(player, matching) : matching;
    }

    @WrapOperation(method = "setupResultSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/SelectableRecipe;recipe()Ljava/util/Optional;"))
    private Optional<? extends RecipeHolder<?>> historystages$gateChosenRecipe(SelectableRecipe<?> entry,
                                                                               Operation<Optional<? extends RecipeHolder<?>>> original) {
        Optional<? extends RecipeHolder<?>> recipe = original.call(entry);
        if (recipe.isEmpty() || !(this.historystages$crafter instanceof ServerPlayer player)) return recipe;
        RecipeHolder<?> holder = recipe.get();
        boolean locked = RecipeCraftContext.with(player.getUUID(),
                () -> RecipeHandler.isLockedForResolution(holder, false));
        if (!locked) return recipe;
        LockFeedback.sendActionbar(player, FEEDBACK_CATEGORY, LockMessages.recipeLocked(player, holder.id().identifier()));
        return Optional.empty();
    }
}
