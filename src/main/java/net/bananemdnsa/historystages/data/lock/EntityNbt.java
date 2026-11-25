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

    public static Predicate<JsonObject> matcherFor(Entity entity) {
        return new Predicate<>() {
            private CompoundTag saved;
            private boolean failed;

            @Override
            public boolean test(JsonObject criteria) {
                if (saved == null && !failed) {
                    try {
                        saved = entity.saveWithoutId(new CompoundTag());
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
