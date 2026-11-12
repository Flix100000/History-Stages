package net.bananemdnsa.historystages.client.editor.widget.popup.spawn;

import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.GenerationPhase;
import net.bananemdnsa.historystages.data.lock.spawn.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SpawnRuleSummaryTest {

    private static final String P = SpawnRuleSummary.PREFIX;

    @Test
    void aPlainLockSaysItDoesNotSpawn() {
        SpawnRuleSummary.Summary s = SpawnRuleSummary.describe(new EntitySpawnLockEntry("a:b"));
        assertEquals(P + "phase.while_locked", s.phase().key());
        assertEquals(P + "blocked", s.verb().key());
        assertTrue(s.conditions().isEmpty());
        assertNull(s.sources());
        assertNull(s.extra());
    }

    @Test
    void anExtraBiomeOnlyRuleSpawnsAsUsualPlusTheBiomes() {
        SpawnRuleSummary.Summary s = SpawnRuleSummary.describe(new EntitySpawnLockEntry("a:b", null,
                GenerationPhase.AFTER_UNLOCK, null, new ExtraBiomeSpawns(List.of("minecraft:desert"), null, false)));
        assertEquals(P + "phase.after_unlock", s.phase().key());
        assertEquals(P + "unrestricted", s.verb().key());
        assertEquals(new SpawnRuleSummary.Names(List.of("minecraft:desert"), null), s.extra().args().get(0));
    }

    @Test
    void conditionsAreListedTimeFirst() {
        SpawnRuleSummary.Summary s = SpawnRuleSummary.describe(new EntitySpawnLockEntry("a:b", List.of("natural"),
                GenerationPhase.WHILE_LOCKED,
                new SpawnConditions(IdFilter.only(List.of("minecraft:overworld")), null, null, new IntRange(-64, 40),
                        TimeOfDay.NIGHT, null, null, Set.of(4, 0)), null));

        assertEquals(P + "only", s.verb().key());
        assertEquals(List.of(P + "cond.time.night", P + "cond.dimensions.only", P + "cond.height", P + "cond.moon"),
                s.conditions().stream().map(SpawnRuleSummary.Fragment::key).toList());
        assertEquals(List.of(-64, 40), s.conditions().get(2).args());
        assertEquals(new SpawnRuleSummary.Names(List.of("0", "4"), "editor.historystages.spawn_control.moon."),
                s.conditions().get(3).args().get(0));
        assertEquals(P + "sources", s.sources().key());
    }
}
