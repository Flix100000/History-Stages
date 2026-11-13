package net.bananemdnsa.historystages.client.editor.widget.popup;

import net.bananemdnsa.historystages.client.editor.tab.LockActionGroups;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The "blocked actions" popup: one toggle per action of a vocabulary, grouped, with All / None /
 * Done. Used for a single entry in the stage editor and for the fixed actions in the stage
 * settings, so both look and behave the same.
 *
 * <p>Toggles show the <em>blocked</em> actions. Clicking outside closes without reporting
 * anything. Fixed actions carry a padlock. Two ways to open it:
 * <ul>
 *   <li>{@link #show} — for an entry. Fixed actions sit on the stage's value and cannot be
 *       clicked; Done hands back the blocked list, fixed ones included.</li>
 *   <li>{@link #showFixing} — for the stage settings. Left click sets a value, right click fixes
 *       or releases the action; Done hands back the fixed actions with their value. Values of
 *       actions that are not fixed mean nothing and are dropped.</li>
 * </ul>
 */
public class LockActionsPopup {

    private static final int PAD          = 8;
    private static final int MIN_WIDTH    = 232;
    private static final int COLS         = 3;
    private static final int HEADER_H     = 18;   // title block (title + underline)
    private static final int HINT_H       = 10;
    private static final int GROUP_HEAD_H = 10;
    private static final int TOGGLE_H     = 14;
    private static final int TOGGLE_GAP   = 2;
    private static final int GROUP_GAP    = 5;
    private static final int DESC_H       = 11;
    private static final int FOOTER_H     = 20;
    private static final int BTN_H        = 14;

    private boolean visible = false;
    private List<String> vocabulary = List.of();
    private List<String> current = new ArrayList<>();
    /** Fixed actions. Their value is whatever {@link #current} says. */
    private Set<String> fixed = new LinkedHashSet<>();
    /** True in the stage settings: fixing is what this popup is for there. */
    private boolean fixing = false;
    private Runnable onDone = () -> {};
    private int cachedX, cachedY, cachedW, cachedH;

    public boolean isVisible() { return visible; }

    public void hide() { visible = false; }

    /**
     * Opens the popup for one entry.
     *
     * @param vocabulary every action the popup offers, in the category's order
     * @param blocked    the entry's blocked actions, or null for "all of them"
     * @param fixed      actions the stage fixes, with their value (true = locked); null for none
     * @param onDone     receives the blocked actions, fixed ones included, when Done is pressed
     */
    public void show(List<String> vocabulary, @Nullable List<String> blocked,
                     @Nullable Map<String, Boolean> fixed, Consumer<List<String>> onDone) {
        open(vocabulary, blocked != null ? blocked : vocabulary, fixed, false);
        this.onDone = () -> onDone.accept(new ArrayList<>(current));
    }

    /**
     * Opens the popup for the stage settings, where actions are fixed for every entry.
     *
     * @param fixed  the fixed actions so far, with their value; null for none
     * @param onDone receives the fixed actions with their value (true = locked), in vocabulary order
     */
    public void showFixing(List<String> vocabulary, @Nullable Map<String, Boolean> fixed,
                           Consumer<Map<String, Boolean>> onDone) {
        open(vocabulary, List.of(), fixed, true);
        this.onDone = () -> {
            Map<String, Boolean> result = new LinkedHashMap<>();
            for (String action : this.vocabulary) {
                if (this.fixed.contains(action)) result.put(action, current.contains(action));
            }
            onDone.accept(result);
        };
    }

    private void open(List<String> vocabulary, List<String> blocked, @Nullable Map<String, Boolean> fixedValues,
                      boolean fixing) {
        this.vocabulary = List.copyOf(vocabulary);
        this.current = new ArrayList<>(blocked);
        this.fixed = new LinkedHashSet<>();
        if (fixedValues != null) {
            for (Map.Entry<String, Boolean> e : fixedValues.entrySet()) {
                if (!this.vocabulary.contains(e.getKey())) continue;
                fixed.add(e.getKey());
                setBlocked(e.getKey(), Boolean.TRUE.equals(e.getValue()));
            }
        }
        this.fixing = fixing;
        this.cachedW = 0;
        this.visible = true;
    }

    /** Consumes every click while visible. */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        int popupW = cachedW, popupH = cachedH;
        int popupX = cachedX, popupY = cachedY;
        if (popupW == 0) return true; // not yet rendered

        int btnY = popupY + popupH - BTN_H - 6;
        Font font = Minecraft.getInstance().font;

        if (button == 0) {
            int doneW = doneBtnWidth(font);
            int doneX = popupX + popupW - doneW - PAD;
            if (inside(mouseX, mouseY, doneX, btnY, doneW, BTN_H)) {
                click();
                visible = false;
                onDone.run();
                return true;
            }

            int qBtnW = quickBtnWidth(font);
            int allX = popupX + PAD;
            if (inside(mouseX, mouseY, allX, btnY, qBtnW, BTN_H)) {
                click();
                setAll(true);
                return true;
            }

            int noneX = allX + qBtnW + 3;
            if (inside(mouseX, mouseY, noneX, btnY, qBtnW, BTN_H)) {
                click();
                setAll(false);
                return true;
            }
        }

        String action = actionAt(mouseX, mouseY);
        if (action != null) {
            if (button == 1 && fixing) {
                click();
                if (!fixed.remove(action)) fixed.add(action);
            } else if (button == 0 && (fixing || !fixed.contains(action))) {
                click();
                setBlocked(action, !current.contains(action));
            }
            return true;
        }

        // Click outside closes (and discards changes)
        if (mouseX < popupX || mouseX > popupX + popupW || mouseY < popupY || mouseY > popupY + popupH) {
            visible = false;
        }
        return true;
    }

    /** All / None. For an entry the fixed actions keep the stage's value. */
    private void setAll(boolean blocked) {
        for (String action : vocabulary) {
            if (!fixing && fixed.contains(action)) continue;
            setBlocked(action, blocked);
        }
    }

    private void setBlocked(String action, boolean blocked) {
        if (blocked) {
            if (!current.contains(action)) current.add(action);
        } else {
            current.remove(action);
        }
    }

    /** The action whose toggle is under the mouse, or null. Walks the same layout render uses. */
    @Nullable
    private String actionAt(double mouseX, double mouseY) {
        int curY = cachedY + HEADER_H + hintHeight() + 3;
        int toggleW = toggleWidth(cachedW);
        for (String[] group : LockActionGroups.forVocabulary(vocabulary)) {
            curY += GROUP_HEAD_H;
            int actionCount = group.length - 1;
            for (int j = 0; j < actionCount; j++) {
                int tx = cachedX + PAD + (j % COLS) * (toggleW + 3);
                int ty = curY + (j / COLS) * (TOGGLE_H + TOGGLE_GAP);
                if (inside(mouseX, mouseY, tx, ty, toggleW, TOGGLE_H)) return group[j + 1];
            }
            curY += groupRowsHeight(actionCount) + GROUP_GAP;
        }
        return null;
    }

    public void render(GuiGraphics g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        if (!visible) return;
        List<String[]> groups = LockActionGroups.forVocabulary(vocabulary);

        int contentH = 0;
        for (String[] group : groups) {
            contentH += GROUP_HEAD_H + groupRowsHeight(group.length - 1) + GROUP_GAP;
        }
        contentH -= GROUP_GAP; // no gap after last group

        int popupW = popupWidth(font, screenW);
        int popupH = HEADER_H + hintHeight() + 3 + contentH + DESC_H + FOOTER_H;
        int popupX = screenW / 2 - popupW / 2;
        int popupY = screenH / 2 - popupH / 2;
        cachedX = popupX;
        cachedY = popupY;
        cachedW = popupW;
        cachedH = popupH;

        // Backdrop dim
        g.fill(0, 0, screenW, screenH, 0x88000000);
        // Drop shadow
        g.fill(popupX + 3, popupY + 3, popupX + popupW + 3, popupY + popupH + 3, 0x50000000);
        // Outer border + inner background (matches editor dialog style)
        g.fill(popupX - 1, popupY - 1, popupX + popupW + 1, popupY + popupH + 1, 0xFF333333);
        g.fill(popupX, popupY, popupX + popupW, popupY + popupH, 0xFF1A1A1A);

        // Title with subtle gold underline
        g.drawCenteredString(font, Component.translatable("editor.historystages.lock_actions.title"),
                popupX + popupW / 2, popupY + 5, 0xFFFFFFFF);
        int accentW = 40;
        int accentX = popupX + (popupW - accentW) / 2;
        g.fill(accentX, popupY + 15, accentX + accentW, popupY + 16, 0xFFFFCC00);

        g.drawCenteredString(font, Component.translatable(hintKey()),
                popupX + popupW / 2, popupY + HEADER_H, 0x888888);
        if (fixing) {
            // Right click is invisible until someone tries it, so the second line says what the
            // padlock means instead of leaving it to be discovered.
            g.drawCenteredString(font, Component.translatable("editor.historystages.lock_actions.hint_fixing_effect"),
                    popupX + popupW / 2, popupY + HEADER_H + HINT_H, 0x888888);
        }

        int curY = popupY + HEADER_H + hintHeight() + 3;
        int toggleW = toggleWidth(popupW);
        String hoveredAction = null;

        for (String[] group : groups) {
            // Group header — subtle label with thin separator line
            Component groupLabel = Component.translatable("editor.historystages.lock_actions.group." + group[0]);
            g.drawString(font, groupLabel, popupX + PAD, curY + 1, 0xCCCCCC, false);
            int sepX = popupX + PAD + font.width(groupLabel) + 5;
            int sepY = curY + 4;
            g.fill(sepX, sepY, popupX + popupW - PAD, sepY + 1, 0xFF2E2E2E);
            curY += GROUP_HEAD_H;

            int actionCount = group.length - 1;
            for (int j = 0; j < actionCount; j++) {
                String action = group[j + 1];
                int tx = popupX + PAD + (j % COLS) * (toggleW + 3);
                int ty = curY + (j / COLS) * (TOGGLE_H + TOGGLE_GAP);
                boolean hoveredHere = inside(mouseX, mouseY, tx, ty, toggleW, TOGGLE_H);
                if (hoveredHere) hoveredAction = action;
                renderToggle(g, font, action, tx, ty, toggleW, hoveredHere);
            }
            curY += groupRowsHeight(actionCount) + GROUP_GAP;
        }

        // Description line — shows hovered action's description, or the count
        int descY = popupY + popupH - FOOTER_H - DESC_H + 1;
        g.fill(popupX + PAD, descY - 1, popupX + popupW - PAD, descY, 0xFF2E2E2E);
        Component descText;
        int descColor;
        if (hoveredAction != null) {
            descText = describe(hoveredAction);
            descColor = 0xCCCCCC;
        } else {
            descText = statusLine();
            descColor = 0x888888;
        }
        g.drawCenteredString(font, descText, popupX + popupW / 2, descY + 2, descColor);

        int btnY = popupY + popupH - BTN_H - 6;
        int qBtnW = quickBtnWidth(font);
        int allX = popupX + PAD;
        drawQuickButton(g, font, allX, btnY, qBtnW, "editor.historystages.lock_actions.btn_all", mouseX, mouseY);
        int noneX = allX + qBtnW + 3;
        drawQuickButton(g, font, noneX, btnY, qBtnW, "editor.historystages.lock_actions.btn_none", mouseX, mouseY);

        // Done (gold accent)
        int doneW = doneBtnWidth(font);
        int doneX = popupX + popupW - doneW - PAD;
        boolean doneHov = inside(mouseX, mouseY, doneX, btnY, doneW, BTN_H);
        g.fill(doneX, btnY, doneX + doneW, btnY + BTN_H, doneHov ? 0x50FFCC00 : 0x25FFCC00);
        g.fill(doneX, btnY + BTN_H - 1, doneX + doneW, btnY + BTN_H, doneHov ? 0xFFFFCC00 : 0x80FFCC00);
        g.drawCenteredString(font, Component.translatable("editor.historystages.lock_actions.btn_done"),
                doneX + doneW / 2, btnY + 3, doneHov ? 0xFFFFFF : 0xEEEEEE);
    }

    private void renderToggle(GuiGraphics g, Font font, String action, int tx, int ty, int toggleW,
                              boolean hoveredHere) {
        boolean blocked = current.contains(action);
        boolean isFixed = fixed.contains(action);
        // An entry cannot change a fixed action, so it gets no hover glow and muted colours.
        boolean locked = isFixed && !fixing;
        boolean hovered = hoveredHere && !locked;

        int bg = blocked
                ? (hovered ? 0x40FFCC00 : locked ? 0x18FFCC00 : 0x25FFCC00)
                : (hovered ? 0x25FFFFFF : locked ? 0x08FFFFFF : 0x10FFFFFF);
        g.fill(tx, ty, tx + toggleW, ty + TOGGLE_H, bg);

        int accent = blocked
                ? (hovered ? 0xFFFFCC00 : locked ? 0x60FFCC00 : 0xB0FFCC00)
                : (hovered ? 0x40FFFFFF : 0x20FFFFFF);
        g.fill(tx, ty + TOGGLE_H - 1, tx + toggleW, ty + TOGGLE_H, accent);

        if (isFixed) {
            drawPadlock(g, tx + 3, ty + 3, blocked ? 0xFFFFCC00 : 0xFF9A9A9A);
        } else {
            g.fill(tx + 4, ty + 6, tx + 7, ty + 9, blocked ? 0xFFFFCC00 : 0xFF555555);
        }

        int textColor = locked ? (blocked ? 0xBBBBBB : 0x777777) : (blocked ? 0xFFFFFF : 0x999999);
        g.drawString(font, Component.translatable("editor.historystages.lock_actions.action." + action),
                tx + 10, ty + 3, textColor, false);
    }

    private Component describe(String action) {
        Component name = Component.translatable("editor.historystages.lock_actions.action." + action);
        String tail;
        if (fixing) {
            // Says what a right click does to this action right now.
            tail = fixed.contains(action)
                    ? "editor.historystages.lock_actions.right_click_release"
                    : "editor.historystages.lock_actions.right_click_fix";
        } else {
            tail = fixed.contains(action)
                    ? "editor.historystages.lock_actions.fixed_by_stage"
                    : "editor.historystages.lock_actions.desc." + action;
        }
        return name.copy().append(Component.literal(" — ")).append(Component.translatable(tail));
    }

    private Component statusLine() {
        if (fixing) {
            return Component.translatable("editor.historystages.lock_actions.fixed_status",
                    fixed.size(), vocabulary.size());
        }
        return Component.translatable("editor.historystages.lock_actions.status",
                current.size(), vocabulary.size());
    }

    private int hintHeight() {
        return fixing ? 2 * HINT_H : HINT_H;
    }

    private String hintKey() {
        return fixing ? "editor.historystages.lock_actions.hint_fixing" : "editor.historystages.lock_actions.hint";
    }

    /** A 5x8 padlock: shackle on top, body below. Marks a fixed action or row in every editor popup. */
    public static void drawPadlock(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y, x + 4, y + 1, color);
        g.fill(x, y + 1, x + 1, y + 4, color);
        g.fill(x + 4, y + 1, x + 5, y + 4, color);
        g.fill(x - 1, y + 4, x + 6, y + 8, color);
    }

    private static void drawQuickButton(GuiGraphics g, Font font, int x, int y, int w, String key,
                                        int mouseX, int mouseY) {
        boolean hov = inside(mouseX, mouseY, x, y, w, BTN_H);
        g.fill(x, y, x + w, y + BTN_H, hov ? 0x25FFFFFF : 0x10FFFFFF);
        g.fill(x, y + BTN_H - 1, x + w, y + BTN_H, hov ? 0x80FFFFFF : 0x40FFFFFF);
        g.drawCenteredString(font, Component.translatable(key), x + w / 2, y + 3, hov ? 0xFFFFFF : 0xCCCCCC);
    }

    private static int toggleWidth(int popupW) {
        return (popupW - 2 * PAD - (COLS - 1) * 3) / COLS;
    }

    private static int groupRowsHeight(int actionCount) {
        int rows = (actionCount + COLS - 1) / COLS;
        return rows * TOGGLE_H + (rows - 1) * TOGGLE_GAP;
    }

    /**
     * Popup width grown to fit every piece of text it holds — title, hint, toggle labels, the
     * widest hover-description/status line, and the footer button row — instead of shrinking text
     * into a fixed width.
     */
    private int popupWidth(Font font, int screenW) {
        int maxToggleW = 0;
        int maxLineW = 0;
        for (String action : vocabulary) {
            maxToggleW = Math.max(maxToggleW,
                    font.width(Component.translatable("editor.historystages.lock_actions.action." + action)));
            maxLineW = Math.max(maxLineW, font.width(describe(action)));
            if (fixing) {
                // Both right-click texts, so the box does not jump when an action is fixed.
                maxLineW = Math.max(maxLineW, font.width(Component.translatable(
                        "editor.historystages.lock_actions.action." + action).copy()
                        .append(Component.literal(" — "))
                        .append(Component.translatable("editor.historystages.lock_actions.right_click_release"))));
                maxLineW = Math.max(maxLineW, font.width(Component.translatable(
                        "editor.historystages.lock_actions.action." + action).copy()
                        .append(Component.literal(" — "))
                        .append(Component.translatable("editor.historystages.lock_actions.right_click_fix"))));
            }
        }
        maxLineW = Math.max(maxLineW, font.width(Component.translatable(
                "editor.historystages.lock_actions.status", vocabulary.size(), vocabulary.size())));
        maxLineW = Math.max(maxLineW, font.width(Component.translatable(
                "editor.historystages.lock_actions.fixed_status", vocabulary.size(), vocabulary.size())));
        maxLineW = Math.max(maxLineW, font.width(Component.translatable("editor.historystages.lock_actions.title")));
        maxLineW = Math.max(maxLineW, font.width(Component.translatable(hintKey())));
        if (fixing) {
            maxLineW = Math.max(maxLineW, font.width(
                    Component.translatable("editor.historystages.lock_actions.hint_fixing_effect")));
        }

        int neededToggleW = maxToggleW + 14; // dot + gap (10px) + right margin (4px)
        int neededFromGrid = 2 * PAD + COLS * neededToggleW + (COLS - 1) * 3;
        int neededFromLine = maxLineW + 2 * PAD;
        // Footer: [All][None] on the left, [Done] on the right, with a small gap between the groups.
        int neededFromFooter = 2 * PAD + 2 * quickBtnWidth(font) + 3 + 8 + doneBtnWidth(font);
        int needed = Math.max(MIN_WIDTH, Math.max(Math.max(neededFromGrid, neededFromLine), neededFromFooter));
        // Never wider than the screen: on a very small GUI the box would otherwise spill off both
        // edges (it is screen-centered). Degrades to slight internal overflow, not an off-screen box.
        return Math.min(needed, screenW - 8);
    }

    /** Width for the "All"/"None" footer buttons, grown to fit whichever label is wider. */
    private static int quickBtnWidth(Font font) {
        int w = font.width(Component.translatable("editor.historystages.lock_actions.btn_all"));
        w = Math.max(w, font.width(Component.translatable("editor.historystages.lock_actions.btn_none")));
        return Math.max(34, w + 10);
    }

    /** Width for the "Done" footer button, grown to fit its label. */
    private static int doneBtnWidth(Font font) {
        return Math.max(48, font.width(Component.translatable("editor.historystages.lock_actions.btn_done")) + 10);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
