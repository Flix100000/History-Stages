package net.bananemdnsa.historystages.client.editor.zone;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The strip of section names down the left of the zone screen.
 *
 * <p>Each entry carries a count of how much inside it is switched on, and that number is what
 * makes splitting a zone into sections bearable at all. Without it you would have to click through
 * all five to answer "is something still on somewhere" — and for a zone, the one thing in this
 * editor that can hurt a player, that is the question asked most often.
 *
 * <p>Zero is written as a dash rather than "0". This is an overview, not a statistic.
 */
public final class ZoneSectionRail {

    public static final int WIDTH = 92;
    public static final int ENTRY_H = 20;

    private static final int TEXT = 0xFF999999;
    private static final int TEXT_ACTIVE = 0xFFFFFFFF;
    private static final int COUNT = 0xFF777777;
    private static final int ACCENT = 0xFFCC00;
    private static final int EDGE = 0xFF333333;

    /** One entry. A {@code count} below zero hides the number — for a section with nothing to count. */
    public record Section(String title, int count) {}

    private final Map<Integer, Anim> hover = new HashMap<>();

    public int height(int sectionCount) {
        return sectionCount * ENTRY_H;
    }

    public void render(GuiGraphics g, Font font, int x, int y, List<Section> sections,
                       int active, int mouseX, int mouseY, boolean blocked) {
        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);
            int top = y + i * ENTRY_H;
            boolean hovered = !blocked && mouseX >= x && mouseX < x + WIDTH
                    && mouseY >= top && mouseY < top + ENTRY_H;
            float hp = Ease.outCubic(hover.computeIfAbsent(i, k -> new Anim())
                    .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

            if (i == active) {
                g.fill(x, top, x + WIDTH, top + ENTRY_H, Fade.rgba(ACCENT, 0.12f));
                g.fill(x, top, x + 1, top + ENTRY_H, 0xFF000000 | ACCENT);
            } else if (hp > 0.01f) {
                g.fill(x, top, x + WIDTH, top + ENTRY_H, Fade.rgba(0xFFFFFF, 0.082f * hp));
                g.fill(x, top, x + 1, top + ENTRY_H, Fade.rgba(ACCENT, hp * 0.8f));
            }

            g.drawString(font, section.title(), x + 8, top + (ENTRY_H - 8) / 2,
                    i == active || hovered ? TEXT_ACTIVE : TEXT, false);

            if (section.count() >= 0) {
                String number = section.count() == 0 ? "–" : String.valueOf(section.count());
                g.drawString(font, number, x + WIDTH - 8 - font.width(number),
                        top + (ENTRY_H - 8) / 2, COUNT, false);
            }
        }

        g.fill(x + WIDTH, y, x + WIDTH + 1, y + height(sections.size()), EDGE);
    }

    /** Which section the cursor is over, or -1. Pure arithmetic — safe before the first frame. */
    public int sectionAt(int x, int y, int sectionCount, double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + WIDTH) return -1;
        if (mouseY < y) return -1;
        int index = (int) ((mouseY - y) / ENTRY_H);
        return index < sectionCount ? index : -1;
    }
}
