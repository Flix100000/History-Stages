package net.bananemdnsa.historystages.platform.neoforge;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.util.thread.EffectiveSide;
import org.jetbrains.annotations.Nullable;

/** NeoForge side of {@code platform.LoaderContext}. */
public final class LoaderContextImpl {

    private LoaderContextImpl() {}

    public static boolean isEffectiveClient() {
        return EffectiveSide.get().isClient();
    }

    public static @Nullable String activeModId() {
        return ModLoadingContext.get().getActiveContainer().getModId();
    }
}
