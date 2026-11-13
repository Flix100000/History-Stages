package net.bananemdnsa.historystages.util;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.stream.Stream;

/**
 * Block entity data through 26.2's {@link ValueInput}/{@link ValueOutput}, keeping the 1.21.1
 * code that reads and writes a {@link CompoundTag}. Every key of the tag becomes a field of its
 * own, so a pedestal saved by 1.21.1 loads unchanged and is written back in the same shape.
 */
public final class LegacyNbt {

    private LegacyNbt() {}

    /** Passes a compound's keys straight through as the fields of the surrounding record. */
    public static final MapCodec<CompoundTag> FLAT = new MapCodec<>() {
        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.empty();
        }

        @Override
        public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops, MapLike<T> input) {
            CompoundTag tag = new CompoundTag();
            input.entries().forEach(entry -> ops.getStringValue(entry.getFirst()).result().ifPresent(key -> {
                Tag value = ops.convertTo(NbtOps.INSTANCE, entry.getSecond());
                if (value != null) tag.put(key, value);
            }));
            return DataResult.success(tag);
        }

        @Override
        public <T> RecordBuilder<T> encode(CompoundTag input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            for (String key : input.keySet()) {
                Tag value = input.get(key);
                if (value != null) prefix.add(key, NbtOps.INSTANCE.convertTo(ops, value));
            }
            return prefix;
        }
    };

    public static void write(ValueOutput output, CompoundTag tag) {
        output.store(FLAT, tag);
    }

    public static CompoundTag read(ValueInput input) {
        return input.read(FLAT).orElseGet(CompoundTag::new);
    }
}
