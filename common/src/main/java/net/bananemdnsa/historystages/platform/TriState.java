package net.bananemdnsa.historystages.platform;

/**
 * Yes, no, or "leave it to vanilla", shaped like NeoForge's {@code TriState} so handlers written
 * against that one carry over with only the import changed. Fabric's own TriState has the same
 * three values; its bridge converts at the edge.
 */
public enum TriState {
    TRUE,
    DEFAULT,
    FALSE;

    public boolean isTrue() {
        return this == TRUE;
    }

    public boolean isDefault() {
        return this == DEFAULT;
    }

    public boolean isFalse() {
        return this == FALSE;
    }
}
