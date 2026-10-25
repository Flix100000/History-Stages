package net.bananemdnsa.historystages.api.editor.widget;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The editor's tick box: grey frame when off, gold frame and a gold tick written on when on, a
 * bar for "some of what is below". The filter menu drew it for itself; an addon listing things to
 * tick had nothing but a hand-painted copy, which drifts the first time the editor's colours move.
 *
 * <p>Usage: one {@link State} per box, {@link State#update} once a frame, then {@link #draw}.
 */
public final class Checkbox {

    public static final int SIZE = 10;

    public enum Mark { OFF, ON, PARTIAL }

    private static final int FRAME_OFF = 0xFF666666;
    private static final int FRAME_ON = 0xFFFFCC00;
    private static final int FILL_OFF = 0xFF0D0D0D;
    private static final int FILL_ON = 0xFF2A2510;
    private static final int TICK = 0xFFCC00;

    /** The tick's ramp, held by whoever draws the box so a list of them animates separately. */
    public static final class State {
        private final Anim check = new Anim();
        // What a box fading out still shows: a bar unticked keeps fading as a bar, not as a tick.
        private Mark shown = Mark.ON;

        public void update(Mark mark) {
            if (mark != Mark.OFF) shown = mark;
            check.ramp(mark != Mark.OFF, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS);
        }
    }

    private Checkbox() {
    }

    /**
     * @param dimmed for a box whose answer comes from somewhere above it – drawn at a little under
     *               half strength, so it reads as "covered" rather than "ticked here"
     */
    public static void draw(GuiGraphics g, int x, int y, Mark mark, State state, boolean dimmed) {
        if (mark == Mark.OFF) mark = state.shown;
        float check = Ease.outCubic(state.check.value());
        float strength = dimmed ? 0.45f : 1.0f;
        g.fill(x, y, x + SIZE, y + SIZE, Fade.alpha(Fade.mix(FRAME_OFF, FRAME_ON, check), strength));
        g.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, Fade.alpha(Fade.mix(FILL_OFF, FILL_ON, check), strength));
        if (check <= 0.001f) return;
        int ink = Fade.alpha(Fade.rgba(TICK, check), strength);
        if (mark == Mark.PARTIAL) {
            g.fill(x + 2, y + 4, x + SIZE - 2, y + 6, ink);
            return;
        }
        // Drawn left to right so the stroke reads as being written on.
        int steps = Math.max(1, Math.round(5 * check));
        if (steps > 0) g.fill(x + 3, y + 5, x + 4, y + 8, ink);
        if (steps > 1) g.fill(x + 4, y + 6, x + 5, y + 9, ink);
        if (steps > 2) g.fill(x + 5, y + 5, x + 6, y + 8, ink);
        if (steps > 3) g.fill(x + 6, y + 4, x + 7, y + 7, ink);
        if (steps > 4) g.fill(x + 7, y + 3, x + 8, y + 6, ink);
    }
}
