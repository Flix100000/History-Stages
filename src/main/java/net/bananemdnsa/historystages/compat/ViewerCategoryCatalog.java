package net.bananemdnsa.historystages.compat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;

/**
 * The recipe categories the installed viewers know, for the exemption picker.
 *
 * <p>JEI and EMI each hand in a supplier once their runtime is up; nothing is copied until the
 * picker asks, so a category a plugin adds late still shows. EMI also lists JEI's categories
 * through its bridge, so the two are merged by id.
 */
public final class ViewerCategoryCatalog {

    public record Category(String id, Component title) {}

    private static final Map<String, Supplier<Collection<Category>>> SOURCES = new LinkedHashMap<>();

    private ViewerCategoryCatalog() {}

    /** Called again on every viewer reload; the key keeps one supplier per viewer. */
    public static synchronized void register(String viewer, Supplier<Collection<Category>> source) {
        SOURCES.put(viewer, source);
    }

    /** Every known category once, sorted by id. Empty when no viewer is installed. */
    public static synchronized List<Category> all() {
        Map<String, Category> byId = new LinkedHashMap<>();
        for (Supplier<Collection<Category>> source : SOURCES.values()) {
            try {
                for (Category category : source.get()) byId.putIfAbsent(category.id(), category);
            } catch (RuntimeException ignored) {
                // A viewer mid-reload has no list to give. The other one may still have one.
            }
        }
        List<Category> out = new ArrayList<>(byId.values());
        out.sort(Comparator.comparing(Category::id));
        return out;
    }
}
