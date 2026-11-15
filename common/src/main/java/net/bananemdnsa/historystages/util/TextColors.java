package net.bananemdnsa.historystages.util;

/**
 * Text colours for the font. The editor writes plain {@code 0xRRGGBB} everywhere, with no alpha,
 * and the font draws nothing for alpha 0, so an alpha below 4 is made opaque here.
 */
public final class TextColors {

    private TextColors() {}

    /** Alpha below 4 counts as unset and becomes opaque. */
    public static int legacy(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }
}
