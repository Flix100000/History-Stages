package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.api.editor.widget.NumberStepper;
import net.bananemdnsa.historystages.api.editor.widget.SegmentBar;
import net.bananemdnsa.historystages.client.editor.dialog.ColorInputScreen;
import net.bananemdnsa.historystages.client.editor.tab.SlotCategoryTab;
import net.bananemdnsa.historystages.client.editor.widget.EditorTooltip;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableItemList;
import net.bananemdnsa.historystages.client.slotlock.ClientSlotPick;
import net.bananemdnsa.historystages.client.slotlock.PreviewMenus;
import net.bananemdnsa.historystages.client.slotlock.SlotLockClient;
import net.bananemdnsa.historystages.data.lock.SlotLockEntry;
import net.bananemdnsa.historystages.data.lock.SlotLockMode;
import net.bananemdnsa.historystages.data.lock.SlotLook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Picks the slots of one slot lock on the real GUI: the GUI on the left, a side panel with two
 * pages on the right — the slots themselves, and how they look once locked. Click or drag over
 * slots to toggle them.
 */
public class SlotPickerScreen extends Screen {

    private static final int MARGIN = 10;
    private static final int TITLE_Y = 6;
    private static final int HAIRLINE_Y = 19;
    private static final int TOP = 26;
    private static final int PANEL_W = 176;
    private static final int FOOTER_H = 28;
    private static final int BUTTON_H = 20;
    private static final int FIELD_H = 16;
    private static final int LARGE_CHEST_FIRST_SLOT = 27;

    private static final int BACKDROP = 0xE0101010;
    private static final int HAIRLINE = 0x40FFFFFF;
    private static final int SEPARATOR = 0xFF555555;
    private static final int PANEL_BODY = 0xFF1A1A1A;
    private static final int PANEL_BORDER = 0xFF333333;
    private static final int ACCENT = 0xFFFFCC00;
    private static final int LABEL_GREY = 0xFF999999;
    private static final int FIELD_BG = 0xFF0D0D0D;
    private static final int FIELD_BORDER = 0xFF4A4A4A;
    private static final int VANILLA_PANEL = 0xFFC6C6C6;
    private static final int DIM = 0xA0101010;
    private static final int SELECTED_TINT = 0x90C02020;

    private enum Page { SLOTS, LOOK }

    private final Screen parent;
    private final SlotLockEntry entry;
    private final Consumer<SlotLockEntry> onDone;
    private final TreeSet<Integer> selected;
    private SlotLockMode mode;
    private final SlotLook look;
    private String hint;
    private boolean preview;
    private Page page = Page.SLOTS;
    private boolean large;

    @Nullable private PreviewMenus.View view;
    private boolean failed;
    private float scale = 1f;
    private float offsetX;
    private float offsetY;

    /** While dragging: true adds slots, false removes them, null when not dragging. */
    @Nullable private Boolean dragAdds;
    @Nullable private String tooltip;

    /** Segment bars of the current page, rebuilt with the widgets. */
    private final List<Segments> segments = new ArrayList<>();
    @Nullable private NumberStepper opacityStepper;
    private static final int SWATCH = 12;
    private int swatchX = -1;
    private int swatchY = -1;
    @Nullable private EditBox hintBox;
    @Nullable private net.bananemdnsa.historystages.api.editor.widget.PickerOverlay itemPicker;

    private record Segments(int x, int y, List<String> labels, SegmentBar.State state,
                            java.util.function.IntSupplier selected, IntConsumer onPick,
                            @Nullable List<String> tooltips) {}

    public SlotPickerScreen(Screen parent, SlotLockEntry entry, Consumer<SlotLockEntry> onDone) {
        super(Component.translatable("editor.historystages.slots.picker.title",
                SlotCategoryTab.targetName(entry.target())));
        this.parent = parent;
        this.entry = entry;
        this.onDone = onDone;
        this.selected = new TreeSet<>(entry.slots());
        this.mode = entry.mode();
        this.look = entry.look().copy();
        this.hint = entry.hint();
        // A lock that already reaches into the lower half was made on the large chest.
        this.large = PreviewMenus.isChestLike(entry.target())
                && !selected.isEmpty() && selected.last() >= LARGE_CHEST_FIRST_SLOT;
    }

    private boolean player() {
        return entry.isPlayerTarget();
    }

    // ---- layout ------------------------------------------------------------------------

    private int panelX() { return this.width - MARGIN - PANEL_W; }
    private int areaX() { return MARGIN; }
    private int areaW() { return panelX() - MARGIN - areaX(); }
    private int areaH() { return contentBottom() - TOP; }
    private int contentBottom() { return this.height - FOOTER_H - 4; }

    @Override
    protected void init() {
        Optional<PreviewMenus.View> built = large
                ? PreviewMenus.buildLarge(entry.target(), this.width, this.height)
                : PreviewMenus.build(entry.target(), this.width, this.height);
        view = built.orElse(null);
        failed = view == null;
        layoutView();

        segments.clear();
        opacityStepper = null;
        swatchX = -1;
        fullLabels.clear();
        hintBox = null;

        int px = panelX() + 8;
        int y = TOP + 32;
        if (page == Page.SLOTS) initSlotsPage(px, y); else initLookPage(px, y);

        int footerY = this.height - FOOTER_H + 4;
        int fw = 80;
        int fx = this.width / 2 - (fw * 3 + 8) / 2;
        addRenderableWidget(StyledButton.of(Component.translatable("editor.historystages.slots.done"),
                b -> done(), fx, footerY, fw, BUTTON_H));
        addRenderableWidget(StyledButton.of(Component.translatable("editor.historystages.slots.cancel"),
                b -> onClose(), fx + fw + 4, footerY, fw, BUTTON_H));
        addRenderableWidget(StyledButton.of(Component.translatable(preview
                        ? "editor.historystages.slots.preview.on" : "editor.historystages.slots.preview.off"),
                b -> { preview = !preview; rebuildWidgets(); }, fx + (fw + 4) * 2, footerY, fw, BUTTON_H));

        if (failed && !player()) {
            int bx = areaX() + areaW() / 2 - 70;
            addRenderableWidget(StyledButton.of(Component.translatable("editor.historystages.slots.pick_in_world"),
                    b -> pickInWorld(), bx, TOP + areaH() / 2 + 10, 140, BUTTON_H));
        }
    }

    private void initSlotsPage(int px, int y) {
        if (PreviewMenus.isChestLike(entry.target())) {
            segments.add(new Segments(px, y + 11,
                    List.of(text("editor.historystages.slots.picker.size.small"), text("editor.historystages.slots.picker.size.large")),
                    new SegmentBar.State(), () -> large ? 1 : 0, i -> { large = i == 1; rebuildWidgets(); }, null));
            y += 36;
        }
        segments.add(new Segments(px, y + 11, modeLabels(), new SegmentBar.State(),
                () -> mode == SlotLockMode.HIDE ? 0 : 1,
                i -> mode = i == 0 ? SlotLockMode.HIDE : SlotLockMode.OVERLAY,
                List.of(text("editor.historystages.slots.mode.hide.tooltip"),
                        text("editor.historystages.slots.mode.overlay.tooltip"))));

        int bw = (PANEL_W - 20) / 2;
        int quickY = contentBottom() - 8 - BUTTON_H * 2 - 4;
        if (player()) {
            addQuick("editor.historystages.slots.quick.hotbar", px, quickY, bw, () -> toggleGroup(0, 8));
            addQuick("editor.historystages.slots.quick.inventory", px + bw + 4, quickY, bw, () -> toggleGroup(9, 35));
            addQuick("editor.historystages.slots.quick.armor", px, quickY + 24, bw, () -> toggleGroup(36, 39));
            addQuick("editor.historystages.slots.quick.none", px + bw + 4, quickY + 24, bw, selected::clear);
        } else {
            addQuick("editor.historystages.slots.quick.all", px, quickY, bw, this::selectAll);
            addQuick("editor.historystages.slots.quick.none", px + bw + 4, quickY, bw, selected::clear);
            addQuick("editor.historystages.slots.quick.invert", px, quickY + 24, bw, this::invert);
        }
    }

    private void initLookPage(int px, int y) {
        int fieldW = PANEL_W - 16;
        int labelW = 58;
        int controlX = px + labelW;
        int controlW = fieldW - labelW;
        if (mode == SlotLockMode.OVERLAY) {
            // A swatch like the config rows' colour control; the dialog behind it does the rest.
            swatchX = controlX;
            swatchY = y + 2;
            y += FIELD_H + 4;

            opacityStepper = new NumberStepper(0, 100, 5, look.opacity(), look::setOpacity);
            opacityStepper.setPosition(controlX, y);
            y += FIELD_H + 4;

            y += 11;
            segments.add(new Segments(px, y, List.of(text("editor.historystages.slots.look.icon.lock"),
                    text("editor.historystages.slots.look.icon.item"), text("editor.historystages.slots.look.icon.none")),
                    new SegmentBar.State(), () -> look.icon().ordinal(),
                    i -> { look.setIcon(SlotLook.Icon.values()[i]); rebuildWidgets(); }, null));
            y += SegmentBar.height() + 4;

            if (look.icon() == SlotLook.Icon.ITEM) {
                addRenderableWidget(fitted(itemLabel(), b -> openItemPicker(), controlX, y, controlW));
                y += FIELD_H + 4;
            }

            y += 11;
            segments.add(new Segments(px, y, List.of(text("editor.historystages.slots.look.size.small"),
                    text("editor.historystages.slots.look.size.medium"), text("editor.historystages.slots.look.size.full")),
                    new SegmentBar.State(), () -> look.size().ordinal(),
                    i -> look.setSize(SlotLook.Size.values()[i]), null));
            y += SegmentBar.height() + 4;

            boolean hasTexture = !look.texture().isEmpty();
            int textureW = hasTexture ? controlW - FIELD_H - 2 : controlW;
            addRenderableWidget(fitted(textureLabel(), b -> openTexturePicker(), controlX, y, textureW));
            if (hasTexture) {
                addRenderableWidget(StyledButton.of(Component.literal("✕"),
                        b -> { look.setTexture(""); rebuildWidgets(); }, controlX + textureW + 2, y, FIELD_H, FIELD_H));
            }
            y += FIELD_H + 8;

            hintBox = field(px + 4, y + 11, fieldW - 8, hint, v -> hint = v);
            y += FIELD_H + 18;

            addRenderableWidget(StyledButton.of(Component.translatable("editor.historystages.slots.look.reset"),
                    b -> resetLook(), px, y, 70, BUTTON_H));
        }
    }

    /**
     * A button whose label is cut to fit, with the whole of it as a tooltip; texture and item
     * names easily run wider than the panel.
     */
    private StyledButton fitted(String label, StyledButton.OnPress onPress, int x, int y, int w) {
        int room = w - 8;
        String shown = label;
        if (this.font.width(label) > room) {
            shown = this.font.plainSubstrByWidth(label, room - this.font.width("…")) + "…";
        }
        StyledButton button = StyledButton.of(Component.literal(shown), onPress, x, y, w, FIELD_H);
        if (!shown.equals(label)) fullLabels.put(button, label);
        return button;
    }

    private final java.util.Map<StyledButton, String> fullLabels = new java.util.HashMap<>();

    private EditBox field(int x, int y, int w, String value, Consumer<String> onChange) {
        EditBox box = new EditBox(this.font, x, y + (FIELD_H - 8) / 2, w, 8, Component.empty());
        box.setBordered(false);
        box.setMaxLength(160);
        box.setValue(value);
        box.setResponder(onChange);
        addRenderableWidget(box);
        return box;
    }

    private void addQuick(String key, int x, int y, int w, Runnable action) {
        addRenderableWidget(StyledButton.of(Component.translatable(key), b -> action.run(), x, y, w, BUTTON_H));
    }

    private String text(String key) {
        return Component.translatable(key).getString();
    }

    /** Fits the GUI into the left area, never larger than its own size. */
    private void layoutView() {
        if (view == null) return;
        int availW = areaW() - 8;
        int availH = areaH() - 8;
        scale = Math.min(1f, Math.min(availW / (float) view.imageWidth(), availH / (float) view.imageHeight()));
        float drawnW = view.imageWidth() * scale;
        float drawnH = view.imageHeight() * scale;
        offsetX = areaX() + (areaW() - drawnW) / 2f - view.left() * scale;
        offsetY = TOP + (areaH() - drawnH) / 2f - view.top() * scale;
    }

    // ---- look actions --------------------------------------------------------------------

    private void pickColour() {
        this.minecraft.setScreen(new ColorInputScreen(this, Component.translatable("editor.historystages.slots.look.color"),
                look.color(), SlotLook.DEFAULT_COLOR, picked -> look.setColor(picked)));
    }

    private void openItemPicker() {
        SearchableItemList list = new SearchableItemList(id -> {
            look.setItem(id);
            itemPicker.hide();
            rebuildWidgets();
        });
        itemPicker = list;
        list.show(this.width / 2, this.height / 2, this.width);
    }

    /** Block and item textures: both live in the block atlas, so rows and the overlay draw cheaply. */
    private void openTexturePicker() {
        var list = new net.bananemdnsa.historystages.client.editor.widget.list.SearchableTextureList(path -> {
            look.setTexture(path);
            itemPicker.hide();
            rebuildWidgets();
        }, List.of("textures/block", "textures/item"),
                List.of(text("editor.historystages.slots.look.texture.tab.blocks"),
                        text("editor.historystages.slots.look.texture.tab.items")));
        itemPicker = list;
        list.show(this.width / 2, this.height / 2, this.width);
    }

    private String textureLabel() {
        if (look.texture().isEmpty()) return text("editor.historystages.slots.look.texture.pick");
        return look.texture().replaceFirst("^[a-z0-9_.-]+:textures/[a-z_]+/", "").replace(".png", "");
    }

    private String itemLabel() {
        ResourceLocation id = ResourceLocation.tryParse(look.item());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return text("editor.historystages.slots.look.item.pick");
        }
        return BuiltInRegistries.ITEM.get(id).getDescription().getString();
    }

    private void resetLook() {
        SlotLook fresh = new SlotLook();
        look.setColor(fresh.color());
        look.setOpacity(fresh.opacity());
        look.setIcon(fresh.icon());
        look.setItem("");
        look.setSize(fresh.size());
        look.setTexture("");
        rebuildWidgets();
    }

    // ---- selection ---------------------------------------------------------------------

    /** The slot-lock index of a menu slot, or -1 when it does not belong to this target. */
    private int keyOf(Slot slot) {
        boolean inventory = slot.container instanceof Inventory;
        if (player()) return inventory ? slot.getContainerSlot() : -1;
        return inventory ? -1 : slot.index;
    }

    private void toggleGroup(int from, int to) {
        boolean all = true;
        for (int i = from; i <= to; i++) all &= selected.contains(i);
        for (int i = from; i <= to; i++) {
            if (all) selected.remove(i); else selected.add(i);
        }
    }

    private void selectAll() {
        if (view == null) return;
        for (Slot slot : view.slots()) {
            int key = keyOf(slot);
            if (key >= 0) selected.add(key);
        }
    }

    private void invert() {
        if (view == null) return;
        for (Slot slot : view.slots()) {
            int key = keyOf(slot);
            if (key < 0) continue;
            if (!selected.remove(key)) selected.add(key);
        }
    }

    @Nullable
    private Slot slotAt(double mouseX, double mouseY) {
        if (view == null) return null;
        double ix = (mouseX - offsetX) / scale;
        double iy = (mouseY - offsetY) / scale;
        for (Slot slot : view.slots()) {
            int sx = view.left() + slot.x;
            int sy = view.top() + slot.y;
            if (ix >= sx - 1 && ix < sx + 17 && iy >= sy - 1 && iy < sy + 17) return slot;
        }
        return null;
    }

    private boolean pickerOpen() {
        return itemPicker != null && itemPicker.isVisible();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pickerOpen()) {
            if (!itemPicker.mouseClicked(mouseX, mouseY)) itemPicker.hide();
            return true;
        }
        if (button == 0) {
            int tab = pageTabAt(mouseX, mouseY);
            if (tab >= 0) {
                if (tab != page.ordinal()) {
                    page = Page.values()[tab];
                    rebuildWidgets();
                }
                return true;
            }
            for (Segments bar : segments) {
                int seg = SegmentBar.segmentAt(this.font, bar.x(), bar.y(), mouseX, mouseY, bar.labels());
                if (seg >= 0) {
                    bar.onPick().accept(seg);
                    return true;
                }
            }
            if (opacityStepper != null && opacityStepper.mouseClicked(mouseX, mouseY)) return true;
            if (overSwatch(mouseX, mouseY)) {
                pickColour();
                return true;
            }
            Slot slot = slotAt(mouseX, mouseY);
            if (slot != null && keyOf(slot) >= 0) {
                int key = keyOf(slot);
                dragAdds = !selected.contains(key);
                apply(key);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pickerOpen()) return itemPicker.mouseDragged(mouseX, mouseY);
        if (dragAdds != null) {
            Slot slot = slotAt(mouseX, mouseY);
            if (slot != null && keyOf(slot) >= 0) apply(keyOf(slot));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragAdds = null;
        if (pickerOpen()) return itemPicker.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (pickerOpen()) return itemPicker.mouseScrolled(mouseX, mouseY, scrollY);
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pickerOpen()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                itemPicker.hide();
                return true;
            }
            return itemPicker.keyPressed(keyCode);
        }
        if (opacityStepper != null && opacityStepper.keyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (pickerOpen()) return itemPicker.charTyped(c);
        if (opacityStepper != null && opacityStepper.charTyped(c)) return true;
        return super.charTyped(c, modifiers);
    }

    private void apply(int key) {
        if (Boolean.TRUE.equals(dragAdds)) selected.add(key); else selected.remove(key);
    }

    private List<String> modeLabels() {
        return List.of(text("editor.historystages.slots.mode.hide"), text("editor.historystages.slots.mode.overlay"));
    }

    // ---- actions -----------------------------------------------------------------------

    private SlotLockEntry result() {
        SlotLockEntry out = new SlotLockEntry(entry.target(), new ArrayList<>(selected), mode);
        out.setLook(look.copy());
        out.setHint(hint);
        return out;
    }

    private void done() {
        this.minecraft.setScreen(parent);
        onDone.accept(result());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    /** The real container fills in what this screen could not show; mode, look and hint carry over. */
    private void pickInWorld() {
        SlotLockEntry kept = result();
        ClientSlotPick.start(parent, picked -> {
            picked.setMode(kept.mode());
            picked.setLook(kept.look());
            picked.setHint(kept.hint());
            this.minecraft.setScreen(new SlotPickerScreen(parent, picked, onDone));
        });
    }

    // ---- rendering ---------------------------------------------------------------------

    /** Own background, drawn in render — this dodges 1.21's menu blur, as the other editor screens do. */
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        tooltip = null;
        int mx = pickerOpen() ? -1 : mouseX;
        int my = pickerOpen() ? -1 : mouseY;
        g.fill(0, 0, this.width, this.height, BACKDROP);
        g.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);
        g.fill(MARGIN, HAIRLINE_Y, this.width - MARGIN, HAIRLINE_Y + 1, HAIRLINE);
        g.fill(MARGIN, contentBottom() + 4, this.width - MARGIN, contentBottom() + 5, SEPARATOR);

        if (view != null && !failed) {
            renderView(g, mx, my, partialTick);
            if (preview) renderPreviewFrame(g);
        } else {
            renderFailure(g);
        }
        renderPanel(g, mx, my);

        // Late, so the buttons sit on top of the content.
        super.render(g, mx, my, partialTick);
        if (pickerOpen()) {
            // Locked-slot overlays sit high up so they cover items; the picker has to sit higher still.
            g.pose().pushPose();
            g.pose().translate(0, 0, 500);
            itemPicker.render(g, this.font, mouseX, mouseY);
            g.pose().popPose();
        }
        if (tooltip == null && !pickerOpen()) {
            for (var e : fullLabels.entrySet()) {
                if (e.getKey().isMouseOver(mouseX, mouseY)) tooltip = e.getValue();
            }
        }
        if (tooltip != null) EditorTooltip.draw(g, this.font, tooltip, mouseX, mouseY, this.width, this.height);
    }

    private void renderView(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int innerMouseX = (int) ((mouseX - offsetX) / scale);
        int innerMouseY = (int) ((mouseY - offsetY) / scale);
        // Remembered so a foreign screen that throws halfway, with poses of its own still pushed,
        // can be unwound back to exactly where this screen was.
        var mark = g.pose().last();
        g.pose().pushPose();
        g.pose().translate(offsetX, offsetY, 0);
        g.pose().scale(scale, scale, 1f);
        try {
            view.render(g, innerMouseX, innerMouseY, partialTick);
        } catch (Throwable t) {
            // Built fine but cannot draw without its machine: same answer as failing to build.
            while (g.pose().last() != mark && !g.pose().clear()) g.pose().popPose();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            PreviewMenus.GUARD.markFailed(large ? entry.target() + "#large" : entry.target());
            failed = true;
            rebuildWidgets();
            return;
        }

        Slot hovered = slotAt(mouseX, mouseY);
        for (Slot slot : view.slots()) {
            int x = view.left() + slot.x;
            int y = view.top() + slot.y;
            int key = keyOf(slot);
            if (!slot.getItem().isEmpty()) g.renderItem(slot.getItem(), x, y);
            if (key < 0) {
                g.fill(x, y, x + 16, y + 16, DIM);
                continue;
            }
            if (selected.contains(key)) {
                if (preview) {
                    if (mode == SlotLockMode.HIDE && view.vanillaLook()) {
                        g.fill(x - 1, y - 1, x + 17, y + 17, VANILLA_PANEL);
                    } else if (mode == SlotLockMode.OVERLAY) {
                        SlotLockClient.drawOverlay(g, x, y, look);
                    } else {
                        g.fill(x, y, x + 16, y + 16, DIM);
                    }
                } else {
                    g.fill(x, y, x + 16, y + 16, SELECTED_TINT);
                    SlotLockClient.drawOverlay(g, x, y);
                }
            } else if (slot == hovered && !preview) {
                g.fill(x, y, x + 16, y + 16, 0x60FFFFFF);
            }
        }
        g.pose().popPose();
    }

    /** Gold frame and caption round the GUI, so it is plain that this is the in-game look, not the selection. */
    private void renderPreviewFrame(GuiGraphics g) {
        int left = Math.round(offsetX + view.left() * scale) - 4;
        int top = Math.round(offsetY + view.top() * scale) - 4;
        int right = Math.round(offsetX + (view.left() + view.imageWidth()) * scale) + 4;
        int bottom = Math.round(offsetY + (view.top() + view.imageHeight()) * scale) + 4;
        g.renderOutline(left, top, right - left, bottom - top, ACCENT);
        g.renderOutline(left + 1, top + 1, right - left - 2, bottom - top - 2, 0x80FFCC00);
        Component caption = Component.translatable("editor.historystages.slots.preview.caption");
        int cw = this.font.width(caption) + 8;
        int cx = (left + right) / 2 - cw / 2;
        g.fill(cx, top - 6, cx + cw, top + 6, ACCENT);
        g.drawString(this.font, caption, cx + 4, top - 4, 0xFF000000, false);
    }

    private void renderFailure(GuiGraphics g) {
        int cx = areaX() + areaW() / 2;
        int cy = TOP + areaH() / 2;
        g.drawCenteredString(this.font, Component.translatable("editor.historystages.slots.picker.no_preview"),
                cx, cy - 14, 0xFFFFFF);
        if (player()) return;
        g.drawCenteredString(this.font, Component.translatable("editor.historystages.slots.picker.no_preview.hint"),
                cx, cy - 2, LABEL_GREY);
    }

    private void renderPanel(GuiGraphics g, int mouseX, int mouseY) {
        int x = panelX();
        int bottom = contentBottom();
        g.fill(x, TOP, x + PANEL_W, bottom, PANEL_BORDER);
        g.fill(x + 1, TOP + 1, x + PANEL_W - 1, bottom - 1, PANEL_BODY);
        g.fill(x, TOP, x + PANEL_W, TOP + 1, ACCENT);

        renderPageTabs(g, mouseX, mouseY);
        int px = x + 8;
        if (page == Page.SLOTS) {
            renderSlotsLabels(g, px);
        } else {
            renderLookLabels(g, px, mouseX, mouseY);
        }

        for (Segments bar : segments) {
            int hoveredSeg = SegmentBar.segmentAt(this.font, bar.x(), bar.y(), mouseX, mouseY, bar.labels());
            int chosen = bar.selected().getAsInt();
            bar.state().update(chosen, hoveredSeg, bar.labels().size());
            SegmentBar.draw(g, this.font, bar.x(), bar.y(), bar.labels(), chosen, bar.state(),
                    new boolean[bar.labels().size()]);
            if (hoveredSeg >= 0 && bar.tooltips() != null) tooltip = bar.tooltips().get(hoveredSeg);
        }
        if (opacityStepper != null) opacityStepper.render(g, this.font, mouseX, mouseY);
        drawField(g, hintBox);
    }

    // ---- page tabs: drawn like the tabs of the editor's search lists, so they read as "where am
    // I" rather than as one more switch that changes the lock --------------------------------

    private static final int TAB_H = 14;

    private int tabWidth() {
        return (PANEL_W - 16) / Page.values().length;
    }

    private int pageTabAt(double mouseX, double mouseY) {
        int x0 = panelX() + 8;
        int y0 = TOP + 7;
        if (mouseY < y0 || mouseY >= y0 + TAB_H || mouseX < x0) return -1;
        int i = (int) ((mouseX - x0) / tabWidth());
        return i < Page.values().length ? i : -1;
    }

    private void renderPageTabs(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = panelX() + 8;
        int y0 = TOP + 7;
        int w = tabWidth();
        int hovered = pageTabAt(mouseX, mouseY);
        for (Page p : Page.values()) {
            int tx = x0 + p.ordinal() * w;
            boolean active = p == page;
            int bg = active ? 0x40FFCC00 : hovered == p.ordinal() ? 0x25FFFFFF : 0x15FFFFFF;
            g.fill(tx, y0, tx + w - 2, y0 + TAB_H, bg);
            String label = text(p == Page.SLOTS ? "editor.historystages.slots.picker.page.slots"
                    : "editor.historystages.slots.picker.page.look");
            int color = active ? 0xFFFFFFFF : hovered == p.ordinal() ? 0xFFDDDDDD : 0xFF999999;
            g.drawString(this.font, label, tx + (w - 2 - this.font.width(label)) / 2, y0 + 3, color, false);
            if (active) g.fill(tx, y0 + TAB_H - 2, tx + w - 2, y0 + TAB_H, ACCENT);
        }
        g.fill(x0, y0 + TAB_H, x0 + PANEL_W - 16, y0 + TAB_H + 1, SEPARATOR);
    }

    private void renderSlotsLabels(GuiGraphics g, int px) {
        int y = TOP + 32;
        if (PreviewMenus.isChestLike(entry.target())) {
            g.drawString(this.font, Component.translatable("editor.historystages.slots.picker.size"), px, y, ACCENT);
            y += 36;
        }
        g.drawString(this.font, Component.translatable("editor.historystages.slots.picker.mode"), px, y, ACCENT);
        y += 36;

        g.drawString(this.font, Component.translatable("editor.historystages.slots.picker.selected"), px, y, ACCENT);
        int lineY = y + 12;
        int quickLabelY = contentBottom() - 8 - BUTTON_H * 2 - 16;
        for (var line : this.font.split(Component.literal(SlotCategoryTab.summary(result())), PANEL_W - 16)) {
            if (lineY > quickLabelY - 10) break;
            g.drawString(this.font, line, px, lineY, 0xFFDDDDDD);
            lineY += this.font.lineHeight + 1;
        }
        g.drawString(this.font, Component.translatable("editor.historystages.slots.picker.quick"), px, quickLabelY, ACCENT);
    }

    private void renderLookLabels(GuiGraphics g, int px, int mouseX, int mouseY) {
        int y = TOP + 32;
        if (mode != SlotLockMode.OVERLAY) {
            for (var line : this.font.split(Component.translatable("editor.historystages.slots.look.hide_only"), PANEL_W - 16)) {
                g.drawString(this.font, line, px, y, LABEL_GREY);
                y += this.font.lineHeight + 1;
            }
            return;
        }
        int controlX = px + 58;
        label(g, "editor.historystages.slots.look.color", px, y);
        boolean swatchHovered = overSwatch(mouseX, mouseY);
        g.fill(swatchX - 1, swatchY - 1, swatchX + SWATCH + 1, swatchY + SWATCH + 1,
                swatchHovered ? ACCENT : 0xFF555555);
        g.fill(swatchX, swatchY, swatchX + SWATCH, swatchY + SWATCH, 0xFF000000 | look.rgb());
        if (swatchHovered) tooltip = text("editor.historystages.slots.look.color.tooltip");
        y += FIELD_H + 4;
        label(g, "editor.historystages.slots.look.opacity", px, y);
        y += FIELD_H + 4;
        g.drawString(this.font, Component.translatable("editor.historystages.slots.look.icon"), px, y, LABEL_GREY, false);
        y += 11 + SegmentBar.height() + 4;
        if (look.icon() == SlotLook.Icon.ITEM) {
            label(g, "editor.historystages.slots.look.item", px, y);
            y += FIELD_H + 4;
        }
        g.drawString(this.font, Component.translatable("editor.historystages.slots.look.size"), px, y, LABEL_GREY, false);
        y += 11 + SegmentBar.height() + 4;
        label(g, "editor.historystages.slots.look.texture", px, y);
        if (mouseX >= px && mouseX < px + 56 && mouseY >= y && mouseY < y + FIELD_H) {
            tooltip = text("editor.historystages.slots.look.texture.tooltip");
        }
        y += FIELD_H + 8;
        g.drawString(this.font, Component.translatable("editor.historystages.slots.look.hint"), px, y, ACCENT);
    }

    private boolean overSwatch(double mouseX, double mouseY) {
        return swatchX >= 0 && mouseX >= swatchX - 1 && mouseX < swatchX + SWATCH + 1
                && mouseY >= swatchY - 1 && mouseY < swatchY + SWATCH + 1;
    }

    private void label(GuiGraphics g, String key, int x, int y) {
        g.drawString(this.font, Component.translatable(key), x, y + (FIELD_H - 8) / 2, LABEL_GREY, false);
    }

    /** The editor's own field frame around a borderless vanilla text box. */
    private void drawField(GuiGraphics g, @Nullable EditBox box) {
        if (box == null) return;
        int x = box.getX() - 4;
        int y = box.getY() - (FIELD_H - 8) / 2;
        int w = box.getWidth() + 8;
        g.fill(x - 1, y - 1, x + w + 1, y + FIELD_H + 1, box.isFocused() ? ACCENT : FIELD_BORDER);
        g.fill(x, y, x + w, y + FIELD_H, FIELD_BG);
    }
}
