package net.bananemdnsa.historystages.reload;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.mojang.logging.LogUtils;

import net.bananemdnsa.historystages.Config;
import net.minecraft.Util;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.tags.TagManager;
import net.minecraft.util.Unit;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import org.slf4j.Logger;

/**
 * PROTOTYPE. A stand-in for the full datapack reload a stage change sets off.
 *
 * <p>The full reload is there for one reason only: machines that took a copy of the recipe list
 * have to be told to let go of it. Everything else it does — reopening every pack, parsing every
 * recipe, loot table, advancement and function again, rebinding every tag, saving every player —
 * produces exactly what was there before, because a stage change touches no file. And it does all
 * of that with the server thread waiting on it.
 *
 * <p>Mods hear about a reload through the listeners they add to it, so this runs those again and
 * nothing else: same open resource manager, no vanilla listener, no new recipe manager. The list
 * comes from {@code SimpleReloadInstanceMixin}, kept from the last real reload.
 *
 * <p>Script loaders are left out on purpose: they would run the scripts again against a recipe
 * manager they have already edited once. They are named in {@link #ALWAYS_SKIPPED}; what else a
 * pack leaves out is {@code [advanced] targetedReloadSkip} in gameplay.toml.
 */
public final class LightReload {

    private static final Logger LOGGER = LogUtils.getLogger();


    /**
     * Listeners a targeted reload always leaves out, whatever the config says.
     *
     * <p>The two script loaders must not run twice on one recipe load: they add and remove recipes,
     * and a second pass would apply every change again to the result of the first. That is a
     * question of correctness, not of speed, so it is not left to a list a pack can empty.
     */
    private static final Set<String> ALWAYS_SKIPPED = Set.of(
            "dev.latvian.mods.kubejs.server.KubeJSReloadListener",
            "com.blamejared.crafttweaker.impl.script.ScriptReloadListener");

    /** A mode set with the lab command, in force until the server restarts. Null = the config. */
    private static volatile StageReloadMode modeOverride;
    /** Same for the recipe resend. Null = resend only in FULL mode, as 6.x always did. */
    private static volatile Boolean resendOverride;

    /**
     * Whether a stage change sends clients the recipe list. Sending it restarts JEI from scratch;
     * not sending it leaves JEI to the hide pass on the stage sync. Only FULL still sends it, so
     * that mode stays exactly what 6.x shipped.
     */
    public static boolean resendRecipes() {
        Boolean forced = resendOverride;
        return forced != null ? forced : mode() == StageReloadMode.FULL;
    }

    /** Lab command only, in memory until the server restarts. */
    public static void setResendRecipes(boolean resend) {
        resendOverride = resend;
    }
    private static volatile List<PreparableReloadListener> remembered = List.of();
    private static volatile boolean profileRequested;

    private LightReload() {}

    /** The lab override if one is set, otherwise {@code [advanced] reloadAfterStageChange}. */
    public static StageReloadMode mode() {
        StageReloadMode forced = modeOverride;
        if (forced != null) return forced;
        try {
            return Config.GAMEPLAY.reloadAfterStageChange.get();
        } catch (IllegalStateException e) {
            // Config not loaded yet: nothing can have changed a stage at that point either.
            return StageReloadMode.TARGETED;
        }
    }

    /**
     * Lab command only: in memory until the server restarts, the config file is not touched.
     * Null goes back to the config.
     */
    public static void setMode(StageReloadMode newMode) {
        modeOverride = newMode;
    }

    /** The lab override, or null while the config decides. For putting it back after a bench. */
    public static StageReloadMode modeOverride() {
        return modeOverride;
    }

    /** Server going away: lab overrides do not outlive the world they were set in. */
    public static void forgetOverrides() {
        modeOverride = null;
        resendOverride = null;
    }

    /**
     * Keeps the listener list of a server data reload. Returns whether it was one.
     *
     * <p>Told apart from a client resource reload by the recipe manager in it. A light reload's
     * own list has none either, so it never replaces the list it came from.
     */
    public static boolean rememberIfServerData(List<PreparableReloadListener> listeners) {
        for (PreparableReloadListener listener : listeners) {
            if (listener instanceof RecipeManager) {
                remembered = List.copyOf(listeners);
                return true;
            }
        }
        return false;
    }

    /** The kept list as it is, so a test that swaps in its own can put the real one back. */
    public static List<PreparableReloadListener> remembered() {
        return remembered;
    }

    /**
     * Puts a list back as it was, unchecked. Not {@link #rememberIfServerData}: that one refuses a
     * list without a recipe manager, and an empty one is exactly what a test may have to restore.
     */
    public static void restoreRemembered(List<PreparableReloadListener> listeners) {
        remembered = listeners;
    }

    /** Has the next server reload log how long each listener took. */
    public static void requestProfile() {
        profileRequested = true;
    }

    public static boolean takeProfileRequest() {
        boolean requested = profileRequested;
        profileRequested = false;
        return requested;
    }

    /** One listener as the lab command lists it. */
    public record Entry(String name, Kind kind) {}

    public enum Kind { VANILLA, REPLAYED, SKIPPED }

    /** Everything the last reload ran, sorted into what a light reload would do with it. */
    public static List<Entry> describe() {
        List<Entry> out = new ArrayList<>();
        for (PreparableReloadListener listener : remembered) {
            PreparableReloadListener inner = unwrap(listener);
            out.add(new Entry(inner.getClass().getName(), kindOf(inner, configSkipped())));
        }
        return out;
    }

    /** What one light reload did. */
    public record Result(long millis, int replayed, int skipped) {}

    /** Whether a reload has been seen to replay. Without one, there is nothing to run. */
    public static boolean hasRemembered() {
        return !remembered.isEmpty();
    }

    /**
     * Runs the mods' listeners again. Returns at once; the future completes on the server thread.
     *
     * <p>Not waited for, for the same reason as {@link BackgroundReload}: nothing it touches
     * differs from what is loaded, and the gate answers on every lookup, so a machine rebuilding
     * its cache while this runs already gets the right list.
     *
     * <p>Each listener prepares on the background pool and applies on the server thread between
     * ticks — the split vanilla uses — and the log names every listener and what it cost, which is
     * the list to look at when one pack is still slow.
     */
    public static CompletableFuture<Result> start(MinecraftServer server, boolean fireTagsUpdated) {
        long start = System.nanoTime();

        Set<String> configSkipped = configSkipped();
        List<PreparableReloadListener> toRun = new ArrayList<>();
        List<Timed> timed = new ArrayList<>();
        int skipped = 0;
        for (PreparableReloadListener listener : remembered) {
            PreparableReloadListener inner = unwrap(listener);
            switch (kindOf(inner, configSkipped)) {
                case REPLAYED -> {
                    toRun.add(inner);
                    timed.add(new Timed(inner));
                }
                case SKIPPED -> skipped++;
                case VANILLA -> { }
            }
        }
        int skippedFinal = skipped;

        // No condition context to hand over here: on Forge 1.20.1 a listener gets it when it is
        // built (AddReloadListenerEvent) and keeps it, unlike NeoForge, which injects and clears it.
        CompletableFuture<?> done;
        try {
            // Not vanilla's profiled run: its log names lambdas by an empty string. Timed knows the
            // real class, which is what a pack author has to put in the skip list.
            done = SimpleReloadInstance.create(server.getResourceManager(), List.copyOf(timed),
                    Util.backgroundExecutor(), server, CompletableFuture.completedFuture(Unit.INSTANCE),
                    false).done();
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }

        return done.handleAsync((ignored, error) -> {
            if (error != null) throw new java.util.concurrent.CompletionException(error);
            if (fireTagsUpdated) {
                // Some mods build their recipe caches when tags arrive rather than in a listener of
                // their own, since that is the first moment both recipes and tags are ready.
                MinecraftForge.EVENT_BUS.post(new TagsUpdatedEvent(server.registryAccess(), false, false));
            }
            long millis = (System.nanoTime() - start) / 1_000_000;
            LOGGER.info("[HistoryStages/LightReload] Replayed {} mod reload listeners ({} skipped{}) in {} ms",
                    toRun.size(), skippedFinal, fireTagsUpdated ? ", tags event fired" : "", millis);
            logSlowest(timed);
            return new Result(millis, toRun.size(), skippedFinal);
        }, server);
    }

    private static Kind kindOf(PreparableReloadListener inner, Set<String> configSkipped) {
        if (inner instanceof TagManager || inner instanceof RecipeManager
                || inner instanceof ServerFunctionLibrary || inner instanceof ServerAdvancementManager) {
            return Kind.VANILLA;
        }
        String name = inner.getClass().getName();
        return ALWAYS_SKIPPED.contains(name) || configSkipped.contains(name) ? Kind.SKIPPED : Kind.REPLAYED;
    }

    private static Set<String> configSkipped() {
        try {
            Set<String> out = new java.util.HashSet<>();
            for (String name : Config.GAMEPLAY.targetedReloadSkip.get()) out.add(name.trim());
            return out;
        } catch (IllegalStateException e) {
            return Set.copyOf(StageReloadMode.DEFAULT_TARGETED_SKIP);
        }
    }

    private static int hintThresholdMs() {
        try {
            return Config.GAMEPLAY.slowListenerHintMs.get();
        } catch (IllegalStateException e) {
            return 250;
        }
    }

    /**
     * Names the listeners that held the server thread longest, so a pack author can find their own
     * Modular Machinery without a profiler. Only the part on the server thread counts: what a
     * listener prepares on the background pool costs the players nothing.
     */
    private static void logSlowest(List<Timed> timed) {
        int threshold = hintThresholdMs();
        if (threshold <= 0) return;
        List<Timed> slow = timed.stream()
                .filter(t -> t.applyMillis() >= threshold)
                .sorted(java.util.Comparator.comparingLong(Timed::applyMillis).reversed())
                .limit(3)
                .toList();
        if (slow.isEmpty()) return;
        StringBuilder line = new StringBuilder();
        for (Timed t : slow) {
            if (line.length() > 0) line.append(", ");
            line.append(t.inner.getClass().getName());
            String module = t.inner.getClass().getModule().getName();
            if (module != null) line.append(" [").append(module).append(']');
            line.append(' ').append(t.applyMillis()).append(" ms");
        }
        LOGGER.warn("[HistoryStages/TargetedReload] Held the server thread longest: {}. A listener that "
                + "only loads its own data and keeps no recipes can be left out of this reload: add its "
                + "class name to targetedReloadSkip under [advanced] in gameplay.toml. Threshold: {} ms "
                + "(slowListenerHintMs, 0 turns this off).", line, threshold);
    }

    /**
     * A mod's listener with a stopwatch on the part that runs on the server thread. The listener
     * itself is untouched: same preparation, same resource manager, same condition context.
     */
    private static final class Timed implements PreparableReloadListener {
        final PreparableReloadListener inner;
        private final java.util.concurrent.atomic.AtomicLong applyNanos =
                new java.util.concurrent.atomic.AtomicLong();

        Timed(PreparableReloadListener inner) {
            this.inner = inner;
        }

        long applyMillis() {
            return applyNanos.get() / 1_000_000;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier,
                                              net.minecraft.server.packs.resources.ResourceManager resourceManager,
                                              net.minecraft.util.profiling.ProfilerFiller preparationsProfiler,
                                              net.minecraft.util.profiling.ProfilerFiller reloadProfiler,
                                              java.util.concurrent.Executor backgroundExecutor,
                                              java.util.concurrent.Executor gameExecutor) {
            java.util.concurrent.Executor timedGame = task -> gameExecutor.execute(() -> {
                long start = System.nanoTime();
                try {
                    task.run();
                } finally {
                    applyNanos.addAndGet(System.nanoTime() - start);
                }
            });
            return inner.reload(barrier, resourceManager, preparationsProfiler, reloadProfiler,
                    backgroundExecutor, timedGame);
        }

        @Override
        public String getName() {
            return inner.getName();
        }
    }

    /**
     * Takes off the wrapper Forge puts around every listener a mod adds.
     *
     * <p>Two reasons: the wrapper's class is the same for all of them, which makes the profiled log
     * useless, and the skip list has to see the real class. The wrapper itself only adds a check
     * for mod loading errors, which cannot have happened on a running server.
     */
    private static PreparableReloadListener unwrap(PreparableReloadListener listener) {
        if (!listener.getClass().getName().endsWith("AddReloadListenerEvent$WrappedStateAwareListener")) {
            return listener;
        }
        try {
            Field wrapped = listener.getClass().getDeclaredField("wrapped");
            wrapped.setAccessible(true);
            return (PreparableReloadListener) wrapped.get(listener);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return listener;
        }
    }
}
