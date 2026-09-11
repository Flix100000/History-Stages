package net.bananemdnsa.historystages.api.editor.widget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.DropdownChrome;
import net.bananemdnsa.historystages.api.editor.TabInputContext;
import net.bananemdnsa.historystages.api.editor.TabRenderContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * The editor's card row, as one widget instead of two private copies.
 *
 * <p>It drew twice before this: inline in {@code StageDetailScreen}'s render loop and again as
 * {@code DependencyEditorScreen.renderCardWithText}, and the two did not match — the stage editor
 * lifted a hovered card and slid cards in on a tab switch, the dependency editor did neither. This
 * is the stage editor's version, and the dependency editor gains both.
 *
 * <p>It owns chrome, hover, the staggered slide-in, the marquee, the badge stack and hit testing.
 * It does <strong>not</strong> own scrolling: both screens have their own scrollbar, drag handling
 * and smooth-scroll animation, and pulling those in would make this the screen.
 *
 * <p>One instance per tab. That is also what retires the {@code 100 + i} / {@code 200 + i} hover
 * keys the dependency editor used to keep two tabs' animations apart.
 */
public final class EditorRowList {

    public static final int CARD_HEIGHT = 22;
    public static final int CARD_GAP = 3;

    /** Clearance between the rightmost badge or button and the row's right edge. */
    private static final int SLOT_MARGIN = 2;

    /** Below this a row has no room for a second line, and a subtitle set on it is ignored. */
    private static final int TWO_LINE_MIN_HEIGHT = 30;

    /** Width a dropdown slot adds for its caret. */
    private static final int CARET_SLOT_W = DropdownChrome.CARET_WIDTH;

    /** Content height for a list of plain rows at the default height. */
    public static int heightFor(int rowCount) {
        return rowCount * (CARD_HEIGHT + CARD_GAP);
    }

    /** Draws into the box a row reserved at its left. */
    @FunctionalInterface
    public interface RowPainter {
        void paint(GuiGraphics g, int x, int y, int width, int height);
    }

    /** Describes one row. Called once per visible row, per frame. */
    @FunctionalInterface
    public interface RowBuilder {
        void build(Row row, int index);
    }

    /**
     * A slot's click, told where the slot was drawn.
     *
     * <p>A plain {@code Runnable} is enough for a slot that only flips a value. It is not enough
     * for one that opens something anchored to itself: the row list lays the slots out, so it is
     * the only thing that knows where the popup has to appear.
     */
    @FunctionalInterface
    public interface SlotClick {
        void fire(int x, int y, int width, int height);
    }

    /**
     * A toggle slot's click, told which half was hit.
     *
     * <p>Not a {@code Runnable}: the segmented switch is picked, not flipped. Clicking the side
     * that is already showing has to be a no-op, or a double click undoes itself.
     */
    @FunctionalInterface
    public interface ToggleClick {
        void fire(boolean value);
    }

    /**
     * One row's declaration: what to put in it, not how to draw it.
     *
     * <p>Four of the five slots were read off the seven built-in dependency tabs rather than
     * invented. The toggle joined them for the zone rules, where a row's whole content is one
     * yes-or-no and a button labelled with its own state would have been a third way to draw
     * something the editor already draws one way.
     */
    public static final class Row {
        private int leadingWidth;
        @Nullable
        private RowPainter leadingPainter;
        private String text = "";
        private int indent;
        private String subtitle = "";
        private int subtitleColour = 0x888888;
        private boolean hovered;
        private boolean caretUp;
        private final List<Slot> slots = new ArrayList<>();

        /**
         * Whether the cursor is on this row, for a builder that wants to show something only then
         * — a tooltip, say. Set before the builder runs, so it is this frame's answer and not the
         * last one's.
         */
        public boolean isHovered() {
            return hovered;
        }

        /** Reserves {@code width} pixels at the left and lets the caller paint them. */
        public Row leading(int width, RowPainter painter) {
            this.leadingWidth = width;
            this.leadingPainter = painter;
            return this;
        }

        public Row text(String text) {
            this.text = text == null ? "" : text;
            return this;
        }

        /**
         * Shifts this row's text to the right, for one that depends on the row above it.
         *
         * <p>What a frame around a group would otherwise say. A switch's dependent rows are only
         * shown while it is on, so the indent is all that is left to make the relationship
         * visible — and it does that without drawing a box around anything.
         */
        public Row indent(int pixels) {
            this.indent = Math.max(0, pixels);
            return this;
        }

        /**
         * A second, dimmer line under the text.
         *
         * <p>Drawn only when the list was built with room for it. At the default row height it is
         * ignored, so a row that sets one on a normal list looks exactly as it did before rather
         * than growing a clipped half-line.
         */
        public Row subtitle(String subtitle) {
            this.subtitle = subtitle == null ? "" : subtitle;
            return this;
        }

        /** The same, in a colour of its own — for a subtitle that reports something wrong. */
        public Row subtitle(String subtitle, int colour) {
            this.subtitleColour = colour;
            return subtitle(subtitle);
        }

        /** Short text at the right. Several stack right to left, in declaration order. */
        public Row badge(String text) {
            return badge(text, 0xFFCC00);
        }

        public Row badge(String text, int colour) {
            slots.add(new Slot(text, colour, null, null, false, null, false));
            return this;
        }

        /** A badge that can be clicked, with its own hit zone. */
        public Row button(String label, Runnable onClick) {
            return button(label, null, onClick);
        }

        public Row button(String label, @Nullable String tooltip, Runnable onClick) {
            slots.add(new Slot(label, 0xCCCCCC, tooltip, (x, y, w, h) -> onClick.run(), false, null, false));
            return this;
        }

        /**
         * A button that opens a picker: the same box, plus the caret every other dropdown in the
         * editor wears. The handler is told the slot's rectangle, so the popup can hang off it.
         *
         * @param expanded whether the popup is up, which turns the caret over
         */
        public Row dropdown(String label, @Nullable String tooltip, boolean expanded, SlotClick onClick) {
            slots.add(new Slot(label, 0xCCCCCC, tooltip, onClick, true, null, false));
            this.caretUp = expanded;
            return this;
        }

        /**
         * The on/off switch the rest of the editor uses, as a slot.
         *
         * <p>Here rather than as a button labelled "on"/"off", because that would be a third way
         * of drawing the same question — which is precisely what {@link ToggleControl} was
         * introduced to stop.
         */
        public Row toggle(boolean value, @Nullable String tooltip, ToggleClick onPick) {
            slots.add(new Slot("", 0xCCCCCC, tooltip, null, false, onPick, value));
            return this;
        }
    }

    private record Slot(String text, int colour, @Nullable String tooltip,
                        @Nullable SlotClick onClick, boolean caret,
                        @Nullable ToggleClick onToggle, boolean toggleValue) {

        boolean isToggle() {
            return onToggle != null;
        }
    }

    /**
     * A button's rectangle from the last frame, so a click can find it.
     *
     * <p>{@code valueAt} is set for a toggle only. It closes over the font and the slot's origin
     * from the frame that drew it, which keeps the answer to "which half is this" with
     * {@link ToggleControl} rather than duplicating its geometry here.
     */
    private record ButtonZone(int x, int y, int width, int height, @Nullable SlotClick onClick,
                              @Nullable ToggleClick onToggle,
                              @Nullable java.util.function.DoubleFunction<Boolean> valueAt) {}

    private final int rowHeight;
    private boolean flat;
    private final Map<Integer, Anim> hoverAnim = new HashMap<>();
    /** Caret rotation per row, so a dropdown slot turns over instead of jumping. */
    private final Map<Integer, Anim> caretAnim = new HashMap<>();
    private final List<ButtonZone> buttonZones = new ArrayList<>();
    /** Hover state per toggle slot, keyed by row and slot, so neighbours animate separately. */
    private final Map<Integer, ToggleControl.State> toggleStates = new HashMap<>();
    private int hoveredRow = -1;
    private long hoverStart = 0;
    private long slideStart = 0;

    public EditorRowList() {
        this(CARD_HEIGHT);
    }

    public EditorRowList(int rowHeight) {
        this.rowHeight = rowHeight;
    }

    public int rowHeight() {
        return rowHeight;
    }

    /**
     * Drops the card: no frame, no lift, only the hover wash and the gold edge the config editor
     * uses.
     *
     * <p>For a form — a label and its control — as opposed to a list of things. The two are
     * genuinely different questions and the editor has always drawn them differently; a settings
     * row wearing a card reads as an entry in a list, which is exactly what a name field must not
     * look like.
     *
     * <p>A mode rather than a widget of its own, so there stays one place where an editor row is
     * drawn. Two widgets with a set of slots each drift apart, which is what this class was
     * introduced to stop in the first place.
     */
    public EditorRowList flat() {
        this.flat = true;
        return this;
    }

    /** Content height for this list's row height. */
    public int heightForRows(int rowCount) {
        return rowCount * (rowHeight + CARD_GAP);
    }

    /** Restarts the staggered slide-in. Call when the tab changes or the container changes under it. */
    public void resetSlideIn() {
        slideStart = System.currentTimeMillis();
    }

    /**
     * Draws {@code rowCount} rows starting at {@code ctx.y()}.
     *
     * @return the y below the last row, so the caller can put something after it
     */
    public int render(TabRenderContext ctx, int rowCount, RowBuilder builder) {
        buttonZones.clear();
        long slideElapsed = System.currentTimeMillis() - slideStart;
        int nowHovered = -1;
        int y = ctx.y();

        for (int i = 0; i < rowCount; i++) {
            // Hit-tested against the laid-out rectangle, never the lifted one. Lifting a hovered
            // card moves it out from under the cursor, and hover then flickers between two rows
            // at their shared edge.
            boolean hovered = !ctx.inputBlocked()
                    && ctx.mouseX() >= ctx.x() && ctx.mouseX() < ctx.x() + ctx.width()
                    && ctx.mouseY() >= Math.max(y, ctx.clipTop())
                    && ctx.mouseY() < Math.min(y + rowHeight, ctx.clipBottom());
            if (hovered) nowHovered = i;

            if (y + rowHeight > ctx.clipTop() - 20 && y < ctx.clipBottom() + 20) {
                Row row = new Row();
                row.hovered = hovered;
                builder.build(row, i);
                drawRow(ctx, row, i, y, hovered, slideElapsed);
            }
            y += rowHeight + CARD_GAP;
        }

        if (nowHovered != hoveredRow) {
            hoveredRow = nowHovered;
            hoverStart = System.currentTimeMillis();
        }
        return y;
    }

    /** Which row the cursor is over, or -1. Pure arithmetic — safe before the first frame. */
    public int rowAt(TabInputContext ctx, int rowCount) {
        if (ctx.mouseX() < ctx.x() || ctx.mouseX() >= ctx.x() + ctx.width()) return -1;
        if (ctx.mouseY() < ctx.clipTop() || ctx.mouseY() >= ctx.clipBottom()) return -1;
        int y = ctx.y();
        for (int i = 0; i < rowCount; i++) {
            if (ctx.mouseY() >= y && ctx.mouseY() < y + rowHeight) return i;
            y += rowHeight + CARD_GAP;
        }
        return -1;
    }

    /**
     * Fires whichever button slot the cursor is over.
     *
     * <p>Reads the zones the last {@link #render} recorded, which is sound because a click always
     * follows a frame drawn with the same layout.
     *
     * @return true when a button was hit, so the caller stops there
     */
    public boolean mouseClicked(TabInputContext ctx) {
        for (ButtonZone zone : buttonZones) {
            if (ctx.mouseX() < zone.x() || ctx.mouseX() >= zone.x() + zone.width()
                    || ctx.mouseY() < zone.y() || ctx.mouseY() >= zone.y() + zone.height()) {
                continue;
            }
            if (zone.onToggle() != null) {
                Boolean picked = zone.valueAt().apply(ctx.mouseX());
                // Null is the gap between the two halves. Swallowing the click there would look
                // like the switch ignored it; letting it through changes nothing either way.
                if (picked == null) return true;
                zone.onToggle().fire(picked);
                return true;
            }
            // The accessor hands back the handler; fire() is what runs it.
            zone.onClick().fire(zone.x(), zone.y(), zone.width(), zone.height());
            return true;
        }
        return false;
    }

    private void drawRow(TabRenderContext ctx, Row row, int index, int y, boolean hovered,
                         long slideElapsed) {
        GuiGraphics g = ctx.graphics();
        Font font = ctx.font();

        float slide = 1.0f;
        if (slideElapsed < 400) {
            float delay = Math.min(index * 25.0f, 200.0f);
            float progress = Math.min(1.0f, Math.max(0, slideElapsed - delay) / 200.0f);
            slide = 1.0f - (1.0f - progress) * (1.0f - progress);
        }
        float hoverProgress = Ease.outCubic(hoverAnim.computeIfAbsent(index, k -> new Anim())
                .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

        int cardY = y + (flat ? 0 : (int) (hoverProgress * -1.5f));
        int left = ctx.x() + (int) ((1.0f - slide) * 15);
        int right = ctx.x() + ctx.width();
        boolean twoLine = !row.subtitle.isEmpty() && rowHeight >= TWO_LINE_MIN_HEIGHT;
        int textY = twoLine ? cardY + 5 : cardY + (rowHeight - 8) / 2;

        if (flat) {
            if (hoverProgress > 0.01f) {
                g.fill(left, cardY, right, cardY + rowHeight,
                        Fade.rgba(0xFFFFFF, 0.082f * hoverProgress * slide));
                g.fill(left, cardY, left + 1, cardY + rowHeight,
                        Fade.rgba(0xFFCC00, hoverProgress * 0.8f * slide));
            }
        } else {
            int borderAlpha = (int) ((0x30 + hoverProgress * 0x20) * slide);
            int bgAlpha = (int) ((0x20 + hoverProgress * 0x18) * slide);
            g.fill(left, cardY, right, cardY + rowHeight, (borderAlpha << 24) | 0xFFFFFF);
            g.fill(left + 1, cardY + 1, right - 1, cardY + rowHeight - 1, (bgAlpha << 24) | 0xFFFFFF);
            if (hoverProgress > 0.01f) {
                g.fill(left, cardY, left + 2, cardY + rowHeight,
                        ((int) (hoverProgress * 0xCC) << 24) | 0xFFCC00);
            }
        }

        if (row.leadingPainter != null) {
            row.leadingPainter.paint(g, left + 3, cardY + 3, row.leadingWidth, rowHeight - 6);
        }

        // The old hand-written rows kept two pixels clear of the right edge; without it a button
        // sits flush against the scissor and loses its last column.
        int used = SLOT_MARGIN;
        for (int i = row.slots.size() - 1; i >= 0; i--) {
            Slot slot = row.slots.get(i);
            int slotW = slot.isToggle()
                    ? ToggleControl.width(font)
                    : font.width(slot.text()) + 6 + (slot.caret() ? CARET_SLOT_W : 0);
            int slotX = right - used - slotW;
            if (slot.isToggle()) {
                int toggleY = cardY + (rowHeight - ToggleControl.height()) / 2;
                ToggleControl.State state = toggleStates.computeIfAbsent(
                        index * 16 + i, k -> new ToggleControl.State());
                state.update(slot.toggleValue(), ctx.inputBlocked() ? null
                        : ToggleControl.segmentAt(font, slotX, toggleY, ctx.mouseX(), ctx.mouseY()));
                ToggleControl.draw(g, font, slotX, toggleY, slot.toggleValue(), state, false);

                final int originX = slotX;
                buttonZones.add(new ButtonZone(slotX, toggleY, slotW, ToggleControl.height(),
                        null, slot.onToggle(), mx -> ToggleControl.valueAt(font, originX, mx)));
                if (slot.tooltip() != null
                        && ctx.mouseX() >= slotX && ctx.mouseX() < slotX + slotW
                        && ctx.mouseY() >= toggleY && ctx.mouseY() < toggleY + ToggleControl.height()) {
                    ctx.tooltip("row." + index + ".slot." + i, slot.tooltip());
                }
            } else if (slot.onClick() == null) {
                g.drawString(font, slot.text(), slotX, textY, slot.colour(), false);
            } else {
                boolean slotHovered = !ctx.inputBlocked()
                        && ctx.mouseX() >= slotX && ctx.mouseX() < slotX + slotW
                        && ctx.mouseY() >= cardY + 3 && ctx.mouseY() < cardY + rowHeight - 3;
                g.fill(slotX, cardY + 3, slotX + slotW, cardY + rowHeight - 3,
                        slotHovered ? 0xFF3D3520 : 0xFF2A2A2A);
                g.drawString(font, slot.text(), slotX + 3, textY,
                        slotHovered ? 0xFFCC00 : 0xCCCCCC, false);
                if (slot.caret()) {
                    float flip = caretAnim.computeIfAbsent(index, k -> new Anim())
                            .ramp(row.caretUp ? 1.0f : 0.0f, Timing.POPUP_MS);
                    DropdownChrome.drawCaret(g, slotX + slotW - 8, cardY + rowHeight / 2 - 2,
                            slotHovered ? 0xFFDDDDDD : 0xFF999999, flip);
                }
                buttonZones.add(new ButtonZone(slotX, cardY + 3, slotW, rowHeight - 6,
                        slot.onClick(), null, null));
                if (slotHovered && slot.tooltip() != null) {
                    ctx.tooltip("row." + index + ".slot." + i, slot.tooltip());
                }
            }
            used += slotW + 2;
        }

        int textX = left + row.indent + (row.leadingPainter != null ? row.leadingWidth + 6 : 6);
        int available = right - textX - 4 - used;
        drawText(g, font, row.text, textX, cardY, textY, available, index, hovered);

        // Cut rather than marqueed: two lines chasing each other under one cursor is noise, and
        // the subtitle is a summary — losing its tail costs less than the movement.
        if (twoLine && available > 0) {
            g.drawString(font, font.plainSubstrByWidth(row.subtitle, available),
                    textX, cardY + 16, row.subtitleColour, false);
        }
    }

    private void drawText(GuiGraphics g, Font font, String text, int x, int cardY, int textY,
                          int available, int index, boolean hovered) {
        int colour = hovered ? 0xFFFFFF : 0xBBBBBB;
        int textWidth = font.width(text);
        if (textWidth <= available) {
            g.drawString(font, text, x, textY, colour, false);
            return;
        }
        if (hovered && index == hoveredRow) {
            long elapsed = System.currentTimeMillis() - hoverStart;
            if (elapsed > Timing.MARQUEE_DELAY_MS) {
                float progress = (elapsed - Timing.MARQUEE_DELAY_MS) / 1000.0f * Timing.MARQUEE_SPEED;
                int span = textWidth - available + 10;
                float cycle = span * 2.0f;
                float position = progress % cycle;
                int offset = position <= span ? (int) position : (int) (cycle - position);
                g.enableScissor(x, cardY, x + available, cardY + rowHeight);
                g.drawString(font, text, x - offset, textY, colour, false);
                g.disableScissor();
                return;
            }
        }
        g.drawString(font, font.plainSubstrByWidth(text, available - 8) + "...", x, textY, colour, false);
    }
}
