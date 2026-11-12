package net.bananemdnsa.historystages.platform;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

/**
 * A registry entry that is declared before the registry is filled, matching the shape of
 * NeoForge's DeferredHolder so the call sites read the same on both loaders.
 *
 * <p>Reading it before the registry has been filled throws rather than returning null. A null
 * slipping through would surface much later as a missing block or item with nothing pointing back
 * at the initializer order that caused it.
 */
public final class DeferredHolder<T, R extends T> implements Supplier<R> {

    private final RegistrySupplier<R> entry;

    DeferredHolder(RegistrySupplier<R> entry) {
        this.entry = entry;
    }

    @Override
    public R get() {
        if (!entry.isPresent()) {
            throw new IllegalStateException(entry.getId() + " was read before its registry had been registered");
        }
        return entry.get();
    }

    public Identifier getId() {
        return entry.getId();
    }

    /** The registry holder, for the places in 26.2 that take a {@code Holder} rather than a value. */
    public Holder<R> asHolder() {
        return entry.asHolder();
    }

    @Override
    public String toString() {
        return "DeferredHolder[" + entry.getId() + "]";
    }
}
