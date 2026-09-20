package net.bananemdnsa.historystages.data.lock;

/**
 * The three shapes a zone can be drawn with.
 *
 * <p>The serialized names are the contract with the stage file and must not be renamed — a pack
 * written today has them on disk.
 */
public enum ZoneShapeType {
    CUBE("cube"),
    SPHERE("sphere"),
    CYLINDER("cylinder");

    private final String serializedName;

    ZoneShapeType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    /**
     * The type for this name, or {@link #CUBE} when the name is unknown or missing.
     *
     * <p>A fallback rather than an exception: stage files are hand-written, and a typo in one
     * shape should not make the whole file unreadable.
     */
    public static ZoneShapeType fromSerializedName(String name) {
        if (name == null) return CUBE;
        for (ZoneShapeType type : values()) {
            if (type.serializedName.equalsIgnoreCase(name)) return type;
        }
        return CUBE;
    }
}
