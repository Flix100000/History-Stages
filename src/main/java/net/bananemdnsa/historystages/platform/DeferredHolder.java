package net.bananemdnsa.historystages.platform;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * A registry entry that is declared before the registry is filled, matching the shape of
 * NeoForge's DeferredHolder so the call sites read the same on both loaders.
 *
 * <p>Reading it before {@link DeferredRegister#register()} has run throws rather than returning
 * null. A null slipping through would surface much later as a missing block or item with nothing
 * pointing back at the initializer order that caused it.
 */
public final class DeferredHolder<T, R extends T> implements Supplier<R> {

    private final ResourceLocation id;
    private final Supplier<R> factory;
    private R value;

    DeferredHolder(ResourceLocation id, Supplier<R> factory) {
        this.id = id;
        this.factory = factory;
    }

    void bind(Registry<T> registry) {
        if (value == null) {
            value = Registry.register(registry, id, factory.get());
        }
    }

    @Override
    public R get() {
        if (value == null) {
            throw new IllegalStateException(id + " was read before its registry had been registered");
        }
        return value;
    }

    public ResourceLocation getId() {
        return id;
    }

    @Override
    public String toString() {
        return "DeferredHolder[" + id + "]";
    }
}
