package net.bananemdnsa.historystages.client.editor.widget.popup;

import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.data.NbtFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Picks which wood types of a boat (or chest boat) a lock covers. Writes the row's NBT filter as
 * one {@code {"Type": "…"}} alternative per marked wood; nothing marked means no filter, so every
 * boat is locked again.
 *
 * <p>Looks like {@link LockActionsPopup} on purpose — same frame, toggles and footer — with the
 * boat item drawn in front of each toggle instead of a dot.
 *
 * <p>1.21.1 only: here the wood is a variant of one entity type. From 1.21.2 on every wood is its
 * own entity type and this popup has nothing to do.
 */
public class WoodTypesPopup {

    private static final int PAD        = 8;
    private static final int MIN_WIDTH  = 260;
    private static final int COLS       = 3;
    private static final int HEADER_H   = 18;
    private static final int HINT_H     = 12;
    private static final int TOGGLE_H   = 20;
    private static final int TOGGLE_GAP = 2;
    private static final int STATUS_H   = 12;
    private static final int FOOTER_H   = 22;
    private static final int BTN_H      = 14;

    /** Vanilla entity ids that carry a {@code Type} wood variant. */
    public static boolean supports(String entityId) {
        return "minecraft:boat".equals(entityId) || "minecraft:chest_boat".equals(entityId);
    }

    private record Wood(String name, ItemStack icon) {}

    private boolean visible = false;
    private String entityId = "";
    private final List<Wood> woods = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private Consumer<NbtFilter> onDone = f -> {};
    private int cachedX, cachedY, cachedW, cachedH;

    public boolean isVisible() { return visible; }

    public void hide() { visible = false; }

    /**
     * Opens the popup for one boat row. Marks the woods the current filter names; criteria that
     * are not a plain wood choice are not shown and get replaced on Done.
     */
    public void show(String entityId, NbtFilter current, Consumer<NbtFilter> onDone) {
        this.entityId = entityId;
        this.onDone = onDone;
        this.woods.clear();
        for (Boat.Type type : Boat.Type.values()) {
            woods.add(new Wood(type.getSerializedName(), iconFor(entityId, type)));
        }
        selected.clear();
        if (current != null) {
            for (JsonObject alt : current.alternatives()) {
                if (alt.size() == 1 && alt.has("Type") && alt.get("Type").isJsonPrimitive()) {
                    selected.add(alt.get("Type").getAsString());
                }
            }
        }
        cachedW = 0;
        visible = true;
    }

    /** The boat item for this wood, taken from a boat of that wood so chest boats get theirs. */
    private static ItemStack iconFor(String entityId, Boat.Type type) {
        var level = Minecraft.getInstance().level;
        if (level == null) return ItemStack.EMPTY;
        try {
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(entityId));
            Entity entity = entityType.create(level);
            if (!(entity instanceof Boat boat)) return ItemStack.EMPTY;
            boat.setVariant(type);
            ItemStack pick = boat.getPickResult();
            boat.discard();
            return pick != null ? pick : ItemStack.EMPTY;
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    private Component labelOf(Wood wood) {
        return wood.icon().isEmpty() ? Component.literal(wood.name()) : wood.icon().getHoverName();
    }

    private NbtFilter result() {
        List<JsonObject> alts = new ArrayList<>();
        for (Wood wood : woods) {
            if (!selected.contains(wood.name())) continue;
            JsonObject alt = new JsonObject();
            alt.addProperty("Type", wood.name());
            alts.add(alt);
        }
        return NbtFilter.of(alts);
    }

    public boolean keyPressed(int keyCode) {
        if (!visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            visible = false;
            return true;
        }
        return false;
    }

    /** Consumes every click while visible. Clicking outside closes without saving. */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        if (cachedW == 0 || button != 0) return true;
        Font font = Minecraft.getInstance().font;
        int btnY = cachedY + cachedH - BTN_H - 6;

        int doneW = doneBtnWidth(font);
        if (inside(mouseX, mouseY, cachedX + cachedW - doneW - PAD, btnY, doneW, BTN_H)) {
            click();
            visible = false;
            onDone.accept(result());
            return true;
        }
        int qBtnW = quickBtnWidth(font);
        int allX = cachedX + PAD;
        if (inside(mouseX, mouseY, allX, btnY, qBtnW, BTN_H)) {
            click();
            for (Wood wood : woods) selected.add(wood.name());
            return true;
        }
        if (inside(mouseX, mouseY, allX + qBtnW + 3, btnY, qBtnW, BTN_H)) {
            click();
            selected.clear();
            return true;
        }

        int toggleW = toggleWidth(cachedW);
        int gridY = cachedY + HEADER_H + HINT_H + 3;
        for (int i = 0; i < woods.size(); i++) {
            int tx = cachedX + PAD + (i % COLS) * (toggleW + 3);
            int ty = gridY + (i / COLS) * (TOGGLE_H + TOGGLE_GAP);
            if (inside(mouseX, mouseY, tx, ty, toggleW, TOGGLE_H)) {
                click();
                String name = woods.get(i).name();
                if (!selected.remove(name)) selected.add(name);
                return true;
            }
        }

        if (!inside(mouseX, mouseY, cachedX, cachedY, cachedW, cachedH)) visible = false;
        return true;
    }

    public void render(GuiGraphics g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        if (!visible) return;
        int rows = (woods.size() + COLS - 1) / COLS;
        int gridH = rows * TOGGLE_H + (rows - 1) * TOGGLE_GAP;
        int popupW = popupWidth(font, screenW);
        int popupH = HEADER_H + HINT_H + 3 + gridH + 4 + STATUS_H + FOOTER_H;
        int popupX = screenW / 2 - popupW / 2;
        int popupY = screenH / 2 - popupH / 2;
        cachedX = popupX;
        cachedY = popupY;
        cachedW = popupW;
        cachedH = popupH;

        g.fill(0, 0, screenW, screenH, 0x88000000);
        g.fill(popupX + 3, popupY + 3, popupX + popupW + 3, popupY + popupH + 3, 0x50000000);
        g.fill(popupX - 1, popupY - 1, popupX + popupW + 1, popupY + popupH + 1, 0xFF333333);
        g.fill(popupX, popupY, popupX + popupW, popupY + popupH, 0xFF1A1A1A);

        g.drawCenteredString(font, Component.translatable("editor.historystages.wood_types.title"),
                popupX + popupW / 2, popupY + 5, 0xFFFFFFFF);
        int accentX = popupX + (popupW - 40) / 2;
        g.fill(accentX, popupY + 15, accentX + 40, popupY + 16, 0xFFFFCC00);
        g.drawCenteredString(font, Component.translatable("editor.historystages.wood_types.hint", entityId),
                popupX + popupW / 2, popupY + HEADER_H, 0x888888);

        int toggleW = toggleWidth(popupW);
        int gridY = popupY + HEADER_H + HINT_H + 3;
        for (int i = 0; i < woods.size(); i++) {
            Wood wood = woods.get(i);
            int tx = popupX + PAD + (i % COLS) * (toggleW + 3);
            int ty = gridY + (i / COLS) * (TOGGLE_H + TOGGLE_GAP);
            boolean on = selected.contains(wood.name());
            boolean hov = inside(mouseX, mouseY, tx, ty, toggleW, TOGGLE_H);
            g.fill(tx, ty, tx + toggleW, ty + TOGGLE_H,
                    on ? (hov ? 0x40FFCC00 : 0x25FFCC00) : (hov ? 0x25FFFFFF : 0x10FFFFFF));
            g.fill(tx, ty + TOGGLE_H - 1, tx + toggleW, ty + TOGGLE_H,
                    on ? (hov ? 0xFFFFCC00 : 0xB0FFCC00) : (hov ? 0x40FFFFFF : 0x20FFFFFF));
            if (!wood.icon().isEmpty()) g.renderItem(wood.icon(), tx + 2, ty + 2);
            String label = font.plainSubstrByWidth(labelOf(wood).getString(), toggleW - 24);
            g.drawString(font, label, tx + 21, ty + 6, on ? 0xFFFFFF : 0x999999, false);
        }

        int statusY = gridY + gridH + 4;
        g.fill(popupX + PAD, statusY - 1, popupX + popupW - PAD, statusY, 0xFF2E2E2E);
        Component status = selected.isEmpty()
                ? Component.translatable("editor.historystages.wood_types.status_none")
                : Component.translatable("editor.historystages.wood_types.status", selected.size(), woods.size());
        g.drawCenteredString(font, status, popupX + popupW / 2, statusY + 2, 0x888888);

        int btnY = popupY + popupH - BTN_H - 6;
        int qBtnW = quickBtnWidth(font);
        drawQuickButton(g, font, popupX + PAD, btnY, qBtnW, "editor.historystages.lock_actions.btn_all", mouseX, mouseY);
        drawQuickButton(g, font, popupX + PAD + qBtnW + 3, btnY, qBtnW,
                "editor.historystages.lock_actions.btn_none", mouseX, mouseY);
        int doneW = doneBtnWidth(font);
        int doneX = popupX + popupW - doneW - PAD;
        boolean doneHov = inside(mouseX, mouseY, doneX, btnY, doneW, BTN_H);
        g.fill(doneX, btnY, doneX + doneW, btnY + BTN_H, doneHov ? 0x50FFCC00 : 0x25FFCC00);
        g.fill(doneX, btnY + BTN_H - 1, doneX + doneW, btnY + BTN_H, doneHov ? 0xFFFFCC00 : 0x80FFCC00);
        g.drawCenteredString(font, Component.translatable("editor.historystages.lock_actions.btn_done"),
                doneX + doneW / 2, btnY + 3, doneHov ? 0xFFFFFF : 0xEEEEEE);
    }

    private int popupWidth(Font font, int screenW) {
        int maxLabel = 0;
        for (Wood wood : woods) maxLabel = Math.max(maxLabel, font.width(labelOf(wood)));
        int fromGrid = 2 * PAD + COLS * (maxLabel + 26) + (COLS - 1) * 3;
        int fromLines = 2 * PAD + Math.max(
                font.width(Component.translatable("editor.historystages.wood_types.hint", entityId)),
                font.width(Component.translatable("editor.historystages.wood_types.status_none")));
        int fromFooter = 2 * PAD + 2 * quickBtnWidth(font) + 3 + 8 + doneBtnWidth(font);
        int needed = Math.max(MIN_WIDTH, Math.max(fromGrid, Math.max(fromLines, fromFooter)));
        return Math.min(needed, screenW - 8);
    }

    private static int toggleWidth(int popupW) {
        return (popupW - 2 * PAD - (COLS - 1) * 3) / COLS;
    }

    private static void drawQuickButton(GuiGraphics g, Font font, int x, int y, int w, String key,
                                        int mouseX, int mouseY) {
        boolean hov = inside(mouseX, mouseY, x, y, w, BTN_H);
        g.fill(x, y, x + w, y + BTN_H, hov ? 0x25FFFFFF : 0x10FFFFFF);
        g.fill(x, y + BTN_H - 1, x + w, y + BTN_H, hov ? 0x80FFFFFF : 0x40FFFFFF);
        g.drawCenteredString(font, Component.translatable(key), x + w / 2, y + 3, hov ? 0xFFFFFF : 0xCCCCCC);
    }

    private static int quickBtnWidth(Font font) {
        int w = font.width(Component.translatable("editor.historystages.lock_actions.btn_all"));
        w = Math.max(w, font.width(Component.translatable("editor.historystages.lock_actions.btn_none")));
        return Math.max(34, w + 10);
    }

    private static int doneBtnWidth(Font font) {
        return Math.max(48, font.width(Component.translatable("editor.historystages.lock_actions.btn_done")) + 10);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
