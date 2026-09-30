package net.bananemdnsa.historystages.client.editor;

import com.google.gson.JsonArray;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.client.cache.ClientStageStates;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.logic.LogicBlockCard;
import net.bananemdnsa.historystages.client.editor.logic.LogicDraft;
import net.bananemdnsa.historystages.client.editor.logic.LogicStagePickerList;
import net.bananemdnsa.historystages.client.editor.widget.Scrollbar;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.DropdownOverlay;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.EnumDropdown;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.logic.LogicBlockTypes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The stage's logic blocks: one card per block, "+ Block" to add one.
 *
 * <p>Laid out like {@link NbtItemEditScreen} — header, toolbar, a scrolling list of cards, back
 * and save at the bottom — because it is the same job: a short list of rules, each with its own
 * little editor. Save persists the whole stage through the callback and stays here, like every
 * other sub-screen of the stage editor.
 */
public class StageLogicScreen extends Screen {

    private final Screen parent;
    private final String stageId;
    private final String stageName;
    private final boolean individual;
    private final Consumer<JsonArray> onSave;

    private final LogicDraft draft;
    private final List<LogicBlockCard.Built> cards = new ArrayList<>();
    private LogicBlockCard card;
    private JsonArray savedSnapshot;
    private boolean dirty;

    private PickerOverlay pickerOverlay;
    private DropdownOverlay addMenu;
    private StyledButton addButton;

    private double scrollOffset;
    private final Anim smoothScroll = new Anim();
    private int maxScroll;
    private final Scrollbar scrollbar = new Scrollbar();

    private static final int PADDING = 20;
    private static final int HEADER_HEIGHT = 58;
    private static final int TOOLBAR_Y = 30;
    private static final int TOOLBAR_H = 18;
    private static final int CARD_GAP = 6;
    private static final int FOOTER_HEIGHT = 40;

    public StageLogicScreen(Screen parent, String stageId, String stageName, boolean individual,
                            @Nullable JsonArray logic, Consumer<JsonArray> onSave) {
        super(Component.translatable("editor.historystages.logic.title"));
        this.parent = parent;
        this.stageId = stageId;
        this.stageName = stageName;
        this.individual = individual;
        this.onSave = onSave;
        this.draft = LogicDraft.read(logic);
        // Compared against what the draft writes, not the file: an unscoped single term written
        // back as a group is the same rule, and flagging it would cry wolf on every open.
        this.savedSnapshot = draft.write();
    }

    @Override
    protected void init() {
        this.card = new LogicBlockCard(this.font);

        Component addLabel = Component.literal("+ ")
                .append(Component.translatable("editor.historystages.logic.add_block")).append(" ▾");
        int addW = this.font.width(addLabel) + 16;
        addButton = this.addRenderableWidget(StyledButton.of(addLabel, btn -> openAddMenu(),
                PADDING, TOOLBAR_Y, addW, TOOLBAR_H));

        this.addRenderableWidget(StyledButton.of(Component.translatable("editor.historystages.back"),
                btn -> this.minecraft.setScreen(parent), PADDING, this.height - 30, 60, 20));
        this.addRenderableWidget(StyledButton.of(Component.translatable("editor.historystages.save"),
                btn -> save(), this.width - PADDING - 100, this.height - 30, 100, 20));

        refresh();
    }

    // ---- derived state ----------------------------------------------------------------

    private void refresh() {
        cards.clear();
        if (card != null) {
            LogicBlockCard.Context ctx = context();
            int width = contentWidth();
            for (int i = 0; i < draft.blocks.size(); i++) {
                cards.add(card.layout(draft.blocks.get(i), i == 0, i == draft.blocks.size() - 1, width, ctx));
            }
        }
        dirty = !Objects.equals(draft.write(), savedSnapshot);
        int total = 0;
        for (LogicBlockCard.Built built : cards) total += built.height() + CARD_GAP;
        maxScroll = Math.max(0, total - (listBottom() - listTop()));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    private LogicBlockCard.Context context() {
        return new LogicBlockCard.Context(stageId, individual ? StageScope.INDIVIDUAL : StageScope.GLOBAL,
                StageManager.getStages(), StageManager.getIndividualStages(),
                ClientStageStates.global(), ClientStageStates.individual());
    }

    private int contentWidth() {
        return this.width - PADDING * 2 - Scrollbar.WIDTH - 4;
    }

    private int scrollbarX() {
        return this.width - PADDING - Scrollbar.WIDTH;
    }

    private int listTop() {
        return HEADER_HEIGHT;
    }

    private int listBottom() {
        return this.height - FOOTER_HEIGHT;
    }

    // ---- rendering --------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Drawn in render(); the default would add 1.21's blur.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xE0101010);
        smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
        smoothScroll.settle((float) scrollOffset, 0.5f);

        String title = Component.translatable("editor.historystages.logic.title_for", stageName).getString();
        g.drawCenteredString(this.font, title, this.width / 2, 8, 0xFFCC00);
        String hint = Component.translatable("editor.historystages.logic.hint").getString();
        g.drawString(this.font, this.font.plainSubstrByWidth(hint, this.width - PADDING * 2 - addButton.getWidth() - 12),
                PADDING + addButton.getWidth() + 10, TOOLBAR_Y + 5, 0x888888, false);
        g.fill(PADDING, HEADER_HEIGHT - 6, this.width - PADDING, HEADER_HEIGHT - 5, 0x40FFCC00);

        int top = listTop();
        int bottom = listBottom();
        g.enableScissor(0, top, this.width, bottom);
        if (draft.blocks.isEmpty()) {
            String empty = Component.translatable("editor.historystages.logic.empty").getString();
            g.drawString(this.font, empty, (this.width - this.font.width(empty)) / 2, (top + bottom) / 2 - 4, 0x7A7A7A, false);
        } else {
            boolean pickerOpen = pickerOverlay != null || (addMenu != null && addMenu.isVisible());
            int hoverX = pickerOpen ? -1 : mouseX;
            int hoverY = pickerOpen ? -1 : mouseY;
            int y = top - Math.round(smoothScroll.value());
            for (int i = 0; i < cards.size(); i++) {
                LogicBlockCard.Built built = cards.get(i);
                if (y + built.height() > top && y < bottom) {
                    card.render(g, built, draft.blocks.get(i), PADDING, y, hoverX, hoverY);
                }
                y += built.height() + CARD_GAP;
            }
        }
        g.disableScissor();

        scrollbar.render(g, scrollbarX(), top, bottom, smoothScroll.value(), maxScroll, mouseX, mouseY);
        if (dirty) renderUnsavedMarker(g);

        super.render(g, mouseX, mouseY, partialTick);

        syncPickerState();
        if (pickerOverlay != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(0, 0, this.width, this.height, 0x80000000);
            pickerOverlay.render(g, this.font, mouseX, mouseY);
            g.pose().popPose();
        } else if (addMenu != null && addMenu.isVisible()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            addMenu.render(g, this.font, mouseX, mouseY);
            g.pose().popPose();
        }
    }

    private void renderUnsavedMarker(GuiGraphics g) {
        float phase = (System.currentTimeMillis() % (long) Timing.BREATHE_PERIOD_MS) / Timing.BREATHE_PERIOD_MS;
        int alpha = (int) ((0.35f + 0.45f * Ease.breathe(phase)) * 255);
        String label = Component.translatable("editor.historystages.unsaved").getString();
        int labelX = this.width - PADDING - 100 - 8 - this.font.width(label);
        int y = this.height - 24;
        g.fill(labelX - 10, y + 1, labelX - 4, y + 7, (alpha << 24) | 0xFFCC00);
        g.drawString(this.font, label, labelX, y, 0xFFCC00, false);
    }

    // ---- input ------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        syncPickerState();
        if (pickerOverlay != null) {
            pickerOverlay.mouseClicked(mouseX, mouseY);
            syncPickerState();
            return true;
        }
        if (addMenu != null && addMenu.isVisible()) {
            addMenu.mouseClicked(mouseX, mouseY);
            return true;
        }
        if (scrollbar.mouseClicked(mouseX, mouseY)) {
            scrollOffset = scrollbar.scrollFor(mouseY);
            return true;
        }
        if (mouseY >= listTop() && mouseY < listBottom()) {
            int y = listTop() - Math.round(smoothScroll.value());
            for (int i = 0; i < cards.size(); i++) {
                LogicBlockCard.Built built = cards.get(i);
                LogicBlockCard.Hit hit = card.hitTest(built, PADDING, y, mouseX, mouseY);
                if (hit != null) {
                    playClick();
                    handleHit(i, hit);
                    return true;
                }
                y += built.height() + CARD_GAP;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleHit(int index, LogicBlockCard.Hit hit) {
        LogicDraft.Block block = draft.blocks.get(index);
        switch (hit) {
            case LogicBlockCard.Hit.Remove r -> draft.blocks.remove(index);
            case LogicBlockCard.Hit.MoveUp m -> {
                if (index > 0) draft.blocks.add(index - 1, draft.blocks.remove(index));
            }
            case LogicBlockCard.Hit.MoveDown m -> {
                if (index < draft.blocks.size() - 1) draft.blocks.add(index + 1, draft.blocks.remove(index));
            }
            case LogicBlockCard.Hit.SetGroupAll s -> block.groups.get(s.group()).all = s.all();
            case LogicBlockCard.Hit.RemoveGroup r -> block.groups.remove(r.group());
            case LogicBlockCard.Hit.AddGroup a -> block.groups.add(new LogicDraft.Group(true));
            case LogicBlockCard.Hit.SetTermUnlocked s -> block.groups.get(s.group()).terms.get(s.term()).unlocked = s.unlocked();
            case LogicBlockCard.Hit.RemoveTerm r -> block.groups.get(r.group()).terms.remove(r.term());
            case LogicBlockCard.Hit.AddTerm a -> {
                LogicDraft.Term term = new LogicDraft.Term(null, null, true);
                block.groups.get(a.group()).terms.add(term);
                // A term without a stage is useless; ask for one straight away.
                openStagePicker(term);
            }
            case LogicBlockCard.Hit.PickStage p -> openStagePicker(block.groups.get(p.group()).terms.get(p.term()));
            case LogicBlockCard.Hit.SetHiddenMode m -> block.setHiddenMode(m.mode());
            case LogicBlockCard.Hit.EditPercent e -> {
                openPercentInput(block, e.key());
                return;
            }
        }
        refresh();
    }

    /** A percentage, 0 to 1000; anything that is not a number leaves the value as it was. */
    private void openPercentInput(LogicDraft.Block block, String key) {
        String title = Component.translatable("editor.historystages.logic.cost." + key).getString() + " (%)";
        this.minecraft.setScreen(new NbtItemEditScreen.SuggestingInputScreen(this, title, String.valueOf(block.percent(key)),
                List.of("0", "25", "50", "75", "100", "150", "200"), value -> {
                    try {
                        block.setPercent(key, (int) Math.round(Double.parseDouble(value.trim().replace("%", ""))));
                    } catch (NumberFormatException ignored) {
                        // Not a number: keep what was there rather than guess.
                    }
                    refresh();
                }));
    }

    private void openStagePicker(LogicDraft.Term term) {
        StageScope owner = individual ? StageScope.INDIVIDUAL : StageScope.GLOBAL;
        LogicStagePickerList picker = new LogicStagePickerList(selection -> {
            closePicker();
            LogicStagePickerList.Picked picked = LogicStagePickerList.parse(selection);
            term.stageId = picked.id();
            // Written without a scope when it matches the owner's: that is what an unscoped term
            // means, and it keeps hand-written files and editor output looking the same.
            term.scope = picked.scope() == owner ? null : picked.scope();
            refresh();
        }, owner, stageId);
        pickerOverlay = picker;
        picker.show(this.width / 2, this.height / 2, this.width);
    }

    private void openAddMenu() {
        if (addMenu == null) {
            addMenu = new DropdownOverlay(new EnumDropdown(LogicBlockTypes.KNOWN, LogicBlockTypes.KNOWN.get(0), 0,
                    type -> Component.translatable(LogicBlockCard.typeTitleKey(type)),
                    this::addBlock).alwaysFires());
        }
        addMenu.openAt(addButton.getX(), addButton.getY(), addButton.getHeight());
    }

    private void addBlock(String type) {
        LogicDraft.Block block = LogicDraft.Block.newBlock(type);
        draft.blocks.add(block);
        refresh();
        scrollOffset = maxScroll;
        // The first term straight away, like adding a term: an empty block cannot be saved.
        LogicDraft.Term term = new LogicDraft.Term(null, null, true);
        block.groups.get(0).terms.add(term);
        openStagePicker(term);
    }

    private void save() {
        JsonArray written = draft.write();
        // Blocks without a single picked stage are dropped on write; take them off the screen too
        // so what is shown is what was stored.
        draft.blocks.removeIf(LogicDraft.Block::isEmpty);
        onSave.accept(written);
        savedSnapshot = written;
        refresh();
    }

    private void playClick() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        }
    }

    private void syncPickerState() {
        if (pickerOverlay != null && !pickerOverlay.isVisible()) pickerOverlay = null;
    }

    private void closePicker() {
        if (pickerOverlay != null) {
            pickerOverlay.hide();
            pickerOverlay = null;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        syncPickerState();
        if (pickerOverlay != null) return pickerOverlay.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (addMenu != null && addMenu.isVisible()) return addMenu.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 12));
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        syncPickerState();
        if (pickerOverlay != null && pickerOverlay.mouseDragged(mouseX, mouseY)) return true;
        if (scrollbar.isDragging()) {
            scrollOffset = scrollbar.scrollFor(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        syncPickerState();
        if (pickerOverlay != null && pickerOverlay.mouseReleased()) return true;
        if (scrollbar.isDragging()) {
            scrollbar.mouseReleased();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        syncPickerState();
        if (pickerOverlay != null && pickerOverlay.keyPressed(keyCode)) {
            syncPickerState();
            return true;
        }
        if (addMenu != null && addMenu.isVisible() && addMenu.keyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        syncPickerState();
        if (pickerOverlay != null && pickerOverlay.charTyped(c)) return true;
        return super.charTyped(c, modifiers);
    }

    /** Escape goes back to the stage, not out of the editor. */
    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
