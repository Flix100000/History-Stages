package net.bananemdnsa.historystages.compat.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;

/**
 * The entry point KubeJS finds through {@code kubejs.plugins.txt}. Nothing in HistoryStages
 * references this class, so without KubeJS installed it is never loaded.
 */
public class HistoryStagesKubePlugin extends KubeJSPlugin {

    @Override
    public void registerEvents() {
        HistoryStagesKubeEvents.GROUP.register();
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        if (event.getType() == ScriptType.SERVER) {
            event.add("HistoryStages", HistoryStagesBindings.class);
        } else if (event.getType() == ScriptType.CLIENT) {
            event.add("HistoryStages", HistoryStagesClientBindings.class);
        }
        // Startup scripts get nothing on purpose: they run before a world exists, so every
        // answer about stage state would be a lie rather than a "no".
    }
}
