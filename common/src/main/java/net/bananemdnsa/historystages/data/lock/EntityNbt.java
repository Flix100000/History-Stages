package net.bananemdnsa.historystages.data.lock;

import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.data.NbtMatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

import java.util.function.Predicate;

/**
 * Builds the NBT check the lock engine hands to entity entries. The entity is only saved once,
 * and only when some entry actually has a filter to compare — most checks never get that far.
 */
public final class EntityNbt {

    private EntityNbt() {}

    /**
     * What the entity saves, the same tag 1.21.1's {@code saveWithoutId(new CompoundTag())} gave.
     * 26.2 writes entities through a {@code ValueOutput}; the tag one keeps registry-backed values
     * (variants, items) readable by name.
     */
    public static CompoundTag save(Entity entity) {
        net.minecraft.world.level.storage.TagValueOutput out =
                net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                        net.minecraft.util.ProblemReporter.DISCARDING, entity.registryAccess());
        entity.saveWithoutId(out);
        return out.buildResult();
    }

    public static Predicate<JsonObject> matcherFor(Entity entity) {
        return new Predicate<>() {
            private CompoundTag saved;
            private boolean failed;

            @Override
            public boolean test(JsonObject criteria) {
                if (saved == null && !failed) {
                    try {
                        saved = save(entity);
                    } catch (RuntimeException e) {
                        // Some modded entities cannot save on the client. No data means no match,
                        // and the server, which can save, still has the final word.
                        failed = true;
                    }
                }
                return saved != null && NbtMatcher.matchesTag(saved, criteria);
            }
        };
    }
}
