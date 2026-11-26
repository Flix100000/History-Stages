package net.bananemdnsa.historystages.client.slotlock;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.LockIconRenderer;
import net.bananemdnsa.historystages.data.lock.SlotLook;
import net.bananemdnsa.historystages.util.lock.HotbarSelection;
import net.minecraft.network.chat.Component;
import java.util.List;
import net.bananemdnsa.historystages.util.lock.SlotLockState;
import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.bananemdnsa.historystages.platform.Dist;
import net.bananemdnsa.historystages.platform.bus.SubscribeEvent;
import net.bananemdnsa.historystages.platform.bus.EventBusSubscriber;
import net.bananemdnsa.historystages.platform.event.client.ClientTickEvent;
import net.bananemdnsa.historystages.platform.event.client.ContainerScreenEvent;

/** Client side of slot locks: keeping the selection off locked slots and drawing what is locked. */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public final class SlotLockClient {

    public static final Identifier LOCK_ICON =
            Identifier.fromNamespaceAndPath(HistoryStages.MOD_ID, "textures/gui/lock/overlay_global.png");

    /** The background colour of vanilla container textures, used to paint a hidden slot away. */
    private static final int VANILLA_PANEL = 0xFFC6C6C6;

    private static int lastOpenSelection = 0;

    private SlotLockClient() {}

    public static boolean hotbarLocked(LocalPlayer player, int slot) {
        return SlotLocks.playerSlot(player, slot).blocks();
    }

    /**
     * Number keys set the selection straight in the keybind handler, with no hook in between, so
     * a pick onto a locked slot is undone here before the tick sends it. A slot that got locked
     * while selected moves on to an open one.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !SlotLocks.anyPlayerSlotLocks(player)) return;
        var inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        if (!hotbarLocked(player, selected)) {
            lastOpenSelection = selected;
            return;
        }
        inventory.setSelectedSlot(HotbarSelection.settle(lastOpenSelection, slot -> hotbarLocked(player, slot)));
    }

    /** A slot remembered from the last world means nothing in the next one. */
    @SubscribeEvent
    public static void onLoggingOut(net.bananemdnsa.historystages.platform.event.client.ClientPlayerNetworkEvent.LoggingOut event) {
        lastOpenSelection = 0;
    }

    /** Hidden slots in vanilla screens: paint the frame baked into the texture over. */
    @SubscribeEvent
    public static void onScreenBackground(ContainerScreenEvent.Render.Background event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        if (!isVanilla(screen)) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (!SlotLocks.anyPlayerSlotLocks(player) && !anyContainerLocks()) return;
        GuiGraphicsExtractor g = event.getGuiGraphics();
        for (Slot slot : screen.getMenu().slots) {
            if (SlotLocks.stateOf(slot, player) != SlotLockState.HIDDEN) continue;
            int x = ContainerScreens.left(screen) + slot.x - 1;
            int y = ContainerScreens.top(screen) + slot.y - 1;
            g.fill(x, y, x + 18, y + 18, VANILLA_PANEL);
        }
    }

    /** Overlay slots, in any screen: a dark tint and the lock on top of whatever is in them. */
    @SubscribeEvent
    public static void onScreenForeground(ContainerScreenEvent.Render.Foreground event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isCreative()) return;
        if (!net.bananemdnsa.historystages.data.lock.engine.StageLocks.engine().anySlotLocks()) return;
        GuiGraphicsExtractor g = event.getGuiGraphics();
        boolean lifted = false;
        for (Slot slot : event.getContainerScreen().getMenu().slots) {
            if (SlotLocks.stateOf(slot, player) != SlotLockState.OVERLAY) continue;
            // 26.2 has no z: a new stratum is what puts the tint over the slot's item.
            if (!lifted) {
                g.nextStratum();
                lifted = true;
            }
            drawOverlay(g, slot.x, slot.y, lookOf(SlotLocks.infoOf(slot, player)));
        }
    }

    /** An empty locked slot shows its hint on its own; one holding an item adds it to the item's tooltip. */
    @SubscribeEvent
    public static void onScreenRendered(net.bananemdnsa.historystages.platform.event.client.ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isCreative() || !screen.getMenu().getCarried().isEmpty()) return;
        if (!net.bananemdnsa.historystages.Config.VISUAL.slotShowTooltip.get()) return;
        Slot slot = ContainerScreens.slotUnderMouse(screen);
        if (slot == null || slot.hasItem()) return;
        if (SlotLocks.stateOf(slot, player) != SlotLockState.OVERLAY) return;
        List<Component> lines = SlotHints.lines(SlotLocks.infoOf(slot, player));
        event.getGuiGraphics().setComponentTooltipForNextFrame(Minecraft.getInstance().font, lines,
                event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public static void onItemTooltip(net.bananemdnsa.historystages.platform.event.entity.player.ItemTooltipEvent event) {
        if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen)) return;
        LocalPlayer player = Minecraft.getInstance().player;
        Slot slot = ContainerScreens.slotUnderMouse(screen);
        if (player == null || slot == null || slot.getItem() != event.getItemStack()) return;
        if (!net.bananemdnsa.historystages.Config.VISUAL.slotShowTooltip.get()) return;
        if (SlotLocks.stateOf(slot, player) != SlotLockState.OVERLAY) return;
        event.getToolTip().addAll(SlotHints.lines(SlotLocks.infoOf(slot, player)));
    }

    public static SlotLook lookOf(SlotLocks.Info info) {
        return info.entry() != null ? info.entry().look() : new SlotLook();
    }

    /** The original look: half-dark tint and a lock. */
    public static void drawOverlay(GuiGraphicsExtractor g, int x, int y) {
        drawOverlay(g, x, y, new SlotLook());
    }

    /**
     * A locked slot as its entry wants it. A pack texture replaces tint and symbol; otherwise the
     * tint goes first and the symbol sits on top of whatever item lies in the slot.
     */
    public static void drawOverlay(GuiGraphicsExtractor g, int x, int y, SlotLook look) {
        g.pose().pushMatrix();
        g.pose().translate(0, 0);
        if (!look.texture().isEmpty()) {
            // Picked from the block and item textures, so the atlas already holds it; drawing the
            // .png by path would load it as a standalone texture on first use.
            var sprite = net.bananemdnsa.historystages.client.editor.widget.list.SearchableTextureList.spriteFor(look.texture());
            if (sprite != null) g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16);
            g.pose().popMatrix();
            return;
        }
        g.fill(x, y, x + 16, y + 16, look.argb());
        g.pose().popMatrix();

        int size = switch (look.size()) {
            case SMALL -> 8;
            case MEDIUM -> 12;
            case FULL -> 16;
        };
        int offset = look.size() == SlotLook.Size.SMALL ? 0 : (16 - size) / 2;
        switch (look.icon()) {
            case LOCK -> LockIconRenderer.drawSized(g, LOCK_ICON, x + offset, y + offset, size);
            case ITEM -> drawItem(g, look.item(), x + offset, y + offset, size);
            case NONE -> { }
        }
    }

    private static void drawItem(GuiGraphicsExtractor g, String itemId, int x, int y, int size) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) return;
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id));
        g.pose().pushMatrix();
        // renderItem adds its own ~150 of depth: from 100 it lands just above the tint (240) and
        // well below tooltips (400), which it would cover from any higher start.
        g.pose().translate(x, y);
        g.pose().scale(size / 16f, size / 16f);
        g.item(stack, 0, 0);
        g.pose().popMatrix();
    }


    private static boolean anyContainerLocks() {
        return net.bananemdnsa.historystages.data.lock.engine.StageLocks.engine().anySlotLocks();
    }

    /**
     * Only vanilla screens get painted over: their background colour is known. A mod screen with
     * its own look would end up with grey squares, so there a hidden slot is just empty and inert.
     */
    private static boolean isVanilla(AbstractContainerScreen<?> screen) {
        return screen.getClass().getName().startsWith("net.minecraft.");
    }
}
