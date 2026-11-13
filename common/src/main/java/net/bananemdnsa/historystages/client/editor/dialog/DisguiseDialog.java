package net.bananemdnsa.historystages.client.editor.dialog;

import net.bananemdnsa.historystages.api.editor.widget.AbstractInputScreen;
import net.bananemdnsa.historystages.api.editor.widget.InputField;
import net.bananemdnsa.historystages.api.editor.widget.InputValues;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.api.editor.widget.ToggleControl;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableItemList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableTagList;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.disguise.DisguiseData;
import net.bananemdnsa.historystages.data.disguise.DisguiseRule;
import net.bananemdnsa.historystages.data.disguise.DisguiseRuleSet;
import net.bananemdnsa.historystages.data.disguise.Disguises;
import net.bananemdnsa.historystages.data.disguise.DropsMode;
import net.bananemdnsa.historystages.data.lock.NamedLockEntry;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.network.serverbound.SaveDisguisePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.bananemdnsa.historystages.network.PacketHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sets, changes or removes the disguise of one item or tag.
 *
 * <p>Opened from a stage's entry (the key is fixed) and from the disguise list (the key can be
 * picked, which is how a new rule starts). Either way the rule is not the stage's: it lives in
 * {@code disguises.json} and applies wherever the key is locked, which the subtitle says out loud.
 */
public class DisguiseDialog extends AbstractInputScreen {

    private static final int ROW_H = 22;
    private static final int LINE_H = 11;
    private static final int SLOT_W = 150;
    private static final int SLOT_H = 18;
    private static final int INFO_LINES = 3;
    private static final int WARN = 0xFFAA33;
    private static final int OK_GREEN = 0x7FD27F;
    private static final int REMOVE_RED = 0xFF6B6B;

    private final boolean keyEditable;
    private final String originalKey;

    private String key;
    private String target;
    private boolean dropsLikeDisguise;
    private boolean hints;
    @Nullable private final com.google.gson.JsonObject nbt;

    private final ToggleControl.State dropsToggle = new ToggleControl.State();
    private final ToggleControl.State hintsToggle = new ToggleControl.State();

    private PickerOverlay overlay;

    /** Hit boxes from the last frame. */
    private int keySlotX, keySlotY, tagBtnX, targetSlotX, targetSlotY, dropsX, dropsY, hintsX, hintsY;
    private int removeX, removeY, removeW;

    /**
     * @param key         item id or {@code #tag}; null to start a new rule from the list
     * @param keyEditable whether the locked item/tag may be changed here (only from the list)
     */
    public DisguiseDialog(Screen parent, @Nullable String key, boolean keyEditable) {
        super(parent, Component.translatable("editor.historystages.disguise.title_new"));
        this.keyEditable = keyEditable;
        this.originalKey = key;
        this.key = key;
        DisguiseRule rule = key != null ? DisguiseData.get().get(key) : null;
        this.target = rule != null ? rule.as() : null;
        this.dropsLikeDisguise = rule != null && rule.drops() == DropsMode.DISGUISE;
        this.hints = rule == null || rule.hints();
        this.nbt = rule != null ? rule.nbt() : null;
    }

    // --- modal frame ---

    @Override
    protected int dialogWidth() {
        return 320;
    }

    @Override
    protected Component titleText() {
        if (key == null) return Component.translatable("editor.historystages.disguise.title_new");
        return Component.translatable("editor.historystages.disguise.title", keyName(key));
    }

    @Override
    protected Component subtitle() {
        return Component.translatable("editor.historystages.disguise.subtitle");
    }

    @Override
    protected List<InputField> fields() {
        return List.of();
    }

    @Override
    protected int extraContentHeight() {
        // Rows, info block, warning line, remove link — fixed, so the box never jumps.
        return (keyEditable ? ROW_H : 0) + ROW_H * 3 + 4 + LINE_H * INFO_LINES + 4 + LINE_H + 4 + LINE_H;
    }

    @Override
    protected boolean canConfirm() {
        return key != null && target != null && !target.equals(key) && warning() != Warning.CYCLE;
    }

    @Override
    protected void onConfirm(InputValues values) {
        DisguiseRule rule = new DisguiseRule(key, target,
                dropsLikeDisguise ? DropsMode.DISGUISE : DropsMode.REAL, hints, key.startsWith("#") ? null : nbt);
        send(SaveDisguisePacket.ruleJson(rule));
    }

    private void remove() {
        if (originalKey == null) return;
        key = originalKey;
        send("");
    }

    private void send(String ruleJson) {
        String previous = originalKey != null && !originalKey.equals(key) ? originalKey : "";
        PacketHandler.sendToServer(new SaveDisguisePacket(key, previous, ruleJson));
        this.minecraft.setScreen(parent);
    }

    // --- content ---

    @Override
    protected void renderExtraContent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        int row = y;
        int controlX = x + w - SLOT_W;

        if (keyEditable) {
            label(g, "editor.historystages.disguise.locked", x, row);
            keySlotX = controlX;
            keySlotY = row;
            int tagW = 34;
            tagBtnX = controlX + SLOT_W - tagW;
            slot(g, keySlotX, keySlotY, SLOT_W - tagW - 2, key, mouseX, mouseY);
            button(g, tagBtnX, row, tagW, Component.literal("#…"), mouseX, mouseY);
            row += ROW_H;
        }

        label(g, "editor.historystages.disguise.as", x, row);
        targetSlotX = controlX;
        targetSlotY = row;
        slot(g, targetSlotX, targetSlotY, SLOT_W, target, mouseX, mouseY);
        row += ROW_H;

        label(g, "editor.historystages.disguise.drops", x, row);
        dropsX = x + w - ToggleControl.width(this.font);
        dropsY = row + 1;
        dropsToggle.update(dropsLikeDisguise, ToggleControl.segmentAt(this.font, dropsX, dropsY, mouseX, mouseY));
        ToggleControl.draw(g, this.font, dropsX, dropsY, dropsLikeDisguise, dropsToggle, false);
        row += ROW_H;

        label(g, "editor.historystages.disguise.hints", x, row);
        hintsX = x + w - ToggleControl.width(this.font);
        hintsY = row + 1;
        hintsToggle.update(hints, ToggleControl.segmentAt(this.font, hintsX, hintsY, mouseX, mouseY));
        ToggleControl.draw(g, this.font, hintsX, hintsY, hints, hintsToggle, false);
        row += ROW_H + 4;

        for (Component line : infoLines()) {
            g.drawString(this.font, this.font.plainSubstrByWidth(line.getString(), w), x, row,
                    line.getStyle().getColor() != null ? line.getStyle().getColor().getValue() : LABEL_GREY, false);
            row += LINE_H;
        }
        row = y + extraContentHeight() - LINE_H * 2 - 4;

        Warning warning = warning();
        if (warning != Warning.NONE) {
            g.drawString(this.font, this.font.plainSubstrByWidth("⚠ " + warningText(warning).getString(), w),
                    x, row, WARN, false);
        }
        row += LINE_H + 4;

        removeW = 0;
        if (originalKey != null && DisguiseData.get().get(originalKey) != null) {
            Component remove = Component.translatable("editor.historystages.disguise.remove");
            removeW = this.font.width(remove);
            removeX = x + w - removeW;
            removeY = row;
            boolean hover = inside(mouseX, mouseY, removeX, removeY, removeW, 9);
            g.drawString(this.font, hover ? remove.copy().withStyle(s -> s.withUnderlined(true)) : remove,
                    removeX, removeY, REMOVE_RED, false);
        }
    }

    private void label(GuiGraphics g, String langKey, int x, int rowY) {
        g.drawString(this.font, Component.translatable(langKey), x, rowY + 5, 0xFFE0E0E0, false);
    }

    /** A picker slot: icon plus name, or "Choose…" when empty. */
    private void slot(GuiGraphics g, int sx, int sy, int sw, @Nullable String id, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, sx, sy, sw, SLOT_H);
        g.fill(sx, sy, sx + sw, sy + SLOT_H, hover ? 0xFF888888 : FIELD_BORDER);
        g.fill(sx + 1, sy + 1, sx + sw - 1, sy + SLOT_H - 1, hover ? 0xFF252525 : FIELD_BG);
        if (id == null) {
            g.drawString(this.font, Component.translatable("editor.historystages.disguise.choose"),
                    sx + 6, sy + 5, LABEL_GREY, false);
            return;
        }
        int textX = sx + 4;
        Item item = id.startsWith("#") ? null : Disguises.itemById(id);
        if (item != null) {
            g.renderItem(new ItemStack(item), sx + 1, sy + 1);
            textX = sx + 20;
        }
        String name = keyName(id).getString();
        g.drawString(this.font, this.font.plainSubstrByWidth(name, sx + sw - 4 - textX), textX, sy + 5, 0xFFEEEEEE, false);
    }

    private void button(GuiGraphics g, int bx, int by, int bw, Component text, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, bx, by, bw, SLOT_H);
        g.fill(bx, by, bx + bw, by + SLOT_H, hover ? ACCENT_GOLD : FIELD_BORDER);
        g.fill(bx + 1, by + 1, bx + bw - 1, by + SLOT_H - 1, hover ? 0xFF252525 : FIELD_BG);
        g.drawCenteredString(this.font, text, bx + bw / 2, by + 5, 0xFFEEEEEE);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // --- info and warnings ---

    private List<Component> infoLines() {
        List<Component> out = new ArrayList<>();
        if (key == null) return out;

        List<String> stages = lockingStageNames(key);
        out.add(stages.isEmpty()
                ? Component.translatable("editor.historystages.disguise.locked_nowhere").withColor(WARN)
                : Component.translatable("editor.historystages.disguise.locked_in", String.join(", ", stages)));

        if (target != null) {
            boolean worldToo = isBlockKey(key) && Disguises.itemById(target) instanceof BlockItem && !hasNbt();
            out.add(worldToo
                    ? Component.translatable("editor.historystages.disguise.world_too").withColor(OK_GREEN)
                    : Component.translatable("editor.historystages.disguise.world_no"));

            DisguiseRule next = DisguiseData.get().get(target);
            if (next != null && !next.isTag()) {
                out.add(Component.translatable("editor.historystages.disguise.chain",
                        keyName(next.as()), keyName(target)));
            }
        }
        return out;
    }

    private enum Warning { NONE, CYCLE, BREAK_REAL, NOT_BLOCK, NBT }

    private Warning warning() {
        if (key == null || target == null) return Warning.NONE;
        DisguiseRuleSet set = DisguiseData.get();
        if (originalKey != null && !originalKey.equals(key)) set = set.without(originalKey);
        if (set.wouldCycle(key, target)) return Warning.CYCLE;
        if (!dropsLikeDisguise && breakLocked(key)) return Warning.BREAK_REAL;
        if (isBlockKey(key) && !(Disguises.itemById(target) instanceof BlockItem)) return Warning.NOT_BLOCK;
        if (hasNbt()) return Warning.NBT;
        return Warning.NONE;
    }

    private Component warningText(Warning w) {
        return switch (w) {
            case CYCLE -> Component.translatable("editor.historystages.disguise.warn.cycle", keyName(key));
            case BREAK_REAL -> Component.translatable("editor.historystages.disguise.warn.break_real");
            case NOT_BLOCK -> Component.translatable("editor.historystages.disguise.warn.not_block");
            case NBT -> Component.translatable("editor.historystages.disguise.warn.nbt");
            case NONE -> Component.empty();
        };
    }

    private boolean hasNbt() {
        return nbt != null && nbt.size() > 0 && key != null && !key.startsWith("#");
    }

    /** A tag may hold blocks; an item key counts when it is a block's item. */
    private static boolean isBlockKey(String key) {
        return key.startsWith("#") || Disguises.itemById(key) instanceof BlockItem;
    }

    private static boolean breakLocked(String key) {
        if (key.startsWith("#")) return false;
        Item item = Disguises.itemById(key);
        if (item == null) return false;
        String modId = key.substring(0, key.indexOf(':'));
        ItemStack stack = new ItemStack(item);
        return !StageLocks.engine().gatingStagesForItemAction(key, modId, stack, "break", StageScope.GLOBAL).isEmpty()
                || !StageLocks.engine().gatingStagesForItemAction(key, modId, stack, "break", StageScope.INDIVIDUAL).isEmpty();
    }

    /** Display names of the stages that lock this key, global ones first. */
    public static List<String> lockingStageNames(String key) {
        Set<String> names = new LinkedHashSet<>();
        if (key.startsWith("#")) {
            String tag = key.substring(1);
            collectTagStages(StageManager.getStages(), tag, names);
            collectTagStages(StageManager.getIndividualStages(), tag, names);
        } else {
            Item item = Disguises.itemById(key);
            if (item == null) return List.of();
            String modId = key.substring(0, key.indexOf(':'));
            ItemStack stack = new ItemStack(item);
            for (String id : StageLocks.engine().gatingStagesForItem(key, modId, stack, StageScope.GLOBAL)) {
                StageEntry e = StageManager.getStages().get(id);
                if (e != null) names.add(e.getDisplayName());
            }
            for (String id : StageLocks.engine().gatingStagesForItem(key, modId, stack, StageScope.INDIVIDUAL)) {
                StageEntry e = StageManager.getIndividualStages().get(id);
                if (e != null) names.add(e.getDisplayName());
            }
        }
        return new ArrayList<>(names);
    }

    private static void collectTagStages(Map<String, StageEntry> stages, String tag, Set<String> out) {
        for (StageEntry stage : stages.values()) {
            for (NamedLockEntry entry : stage.getTagEntries()) {
                if (entry.getId().equals(tag)) {
                    out.add(stage.getDisplayName());
                    break;
                }
            }
        }
    }

    /** "Emerald Ore" for an item id, "#c:ores" for a tag, the raw id for anything unknown. */
    public static Component keyName(String id) {
        if (id.startsWith("#")) return Component.literal(id);
        Item item = Disguises.itemById(id);
        return item != null ? new ItemStack(item).getItem().getDescription() : Component.literal(id);
    }

    // --- input ---

    @Override
    protected boolean extraContentMouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        if (keyEditable && inside(mx, my, tagBtnX, keySlotY, 34, SLOT_H)) {
            openOverlay(new SearchableTagList(tag -> { key = "#" + tag; overlay = null; }));
            return true;
        }
        if (keyEditable && inside(mx, my, keySlotX, keySlotY, SLOT_W - 36, SLOT_H)) {
            openOverlay(new SearchableItemList(id -> { key = id; overlay = null; }));
            return true;
        }
        if (inside(mx, my, targetSlotX, targetSlotY, SLOT_W, SLOT_H)) {
            openOverlay(new SearchableItemList(id -> { target = id; overlay = null; }));
            return true;
        }
        Boolean drops = ToggleControl.valueAt(this.font, dropsX, mx);
        if (drops != null && my >= dropsY && my < dropsY + ToggleControl.height()) {
            if (drops != dropsLikeDisguise) click();
            dropsLikeDisguise = drops;
            return true;
        }
        Boolean hint = ToggleControl.valueAt(this.font, hintsX, mx);
        if (hint != null && my >= hintsY && my < hintsY + ToggleControl.height()) {
            if (hint != hints) click();
            hints = hint;
            return true;
        }
        if (removeW > 0 && inside(mx, my, removeX, removeY, removeW, 9)) {
            click();
            remove();
            return true;
        }
        return false;
    }

    private void openOverlay(PickerOverlay picker) {
        click();
        overlay = picker;
        overlay.show(this.width / 2, this.height / 2, this.width);
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** The picker hides itself on an outside click; drop the reference when it has. */
    private boolean overlayUp() {
        if (overlay != null && !overlay.isVisible()) overlay = null;
        return overlay != null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean picking = overlayUp();
        super.render(g, picking ? -1 : mouseX, picking ? -1 : mouseY, partialTick);
        if (!picking) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 600);
        g.fill(0, 0, this.width, this.height, 0x80000000);
        overlay.render(g, this.font, mouseX, mouseY);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (overlayUp()) {
            overlay.mouseClicked(mx, my);
            overlayUp();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (overlayUp()) return overlay.mouseDragged(mx, my);
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (overlayUp()) return overlay.mouseReleased();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (overlayUp()) return overlay.mouseScrolled(mx, my, sx, sy);
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (overlayUp()) {
            if (keyCode == 256) {
                overlay = null;
                return true;
            }
            overlay.keyPressed(keyCode);
            overlayUp();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (overlayUp()) return overlay.charTyped(c);
        return super.charTyped(c, modifiers);
    }
}
