package net.bananemdnsa.historystages.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** {@code ChatFormatting} lookups that 26.2 took off the enum. */
public final class Formats {

    private Formats() {}

    /**
     * 1.21.1's {@code ChatFormatting.getByName}: case and anything but letters are ignored, so
     * "gold", "dark_aqua", "darkaqua" and "Dark Aqua" all match.
     */
    public static @Nullable ChatFormatting byName(@Nullable String name) {
        if (name == null) return null;
        String wanted = clean(name);
        for (ChatFormatting format : ChatFormatting.values()) {
            if (clean(format.name()).equals(wanted)) return format;
        }
        return null;
    }

    private static String clean(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    /** 1.21.1's {@code ChatFormatting.getColor}: the RGB of a colour code, null for a style code. */
    public static @Nullable Integer colorOf(ChatFormatting format) {
        TextColor color = TextColor.fromLegacyFormat(format);
        return color == null ? null : color.getValue();
    }
}
