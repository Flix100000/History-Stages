package net.bananemdnsa.historystages.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The GameTests ported from 1.21.1, in one list both loaders register from.
 *
 * <p>Names are {@code <class>/<method>} in snake case, so a failure reads like the 1.21.1 one
 * ({@code locktests.alockeditem...}) did.
 */
public final class HistoryStagesGameTests {

    /** 1.21.1's {@code template = "empty"}: the mod's own 3x3x3 air structure, not vanilla's 1x1x1. */
    public static final net.minecraft.resources.Identifier STRUCTURE =
            net.minecraft.resources.Identifier.fromNamespaceAndPath("historystages", "empty");

    /** One test as the loaders need it. */
    public record Entry(String name, Consumer<GameTestHelper> test, int maxTicks, boolean required, String batch) {}

    /**
     * Test classes. Not ported, because the mod they test has no 26.2 build yet (port-later/):
     * FastSuiteRecipeLockTests, KubeJsRecipeLockTests.
     */
    private static final List<Class<?>> CLASSES = List.of(
            HarnessTests.class,
            AddonApiTests.class,
            AddonHookTests.class,
            AutoTriggerTests.class,
            ConfigCodecTests.class,
            ContainerLockTests.class,
            DependencyCheckerTests.class,
            DisguiseGameTests.class,
            EnchantmentEffectLockTests.class,
            FluidLockTests.class,
            GraphReachabilityTests.class,
            InterchangeableStageTests.class,
            ItemTagDepositTests.class,
            LockTests.class,
            LootrLootFilterTests.class,
            PacketTests.class,
            PersistenceTests.class,
            ReachTests.class,
            RecipeBookTests.class,
            RecipeLockTests.class,
            RelockTests.class,
            ScrollShelfTests.class,
            SpawnControlTests.class,
            StageLogicRevokeTests.class,
            StageLogicRound2Tests.class,
            StageLogicTests.class,
            StonecutterLockTests.class,
            StageQueryTests.class,
            TeamShareTests.class,
            TradeLockTests.class,
            ZoneLockTests.class,
            ZoneMarkingTests.class);

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static List<Entry> entries;

    private HistoryStagesGameTests() {}

    public static synchronized List<Entry> all() {
        if (entries == null) {
            List<Entry> found = new ArrayList<>();
            for (Class<?> type : CLASSES) {
                Method[] methods = type.getDeclaredMethods();
                Arrays.sort(methods, Comparator.comparing(Method::getName));
                for (Method method : methods) {
                    HsGameTest test = method.getAnnotation(HsGameTest.class);
                    if (test == null) continue;
                    if (!test.gap().isEmpty()) {
                        LOGGER.warn("GameTest {}.{} not registered, PORT(26.2-gap): {}",
                                type.getSimpleName(), method.getName(), test.gap());
                        continue;
                    }
                    if (!Modifier.isStatic(method.getModifiers())) {
                        throw new IllegalStateException(type.getName() + "." + method.getName() + " must be static");
                    }
                    method.setAccessible(true);
                    found.add(new Entry(snake(type.getSimpleName()) + "/" + snake(method.getName()),
                            helper -> invoke(method, helper), test.timeoutTicks(), test.required(), test.batch()));
                }
            }
            entries = List.copyOf(found);
        }
        return entries;
    }

    private static void invoke(Method method, GameTestHelper helper) {
        try {
            method.invoke(null, helper);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            if (e.getCause() instanceof Error error) throw error;
            throw new RuntimeException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static String snake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
