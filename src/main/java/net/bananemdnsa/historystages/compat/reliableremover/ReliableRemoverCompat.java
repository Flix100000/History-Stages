package net.bananemdnsa.historystages.compat.reliableremover;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Asks Reliable Remover whether a pack has deleted an item, so the editor's pickers can keep
 * those out of the way.
 *
 * <p>Reached reflectively — Reliable Remover is not on the compile classpath and most installs
 * won't have it, so this class names no type of its own. {@code isItemHidden} is the right
 * question rather than {@code isCreativeBlocked}: the latter answers no as soon as somebody turns
 * off Reliable Remover's creative-tab option, even though the item is still gone from the game.
 *
 * <p>The rules live in the client's own config and are evaluated locally, which is what makes
 * this usable from a GUI at all.
 */
public final class ReliableRemoverCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Method isItemHidden;
    private static boolean resolved;

    private ReliableRemoverCompat() {}

    /** Whether Reliable Remover is installed and its API is where we expect it. */
    public static boolean isPresent() {
        return api() != null;
    }

    /** Whether the pack has deleted this item. False when Reliable Remover isn't installed. */
    public static boolean isRemoved(ItemStack stack) {
        Method api = api();
        if (api == null) {
            return false;
        }
        try {
            return (boolean) api.invoke(null, stack);
        } catch (Throwable t) {
            // One bad answer means every following one is bad too, so stop asking rather than
            // log the same failure once per registry entry.
            isItemHidden = null;
            LOGGER.warn("[HistoryStages/ReliableRemover] isItemHidden failed, giving up on the filter", t);
            return false;
        }
    }

    private static Method api() {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> apiClass = Class.forName("com.evandev.reliable_remover.api.ReliableRemoverAPI");
                isItemHidden = apiClass.getMethod("isItemHidden", ItemStack.class);
            } catch (ClassNotFoundException e) {
                // Not installed.
            } catch (Throwable t) {
                LOGGER.warn("[HistoryStages/ReliableRemover] Found the mod but not its API", t);
            }
        }
        return isItemHidden;
    }
}
