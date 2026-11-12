package net.bananemdnsa.historystages.platform;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.util.function.Supplier;

/**
 * Collects registry entries at class-init time and puts them into the game registries later,
 * the way NeoForge's DeferredRegister does.
 *
 * <p>The entries are declared as static fields that read each other
 * ({@code ModItems.RESEARCH_PEDESTAL_ITEM} wraps {@code ModBlocks.RESEARCH_PEDESTAL}), and the
 * call sites read them through {@code .get()}. Keeping that shape means the init classes and every
 * call site stay as they are on the NeoForge main branch.
 *
 * <p>Underneath is Architectury's DeferredRegister, which registers through each loader's own
 * mechanism. On NeoForge that is the registry event, one registry at a time; on Fabric the entries
 * go in when {@link #register()} is called, so the initializer still has to call the registers in
 * dependency order: blocks, then items, then block entities, then the rest.
 */
public final class DeferredRegister<T> {

    private final dev.architectury.registry.registries.DeferredRegister<T> delegate;
    private final ResourceKey<? extends Registry<T>> registryKey;
    private final String namespace;
    private boolean registered;

    @SuppressWarnings("unchecked")
    private DeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        this.registryKey = registryKey;
        this.namespace = namespace;
        this.delegate = dev.architectury.registry.registries.DeferredRegister.create(
                namespace, (ResourceKey<Registry<T>>) registryKey);
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
            // Architectury would accept it on some loaders and drop it on others; failing here
            // keeps the mistake from depending on which loader it was tried on.
            throw new IllegalStateException(namespace + ":" + name + " was added after "
                    + registryKey.identifier() + " had already been registered");
        }
        RegistrySupplier<R> entry = delegate.register(name, factory);
        return new DeferredHolder<>(entry);
    }

    /**
     * Hands every entry to the loader. Calling it twice is a no-op, so a second initializer entry
     * point cannot double-register.
     */
    public void register() {
        if (registered) {
            return;
        }
        delegate.register();
        registered = true;
    }
}
