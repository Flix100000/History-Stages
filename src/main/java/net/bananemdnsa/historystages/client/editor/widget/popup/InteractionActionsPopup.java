package net.bananemdnsa.historystages.client.editor.widget.popup;

import net.bananemdnsa.historystages.data.lock.EntityInteractionLockEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Own popup for restricting an interactionlock entry to specific interaction actions (breed,
 * mount, trade, leash, shear, milk, name, equip, other). Toggles show the <em>blocked</em>
 * actions; all blocked is the default.
 *
 * <p>On confirm it reports the entity id and the blocked actions, or {@code null} for "all
 * blocked" — the default, stored as no filter. An <em>empty</em> list is a real answer and means
 * the entry blocks nothing; the two used to share the empty list and were indistinguishable.
 *
 * <p>{@link #showFixing} opens it for the stage settings instead, where a right click fixes an
 * action for every mob of the stage. On a mob a fixed action shows the stage's value with a
 * padlock and cannot be clicked; on confirm the mob's own value goes back into it, so releasing
 * the action later restores what the mob had.
 */
public class InteractionActionsPopup {

    private static final String[] ACTION_KEYS = EntityInteractionLockEntry.ALL_ACTIONS.toArray(new String[0]);

    private static final int PAD       = 8;
    private static final int WIDTH     = 300;
    private static final int COLS      = 2;
    private static final int HEADER_H  = 18;
    private static final int HINT_H    = 10;
    private static final int TOGGLE_H  = 14;
    private static final int TOGGLE_GAP = 2;
    private static final int FOOTER_H  = 20;

    private final BiConsumer<String, List<String>> onConfirm; // (entityId, blockedActions)

    private boolean visible = false;
    private String entityId = null;
    private List<String> current = new ArrayList<>(); // working set: actions currently BLOCKED
    private int cachedX, cachedY, cachedW, cachedH;

    /** Fixed actions; their value is whatever {@link #current} says. */
    private Set<String> fixed = new LinkedHashSet<>();
    /** True in the stage settings, where fixing is what this popup is for. */
    private boolean fixing = false;
    /** Mob mode: the mob's own list, so its values can go back into the fixed actions. */
    private List<String> own;
    private Consumer<Map<String, Boolean>> onFixed = map -> {};

    public InteractionActionsPopup(BiConsumer<String, List<String>> onConfirm) {
        this.onConfirm = onConfirm;
    }

    public boolean isVisible() { return visible; }

    public void hide() { visible = false; }

    public void show(String entityId, List<String> currentBlocked) {
        show(entityId, currentBlocked, null);
    }

    /** @param fixedValues the stage's fixed actions (true = locked), or null for none */
    public void show(String entityId, List<String> currentBlocked, Map<String, Boolean> fixedValues) {
        this.entityId = entityId;
        this.own = currentBlocked != null ? new ArrayList<>(currentBlocked) : null;
        // Default = all actions blocked (matches "no filter" behaviour)
        open(currentBlocked != null ? currentBlocked : List.of(ACTION_KEYS), fixedValues, false);
    }

    /**
     * Opens the popup for the stage settings.
     *
     * @param fixedValues the fixed actions so far, or null
     * @param onDone      receives the fixed actions with their value, in action order
     */
    public void showFixing(Map<String, Boolean> fixedValues, Consumer<Map<String, Boolean>> onDone) {
        this.entityId = null;
        this.own = null;
        this.onFixed = onDone;
        open(List.of(), fixedValues, true);
    }

    private void open(List<String> blocked, Map<String, Boolean> fixedValues, boolean fixing) {
        this.current = new ArrayList<>(blocked);
        this.fixed = new LinkedHashSet<>();
        if (fixedValues != null) {
            for (Map.Entry<String, Boolean> e : fixedValues.entrySet()) {
                if (!EntityInteractionLockEntry.ALL_ACTIONS.contains(e.getKey())) continue;
                fixed.add(e.getKey());
                setBlocked(e.getKey(), Boolean.TRUE.equals(e.getValue()));
            }
        }
        this.fixing = fixing;
        this.cachedW = 0;
        this.visible = true;
    }

    private void setBlocked(String action, boolean blocked) {
        if (blocked) {
            if (!current.contains(action)) current.add(action);
        } else {
            current.remove(action);
        }
    }

    /** A mob cannot change a fixed action. */
    private boolean locked(String action) {
        return !fixing && fixed.contains(action);
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        int rows = (ACTION_KEYS.length + COLS - 1) / COLS;
        int contentH = rows * TOGGLE_H + (rows - 1) * TOGGLE_GAP;

        int popupW = WIDTH;
        int descMaxWidth = popupW - 2 * PAD - 4;

        // Reserve enough vertical space for the longest possible description (any action).
        int maxDescLines = 1;
        for (String action : ACTION_KEYS) {
            int lines = font.split(describe(action), descMaxWidth).size();
            if (lines > maxDescLines) maxDescLines = lines;
        }
        int descBlockH = maxDescLines * (font.lineHeight + 1) + 4;

        int popupH = HEADER_H + hintHeight() + 3 + contentH + descBlockH + FOOTER_H;
        int popupX = g.guiWidth() / 2 - popupW / 2;
        int popupY = g.guiHeight() / 2 - popupH / 2;

        cachedX = popupX; cachedY = popupY; cachedW = popupW; cachedH = popupH;

        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x88000000);
        g.fill(popupX + 3, popupY + 3, popupX + popupW + 3, popupY + popupH + 3, 0x50000000);
        g.fill(popupX - 1, popupY - 1, popupX + popupW + 1, popupY + popupH + 1, 0xFF333333);
        g.fill(popupX, popupY, popupX + popupW, popupY + popupH, 0xFF1A1A1A);

        g.drawCenteredString(font,
                Component.translatable("editor.historystages.interaction_actions.title"),
                popupX + popupW / 2, popupY + 5, 0xFFFFFFFF);
        int accentW = 40;
        int accentX = popupX + (popupW - accentW) / 2;
        g.fill(accentX, popupY + 15, accentX + accentW, popupY + 16, 0xFFFFCC00);

        if (fixing) {
            // Right click is invisible until someone tries it, so both lines say it.
            g.drawCenteredString(font, Component.translatable("editor.historystages.lock_actions.hint_fixing"),
                    popupX + popupW / 2, popupY + HEADER_H, 0x888888);
            g.drawCenteredString(font, Component.translatable("editor.historystages.lock_actions.hint_fixing_effect"),
                    popupX + popupW / 2, popupY + HEADER_H + HINT_H, 0x888888);
        } else {
            g.drawCenteredString(font,
                    Component.translatable("editor.historystages.interaction_actions.hint"),
                    popupX + popupW / 2, popupY + HEADER_H, 0x888888);
        }

        int curY = popupY + HEADER_H + hintHeight() + 3;
        int toggleW = (popupW - 2 * PAD - (COLS - 1) * 3) / COLS;
        String hoveredAction = null;

        for (int j = 0; j < ACTION_KEYS.length; j++) {
            String action = ACTION_KEYS[j];
            int col = j % COLS;
            int row = j / COLS;
            int tx = popupX + PAD + col * (toggleW + 3);
            int ty = curY + row * (TOGGLE_H + TOGGLE_GAP);

            boolean blocked = current.contains(action);
            boolean hoveredHere = mouseX >= tx && mouseX < tx + toggleW && mouseY >= ty && mouseY < ty + TOGGLE_H;
            if (hoveredHere) hoveredAction = action;
            boolean isLocked = locked(action);
            boolean hovered = hoveredHere && !isLocked;

            int bg = blocked
                    ? (hovered ? 0x40FFCC00 : isLocked ? 0x18FFCC00 : 0x25FFCC00)
                    : (hovered ? 0x25FFFFFF : isLocked ? 0x08FFFFFF : 0x10FFFFFF);
            g.fill(tx, ty, tx + toggleW, ty + TOGGLE_H, bg);

            int accent = blocked
                    ? (hovered ? 0xFFFFCC00 : isLocked ? 0x60FFCC00 : 0xB0FFCC00)
                    : (hovered ? 0x40FFFFFF : 0x20FFFFFF);
            g.fill(tx, ty + TOGGLE_H - 1, tx + toggleW, ty + TOGGLE_H, accent);

            if (fixed.contains(action)) {
                LockActionsPopup.drawPadlock(g, tx + 3, ty + 3, blocked ? 0xFFFFCC00 : 0xFF9A9A9A);
            } else {
                g.fill(tx + 4, ty + 6, tx + 7, ty + 9, blocked ? 0xFFFFCC00 : 0xFF555555);
            }
            int textColor = isLocked ? (blocked ? 0xBBBBBB : 0x777777) : (blocked ? 0xFFFFFF : 0x999999);
            g.drawString(font,
                    Component.translatable("editor.historystages.interaction_actions.action." + action),
                    tx + 10, ty + 3, textColor, false);
        }

        int descY = popupY + popupH - FOOTER_H - descBlockH + 1;
        g.fill(popupX + PAD, descY - 1, popupX + popupW - PAD, descY, 0xFF2E2E2E);
        Component descText;
        int descColor;
        if (hoveredAction != null) {
            descText = describe(hoveredAction);
            descColor = 0xCCCCCC;
        } else if (fixing) {
            descText = Component.translatable("editor.historystages.lock_actions.fixed_status",
                    fixed.size(), ACTION_KEYS.length);
            descColor = 0x888888;
        } else {
            descText = Component.translatable("editor.historystages.interaction_actions.status",
                    current.size(), ACTION_KEYS.length);
            descColor = 0x888888;
        }
        List<FormattedCharSequence> descLines = font.split(descText, descMaxWidth);
        int lineY = descY + 2;
        for (FormattedCharSequence line : descLines) {
            int lineW = font.width(line);
            g.drawString(font, line, popupX + (popupW - lineW) / 2, lineY, descColor, false);
            lineY += font.lineHeight + 1;
        }

        int btnH = 14;
        int btnY = popupY + popupH - btnH - 6;
        int qBtnW = 34;

        int allX = popupX + PAD;
        boolean allHov = mouseX >= allX && mouseX < allX + qBtnW && mouseY >= btnY && mouseY < btnY + btnH;
        g.fill(allX, btnY, allX + qBtnW, btnY + btnH, allHov ? 0x25FFFFFF : 0x10FFFFFF);
        g.fill(allX, btnY + btnH - 1, allX + qBtnW, btnY + btnH, allHov ? 0x80FFFFFF : 0x40FFFFFF);
        g.drawCenteredString(font,
                Component.translatable("editor.historystages.lock_actions.btn_all"),
                allX + qBtnW / 2, btnY + 3, allHov ? 0xFFFFFF : 0xCCCCCC);

        int noneX = allX + qBtnW + 3;
        boolean noneHov = mouseX >= noneX && mouseX < noneX + qBtnW && mouseY >= btnY && mouseY < btnY + btnH;
        g.fill(noneX, btnY, noneX + qBtnW, btnY + btnH, noneHov ? 0x25FFFFFF : 0x10FFFFFF);
        g.fill(noneX, btnY + btnH - 1, noneX + qBtnW, btnY + btnH, noneHov ? 0x80FFFFFF : 0x40FFFFFF);
        g.drawCenteredString(font,
                Component.translatable("editor.historystages.lock_actions.btn_none"),
                noneX + qBtnW / 2, btnY + 3, noneHov ? 0xFFFFFF : 0xCCCCCC);

        int doneW = 48;
        int doneX = popupX + popupW - doneW - PAD;
        boolean doneHov = mouseX >= doneX && mouseX < doneX + doneW && mouseY >= btnY && mouseY < btnY + btnH;
        g.fill(doneX, btnY, doneX + doneW, btnY + btnH, doneHov ? 0x50FFCC00 : 0x25FFCC00);
        g.fill(doneX, btnY + btnH - 1, doneX + doneW, btnY + btnH, doneHov ? 0xFFFFCC00 : 0x80FFCC00);
        g.drawCenteredString(font,
                Component.translatable("editor.historystages.lock_actions.btn_done"),
                doneX + doneW / 2, btnY + 3, doneHov ? 0xFFFFFF : 0xEEEEEE);
    }

    private int hintHeight() {
        return fixing ? 2 * HINT_H : HINT_H;
    }

    /** The description line: on a mob a fixed action says where it is fixed; fixing says what a right click does. */
    private Component describe(String action) {
        String tail;
        if (fixing) {
            tail = fixed.contains(action)
                    ? "editor.historystages.lock_actions.right_click_release"
                    : "editor.historystages.lock_actions.right_click_fix";
        } else {
            tail = fixed.contains(action)
                    ? "editor.historystages.lock_actions.fixed_by_stage"
                    : "editor.historystages.interaction_actions.desc." + action;
        }
        return Component.translatable("editor.historystages.interaction_actions.action." + action)
                .append(Component.literal(" — "))
                .append(Component.translatable(tail));
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        return mouseClicked(mouseX, mouseY, 0);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        int popupW = cachedW, popupH = cachedH;
        int popupX = cachedX, popupY = cachedY;
        if (popupW == 0) return true;

        int btnH = 14;
        int btnY = popupY + popupH - btnH - 6;

        if (button == 0) {
            int doneW = 48;
            int doneX = popupX + popupW - doneW - PAD;
            if (mouseX >= doneX && mouseX < doneX + doneW && mouseY >= btnY && mouseY < btnY + btnH) {
                playClick();
                confirm();
                return true;
            }

            int qBtnW = 34;
            int allX = popupX + PAD;
            if (mouseX >= allX && mouseX < allX + qBtnW && mouseY >= btnY && mouseY < btnY + btnH) {
                playClick();
                setAll(true);
                return true;
            }
            int noneX = allX + qBtnW + 3;
            if (mouseX >= noneX && mouseX < noneX + qBtnW && mouseY >= btnY && mouseY < btnY + btnH) {
                playClick();
                setAll(false);
                return true;
            }
        }

        int curY = popupY + HEADER_H + hintHeight() + 3;
        int toggleW = (popupW - 2 * PAD - (COLS - 1) * 3) / COLS;
        for (int j = 0; j < ACTION_KEYS.length; j++) {
            String action = ACTION_KEYS[j];
            int col = j % COLS;
            int row = j / COLS;
            int tx = popupX + PAD + col * (toggleW + 3);
            int ty = curY + row * (TOGGLE_H + TOGGLE_GAP);
            if (mouseX >= tx && mouseX < tx + toggleW && mouseY >= ty && mouseY < ty + TOGGLE_H) {
                if (button == 1 && fixing) {
                    playClick();
                    if (!fixed.remove(action)) fixed.add(action);
                } else if (button == 0 && !locked(action)) {
                    playClick();
                    setBlocked(action, !current.contains(action));
                }
                return true;
            }
        }

        if (button == 0 && (mouseX < popupX || mouseX > popupX + popupW || mouseY < popupY || mouseY > popupY + popupH)) {
            visible = false;
        }
        return true;
    }

    /** All / None. On a mob the fixed actions keep the stage's value. */
    private void setAll(boolean blocked) {
        for (String action : ACTION_KEYS) {
            if (!locked(action)) setBlocked(action, blocked);
        }
    }

    public boolean keyPressed(int keyCode) {
        if (!visible) return false;
        if (keyCode == 256) { // ESC closes without saving
            visible = false;
            return true;
        }
        return false;
    }

    private void confirm() {
        if (fixing) {
            Map<String, Boolean> result = new LinkedHashMap<>();
            for (String action : ACTION_KEYS) {
                if (fixed.contains(action)) result.put(action, current.contains(action));
            }
            onFixed.accept(result);
            visible = false;
            return;
        }
        // A fixed action shows the stage's value; the mob keeps its own in it.
        List<String> keep = new ArrayList<>(current);
        for (String action : fixed) {
            boolean ownBlocked = own == null || own.contains(action);
            keep.remove(action);
            if (ownBlocked) keep.add(action);
        }
        boolean allBlocked = keep.size() == ACTION_KEYS.length;
        onConfirm.accept(entityId, allBlocked ? null : keep);
        visible = false;
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
