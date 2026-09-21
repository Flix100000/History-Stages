package net.bananemdnsa.historystages.data.graph;

/**
 * A stage's own canvas background, shown in the player view while it is the most recently
 * unlocked stage that has one. Every field may be null, meaning "whatever graph.toml says".
 */
public class CanvasBackgroundStyle {

    /** GRID, SOLID or TEXTURE; null = inherit. */
    public String mode;
    /** Full texture path; null = inherit. */
    public String texture;
    /** {@code #RRGGBB}; null = inherit. */
    public String color;

    public boolean isEmpty() {
        return mode == null && texture == null && color == null;
    }

    public CanvasBackgroundStyle copy() {
        CanvasBackgroundStyle out = new CanvasBackgroundStyle();
        out.mode = mode;
        out.texture = texture;
        out.color = color;
        return out;
    }
}
