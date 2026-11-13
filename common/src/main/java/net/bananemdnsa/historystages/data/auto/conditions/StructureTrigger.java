package net.bananemdnsa.historystages.data.auto.conditions;

import net.bananemdnsa.historystages.api.trigger.StateTrigger;
import net.bananemdnsa.historystages.api.trigger.StateView;

import com.google.gson.annotations.SerializedName;

public record StructureTrigger(@SerializedName("id") String id) implements StateTrigger {
    // An id or a #tag; StateCapture.structuresAt collects both for the player's position.
    @Override public boolean holds(StateView view) {
        if (id == null || id.isEmpty()) return false;
        return id.startsWith("#")
                ? view.structureTags().contains(id.substring(1))
                : view.structureIds().contains(id);
    }

    @Override public String type() { return "structure"; }
    @Override public long signature() { return defaultSignature(id); }
}
