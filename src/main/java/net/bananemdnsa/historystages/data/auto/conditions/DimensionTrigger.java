package net.bananemdnsa.historystages.data.auto.conditions;

import net.bananemdnsa.historystages.api.trigger.StateTrigger;
import net.bananemdnsa.historystages.api.trigger.StateView;

import com.google.gson.annotations.SerializedName;

public record DimensionTrigger(@SerializedName("id") String id) implements StateTrigger {
    @Override public boolean holds(StateView view) { return id != null && id.equals(view.dimension()); }

    @Override public String type() { return "dimension"; }
    @Override public long signature() { return defaultSignature(id); }
}
