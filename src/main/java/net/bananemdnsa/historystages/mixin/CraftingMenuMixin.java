package net.bananemdnsa.historystages.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.bananemdnsa.historystages.util.lock.RecipeCraftContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Tells the recipe filter who is standing at the crafting table.
 *
 * <p>{@code slotChangedCraftingGrid} is static and takes the player, which is why this one hook
 * covers both the crafting table and the 2x2 inventory grid: {@code InventoryMenu.slotsChanged}
 * calls straight into it.
 *
 * <p>The whole method is wrapped rather than the resolution call inside it. Mixin gives a call
 * site to a single redirector, and Polymorph redirects this one to offer its recipe picker — the
 * loser of that fight is dropped and the game dies at class load (Issue #127). Wrapping keeps the
 * body as it is, so every other mod's hook still applies, and their lookups run inside the window
 * and get filtered too. The rest of the method assembles and sends a slot packet, neither of
 * which resolves a recipe, so the wider window costs nothing.
 *
 * <p>Server-side only, and that is vanilla's doing — the method returns immediately on the client
 * and the result slot arrives by packet. Client and server therefore cannot disagree here.
 */
@Mixin(CraftingMenu.class)
public class CraftingMenuMixin {

    @WrapMethod(method = "slotChangedCraftingGrid")
    private static void historystages$resolveForCrafter(
            AbstractContainerMenu menu, Level level, Player player, CraftingContainer craftSlots,
            ResultContainer resultSlots, Operation<Void> original) {
        RecipeCraftContext.with(player.getUUID(),
                () -> original.call(menu, level, player, craftSlots, resultSlots));
    }
}
