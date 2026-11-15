package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Draws popups and tooltips once the screen has finished drawing, above everything else.
 *
 * <p>The 26.2 GUI has no depth. Whatever is drawn later and overlaps an element goes on top of
 * it, so a popup drawn halfway through a screen is buried by everything that follows: the search
 * bar's filter list sat under the very results it filters. Queued here, it is drawn after the
 * screen, from {@code GuiDeferredOverlayMixin}, or by a modal before it covers its parent.
 *
 * <p>{@code depth} keeps the order the old z values gave: a tooltip (500) still covers an open
 * dropdown (400) whichever was queued first. Equal depths draw in queue order.
 */
public final class DeferredOverlay {

    public static final int POPUP = 400;
    /** Above the popups: a per-option tooltip is drawn while a dropdown is open. */
    public static final int TOOLTIP = 500;

    private record Entry(int depth, Matrix3x2f pose, Consumer<GuiGraphicsExtractor> draw) {}

    private static final List<Entry> QUEUE = new ArrayList<>();
    private static GuiGraphicsExtractor owner;

    private DeferredOverlay() {}

    /** Queues {@code draw} with the current transform; it runs on top of this frame. */
    public static void add(GuiGraphicsExtractor g, int depth, Consumer<GuiGraphicsExtractor> draw) {
        if (owner != g) {
            QUEUE.clear();
            owner = g;
        }
        QUEUE.add(new Entry(depth, new Matrix3x2f(g.pose()), draw));
    }

    /** Draws what is queued, above everything drawn so far. */
    public static void flush(GuiGraphicsExtractor g) {
        if (owner != g) {
            QUEUE.clear();
            return;
        }
        // A popup may queue its own tooltip while it draws; that one goes on top in the same pass.
        while (!QUEUE.isEmpty()) {
            List<Entry> pending = new ArrayList<>(QUEUE);
            QUEUE.clear();
            pending.sort(Comparator.comparingInt(Entry::depth));
            for (Entry entry : pending) {
                g.nextStratum();
                g.pose().pushMatrix();
                g.pose().set(entry.pose());
                entry.draw().accept(g);
                g.pose().popMatrix();
            }
        }
    }
}
