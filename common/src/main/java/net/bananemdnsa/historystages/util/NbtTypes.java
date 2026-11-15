package net.bananemdnsa.historystages.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

/**
 * A typed key check for {@code CompoundTag}: the tag id has to match exactly, except that 99
 * means "any number". The code reading stage data relies on that rule.
 */
public final class NbtTypes {

    public static final int ANY_NUMERIC = 99;

    private NbtTypes() {}

    public static boolean has(CompoundTag tag, String key, int type) {
        Tag value = tag.get(key);
        if (value == null) return false;
        if (type == ANY_NUMERIC) return value instanceof NumericTag;
        return value.getId() == type;
    }
}
