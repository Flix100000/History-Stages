package net.bananemdnsa.historystages.data.auto.conditions;

import net.bananemdnsa.historystages.api.trigger.StateTrigger;
import net.bananemdnsa.historystages.api.trigger.StateView;

import com.google.gson.annotations.SerializedName;

public record BiomeTrigger(@SerializedName("id") String id) implements StateTrigger {
    @Override public boolean holds(StateView view) { return id != null && !id.isEmpty() && id.equals(view.biome()); }

    @Override public String type() { return "biome"; }
    @Override public long signature() { return defaultSignature(id); }
}
