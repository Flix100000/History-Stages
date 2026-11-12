package net.bananemdnsa.historystages.platform.bus;

import net.bananemdnsa.historystages.platform.Dist;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as holding event handlers.
 *
 * <p><strong>This does not register anything.</strong> On NeoForge the loader scans the jar for
 * it; nothing does that here, so the initializer names the handler classes itself and
 * {@code EventBusSubscriberCoverageTest} walks the sources to make sure the two have not drifted
 * apart. The annotation stays because it is what says "this file contains rules", and losing that
 * marker would leave the registration list as the only record of it.
 *
 * <p>Which bus a class belongs to is kept because it decides when it is registered: game handlers
 * at startup, mod handlers while the mod is still being built. {@link #value()} says on which
 * side the class may be loaded at all; the registration list has to respect it, since a client
 * handler reaches classes a dedicated server does not have.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventBusSubscriber {

    enum Bus {
        GAME,
        MOD
    }

    String modid() default "";

    Bus bus() default Bus.GAME;

    Dist[] value() default {Dist.CLIENT, Dist.DEDICATED_SERVER};
}
