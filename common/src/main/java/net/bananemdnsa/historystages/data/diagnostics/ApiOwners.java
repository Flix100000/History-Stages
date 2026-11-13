package net.bananemdnsa.historystages.data.diagnostics;

import net.bananemdnsa.historystages.platform.LoaderContext;
import org.jetbrains.annotations.Nullable;

/** Reads which mod's listener is running while History Stages fires one of its register events. */
public final class ApiOwners {

    private ApiOwners() {}

    /**
     * Only set because HS fires its common register events with
     * {@code ModLoader.postEventWrapContainerInModOrder}. Outside such an event FML falls back to
     * Minecraft's container, which is reported as null here. Fabric has no such context; there the
     * caller of an addon's entrypoint sets it ({@code LoaderContextImpl.runAs}).
     */
    public static @Nullable String active() {
        String id = LoaderContext.activeModId();
        return id == null || "minecraft".equals(id) ? null : id;
    }
}
