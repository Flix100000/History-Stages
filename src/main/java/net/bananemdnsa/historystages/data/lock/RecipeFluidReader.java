package net.bananemdnsa.historystages.data.lock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Describes the fluids a loaded recipe holds, in the JSON shape {@link FluidRecipeScanner} reads.
 *
 * <p>The neoforge branch gets that shape by encoding the recipe through its own codec. A 1.20.1
 * serialiser has no codec and no way back to JSON, so this reads the recipe object instead: the
 * fields a mod keeps its fluid stacks and fluid ingredients in. Create's {@code fluidIngredients}
 * and {@code fluidResults}, Mekanism's {@code input} and {@code output} and the like come out as
 * {@code {"ingredients":{"fluid":[...]}}} and {@code {"results":{"fluid":[...]}}}, so the scanner's
 * side heuristic and its test apply unchanged.
 *
 * <p>Reading the object rather than the file also means recipes a script adds after loading are
 * covered on both sides, since the client holds the same objects the server sent.
 *
 * <p>Fails quietly per recipe: a field that cannot be read is skipped, and a recipe nothing can be
 * read from simply mentions no fluid.
 */
public final class RecipeFluidReader {

    /** Deep enough for a fluid ingredient wrapped in a list inside a holder object, no further. */
    private static final int MAX_DEPTH = 3;

    private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();

    /** Accessors fluid ingredient classes commonly offer for the fluids they match. */
    private static final String[] FLUID_ACCESSORS = {
            "getMatchingFluidStacks", "getFluids", "getRepresentations", "getMatchingInstances", "getStacks"};

    private RecipeFluidReader() {}

    public static JsonElement read(Recipe<?> recipe) {
        if (recipe == null) return null;
        JsonObject out = new JsonObject();
        describeFields(recipe, out, 0);
        return out;
    }

    private static void describeFields(Object owner, JsonObject out, int depth) {
        for (Field field : fieldsOf(owner.getClass())) {
            Object value;
            try {
                value = field.get(owner);
            } catch (Throwable ignored) {
                continue;
            }
            JsonElement described = describe(value, depth + 1);
            if (described == null) continue;

            String name = field.getName().toLowerCase(Locale.ROOT);
            JsonElement placed = name.contains("fluid") ? wrapAsFluid(described) : described;
            String key = sideKey(name);
            if (out.has(key)) {
                JsonArray merged = new JsonArray();
                merged.add(out.get(key));
                merged.add(placed);
                out.add(key, merged);
            } else {
                out.add(key, placed);
            }
        }
    }

    private static JsonElement describe(Object value, int depth) {
        if (value == null || depth > MAX_DEPTH) return null;

        if (value instanceof FluidStack stack) {
            return stack.isEmpty() ? null : idOf(stack.getFluid());
        }
        if (value instanceof Fluid fluid) {
            return idOf(fluid);
        }
        if (value instanceof Optional<?> optional) {
            return optional.map(inner -> describe(inner, depth)).orElse(null);
        }
        if (value instanceof Iterable<?> iterable) {
            return describeAll(iterable, depth);
        }
        if (value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
            List<Object> elements = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) elements.add(Array.get(value, i));
            return describeAll(elements, depth);
        }

        Class<?> type = value.getClass();
        if (type.getSimpleName().toLowerCase(Locale.ROOT).contains("fluid")) {
            JsonElement viaAccessor = describeThroughAccessor(value, depth);
            if (viaAccessor != null) return wrapAsFluid(viaAccessor);
        }
        if (isForeignStructure(type)) {
            JsonObject nested = new JsonObject();
            describeFields(value, nested, depth);
            return nested.size() == 0 ? null : nested;
        }
        return null;
    }

    private static JsonElement describeAll(Iterable<?> values, int depth) {
        JsonArray array = new JsonArray();
        for (Object element : values) {
            JsonElement described = describe(element, depth);
            if (described != null) array.add(described);
        }
        return array.size() == 0 ? null : array;
    }

    private static JsonElement describeThroughAccessor(Object ingredient, int depth) {
        for (String name : FLUID_ACCESSORS) {
            try {
                Method method = ingredient.getClass().getMethod(name);
                if (method.getParameterCount() != 0) continue;
                JsonElement described = describe(method.invoke(ingredient), depth);
                if (described != null) return described;
            } catch (Throwable ignored) {
                // not this accessor, or it threw — try the next one
            }
        }
        return null;
    }

    /**
     * The scanner decides a side by exact key, and only knows the handful of names mods use in
     * their JSON. Field names differ from those ({@code fluidResults}, {@code outputFluid}), so they
     * are mapped onto the scanner's words here.
     */
    private static String sideKey(String lowerName) {
        if (lowerName.contains("result") || lowerName.contains("output")) return "results";
        if (lowerName.contains("ingredient") || lowerName.contains("input")) return "ingredients";
        return lowerName;
    }

    /** Puts the value under a fluid-named key, so an id that is also an item is read as the fluid. */
    private static JsonElement wrapAsFluid(JsonElement value) {
        JsonObject wrapped = new JsonObject();
        wrapped.add("fluid", value);
        return wrapped;
    }

    private static JsonElement idOf(Fluid fluid) {
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        return id == null ? null : new JsonPrimitive(id.toString());
    }

    /**
     * A mod's own value object worth looking inside. Vanilla and library types are not: an item
     * stack or an ingredient holds no fluid, and walking into them costs time on every recipe.
     */
    private static boolean isForeignStructure(Class<?> type) {
        String name = type.getName();
        return !type.isPrimitive() && !type.isEnum()
                && !name.startsWith("java.") && !name.startsWith("net.minecraft.")
                && !name.startsWith("com.google.") && !name.startsWith("com.mojang.")
                && !name.startsWith("it.unimi.");
    }

    private static List<Field> fieldsOf(Class<?> type) {
        return FIELDS.computeIfAbsent(type, t -> {
            List<Field> fields = new ArrayList<>();
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    try {
                        field.setAccessible(true);
                        fields.add(field);
                    } catch (Throwable ignored) {
                        // a module that does not open this class — nothing to read here
                    }
                }
            }
            return fields;
        });
    }
}
