package net.bananemdnsa.historystages.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import org.jetbrains.annotations.Nullable;

/**
 * Questions about the calling thread that only the loader can answer. Stands in for NeoForge's
 * {@code EffectiveSide} and {@code ModLoadingContext}; each loader module has a
 * {@code platform.<loader>.LoaderContextImpl}.
 */
public final class LoaderContext {

    private LoaderContext() {}

    /** Whether the calling thread belongs to the logical client. */
    @ExpectPlatform
    public static boolean isEffectiveClient() {
        throw new AssertionError();
    }

    /**
     * The mod whose code is running right now, as far as the loader tracks it. NeoForge reports
     * {@code minecraft} outside a wrapped dispatch; callers treat that the same as null.
     */
    @ExpectPlatform
    public static @Nullable String activeModId() {
        throw new AssertionError();
    }
}
