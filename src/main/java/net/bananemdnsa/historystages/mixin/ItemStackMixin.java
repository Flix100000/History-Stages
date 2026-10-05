package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.client.display.HiddenDisplayResolver;
import net.bananemdnsa.historystages.client.disguise.ClientDisguises;
import net.bananemdnsa.historystages.data.disguise.Disguises;
import net.bananemdnsa.historystages.data.display.DisplayMode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only: replaces or hides the display name of an item that is locked by a stage
 * whose hidden-display name mode is HIDDEN or REPLACE. Because the first tooltip line is
 * derived from {@code getHoverName()}, this also covers the hover tooltip, JEI, anvil, etc.
 *
 * <p>A disguised item takes its disguise's name, unless a stage's hidden-display name is set —
 * that one was written for exactly this item and wins.</p>
 *
 * <p>Guarded to the client render thread so server-side {@code getHoverName} calls (e.g.
 * container titles in singleplayer) are never altered.</p>
 */
@Mixin(ItemStack.class)
public class ItemStackMixin {

    /**
     * Asking the disguise for its name runs this mixin again for the disguise. A chain ends on an
     * item that is not disguised itself, so this only stops the one case that would not end: a
     * loop through tag membership, which the file check cannot see.
     */
    private static final ThreadLocal<Boolean> historystages$inDisguiseName = ThreadLocal.withInitial(() -> false);

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void historystages$hideOrReplaceName(CallbackInfoReturnable<Component> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !mc.isSameThread() || mc.player == null) return;

        ItemStack self = (ItemStack) (Object) this;
        HiddenDisplayResolver.Resolved resolved = HiddenDisplayResolver.resolve(self);
        if (resolved.changesName()) {
            if (resolved.nameMode() == DisplayMode.HIDDEN) {
                cir.setReturnValue(Component.empty());
            } else {
                cir.setReturnValue(Component.literal(resolved.nameText()));
            }
            return;
        }

        if (historystages$inDisguiseName.get()) return;
        Disguises.Resolved disguise = ClientDisguises.forStack(self);
        if (disguise == null) return;
        historystages$inDisguiseName.set(true);
        try {
            cir.setReturnValue(new ItemStack(disguise.target()).getHoverName());
        } finally {
            historystages$inDisguiseName.set(false);
        }
    }
}
