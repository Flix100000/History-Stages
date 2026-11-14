package net.bananemdnsa.historystages.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static {@code (GameTestHelper)} method as a GameTest, for {@link HistoryStagesGameTests}
 * to hand to each loader. 1.21.1 used vanilla's {@code @GameTest}; since 1.21.5 a test is a
 * registry entry, and NeoForge and Fabric register them differently.
 *
 * <p>Every test runs in the mod's 3x3x3 {@code historystages:empty} structure, as 1.21.1's
 * {@code template = "empty"} did, with sky access.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface HsGameTest {

    int timeoutTicks() default 100;

    boolean required() default true;

    /** Tests with their own batch run apart from the rest (1.21.1 {@code batch}). */
    String batch() default "";

    /**
     * Set when 26.2 removed what the test needs (PORT(26.2-gap)). The test is kept, listed in the
     * log and the port notes, and not registered.
     */
    String gap() default "";
}
