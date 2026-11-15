package net.bananemdnsa.historystages.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static {@code (GameTestHelper)} method as a GameTest, for {@link HistoryStagesGameTests}
 * to hand to each loader. A test is a registry entry, and NeoForge and Fabric register them
 * differently.
 *
 * <p>Every test runs in the mod's 3x3x3 {@code historystages:empty} structure, with sky access.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface HsGameTest {

    int timeoutTicks() default 100;

    boolean required() default true;

    /** Tests with their own batch run apart from the rest. */
    String batch() default "";

    /**
     * Set when the game no longer offers what the test needs (PORT(26.2-gap)). The test is kept and
     * listed in the log, but not registered.
     */
    String gap() default "";
}
