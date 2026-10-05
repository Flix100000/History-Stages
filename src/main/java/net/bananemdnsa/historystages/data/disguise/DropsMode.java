package net.bananemdnsa.historystages.data.disguise;

import java.util.Locale;

/**
 * What breaking a disguised block gives the player. {@link #REAL} keeps every existing lock rule;
 * {@link #DISGUISE} makes the block break, need a tool and drop exactly like its disguise.
 */
public enum DropsMode {
    REAL,
    DISGUISE;

    /** Null and anything unknown read as {@link #REAL}, the mode that changes nothing. */
    public static DropsMode parse(String raw) {
        if (raw == null) return REAL;
        return "disguise".equals(raw.trim().toLowerCase(Locale.ROOT)) ? DISGUISE : REAL;
    }

    public String serialize() {
        return name().toLowerCase(Locale.ROOT);
    }
}
