package net.bananemdnsa.historystages.client.slotlock;

import net.bananemdnsa.historystages.data.lock.SlotLockEntry;
import net.bananemdnsa.historystages.mixin.client.slotlock.AbstractContainerScreenInvoker;
import net.bananemdnsa.historystages.mixin.client.slotlock.MenuScreensInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.Optional;

/** Builds the real GUI of a slot-lock target for the picker to draw, behind a {@link PreviewGuard}. */
public final class PreviewMenus {

    /** One per session: a GUI that cannot be built here will not start working later. */
    public static final PreviewGuard GUARD = new PreviewGuard();

    /** Never sent to a server; the preview only needs the slot layout and the background. */
    private static final int PREVIEW_CONTAINER_ID = -100;

    private PreviewMenus() {}

    /** What the picker draws and clicks on, in the GUI's own coordinates. */
    public interface View {
        int left();
        int top();
        int imageWidth();
        int imageHeight();
        List<Slot> slots();
        /** Whether hidden slots may be painted with the vanilla panel colour. */
        boolean vanillaLook();
        void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick);
    }

    private static boolean previewing;

    /** True while a preview draws a container's background; see PreviewBackgroundMixin. */
    public static boolean drawingPreview() {
        return previewing;
    }

    public static Optional<View> build(String target, int width, int height) {
        return GUARD.attempt(target, () -> create(target, width, height));
    }

    /** Chests that join into a double chest: their slot locks count across all 54 slots. */
    public static boolean isChestLike(String target) {
        return "minecraft:chest".equals(target) || "minecraft:trapped_chest".equals(target);
    }

    /**
     * The double chest. A placed double chest is a block pair a fresh block entity cannot form,
     * so the six-row menu it opens is built directly; the slot numbers are the same either way.
     */
    public static Optional<View> buildLarge(String target, int width, int height) {
        return GUARD.attempt(target + "#large", () -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return null;
            var menu = net.minecraft.world.inventory.ChestMenu.sixRows(PREVIEW_CONTAINER_ID, player.getInventory());
            var screen = new net.minecraft.client.gui.screens.inventory.ContainerScreen(menu, player.getInventory(),
                    Component.translatable("container.chestDouble"));
            screen.init(width, height);
            return new ScreenView(screen);
        });
    }

    private static View create(String target, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return null;
        if (SlotLockEntry.PLAYER.equals(target)) return new PlayerView(player, width, height);

        AbstractContainerMenu menu = menuFor(target, player);
        if (menu == null) return null;
        AbstractContainerScreen<?> screen = screenFor(menu, player);
        if (screen == null) return null;
        screen.init(width, height);
        return new ScreenView(screen);
    }

    private static AbstractContainerMenu menuFor(String target, LocalPlayer player) {
        Identifier id = Identifier.tryParse(target);
        if (id == null) return null;
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            return net.bananemdnsa.historystages.util.lock.SlotTargets.previewMenu(BuiltInRegistries.BLOCK.getValue(id), player);
        }
        if (BuiltInRegistries.MENU.containsKey(id)) {
            MenuType<?> type = BuiltInRegistries.MENU.getValue(id);
            // Menus that need opening data cannot be built without it; the in-world pick covers them.
            try {
                return type.create(PREVIEW_CONTAINER_ID, player.getInventory());
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static AbstractContainerScreen<?> screenFor(AbstractContainerMenu menu, LocalPlayer player) {
        Object constructor = MenuScreensInvoker.historystages$screens().get(menu.getType());
        if (constructor == null) return null;
        Object screen;
        try {
            // MenuScreens.ScreenConstructor is private since 26.x; its one method is called by name.
            java.lang.reflect.Method create = Class.forName("net.minecraft.client.gui.screens.MenuScreens$ScreenConstructor")
                    .getDeclaredMethod("create", AbstractContainerMenu.class, net.minecraft.world.entity.player.Inventory.class,
                            Component.class);
            create.setAccessible(true);
            screen = create.invoke(constructor, menu, player.getInventory(), Component.empty());
        } catch (ReflectiveOperationException e) {
            return null;
        }
        return screen instanceof AbstractContainerScreen<?> containerScreen ? containerScreen : null;
    }

    /** Someone else's screen: only its background and labels are drawn, never its widgets. */
    private record ScreenView(AbstractContainerScreen<?> screen) implements View {
        @Override public int left() { return ContainerScreens.left(screen); }
        @Override public int top() { return ContainerScreens.top(screen); }
        @Override public int imageWidth() { return ContainerScreens.imageWidth(screen); }
        @Override public int imageHeight() { return ContainerScreens.imageHeight(screen); }
        @Override public List<Slot> slots() { return screen.getMenu().slots; }
        @Override public boolean vanillaLook() { return screen.getClass().getName().startsWith("net.minecraft."); }

        @Override
        public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            previewing = true;
            try {
                screen.extractBackground(g, mouseX, mouseY, partialTick);
            } finally {
                previewing = false;
            }
            g.pose().pushMatrix();
            g.pose().translate(left(), top());
            ((AbstractContainerScreenInvoker) screen).historystages$extractLabels(g, mouseX, mouseY);
            g.pose().popMatrix();
        }
    }

    /**
     * The player's inventory, drawn by hand. InventoryScreen itself cannot be used: in creative
     * its init switches to the creative screen, which would take the editor with it.
     */
    private static final class PlayerView implements View {
        private static final Identifier TEXTURE =
                Identifier.withDefaultNamespace("textures/gui/container/inventory.png");
        private static final int W = 176;
        private static final int H = 166;

        private final LocalPlayer player;
        private final int left;
        private final int top;

        PlayerView(LocalPlayer player, int width, int height) {
            this.player = player;
            this.left = (width - W) / 2;
            this.top = (height - H) / 2;
        }

        @Override public int left() { return left; }
        @Override public int top() { return top; }
        @Override public int imageWidth() { return W; }
        @Override public int imageHeight() { return H; }
        @Override public List<Slot> slots() { return player.inventoryMenu.slots; }
        @Override public boolean vanillaLook() { return true; }

        @Override
        public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            net.bananemdnsa.historystages.client.LegacyGui.blit(g, TEXTURE, left, top, 0, 0, W, H, 256, 256);
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, left + 26, top + 8, left + 75, top + 78,
                    30, 0.0625F, mouseX, mouseY, player);
        }
    }
}
