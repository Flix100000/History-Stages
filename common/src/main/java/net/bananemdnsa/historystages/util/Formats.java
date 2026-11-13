package net.bananemdnsa.historystages.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** {@code ChatFormatting} lookups that 26.2 took off the enum. */
public final class Formats {

    private Formats() {}

    /** 1.21.1's {@code ChatFormatting.getByName}: the lower-case name, e.g. "gold" or "bold". */
    public static @Nullable ChatFormatting byName(@Nullable String name) {
        if (name == null) return null;
        try {
            return ChatFormatting.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 1.21.1's {@code ChatFormatting.getColor}: the RGB of a colour code, null for a style code. */
    public static @Nullable Integer colorOf(ChatFormatting format) {
        TextColor color = TextColor.fromLegacyFormat(format);
        return color == null ? null : color.getValue();
    }
}
