package net.bananemdnsa.historystages.platform;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Collects registry entries at class-init time and puts them into the game registries later,
 * the way NeoForge's DeferredRegister does.
 *
 * <p>Fabric registers straight into the registry, so a stand-in is not strictly needed. It earns
 * its place anyway: the entries are declared as static fields that read each other
 * ({@code ModItems.RESEARCH_PEDESTAL_ITEM} wraps {@code ModBlocks.RESEARCH_PEDESTAL}), and 54
 * call sites across the mod read them through {@code .get()}. Keeping that shape means the init
 * classes and every one of those call sites stay as they are on the other loader.
 *
 * <p>What Fabric does not do for us is ordering. On NeoForge the mod bus fires one registry at a
 * time, so blocks exist before the item that wraps them. Here the suppliers run when
 * {@link #register()} is called, so the initializer has to call the registers in dependency
 * order: blocks, then items, then block entities, then the rest.
 */
public final class DeferredRegister<T> {

    private final ResourceKey<? extends Registry<T>> registryKey;
    private final String namespace;
    private final List<DeferredHolder<T, ? extends T>> pending = new ArrayList<>();
    private boolean registered;

    private DeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        this.registryKey = registryKey;
        this.namespace = namespace;
    }

    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        return new DeferredRegister<>(registryKey, namespace);
    }

    /** For the registries that are handed out as objects rather than keys, such as RECIPE_SERIALIZER. */
    public static <T> DeferredRegister<T> create(Registry<T> registry, String namespace) {
        return new DeferredRegister<>(registry.key(), namespace);
    }

    public <R extends T> DeferredHolder<T, R> register(String name, Supplier<R> factory) {
        if (registered) {
            throw new IllegalStateException(namespace + ":" + name + " was added after " + registryKey.location()
                    + " had already been registered");
        }
        DeferredHolder<T, R> holder =
                new DeferredHolder<>(ResourceLocation.fromNamespaceAndPath(namespace, name), factory);
        pending.add(holder);
        return holder;
    }

    /**
     * Runs every supplier and puts the result into the game registry. Calling it twice is a no-op,
     * so a second initializer entry point cannot double-register.
     */
    @SuppressWarnings("unchecked")
    public void register() {
        if (registered) {
            return;
        }
        Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.get(registryKey.location());
        if (registry == null) {
            throw new IllegalStateException("No registry named " + registryKey.location());
        }
        for (DeferredHolder<T, ? extends T> holder : pending) {
            holder.bind(registry);
        }
        registered = true;
    }
}
