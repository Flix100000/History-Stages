package net.bananemdnsa.historystages.api.editor.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The editor's badge: a short label in a 1px frame over a very dark fill, all three in one hue.
 *
 * <p>The editor used to draw three kinds side by side — bracketed text ("[NBT]"), frameless
 * translucent pills, and the framed tag the style editor uses for inherited preset values. This
 * is the framed one, because it reads as a label and not as a button or as part of the text.
 *
 * <p>Callers give the text colour; the frame and fill are darker shades of it, so every badge in
 * a row keeps its own hue without anyone picking three colours. Square brackets around the label
 * are dropped, since the frame does their job — that also covers translations and addons that
 * still pass "[x]".
 */
public final class Badge {

    public static final int HEIGHT = 12;
    private static final int PAD = 3;

    private static final float FRAME_SHADE = 0.5f;
    private static final float FILL_SHADE = 0.16f;

    private Badge() {}

    /** Width the badge for {@code text} takes up. */
    public static int width(Font font, String text) {
        return font.width(label(text)) + PAD * 2;
    }

    /**
     * Draws the badge with its top-left corner at (x, y).
     *
     * @return x just past the badge
     */
    public static int draw(GuiGraphics g, Font font, String text, int x, int y, int colour) {
        int w = width(font, text);
        draw(g, font, text, x, y, w, colour);
        return x + w;
    }

    /** The same, stretched to {@code width} with the text centred — for badges kept in a column. */
    public static void draw(GuiGraphics g, Font font, String text, int x, int y, int width, int colour) {
        draw(g, font, text, x, y, width, colour, shade(colour, FRAME_SHADE), shade(colour, FILL_SHADE));
    }

    /** With frame and fill given outright, for a badge whose shades were picked by hand. */
    public static void draw(GuiGraphics g, Font font, String text, int x, int y, int width,
                            int colour, int frame, int fill) {
        String label = label(text);
        g.fill(x, y, x + width, y + HEIGHT, frame);
        g.fill(x + 1, y + 1, x + width - 1, y + HEIGHT - 1, fill);
        g.drawString(font, label, x + (width - font.width(label)) / 2, y + 2, 0xFF000000 | colour, false);
    }

    /** True when (mx, my) is on a badge of {@code width} drawn at (x, y). */
    public static boolean contains(int x, int y, int width, double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + HEIGHT;
    }

    /**
     * The text without surrounding brackets. A leading colour code is dropped with them: the
     * badge's own colour already says it, and "§6[NBT]" would otherwise keep its bracket.
     */
    static String label(String text) {
        String s = text;
        while (s.length() >= 2 && s.charAt(0) == '§') s = s.substring(2);
        if (s.length() >= 2 && s.charAt(0) == '[' && s.charAt(s.length() - 1) == ']') {
            return s.substring(1, s.length() - 1);
        }
        return text;
    }

    private static int shade(int colour, float factor) {
        int r = (int) (((colour >> 16) & 0xFF) * factor);
        int gr = (int) (((colour >> 8) & 0xFF) * factor);
        int b = (int) ((colour & 0xFF) * factor);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }
}
