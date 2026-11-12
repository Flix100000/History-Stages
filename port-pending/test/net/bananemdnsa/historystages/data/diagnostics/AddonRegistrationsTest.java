package net.bananemdnsa.historystages.data.diagnostics;

import net.bananemdnsa.historystages.util.DebugLogger.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class AddonRegistrationsTest {

    private static final Predicate<String> LOADED = Set.of("historystages", "minecraft", "ftbteams", "relics")::contains;

    @AfterEach
    void reset() {
        AddonRegistrations.resetForTest();
    }

    @Test
    void aRegistrationBelongsToTheModWhoseListenerMadeIt() {
        AddonRegistrations.record("relics", "relics:sets", ApiExtension.LOCK_CATEGORY);
        AddonRegistrations.record("relics", "relics:found", ApiExtension.TRIGGER_TYPE);
        AddonRegistrations.record("relics", "relics:hoard", ApiExtension.TRIGGER_TYPE);

        List<AddonRegistrations.AddonSummary> out = AddonRegistrations.summaries(LOADED);
        assertEquals(1, out.size());
        assertEquals("relics", out.get(0).modId());
        assertEquals(Map.of(ApiExtension.LOCK_CATEGORY, 1, ApiExtension.TRIGGER_TYPE, 2), out.get(0).registered());
    }

    @Test
    void historyStagesOwnRegistrationsAreNotAnAddonEvenUnderAForeignNamespace() {
        // HS registers the FTB Teams provider itself; ftbteams is a loaded mod, so it stays HS.
        AddonRegistrations.record("historystages", "ftbteams:party", ApiExtension.TEAM_PROVIDER);
        AddonRegistrations.record("historystages", "historystages:items", ApiExtension.LOCK_CATEGORY);
        AddonRegistrations.record(null, "relics:outside", ApiExtension.LOCK_CATEGORY);

        assertTrue(AddonRegistrations.summaries(LOADED).isEmpty());
    }

    @Test
    void aBundledAddonIsKnownByANamespaceNoLoadedModOwns() {
        // The demo addon ships inside the HS jar, so FML reports HS as the active mod.
        AddonRegistrations.record("historystages", "hsdemo:relics", ApiExtension.LOCK_CATEGORY);

        List<AddonRegistrations.AddonSummary> out = AddonRegistrations.summaries(LOADED);
        assertEquals(1, out.size());
        assertEquals("hsdemo", out.get(0).modId());
    }

    @Test
    void problemsAreCountedForTheModThatRegisteredTheCheck() {
        AddonRegistrations.record("relics", "relics:check", ApiExtension.STAGE_CHECK);
        AddonRegistrations.problem(ApiExtension.STAGE_CHECK, "relics:check", Level.WARN);
        AddonRegistrations.problem(ApiExtension.STAGE_CHECK, "relics:check", Level.WARN);
        AddonRegistrations.problem(ApiExtension.STAGE_CHECK, "relics:check", Level.ERROR);
        AddonRegistrations.problem(ApiExtension.STAGE_CHECK, "relics:check", Level.INFO);

        AddonRegistrations.AddonSummary relics = AddonRegistrations.summaries(LOADED).get(0);
        assertEquals(1, relics.errors());
        assertEquals(2, relics.warnings());

        AddonRegistrations.clearProblems();
        assertEquals(0, AddonRegistrations.summaries(LOADED).get(0).warnings());
    }

    @Test
    void oneIdUsedForSeveralThingsCountsEachProblemOnce() {
        // The demo uses hsdemo:relics for its category, its check and its section.
        AddonRegistrations.record("relics", "relics:relics", ApiExtension.LOCK_CATEGORY);
        AddonRegistrations.record("relics", "relics:relics", ApiExtension.STAGE_CHECK);
        AddonRegistrations.record("relics", "relics:relics", ApiExtension.REPORT_SECTION);
        AddonRegistrations.problem(ApiExtension.STAGE_CHECK, "relics:relics", Level.WARN);
        AddonRegistrations.problem(ApiExtension.REPORT_SECTION, "relics:relics", Level.ERROR);

        AddonRegistrations.AddonSummary relics = AddonRegistrations.summaries(LOADED).get(0);
        assertEquals(1, relics.warnings());
        assertEquals(1, relics.errors());
    }

    @Test
    void labelsArePlural() {
        assertEquals("1 lock category", ApiExtension.LOCK_CATEGORY.label(1));
        assertEquals("3 lock categories", ApiExtension.LOCK_CATEGORY.label(3));
        assertEquals("2 stage checks", ApiExtension.STAGE_CHECK.label(2));
    }
}
