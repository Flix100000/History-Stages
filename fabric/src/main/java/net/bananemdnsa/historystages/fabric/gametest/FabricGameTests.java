package net.bananemdnsa.historystages.fabric.gametest;

import java.util.LinkedHashMap;
import java.util.Map;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.gametest.HistoryStagesGameTests;
import net.fabricmc.fabric.api.event.registry.DynamicRegistrySetupCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;

/**
 * Hands the common GameTests ({@link HistoryStagesGameTests}) to the game on Fabric.
 *
 * <p>Fabric's own API only knows annotated methods on {@code fabric-gametest} entrypoints, one
 * method per test. The common list is registered the way Fabric's API does it underneath: the
 * function into the built-in registry at startup, the instance into the dynamic registry while it
 * is being loaded.
 */
public final class FabricGameTests {

    private FabricGameTests() {}

    /** Same switch as Fabric's GameTest API: the gametest property, or a dev environment. */
    public static void register() {
        if (System.getProperty("fabric-api.gametest") == null && !FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        for (HistoryStagesGameTests.Entry entry : HistoryStagesGameTests.all()) {
            Registry.register(BuiltInRegistries.TEST_FUNCTION, id(entry.name()), entry.test());
        }
        DynamicRegistrySetupCallback.EVENT.register(view -> view.getOptional(Registries.TEST_INSTANCE).ifPresent(
                instances -> view.getOptional(Registries.TEST_ENVIRONMENT).ifPresent(
                        environments -> registerInstances(instances, environments))));
    }

    private static void registerInstances(Registry<GameTestInstance> instances,
                                          Registry<TestEnvironmentDefinition<?>> environments) {
        Holder<TestEnvironmentDefinition<?>> environment = environment(environments, "default");
        // A test with its own batch gets its own environment: tests are batched by environment.
        Map<String, Holder<TestEnvironmentDefinition<?>>> batches = new LinkedHashMap<>();
        for (HistoryStagesGameTests.Entry entry : HistoryStagesGameTests.all()) {
            Holder<TestEnvironmentDefinition<?>> env = entry.batch().isEmpty() ? environment
                    : batches.computeIfAbsent(entry.batch(), batch -> environment(environments, "batch/" + batch));
            // Sky access: the default barrier ceiling sits right on a 1x1x1 structure, and a
            // chest under a solid block does not open.
            Registry.register(instances, id(entry.name()), new FunctionGameTestInstance(
                    ResourceKey.create(Registries.TEST_FUNCTION, id(entry.name())),
                    new TestData<>(env, HistoryStagesGameTests.STRUCTURE, entry.maxTicks(), 0, entry.required(), Rotation.NONE,
                            false, 1, 1, true, 0)));
        }
    }

    private static Holder<TestEnvironmentDefinition<?>> environment(Registry<TestEnvironmentDefinition<?>> environments,
                                                                    String name) {
        TestEnvironmentDefinition<?> none = new TestEnvironmentDefinition.AllOf(java.util.List.of());
        return Registry.registerForHolder(environments, id(name), none);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(HistoryStages.MOD_ID, path);
    }
}
