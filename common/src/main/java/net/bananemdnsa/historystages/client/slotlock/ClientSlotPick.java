package net.bananemdnsa.historystages.client.slotlock;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.data.lock.SlotLockEntry;
import net.bananemdnsa.historystages.data.lock.SlotLockMode;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.serverbound.SlotPickModePacket;
import net.bananemdnsa.historystages.util.lock.SlotLockMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.bananemdnsa.historystages.platform.Dist;
import net.bananemdnsa.historystages.platform.bus.SubscribeEvent;
import net.bananemdnsa.historystages.platform.bus.EventBusSubscriber;
import net.bananemdnsa.historystages.platform.event.client.ClientPlayerNetworkEvent;
import net.bananemdnsa.historystages.platform.event.client.RenderGuiEvent;
import net.bananemdnsa.historystages.platform.event.client.ScreenEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Plan B for GUIs the editor cannot draw: the editor steps aside, the player opens the real
 * container, and clicks on its slots select them instead of moving items. The server is told, so
 * it ignores those clicks too.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public final class ClientSlotPick {

    private static final int BANNER_H = 22;
    private static final int BANNER_BG = 0xE0101010;
    private static final int BANNER_EDGE = 0xFF333333;
    private static final int ACCENT = 0xFFFFCC00;
    private static final int SELECTABLE_OUTLINE = 0xFFFFCC00;
    private static final int BUTTON_W = 70;
    private static final int BUTTON_H = 16;

    private static boolean active;
    @Nullable private static Screen returnTo;
    @Nullable private static Consumer<SlotLockEntry> onDone;
    @Nullable private static String target;
    private static final TreeSet<Integer> selected = new TreeSet<>();
    private static final List<StyledButton> buttons = new ArrayList<>();

    private ClientSlotPick() {}

    public static boolean isActive() {
        return active;
    }

    public static void start(Screen editor, Consumer<SlotLockEntry> done) {
        active = true;
        returnTo = editor;
        onDone = done;
        target = null;
        selected.clear();
        PacketHandler.sendToServer(new SlotPickModePacket(true));
        Minecraft.getInstance().gui.setScreen(null);
    }

    private static void finish(boolean keep) {
        if (!active) return;
        active = false;
        PacketHandler.sendToServer(new SlotPickModePacket(false));
        Screen back = returnTo;
        Consumer<SlotLockEntry> callback = onDone;
        String picked = target;
        List<Integer> slots = new ArrayList<>(selected);
        returnTo = null;
        onDone = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.closeContainer();
        mc.gui.setScreen(back);
        if (keep && callback != null && picked != null) {
            callback.accept(new SlotLockEntry(picked, slots, SlotLockMode.OVERLAY));
        }
    }

    // ---- container screens -------------------------------------------------------------

    /** A different container than before starts the selection over: the slots meant someone else. */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!active || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        String now = ((SlotLockMenu) screen.getMenu()).historystages$slotTarget();
        if (now != null && !now.equals(target)) selected.clear();
        if (now != null) target = now;
        buttons.clear();
        int right = screen.width - 6;
        buttons.add(StyledButton.of(Component.translatable("editor.historystages.slots.done"),
                b -> finish(true), right - BUTTON_W * 2 - 4, 3, BUTTON_W, BUTTON_H));
        buttons.add(StyledButton.of(Component.translatable("editor.historystages.slots.cancel"),
                b -> finish(false), right - BUTTON_W, 3, BUTTON_W, BUTTON_H));
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!active || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        GuiGraphicsExtractor g = event.getGuiGraphics();
        // The packet naming the block can land after the screen opened.
        String now = ((SlotLockMenu) screen.getMenu()).historystages$slotTarget();
        if (now != null && !now.equals(target)) {
            target = now;
            selected.clear();
        }

        g.pose().pushMatrix();
        g.pose().translate(0, 0);
        for (Slot slot : screen.getMenu().slots) {
            if (!selectable(slot)) continue;
            int x = net.bananemdnsa.historystages.client.slotlock.ContainerScreens.left(screen) + slot.x;
            int y = net.bananemdnsa.historystages.client.slotlock.ContainerScreens.top(screen) + slot.y;
            if (selected.contains(slot.index)) {
                g.pose().pushMatrix();
                g.pose().translate(ContainerScreens.left(screen), ContainerScreens.top(screen));
                SlotLockClient.drawOverlay(g, slot.x, slot.y);
                g.pose().popMatrix();
            } else {
                g.outline(x - 1, y - 1, 18, 18, SELECTABLE_OUTLINE);
            }
        }
        drawBanner(g, Minecraft.getInstance().font, screen.width, banner(), event.getMouseX(), event.getMouseY(),
                event.getPartialTick(), true);
        g.pose().popMatrix();
    }

    @SubscribeEvent
    public static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!active || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        for (StyledButton button : buttons) {
            if (button.isMouseOver(event.getMouseX(), event.getMouseY())) {
                button.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(event.getMouseX(), event.getMouseY(),
                        new net.minecraft.client.input.MouseButtonInfo(event.getButton(), 0)), false);
                event.setCanceled(true);
                return;
            }
        }
        Slot slot = net.bananemdnsa.historystages.client.slotlock.ContainerScreens.slotUnderMouse(screen);
        // Every click is swallowed while picking, so nothing moves even on the player's half.
        event.setCanceled(true);
        if (slot == null || !selectable(slot)) return;
        if (!selected.remove(slot.index)) selected.add(slot.index);
    }

    /**
     * Number keys, Q and F would move items, which the server refuses anyway; swallowing them here
     * keeps the screen from showing a move that snaps back. Escape and the inventory key still close.
     */
    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!active || !(event.getScreen() instanceof AbstractContainerScreen<?>)) return;
        if (event.getKeyCode() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return;
        if (Minecraft.getInstance().options.keyInventory.matches(new net.minecraft.client.input.KeyEvent(
                event.getKeyCode(), event.getScanCode(), event.getModifiers()))) return;
        event.setCanceled(true);
    }

    /** Escape from the world while picking means "never mind", not the pause menu. */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (active && event.getNewScreen() instanceof PauseScreen) {
            event.setCanceled(true);
            finish(false);
        }
    }

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        if (!active || Minecraft.getInstance().gui.screen() != null) return;
        GuiGraphicsExtractor g = event.getGuiGraphics();
        drawBanner(g, Minecraft.getInstance().font, g.guiWidth(),
                Component.translatable("editor.historystages.slots.world_pick.hint").getString(), -1, -1, 0, false);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        returnTo = null;
        onDone = null;
        selected.clear();
    }

    private static boolean selectable(Slot slot) {
        return !(slot.container instanceof Inventory);
    }

    private static String banner() {
        String name = target == null ? "?" : net.bananemdnsa.historystages.client.editor.tab.SlotCategoryTab.targetName(target);
        return Component.translatable("editor.historystages.slots.world_pick.banner", name, selected.size()).getString();
    }

    private static void drawBanner(GuiGraphicsExtractor g, Font font, int width, String text, int mouseX, int mouseY,
                                   float partialTick, boolean withButtons) {
        g.fill(0, 0, width, BANNER_H, BANNER_BG);
        g.fill(0, 0, width, 1, ACCENT);
        g.fill(0, BANNER_H - 1, width, BANNER_H, BANNER_EDGE);
        g.text(font, text, 8, (BANNER_H - font.lineHeight) / 2 + 1, 0xFFFFFFFF);
        if (withButtons) {
            for (StyledButton button : buttons) button.extractRenderState(g, mouseX, mouseY, partialTick);
        }
    }
}
