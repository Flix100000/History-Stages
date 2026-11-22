package net.bananemdnsa.historystages.mixin.client.slotlock;

import net.bananemdnsa.historystages.client.slotlock.SlotLockClient;
import net.bananemdnsa.historystages.util.lock.SlotLockState;
import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The HUD hotbar is one 182px strip of {@code widgets.png}. With hidden slots it is drawn slot by
 * slot instead, leaving gaps where the hidden ones are, so slot positions still match the number
 * keys.
 *
 * <p>1.20.1 draws the bar, the selection frame and the offhand frame as plain {@code blit}s from
 * that one texture, so they are told apart by where in the texture they come from.
 */
@Mixin(Gui.class)
public abstract class GuiHotbarMixin {

    private static final int SLOT_STRIDE = 20;
    private static final int HOTBAR_WIDTH = 182;
    private static final int OFFHAND = 40;

    @Shadow protected abstract Player getCameraPlayer();

    // WrapOperation rather than Redirect: other HUD mods wrap these same calls, and only one
    // Redirect per call site can exist.
    @WrapOperation(method = "renderHotbar",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void historystages$hotbarSprite(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v,
                                            int width, int height, Operation<Void> original) {
        Player player = getCameraPlayer();
        if (!SlotLocks.anyPlayerSlotLocks(player)) {
            original.call(g, texture, x, y, u, v, width, height);
            return;
        }
        if (u == 0 && v == 0 && width == HOTBAR_WIDTH) {
            historystages$hotbarPieces(g, player, texture, x, y, height);
        } else if (u == 0 && v == 22) {
            // The selection frame. Only when every slot is hidden can it sit on a hidden one.
            if (SlotLocks.playerSlot(player, player.getInventory().selected) != SlotLockState.HIDDEN) {
                original.call(g, texture, x, y, u, v, width, height);
            }
        } else if ((u == 24 || u == 53) && v == 22) {
            // The offhand frame, left or right of the bar.
            if (SlotLocks.playerSlot(player, OFFHAND) != SlotLockState.HIDDEN) {
                original.call(g, texture, x, y, u, v, width, height);
            }
        } else {
            original.call(g, texture, x, y, u, v, width, height);
        }
    }

    private void historystages$hotbarPieces(GuiGraphics g, Player player, ResourceLocation texture, int x, int y, int height) {
        for (int slot = 0; slot < 9; slot++) {
            if (SlotLocks.playerSlot(player, slot) == SlotLockState.HIDDEN) continue;
            // Each piece takes its own 20px plus the 1px rims on both sides; neighbours overlap.
            int u = slot * SLOT_STRIDE;
            int pieceWidth = Math.min(SLOT_STRIDE + 2, HOTBAR_WIDTH - u);
            g.blit(texture, x + u, y, u, 0, pieceWidth, height);
        }
    }

    @WrapOperation(method = "renderHotbar",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;renderSlot(Lnet/minecraft/client/gui/GuiGraphics;IIFLnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V"))
    private void historystages$hotbarItem(Gui gui, GuiGraphics g, int x, int y, float partialTick, Player player,
                                          ItemStack stack, int seed, Operation<Void> original) {
        if (SlotLocks.anyPlayerSlotLocks(player)
                && SlotLocks.playerSlot(player, historystages$slotAt(g, x)) == SlotLockState.HIDDEN) {
            return;
        }
        original.call(gui, g, x, y, partialTick, player, stack, seed);
    }

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void historystages$hotbarLocks(float partialTick, GuiGraphics g, CallbackInfo ci) {
        Player player = getCameraPlayer();
        if (!SlotLocks.anyPlayerSlotLocks(player)) return;
        int left = g.guiWidth() / 2 - 90 + 2;
        int top = g.guiHeight() - 16 - 3;
        for (int slot = 0; slot < 9; slot++) {
            SlotLocks.Info info = SlotLocks.playerInfo(player, slot);
            if (info.state() != SlotLockState.OVERLAY) continue;
            SlotLockClient.drawOverlay(g, left + slot * SLOT_STRIDE, top, SlotLockClient.lookOf(info));
        }
    }

    /** Hotbar items sit at centre - 90 + 20 * slot + 2; anything outside 0-8 is the offhand. */
    private static int historystages$slotAt(GuiGraphics g, int x) {
        int offset = x - (g.guiWidth() / 2 - 88);
        if (offset < 0) return OFFHAND;
        int slot = offset / SLOT_STRIDE;
        return slot > 8 ? OFFHAND : slot;
    }
}
