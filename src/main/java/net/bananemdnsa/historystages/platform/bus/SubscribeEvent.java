package net.bananemdnsa.historystages.platform.bus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a handler. It takes exactly one argument, the event.
 *
 * <p>Kept at runtime because there is no annotation processor here: {@link EventBus} finds these
 * by reflecting over the classes it is handed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SubscribeEvent {
    EventPriority priority() default EventPriority.NORMAL;
}
