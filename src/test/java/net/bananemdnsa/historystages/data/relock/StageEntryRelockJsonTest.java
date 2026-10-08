package net.bananemdnsa.historystages.data.relock;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.auto.NegatedTrigger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StageEntryRelockJsonTest {

    private static final Gson GSON = new Gson();

    @Test void conditionalModeParses() {
        StageEntry e = GSON.fromJson("{\"mode\":\"conditional\"}", StageEntry.class);
        assertEquals(StageMode.CONDITIONAL, e.getMode());
        assertTrue(e.getMode().usesAutoTrigger());
        assertFalse(e.getMode().allowsLockTrigger());
    }

    @Test void lockTriggerParsesWithDefaults() {
        StageEntry e = GSON.fromJson("""
                {"mode":"auto","lock_trigger":{"triggers":[
                  {"type":"effect","id":"minecraft:fire_resistance","negate":true}]}}""", StageEntry.class);
        LockTrigger lt = e.getLockTrigger();
        assertNotNull(lt);
        assertFalse(lt.isReUnlockable(), "permanent loss is the default");
        assertInstanceOf(NegatedTrigger.class, lt.getTriggers().get(0));
    }

    @Test void reUnlockableRoundTrips() {
        StageEntry e = GSON.fromJson("""
                {"mode":"default","lock_trigger":{"mode":"all","re_unlockable":true,"triggers":[]}}""",
                StageEntry.class);
        assertTrue(e.getLockTrigger().isReUnlockable());
        JsonObject out = GSON.toJsonTree(e).getAsJsonObject().getAsJsonObject("lock_trigger");
        assertTrue(out.get("re_unlockable").getAsBoolean());
        assertEquals("all", out.get("mode").getAsString());
    }

    @Test void notifyDefaultsByMode() {
        assertTrue(GSON.fromJson("{\"mode\":\"auto\"}", StageEntry.class).resolvedNotify());
        assertTrue(GSON.fromJson("{}", StageEntry.class).resolvedNotify());
        assertFalse(GSON.fromJson("{\"mode\":\"conditional\"}", StageEntry.class).resolvedNotify());
        assertTrue(GSON.fromJson("{\"mode\":\"conditional\",\"notify\":true}", StageEntry.class).resolvedNotify());
        assertFalse(GSON.fromJson("{\"mode\":\"auto\",\"notify\":false}", StageEntry.class).resolvedNotify());
    }

    @Test void absentKeysAreNotWritten() {
        JsonObject out = GSON.toJsonTree(GSON.fromJson("{\"mode\":\"auto\"}", StageEntry.class)).getAsJsonObject();
        assertFalse(out.has("lock_trigger"));
        assertFalse(out.has("notify"));
    }

    @Test void copyIsIndependent() {
        StageEntry e = GSON.fromJson("""
                {"mode":"auto","notify":false,"lock_trigger":{"triggers":[{"type":"biome","id":"minecraft:plains"}]}}""",
                StageEntry.class);
        StageEntry c = e.copy();
        c.getLockTrigger().getTriggers().clear();
        assertEquals(1, e.getLockTrigger().getTriggers().size());
        assertFalse(c.resolvedNotify());
    }
}
