package net.bananemdnsa.historystages.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import net.bananemdnsa.historystages.client.toast.DismissibleToast;

public class StageUnlockedToast implements Toast, DismissibleToast {

    private static final Identifier BACKGROUND_SPRITE = Identifier.withDefaultNamespace("toast/advancement");
    private static final int DISPLAY_TIME = 5000;

    private final Component title;
    private final Component stageName;
    private final ItemStack icon;
    private boolean dismissed = false;

    public StageUnlockedToast(String stageName, ItemStack icon) {
        this.title = Component.translatable("toast.historystages.stage_unlocked");
        this.stageName = Component.literal(stageName);
        this.icon = icon != null ? icon : ItemStack.EMPTY;
    }

    @Override
    public void dismiss() {
        this.dismissed = true;
    }


    // 26.2 split Toast.render into update/getWantedVisibility/extractRenderState. The old render
    // body stays and reports the visibility it decided on for the next update.
    private ToastManager toastManager;
    private Visibility wantedVisibility = Visibility.SHOW;

    @Override
    public Visibility getWantedVisibility() {
        return wantedVisibility;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        this.toastManager = manager;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, long fullyVisibleForMs) {
        if (toastManager == null) return;
        wantedVisibility = render(graphics, toastManager, fullyVisibleForMs);
    }

    private Visibility render(GuiGraphicsExtractor guiGraphics, ToastManager toastComponent, long timeSinceLastVisible) {
        if (dismissed) {
            return Visibility.HIDE;
        }

        // Draw the vanilla toast background using 1.21 sprite system
        guiGraphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, this.width(), this.height());

        // Draw title text (line 1) - yellow like vanilla advancement toasts
        guiGraphics.text(toastComponent.getMinecraft().font, this.title, 30, 7, 0xFFFFFF00, false);

        // Draw stage name (line 2) - white
        guiGraphics.text(toastComponent.getMinecraft().font, this.stageName, 30, 18, 0xFFFFFFFF, false);

        // Draw the stage icon
        if (!icon.isEmpty()) {
            guiGraphics.fakeItem(icon, 8, 8);
        }

        return (double) timeSinceLastVisible >= (double) DISPLAY_TIME * toastComponent.getNotificationDisplayTimeMultiplier()
                ? Visibility.HIDE : Visibility.SHOW;
    }
}
