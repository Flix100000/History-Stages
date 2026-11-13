package net.bananemdnsa.historystages.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

/**
 * The typed key check 26.2 dropped from {@code CompoundTag}. 1.21.1's
 * {@code contains(key, type)} matched the tag id exactly, except that 99 meant "any number".
 * The code reading stage data still relies on that, so the check lives on here.
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
