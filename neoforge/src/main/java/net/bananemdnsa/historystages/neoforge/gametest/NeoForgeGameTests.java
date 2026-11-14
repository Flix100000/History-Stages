package net.bananemdnsa.historystages.neoforge.gametest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.gametest.HistoryStagesGameTests;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Hands the mod's GameTests to the game.
 *
 * <p>Since 1.21.5 a GameTest is two registry entries: the test function in the built-in
 * {@code test_function} registry, and a test instance that names the function, the structure and
 * the environment. NeoForge offers {@link RegisterGameTestsEvent} for the instances and only fires
 * it when GameTests are enabled; the functions go through the ordinary {@link RegisterEvent}.
 */
public final class NeoForgeGameTests {

    /** Vanilla's 1x1x1 air structure. Every test builds what it needs itself. */
    private static final Identifier EMPTY = Identifier.fromNamespaceAndPath("minecraft", "empty");
    private static final int MAX_TICKS = 100;

    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();

    static {
        BridgeCancelTests.collect(TESTS);
        BridgePriorityTests.collect(TESTS);
        AddonRegistrationTests.collect(TESTS);
        CommonScenarioTests.collect(TESTS);
    }

    private NeoForgeGameTests() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterEvent.class, event -> event.register(Registries.TEST_FUNCTION, helper -> {
            TESTS.forEach((name, test) -> helper.register(id(name), test));
            HistoryStagesGameTests.all().forEach(entry -> helper.register(id(entry.name()), entry.test()));
        }));
        modBus.addListener(RegisterGameTestsEvent.class, NeoForgeGameTests::registerInstances);
    }

    private static void registerInstances(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(id("default"));
        TESTS.keySet().forEach(name -> event.registerTest(id(name), instance(name, environment, EMPTY, MAX_TICKS, true)));
        // A test with its own batch gets its own environment: 26.2 batches tests by environment.
        Map<String, Holder<TestEnvironmentDefinition<?>>> batches = new LinkedHashMap<>();
        for (HistoryStagesGameTests.Entry entry : HistoryStagesGameTests.all()) {
            Holder<TestEnvironmentDefinition<?>> env = entry.batch().isEmpty() ? environment
                    : batches.computeIfAbsent(entry.batch(), batch -> event.registerEnvironment(id("batch/" + batch)));
            event.registerTest(id(entry.name()), instance(entry.name(), env, HistoryStagesGameTests.STRUCTURE,
                    entry.maxTicks(), entry.required()));
        }
    }

    private static FunctionGameTestInstance instance(String name, Holder<TestEnvironmentDefinition<?>> environment,
                                                     Identifier structure, int maxTicks, boolean required) {
        // Sky access, because the default barrier ceiling sits right on top of a 1x1x1
        // structure, and a chest under a solid block does not open.
        return new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id(name)),
                new TestData<>(environment, structure, maxTicks, 0, required, Rotation.NONE, false, 1, 1, true, 0));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(HistoryStages.MOD_ID, path);
    }
}
