package net.bananemdnsa.historystages.compat.emi;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Best-effort bridge to trigger an EMI reload at runtime so newly-added boosters and
 * other config changes show up immediately in EMI.
 *
 * <p>EMI rebuilds its recipe/stack index only while a plugin's {@code register} runs,
 * which happens on an EMI reload. The reload entry point
 * ({@code dev.emi.emi.runtime.EmiReloadManager#reload}) lives in an internal package
 * that is not on our compile classpath, so it is invoked reflectively and fully guarded
 * — this is a no-op when EMI is absent or the method ever moves. This class intentionally
 * references no EMI type directly, so it is safe to load even without EMI installed.
 */
public final class EmiReloadBridge {
    private static final Logger LOGGER = LogUtils.getLogger();

    private EmiReloadBridge() {}

    /**
     * Reload after a stage change, but only when something is hidden. The lock overlay is drawn
     * fresh every time a recipe opens and needs no reload; the hiding only happens during one.
     */
    public static void reloadIfHiding() {
        if (net.bananemdnsa.historystages.Config.VISUAL.hideLockedItemsInViewers.get()
                || net.bananemdnsa.historystages.Config.VISUAL.hideLockedRecipesInViewers.get()
                || net.bananemdnsa.historystages.compat.HiddenScrolls.anyHiddenBlocks()) {
            reloadIfPresent();
        }
    }

    /** The hiding switches EMI's index was last built with; null until EMI has registered once. */
    private static volatile Boolean builtHideItems;
    private static volatile Boolean builtHideRecipes;
    /** The exempt categories, which EMI's recipe hiding skips. */
    private static volatile java.util.List<String> builtExemptions;

    /** Called when our EMI plugin registers, which is when the hiding is actually applied. */
    public static void rememberBuilt() {
        builtHideItems = net.bananemdnsa.historystages.Config.VISUAL.hideLockedItemsInViewers.get();
        builtHideRecipes = net.bananemdnsa.historystages.Config.VISUAL.hideLockedRecipesInViewers.get();
        builtExemptions = currentExemptions();
    }

    private static java.util.List<String> currentExemptions() {
        return java.util.List.copyOf(net.bananemdnsa.historystages.Config.VISUAL.lockOverlayExemptions.get());
    }

    /**
     * Reload when a config sync changed one of the hiding switches since EMI last built its index.
     *
     * <p>Compared with what EMI was built with rather than with the value before the sync: on an
     * integrated server the client shares the server's spec, so by the time the sync arrives
     * "before" and "after" are already the same, and switching hiding off would never reload.
     */
    public static void reloadIfViewerSettingsChanged() {
        Boolean items = builtHideItems;
        Boolean recipes = builtHideRecipes;
        if (items == null || recipes == null) return; // EMI has not built yet; it will read the new values
        boolean hideRecipesNow = net.bananemdnsa.historystages.Config.VISUAL.hideLockedRecipesInViewers.get();
        // The exemptions only matter to the hiding: the overlay asks them fresh on every draw.
        boolean exemptionsChanged = hideRecipesNow && !currentExemptions().equals(builtExemptions);
        if (items != net.bananemdnsa.historystages.Config.VISUAL.hideLockedItemsInViewers.get()
                || recipes != hideRecipesNow || exemptionsChanged) {
            reloadIfPresent();
        }
    }

    /** Triggers a full EMI reload if EMI is present; silently does nothing otherwise. */
    public static void reloadIfPresent() {
        try {
            Class<?> manager = Class.forName("dev.emi.emi.runtime.EmiReloadManager");
            Method reload = manager.getMethod("reload");
            reload.invoke(null);
            LOGGER.debug("[HistoryStages/EMI] Triggered EMI reload.");
        } catch (ClassNotFoundException e) {
            // EMI not installed — nothing to do.
        } catch (Throwable t) {
            LOGGER.warn("[HistoryStages/EMI] Failed to trigger EMI reload", t);
        }
    }
}
