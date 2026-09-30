package net.bananemdnsa.historystages.data.scroll;

/**
 * How the open scroll marks its chapters along the top of the parchment.
 *
 * <p>Words came first; the icons arrived later and became the default because they never need
 * shortening, whatever a translation costs.
 */
public enum OpenScrollTabStyle {

    /** One small ink icon per chapter, the name as a tooltip. */
    ICONS("icons"),
    /** The chapter names written out, inactive ones shortened when the row runs out of room. */
    WORDS("words");

    private final String id;

    OpenScrollTabStyle(String id) {
        this.id = id;
    }

    public String serialize() {
        return id;
    }

    /** The style with this id, or {@link #ICONS} for an unknown or missing one. */
    public static OpenScrollTabStyle parse(String raw) {
        if (raw == null) return ICONS;
        String trimmed = raw.trim();
        for (OpenScrollTabStyle style : values()) {
            if (style.id.equalsIgnoreCase(trimmed)) return style;
        }
        return ICONS;
    }
}
