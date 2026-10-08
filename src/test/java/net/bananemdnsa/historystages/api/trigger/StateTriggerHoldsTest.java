package net.bananemdnsa.historystages.api.trigger;

import net.bananemdnsa.historystages.data.auto.conditions.*;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StateTriggerHoldsTest {

    private static StateView nether() {
        return StateView.builder()
                .dimension("minecraft:the_nether")
                .biome("minecraft:soul_sand_valley")
                .structures(new StateView.Structures(Set.of("minecraft:fortress"), Set.of("hstg:dungeons")))
                .effects(Set.of("minecraft:fire_resistance"))
                .items(Set.of("minecraft:blaze_rod"))
                .xpLevel(12)
                .weather(true, false)
                .dayTime(18000L)
                .build();
    }

    @Test void dimension() {
        assertTrue(new DimensionTrigger("minecraft:the_nether").holds(nether()));
        assertFalse(new DimensionTrigger("minecraft:overworld").holds(nether()));
    }

    @Test void biome() {
        assertTrue(new BiomeTrigger("minecraft:soul_sand_valley").holds(nether()));
        assertFalse(new BiomeTrigger("minecraft:plains").holds(nether()));
    }

    @Test void structureByIdAndTag() {
        assertTrue(new StructureTrigger("minecraft:fortress").holds(nether()));
        assertTrue(new StructureTrigger("#hstg:dungeons").holds(nether()));
        assertFalse(new StructureTrigger("minecraft:bastion_remnant").holds(nether()));
        assertFalse(new StructureTrigger("").holds(nether()));
    }

    @Test void effectItemXp() {
        assertTrue(new EffectTrigger("minecraft:fire_resistance").holds(nether()));
        assertFalse(new EffectTrigger("minecraft:speed").holds(nether()));
        assertTrue(new ItemTrigger("minecraft:blaze_rod").holds(nether()));
        assertFalse(new ItemTrigger("minecraft:stick").holds(nether()));
        assertTrue(new XpLevelTrigger(10).holds(nether()));
        assertFalse(new XpLevelTrigger(13).holds(nether()));
    }

    @Test void weatherAndTime() {
        assertTrue(new WeatherTrigger("rain").holds(nether()));
        assertFalse(new WeatherTrigger("clear").holds(nether()));
        assertTrue(TimeOfDayTrigger.custom(13000, 23000).holds(nether()));
        assertFalse(TimeOfDayTrigger.custom(0, 12000).holds(nether()));
    }

    /** The whole point of the view: a value fifteen stages ask for is computed once. */
    @Test void valuesAreComputedOnceAndOnlyWhenAsked() {
        AtomicInteger dimCalls = new AtomicInteger();
        AtomicInteger itemCalls = new AtomicInteger();
        StateView v = StateView.builder()
                .dimension(() -> { dimCalls.incrementAndGet(); return "minecraft:the_nether"; })
                .items(() -> { itemCalls.incrementAndGet(); return Set.of(); })
                .build();
        for (int i = 0; i < 15; i++) new DimensionTrigger("minecraft:the_nether").holds(v);
        assertEquals(1, dimCalls.get());
        assertEquals(0, itemCalls.get());
    }

    @Test void anEmptyViewHoldsNothing() {
        StateView empty = StateView.builder().build();
        assertFalse(new DimensionTrigger("minecraft:the_nether").holds(empty));
        assertFalse(new XpLevelTrigger(1).holds(empty));
        assertFalse(new EffectTrigger("minecraft:speed").holds(empty));
    }
}
