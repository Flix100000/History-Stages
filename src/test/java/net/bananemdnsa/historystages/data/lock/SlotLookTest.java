package net.bananemdnsa.historystages.data.lock;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.bananemdnsa.historystages.data.StageEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotLookTest {

    private static final Gson GSON = new Gson();

    @Test
    void defaultsAreTheOriginalLook() {
        SlotLook look = new SlotLook();
        assertEquals(0x80000000, look.argb());
        assertEquals(SlotLook.Icon.LOCK, look.icon());
        assertEquals(SlotLook.Size.MEDIUM, look.size());
        assertTrue(look.isDefault());
    }

    @Test
    void colourAndOpacityMakeTheTint() {
        SlotLook look = new SlotLook();
        look.setColor("#401010");
        look.setOpacity(100);
        assertEquals(0xFF401010, look.argb());
        look.setOpacity(0);
        assertEquals(0x00401010, look.argb());
    }

    /** A hand-edited file with a broken colour must not crash the HUD; it falls back to black. */
    @Test
    void brokenColourFallsBackToBlack() {
        SlotLook look = new SlotLook();
        look.setColor("not a colour");
        assertEquals(0x80000000, look.argb());
    }

    @Test
    void opacityIsClampedIntoRange() {
        SlotLook look = new SlotLook();
        look.setOpacity(250);
        assertEquals(100, look.opacity());
        look.setOpacity(-4);
        assertEquals(0, look.opacity());
    }

    @Test
    void entryRoundTripsLookAndHint() {
        StageEntry stage = GSON.fromJson("""
                {"slots":[{"target":"minecraft:chest","slots":[30],"mode":"overlay",
                  "hint":"Bigger chest in the iron age",
                  "look":{"color":"#102040","opacity":35,"icon":"item","item":"minecraft:barrier","size":"full",
                          "texture":"mypack:gui/slot"}}]}
                """, StageEntry.class);
        StageEntry reloaded = GSON.fromJson(stage.toJson(), StageEntry.class);
        SlotLockEntry entry = reloaded.getSlotLocks().get(0);

        assertEquals("Bigger chest in the iron age", entry.hint());
        assertEquals(0x59102040, entry.look().argb());
        assertEquals(SlotLook.Icon.ITEM, entry.look().icon());
        assertEquals("minecraft:barrier", entry.look().item());
        assertEquals(SlotLook.Size.FULL, entry.look().size());
        assertEquals("mypack:gui/slot", entry.look().texture());
    }

    /** Most entries never touch the look; their files should not grow an empty block. */
    @Test
    void defaultLookAndEmptyHintAreNotWritten() {
        StageEntry stage = new StageEntry();
        SlotLockEntry entry = new SlotLockEntry("player", List.of(0), SlotLockMode.OVERLAY);
        entry.setLook(new SlotLook());
        entry.setHint("");
        stage.setSlotLocks(List.of(entry));

        var json = JsonParser.parseString(stage.toJson()).getAsJsonObject()
                .getAsJsonArray("slots").get(0).getAsJsonObject();
        assertFalse(json.has("look"));
        assertFalse(json.has("hint"));
    }

    @Test
    void copyDoesNotShareTheLook() {
        SlotLockEntry entry = new SlotLockEntry("player", List.of(0), SlotLockMode.OVERLAY);
        SlotLook look = new SlotLook();
        look.setOpacity(20);
        entry.setLook(look);

        SlotLockEntry copy = entry.copy();
        entry.look().setOpacity(90);

        assertEquals(20, copy.look().opacity());
    }
}
