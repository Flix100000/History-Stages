package net.bananemdnsa.historystages.util;

/**
 * Text colours as 1.21.1's font read them. Up to 1.21.1 the font made any colour with an alpha
 * below 4 opaque, so the editor passes plain {@code 0xRRGGBB} everywhere. 26.2 draws nothing
 * for alpha 0, which would leave that text invisible.
 */
public final class TextColors {

    private TextColors() {}

    /** The same rule as 1.21.1's {@code Font.adjustColor}. */
    public static int legacy(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }
}
