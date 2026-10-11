package net.bananemdnsa.historystages.api.diagnostics;

import net.bananemdnsa.historystages.util.HsRuntimeCategories;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeCategoryTest {

    @Test
    void anAddonCategoryNeedsANamespacedIdALabelAndAKind() {
        assertThrows(IllegalArgumentException.class, () -> RuntimeCategory.of("relic_lock", "Relic Lock", Kind.LOCK));
        assertThrows(IllegalArgumentException.class, () -> RuntimeCategory.of(":relic_lock", "Relic Lock", Kind.LOCK));
        assertThrows(IllegalArgumentException.class, () -> RuntimeCategory.of("mymod:", "Relic Lock", Kind.LOCK));
        assertThrows(IllegalArgumentException.class, () -> RuntimeCategory.of("mymod:relic_lock", " ", Kind.LOCK));
        assertThrows(IllegalArgumentException.class, () -> RuntimeCategory.of("mymod:relic_lock", "Relic Lock", null));
    }

    @Test
    void twoCategoriesWithTheSameIdAreEqual() {
        assertEquals(RuntimeCategory.of("mymod:a", "A", Kind.LOCK), RuntimeCategory.of("mymod:a", "Other", Kind.SYSTEM));
        assertNotEquals(RuntimeCategory.of("mymod:a", "A", Kind.LOCK), RuntimeCategory.of("mymod:b", "A", Kind.LOCK));
    }

    @Test
    void writingWithoutALoadedConfigIsSilentlyNothing() {
        // No NeoForge on the test classpath, so reading the config fails the way it does before
        // the config is loaded in game. An addon must never get that exception.
        RuntimeCategory relic = RuntimeCategory.of("mymod:relic_lock", "Relic Lock", Kind.LOCK);
        assertDoesNotThrow(() -> RuntimeLog.write(relic, "x"));
        assertDoesNotThrow(() -> RuntimeLog.throttled(relic, "k", "x"));
    }

    @Test
    void historyStagesOwnCategoriesLiveInItsNamespace() {
        assertEquals("historystages:item_use_lock", HsRuntimeCategories.ITEM_USE_LOCK.id());
        assertEquals("Item Use Lock", HsRuntimeCategories.ITEM_USE_LOCK.label());
        assertEquals(Kind.LOCK, HsRuntimeCategories.ITEM_USE_LOCK.kind());
    }
}
