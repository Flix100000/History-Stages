package net.bananemdnsa.historystages.util;

import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Colour names from 1.21.1 packs match the way ChatFormatting.getByName matched them. */
class FormatsTest {

    @Test
    void caseAndSeparatorsDoNotMatter() {
        assertEquals(ChatFormatting.DARK_AQUA, Formats.byName("dark_aqua"));
        assertEquals(ChatFormatting.DARK_AQUA, Formats.byName("darkaqua"));
        assertEquals(ChatFormatting.DARK_AQUA, Formats.byName("Dark Aqua"));
        assertEquals(ChatFormatting.GOLD, Formats.byName("GOLD"));
    }

    @Test
    void anUnknownNameIsNull() {
        assertNull(Formats.byName("not a colour"));
        assertNull(Formats.byName(null));
    }
}
