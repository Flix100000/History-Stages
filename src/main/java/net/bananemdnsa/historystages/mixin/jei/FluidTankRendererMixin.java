package net.bananemdnsa.historystages.mixin.jei;

import net.bananemdnsa.historystages.client.LockIconRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Draws the lock overlay on gated fluids in JEI.
 *
 * <p>Items get theirs from a NeoForge {@code IItemDecorator}, which — as the name says — only
 * ever runs for items. A fluid in JEI is an ingredient of its own type with its own renderer, so
 * a gated fluid sat there unmarked while the bucket beside it wore a lock.
 *
 * <p>{@code FluidTankRenderer} is the one place JEI draws a fluid, which means this covers the
 * ingredient list and recipe slots at once.
 *
 * <p><strong>The four-argument overload is the one that draws.</strong> The two-argument API
 * method delegates to it, not the other way round — hooking the short one first produced no icon
 * anywhere, because the ingredient list calls the positional form directly. It also carries the
 * slot position, which the short form does not: an overlay hung off the two-argument method
 * would have landed at the origin rather than on the slot.
 *
 * <p>{@code @Pseudo} with {@code require = 0} for the same reason as
 * {@link RecipeLayoutMixin}: JEI may not be installed, and then this is silently skipped. That
 * silence is also why a wrong descriptor here fails invisibly, which is exactly what happened.
 *
 * <p><strong>Wrapped, not injected at the tail (#145).</strong> JEI 15.74.x compiles this method
 * so that it reuses local slot 0 ({@code this}) for the tank width. An injected handler loads
 * {@code this} from that slot and the verifier rejects the whole class, which took world loading
 * down. {@code @WrapMethod} leaves JEI's body untouched and draws after it returns.
 */
@Pseudo
@Mixin(targets = "mezz.jei.library.render.FluidTankRenderer", remap = false)
public abstract class FluidTankRendererMixin {

    @WrapMethod(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/Object;II)V",
            require = 0)
    private void historystages$drawFluidLockIcon(GuiGraphics guiGraphics, Object ingredient,
                                                 int x, int y, Operation<Void> original) {
        original.call(guiGraphics, ingredient, x, y);
        try {
            if (!(ingredient instanceof FluidStack fluid) || fluid.isEmpty()) return;
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
            if (id == null) return;

            ResourceLocation icon = LockIconRenderer.iconForFluid(id.toString());
            if (icon != null) LockIconRenderer.draw(guiGraphics, icon, x, y);
        } catch (Throwable ignored) {
            // Never take a recipe screen down over an overlay.
        }
    }
}
