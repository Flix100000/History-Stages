package net.bananemdnsa.historystages.mixin.client.slotlock;

import net.bananemdnsa.historystages.client.slotlock.SlotLockClient;
import net.bananemdnsa.historystages.util.lock.SlotLockState;
import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
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
 * The HUD hotbar is one 182px sprite. With hidden slots it is drawn slot by slot instead, leaving
 * gaps where the hidden ones are, so slot positions still match the number keys.
 */
@Mixin(net.minecraft.client.gui.Hud.class)
public abstract class GuiHotbarMixin {

    private static final int SLOT_STRIDE = 20;
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int OFFHAND = 40;

    @Shadow protected abstract Player getCameraPlayer();

    // WrapOperation rather than Redirect: other HUD mods wrap these same calls, and only one
    // Redirect per call site can exist. Every sprite of the hotbar passes here: the bar itself,
    // the selection frame and the offhand frame each need to know about hidden slots.
    @WrapOperation(method = "extractItemHotbar",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void historystages$hotbarSprite(GuiGraphicsExtractor g, com.mojang.blaze3d.pipeline.RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height,
                                            Operation<Void> original) {
        Player player = getCameraPlayer();
        if (!SlotLocks.anyPlayerSlotLocks(player)) {
            original.call(g, pipeline, sprite, x, y, width, height);
            return;
        }
        switch (sprite.getPath()) {
            case "hud/hotbar" -> historystages$hotbarPieces(g, pipeline, player, sprite, x, y, height);
            // Only when every slot is hidden can the selection sit on a hidden one.
            case "hud/hotbar_selection" -> {
                if (SlotLocks.playerSlot(player, player.getInventory().getSelectedSlot()) != SlotLockState.HIDDEN) {
                    original.call(g, pipeline, sprite, x, y, width, height);
                }
            }
            case "hud/hotbar_offhand_left", "hud/hotbar_offhand_right" -> {
                if (SlotLocks.playerSlot(player, OFFHAND) != SlotLockState.HIDDEN) {
                    original.call(g, pipeline, sprite, x, y, width, height);
                }
            }
            default -> original.call(g, pipeline, sprite, x, y, width, height);
        }
    }

    private void historystages$hotbarPieces(GuiGraphicsExtractor g, com.mojang.blaze3d.pipeline.RenderPipeline pipeline, Player player, Identifier sprite, int x, int y, int height) {
        for (int slot = 0; slot < 9; slot++) {
            if (SlotLocks.playerSlot(player, slot) == SlotLockState.HIDDEN) continue;
            // Each piece takes its own 20px plus the 1px rims on both sides; neighbours overlap.
            int u = slot * SLOT_STRIDE;
            int pieceWidth = Math.min(SLOT_STRIDE + 2, HOTBAR_WIDTH - u);
            g.blitSprite(pipeline, sprite, HOTBAR_WIDTH, HOTBAR_HEIGHT, u, 0, x + u, y, pieceWidth, height);
        }
    }

    @WrapOperation(method = "extractItemHotbar",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Hud;extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V"))
    private void historystages$hotbarItem(net.minecraft.client.gui.Hud gui, GuiGraphicsExtractor g, int x, int y, DeltaTracker delta, Player player,
                                          ItemStack stack, int seed, Operation<Void> original) {
        if (SlotLocks.anyPlayerSlotLocks(player)
                && SlotLocks.playerSlot(player, historystages$slotAt(g, x)) == SlotLockState.HIDDEN) {
            return;
        }
        original.call(gui, g, x, y, delta, player, stack, seed);
    }

    @Inject(method = "extractItemHotbar", at = @At("TAIL"))
    private void historystages$hotbarLocks(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        Player player = getCameraPlayer();
        if (!SlotLocks.anyPlayerSlotLocks(player)) return;
        int left = g.guiWidth() / 2 - 90 + 2;
        int top = g.guiHeight() - 16 - 3;
        boolean lifted = false;
        for (int slot = 0; slot < 9; slot++) {
            SlotLocks.Info info = SlotLocks.playerInfo(player, slot);
            if (info.state() != SlotLockState.OVERLAY) continue;
            // 26.2 has no z: a new stratum is what puts the tint over the hotbar item.
            if (!lifted) {
                g.nextStratum();
                lifted = true;
            }
            SlotLockClient.drawOverlay(g, left + slot * SLOT_STRIDE, top, SlotLockClient.lookOf(info));
        }
    }

    /** Hotbar items sit at centre - 88 + 20 * slot; anything outside 0-8 is the offhand. */
    private static int historystages$slotAt(GuiGraphicsExtractor g, int x) {
        int offset = x - (g.guiWidth() / 2 - 88);
        if (offset < 0) return OFFHAND;
        int slot = offset / SLOT_STRIDE;
        return slot > 8 ? OFFHAND : slot;
    }
}
