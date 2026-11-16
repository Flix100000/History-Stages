package net.bananemdnsa.historystages.reload;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.mojang.logging.LogUtils;

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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;
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
 * manager they have already edited once. They are named in {@link #SKIPPED}.
 */
public final class LightReload {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * What a stage change that altered the gated recipes does. In memory only, for testing.
     * <ul>
     *   <li>FULL — what shipped: every datapack, and the server waits for it.</li>
     *   <li>BACKGROUND — the same full reload, with the server ticking on, see {@link BackgroundReload}.</li>
     *   <li>TARGETED — only the mods' own listeners plus a tags event, also without waiting.</li>
     * </ul>
     */
    public enum Mode { FULL, BACKGROUND, TARGETED }

    /**
     * Listeners that must not run twice on one recipe load. Both run scripts that add and remove
     * recipes — a second pass would apply every removal and addition again to the result of the
     * first.
     */
    private static final Set<String> SKIPPED = Set.of(
            "dev.latvian.mods.kubejs.server.KubeJSReloadListener",
            "com.blamejared.crafttweaker.impl.script.ScriptReloadListener");

    private static volatile Mode mode = Mode.TARGETED;
    /**
     * Whether a stage change sends clients the recipe list. Sending it restarts JEI from scratch;
     * not sending it leaves JEI to the hide pass on the stage sync. Off on this branch, to test
     * whether that pass is enough on its own.
     */
    private static volatile boolean resendRecipes = false;

    public static boolean resendRecipes() {
        return resendRecipes;
    }

    public static void setResendRecipes(boolean resend) {
        resendRecipes = resend;
    }
    private static volatile List<PreparableReloadListener> remembered = List.of();
    private static volatile boolean profileRequested;

    private LightReload() {}

    public static Mode mode() {
        return mode;
    }

    public static void setMode(Mode newMode) {
        mode = newMode;
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
            out.add(new Entry(inner.getClass().getName(), kindOf(inner)));
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
        ReloadableServerResources managers = server.getServerResources().managers();

        List<PreparableReloadListener> toRun = new ArrayList<>();
        int skipped = 0;
        for (PreparableReloadListener listener : remembered) {
            PreparableReloadListener inner = unwrap(listener);
            switch (kindOf(inner)) {
                case REPLAYED -> toRun.add(inner);
                case SKIPPED -> skipped++;
                case VANILLA -> { }
            }
        }
        int skippedFinal = skipped;

        // NeoForge hands each listener the reload's condition context and clears it again once the
        // reload is over. A listener reading conditions without it would see every one as false.
        injectContext(toRun, managers.getConditionContext(), managers.getRegistryLookup());
        CompletableFuture<?> done;
        try {
            done = SimpleReloadInstance.create(server.getResourceManager(), toRun,
                    Util.backgroundExecutor(), server, CompletableFuture.completedFuture(Unit.INSTANCE),
                    true).done();
        } catch (RuntimeException e) {
            injectContext(toRun, ICondition.IContext.EMPTY, RegistryAccess.EMPTY);
            return CompletableFuture.failedFuture(e);
        }

        return done.handleAsync((ignored, error) -> {
            injectContext(toRun, ICondition.IContext.EMPTY, RegistryAccess.EMPTY);
            if (error != null) throw new java.util.concurrent.CompletionException(error);
            if (fireTagsUpdated) {
                // Some mods build their recipe caches when tags arrive rather than in a listener of
                // their own, since that is the first moment both recipes and tags are ready.
                NeoForge.EVENT_BUS.post(new TagsUpdatedEvent(managers.fullRegistries().get(), false, false));
            }
            long millis = (System.nanoTime() - start) / 1_000_000;
            LOGGER.info("[HistoryStages/LightReload] Replayed {} mod reload listeners ({} skipped{}) in {} ms",
                    toRun.size(), skippedFinal, fireTagsUpdated ? ", tags event fired" : "", millis);
            return new Result(millis, toRun.size(), skippedFinal);
        }, server);
    }

    private static Kind kindOf(PreparableReloadListener inner) {
        if (inner instanceof TagManager || inner instanceof RecipeManager
                || inner instanceof ServerFunctionLibrary || inner instanceof ServerAdvancementManager) {
            return Kind.VANILLA;
        }
        return SKIPPED.contains(inner.getClass().getName()) ? Kind.SKIPPED : Kind.REPLAYED;
    }

    private static void injectContext(List<PreparableReloadListener> listeners, ICondition.IContext context,
                                      net.minecraft.core.HolderLookup.Provider lookup) {
        for (PreparableReloadListener listener : listeners) {
            if (listener instanceof ContextAwareReloadListener aware) aware.injectContext(context, lookup);
        }
    }

    /**
     * Takes off the wrapper NeoForge puts around every listener a mod adds.
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
