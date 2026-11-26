package net.bananemdnsa.historystages.client.slotlock;

import net.bananemdnsa.historystages.data.lock.SlotLockEntry;
import net.bananemdnsa.historystages.mixin.client.slotlock.AbstractContainerScreenInvoker;
import net.bananemdnsa.historystages.mixin.client.slotlock.MenuScreensInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
        void render(GuiGraphics g, int mouseX, int mouseY, float partialTick);
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
            screen.init(Minecraft.getInstance(), width, height);
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
        screen.init(mc, width, height);
        return new ScreenView(screen);
    }

    private static AbstractContainerMenu menuFor(String target, LocalPlayer player) {
        ResourceLocation id = ResourceLocation.tryParse(target);
        if (id == null) return null;
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            return net.bananemdnsa.historystages.util.lock.SlotTargets.previewMenu(BuiltInRegistries.BLOCK.get(id), player);
        }
        if (BuiltInRegistries.MENU.containsKey(id)) {
            MenuType<?> type = BuiltInRegistries.MENU.get(id);
            // A menu that needs opening data has nothing to build from here; the in-world pick covers it.
            if (type instanceof net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<?, ?>) return null;
            return type.create(PREVIEW_CONTAINER_ID, player.getInventory());
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static AbstractContainerScreen<?> screenFor(AbstractContainerMenu menu, LocalPlayer player) {
        MenuScreens.ScreenConstructor constructor = MenuScreensInvoker.historystages$getConstructor((MenuType) menu.getType());
        if (constructor == null) return null;
        Object screen = constructor.create(menu, player.getInventory(), Component.empty());
        return screen instanceof AbstractContainerScreen<?> containerScreen ? containerScreen : null;
    }

    /** Someone else's screen: only its background and labels are drawn, never its widgets. */
    private record ScreenView(AbstractContainerScreen<?> screen) implements View {
        @Override public int left() { return ((net.bananemdnsa.historystages.mixin.client.ContainerScreenAccessor) screen).historystages$getLeftPos(); }
        @Override public int top() { return ((net.bananemdnsa.historystages.mixin.client.ContainerScreenAccessor) screen).historystages$getTopPos(); }
        @Override public int imageWidth() { return ((net.bananemdnsa.historystages.mixin.client.ContainerScreenAccessor) screen).historystages$getImageWidth(); }
        @Override public int imageHeight() { return ((net.bananemdnsa.historystages.mixin.client.ContainerScreenAccessor) screen).historystages$getImageHeight(); }
        @Override public List<Slot> slots() { return screen.getMenu().slots; }
        @Override public boolean vanillaLook() { return screen.getClass().getName().startsWith("net.minecraft."); }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            ((AbstractContainerScreenInvoker) screen).historystages$renderBg(g, partialTick, mouseX, mouseY);
            g.pose().pushPose();
            g.pose().translate(left(), top(), 0);
            ((AbstractContainerScreenInvoker) screen).historystages$renderLabels(g, mouseX, mouseY);
            g.pose().popPose();
        }
    }

    /**
     * The player's inventory, drawn by hand. InventoryScreen itself cannot be used: in creative
     * its init switches to the creative screen, which would take the editor with it.
     */
    private static final class PlayerView implements View {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
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
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.blit(TEXTURE, left, top, 0, 0, W, H);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, left + 26, top + 8, left + 75, top + 78,
                    30, 0.0625F, mouseX, mouseY, player);
        }
    }
}
