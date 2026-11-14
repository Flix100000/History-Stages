package net.bananemdnsa.historystages.neoforge.gametest;

import net.bananemdnsa.historystages.api.lock.RegisterLockCategoriesEvent;
import net.bananemdnsa.historystages.data.diagnostics.ApiOwners;
import net.bananemdnsa.historystages.platform.bus.EventBusSubscriber;
import net.bananemdnsa.historystages.platform.bus.SubscribeEvent;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.function.Consumer;

/**
 * An addon written for 1.21.1 only swaps its imports: a class marked with the platform
 * {@code @EventBusSubscriber} is found in NeoForge's scan data, registered on the platform bus and
 * hears the register events, with its mod named as the owner for the debug report.
 *
 * <p>The subscriber below stands in for the demo addon. It names a mod id of its own, which is
 * what the owner has to come out as.
 */
public final class AddonRegistrationTests {

    static final String TEST_ADDON = "hs_gametest_addon";

    private AddonRegistrationTests() {}

    static void collect(Map<String, Consumer<GameTestHelper>> tests) {
        tests.put("addon/subscriber_receives_register_event", AddonRegistrationTests::subscriberReceivesRegisterEvent);
        tests.put("addon/registration_window_runs_on_the_main_thread", AddonRegistrationTests::registrationWindowOnMainThread);
    }

    /** Common setup runs on modloading workers; addons' register handlers must not. */
    public static void registrationWindowOnMainThread(GameTestHelper helper) {
        String thread = net.bananemdnsa.historystages.HistoryStages.registrationThread();
        if (thread == null || thread.startsWith("modloading-worker")) {
            helper.fail(Component.literal("registration window ran on " + thread + ", expected the main thread"));
            return;
        }
        helper.succeed();
    }

    public static void subscriberReceivesRegisterEvent(GameTestHelper helper) {
        if (!Subscriber.received) {
            helper.fail(Component.literal("the platform @EventBusSubscriber never got RegisterLockCategoriesEvent"));
            return;
        }
        if (!TEST_ADDON.equals(Subscriber.owner)) {
            helper.fail(Component.literal("owner during the event was " + Subscriber.owner + ", expected " + TEST_ADDON));
            return;
        }
        helper.succeed();
    }

    @EventBusSubscriber(modid = TEST_ADDON)
    public static final class Subscriber {
        static volatile boolean received;
        static volatile String owner;

        private Subscriber() {}

        @SubscribeEvent
        public static void onRegister(RegisterLockCategoriesEvent event) {
            received = true;
            owner = ApiOwners.active();
        }
    }
}
