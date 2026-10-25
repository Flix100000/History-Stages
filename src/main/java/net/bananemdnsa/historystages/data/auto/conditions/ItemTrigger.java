package net.bananemdnsa.historystages.data.auto.conditions;

import net.bananemdnsa.historystages.api.trigger.StateTrigger;
import net.bananemdnsa.historystages.api.trigger.StateView;

import com.google.gson.annotations.SerializedName;

public record ItemTrigger(@SerializedName("id") String id) implements StateTrigger {
    @Override public boolean holds(StateView view) { return id != null && view.items().contains(id); }

    @Override public String type() { return "item"; }
    @Override public long signature() { return defaultSignature(id); }
}
