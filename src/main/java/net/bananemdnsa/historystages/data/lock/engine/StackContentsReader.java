package net.bananemdnsa.historystages.data.lock.engine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Reads a stack's enchantments and potion contents into {@link StackContents}.
 *
 * <p>The Minecraft half of that record, kept apart so the record itself stays loadable in unit
 * tests. Only the vanilla tags are read; a mod that keeps its bonuses somewhere else is not
 * enchanting the item as far as Minecraft is concerned either.
 *
 * <p>On 1.20.1 these live in the stack's NBT: {@code Enchantments} on the item itself,
 * {@code StoredEnchantments} on books, {@code Potion} and {@code CustomPotionEffects} for potions
 * and tipped arrows. Ids are read off the tag, so an enchantment whose mod is gone still shows up
 * under its old id, the same way NeoForge keeps the unresolved holder.
 */
public final class StackContentsReader {

    private StackContentsReader() {}

    public static StackContents of(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) return StackContents.EMPTY;
        CompoundTag tag = stack.getTag();

        List<StackContents.Levelled> enchantments = read(tag.getList(ItemStack.TAG_ENCH, Tag.TAG_COMPOUND));
        List<StackContents.Levelled> stored = read(tag.getList("StoredEnchantments", Tag.TAG_COMPOUND));

        List<String> potions = List.of();
        Potion potion = PotionUtils.getPotion(tag);
        if (potion != Potions.EMPTY) {
            potions = List.of(BuiltInRegistries.POTION.getKey(potion).toString());
        }
        List<StackContents.Levelled> effects = new ArrayList<>();
        for (MobEffectInstance effect : PotionUtils.getAllEffects(tag)) {
            ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
            if (id != null) effects.add(new StackContents.Levelled(id.toString(), effect.getAmplifier() + 1));
        }

        if (enchantments.isEmpty() && stored.isEmpty() && effects.isEmpty() && potions.isEmpty()) {
            return StackContents.EMPTY;
        }
        return new StackContents(enchantments, stored, List.copyOf(effects), potions);
    }

    private static List<StackContents.Levelled> read(ListTag list) {
        if (list.isEmpty()) return List.of();
        List<StackContents.Levelled> found = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation id = EnchantmentHelper.getEnchantmentId(entry);
            if (id != null) found.add(new StackContents.Levelled(id.toString(), EnchantmentHelper.getEnchantmentLevel(entry)));
        }
        return found;
    }
}
