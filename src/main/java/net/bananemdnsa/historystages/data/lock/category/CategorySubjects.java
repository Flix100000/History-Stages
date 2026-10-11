package net.bananemdnsa.historystages.data.lock.category;

import java.util.Set;

import net.bananemdnsa.historystages.api.lock.LockSubject;
import net.bananemdnsa.historystages.api.lock.Subjects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Turns what a caller naturally has into what a built-in category reads.
 *
 * <p>The item-shaped built-ins match against an internal subject record that no addon or script
 * can build. Before this existed, {@code isLocked("items", "minecraft:diamond", player)} — the
 * example in the scripting docs — handed them a string, they answered "no" to anything that was
 * not their record, and every item read as unlocked. Nothing threw, nothing logged.
 *
 * <p>So the generic entry points pass every subject through here first. A {@link LockSubject}
 * passes untouched, and so does anything this class has no rule for: an addon category decides
 * for itself what its subjects are.
 */
public final class CategorySubjects {

    private static final Set<String> ITEM_SHAPED = Set.of(
            "historystages:items", "historystages:tags", "historystages:mods");

    private CategorySubjects() {}

    public static Object normalize(String categoryId, Object subject) {
        if (subject == null || subject instanceof LockSubject) return subject;

        if (ITEM_SHAPED.contains(categoryId)) {
            if (subject instanceof ItemStack stack) return Subjects.item(stack);
            if (subject instanceof Item item) return Subjects.item(item);
            String id = idOf(subject);
            return id != null ? Subjects.item(id) : subject;
        }
        switch (categoryId) {
            case "historystages:fluids" -> {
                if (subject instanceof ItemStack stack) return Subjects.item(stack);
                if (subject instanceof FluidStack fluid) return Subjects.fluid(fluidId(fluid.getFluid()));
                if (subject instanceof Fluid fluid) return Subjects.fluid(fluidId(fluid));
                String id = idOf(subject);
                return id != null ? Subjects.fluid(id) : subject;
            }
            case "historystages:enchantments", "historystages:effects" -> {
                if (subject instanceof ItemStack stack) return Subjects.item(stack);
                String id = idOf(subject);
                if (id == null) return subject;
                // No level given, so the highest: "is this gated at any level at all".
                return categoryId.endsWith("enchantments")
                        ? Subjects.enchantment(id, Integer.MAX_VALUE)
                        : Subjects.effect(id, Integer.MAX_VALUE);
            }
            case "historystages:attacklock" -> {
                if (subject instanceof Entity entity) return entityId(entity.getType());
                if (subject instanceof EntityType<?> type) return entityId(type);
            }
            default -> {
                // The id-keyed built-ins compare strings, so any registry handle becomes one.
            }
        }
        String id = idOf(subject);
        return id != null && categoryId.startsWith("historystages:") ? id : subject;
    }

    /** The text id behind a string or registry handle, or null for anything else. */
    private static String idOf(Object subject) {
        if (subject instanceof String s) return s;
        if (subject instanceof ResourceLocation rl) return rl.toString();
        if (subject instanceof ResourceKey<?> key) return key.location().toString();
        if (subject instanceof Holder<?> holder) {
            return holder.unwrapKey().map(key -> key.location().toString()).orElse(null);
        }
        return null;
    }

    private static String entityId(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    private static String fluidId(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid).toString();
    }
}
