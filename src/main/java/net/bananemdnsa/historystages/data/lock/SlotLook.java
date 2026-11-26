package net.bananemdnsa.historystages.data.lock;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

/**
 * How an overlay slot looks: a tint, an optional symbol on top, or a texture of the pack's own
 * that replaces both. Every field has a default, so an empty block is the original look.
 */
public class SlotLook {

    public enum Icon {
        @SerializedName("lock") LOCK,
        @SerializedName("item") ITEM,
        @SerializedName("none") NONE
    }

    public enum Size {
        @SerializedName("small") SMALL,
        @SerializedName("medium") MEDIUM,
        @SerializedName("full") FULL
    }

    public static final String DEFAULT_COLOR = "#000000";
    public static final int DEFAULT_OPACITY = 50;

    private String color;
    private Integer opacity;
    private Icon icon;
    private String item;
    private Size size;
    private String texture;

    public String color() { return color != null ? color : DEFAULT_COLOR; }
    public int opacity() { return opacity != null ? clamp(opacity) : DEFAULT_OPACITY; }
    public Icon icon() { return icon != null ? icon : Icon.LOCK; }
    public String item() { return item != null ? item : ""; }
    public Size size() { return size != null ? size : Size.MEDIUM; }
    public String texture() { return texture != null ? texture : ""; }

    public void setColor(String color) { this.color = color; }
    public void setOpacity(int opacity) { this.opacity = clamp(opacity); }
    public void setIcon(Icon icon) { this.icon = icon; }
    public void setItem(String item) { this.item = item; }
    public void setSize(Size size) { this.size = size; }
    public void setTexture(String texture) { this.texture = texture; }

    /** The tint as ARGB. A colour that does not parse is black, so a typo never breaks drawing. */
    public int argb() {
        int alpha = Math.round(opacity() * 255 / 100f);
        return (alpha << 24) | (rgb() & 0xFFFFFF);
    }

    public int rgb() {
        String hex = color().startsWith("#") ? color().substring(1) : color();
        if (hex.length() != 6) return 0;
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public boolean isDefault() {
        return rgb() == 0 && opacity() == DEFAULT_OPACITY && icon() == Icon.LOCK
                && item().isEmpty() && size() == Size.MEDIUM && texture().isEmpty();
    }

    public SlotLook copy() {
        SlotLook copy = new SlotLook();
        copy.color = color;
        copy.opacity = opacity;
        copy.icon = icon;
        copy.item = item;
        copy.size = size;
        copy.texture = texture;
        return copy;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SlotLook other)) return false;
        return argb() == other.argb() && icon() == other.icon() && item().equals(other.item())
                && size() == other.size() && texture().equals(other.texture());
    }

    @Override
    public int hashCode() {
        return Objects.hash(argb(), icon(), item(), size(), texture());
    }
}
