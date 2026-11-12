package net.bananemdnsa.historystages.data.lock.engine;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

/**
 * Reads a stack's enchantments and potion contents into {@link StackContents}.
 *
 * <p>The Minecraft half of that record, kept apart so the record itself stays loadable in unit
 * tests. Only the vanilla components are read; a mod that keeps its bonuses somewhere else is not
 * enchanting the item as far as Minecraft is concerned either.
 */
public final class StackContentsReader {

    private StackContentsReader() {}

    public static StackContents of(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return StackContents.EMPTY;

        List<StackContents.Levelled> enchantments = read(stack.get(DataComponents.ENCHANTMENTS));
        List<StackContents.Levelled> stored = read(stack.get(DataComponents.STORED_ENCHANTMENTS));

        List<StackContents.Levelled> effects = List.of();
        List<String> potions = List.of();
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) {
            potions = potion.potion()
                    .flatMap(Holder::unwrapKey)
                    .map(key -> List.of(key.location().toString()))
                    .orElse(List.of());
            List<StackContents.Levelled> found = new ArrayList<>();
            for (MobEffectInstance effect : potion.getAllEffects()) {
                effect.getEffect().unwrapKey().ifPresent(key ->
                        found.add(new StackContents.Levelled(key.location().toString(),
                                effect.getAmplifier() + 1)));
            }
            effects = found;
        }

        if (enchantments.isEmpty() && stored.isEmpty() && effects.isEmpty() && potions.isEmpty()) {
            return StackContents.EMPTY;
        }
        return new StackContents(enchantments, stored, effects, potions);
    }

    private static List<StackContents.Levelled> read(@Nullable ItemEnchantments enchantments) {
        if (enchantments == null || enchantments.isEmpty()) return List.of();
        List<StackContents.Levelled> found = new ArrayList<>(enchantments.size());
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            entry.getKey().unwrapKey().ifPresent(key ->
                    found.add(new StackContents.Levelled(key.location().toString(), entry.getIntValue())));
        }
        return found;
    }
}
