package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.TippedArrowItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * What the game itself knows about an enchantment or effect, for the editor's choices: which item
 * types can carry it and how high it goes.
 *
 * <p>Read from the live registries, so a mod's own enchantments and potions answer the same way
 * vanilla ones do. Common code rather than client code, so the GameTests can ask it too.
 */
public final class ItemTypeChoices {

    private ItemTypeChoices() {}

    /**
     * The book plus every item the enchantment can go on, as the enchantment itself declares it.
     * Empty when the id is unknown.
     */
    public static List<String> forEnchantment(RegistryAccess access, String enchantmentId) {
        Enchantment enchantment = enchantment(access, enchantmentId);
        if (enchantment == null) return List.of();
        List<String> ids = new ArrayList<>();
        ids.add("minecraft:enchanted_book");
        // 1.20.1 has no supported-items set; the enchantment answers per item, as the anvil asks it.
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.ENCHANTED_BOOK || !enchantment.canEnchant(new ItemStack(item))) continue;
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (!ids.contains(id)) ids.add(id);
        }
        ids.subList(1, ids.size()).sort(String::compareToIgnoreCase);
        return ids;
    }

    /**
     * Every item type that can carry a potion: potions, splash and lingering potions and tipped
     * arrows, and whatever a mod builds on the same item classes. 1.20.1 has no default potion
     * component to ask, so the class is the sign. An item that only gains a potion tag at runtime
     * is not listed; it is still locked, because the lock reads the stack.
     */
    public static List<String> forEffect() {
        List<String> ids = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof PotionItem) && !(item instanceof TippedArrowItem)) continue;
            ids.add(BuiltInRegistries.ITEM.getKey(item).toString());
        }
        ids.sort(String::compareToIgnoreCase);
        return ids;
    }

    /** The enchantment's maximum level, or 0 when the id is unknown. */
    public static int maxEnchantmentLevel(RegistryAccess access, String enchantmentId) {
        Enchantment enchantment = enchantment(access, enchantmentId);
        return enchantment == null ? 0 : enchantment.getMaxLevel();
    }

    /**
     * The highest level any registered potion gives this effect, or 0 when no potion gives it at
     * all. Effects have no maximum of their own; the potions are what a player can actually get.
     */
    public static int maxEffectLevel(String effectId) {
        int max = 0;
        for (Potion potion : BuiltInRegistries.POTION) {
            for (MobEffectInstance effect : potion.getEffects()) {
                String id = String.valueOf(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect()));
                if (id.equals(effectId)) max = Math.max(max, effect.getAmplifier() + 1);
            }
        }
        return max;
    }

    public static String displayName(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return itemId;
        return BuiltInRegistries.ITEM.get(key).getDescription().getString();
    }

    private static Enchantment enchantment(RegistryAccess access, String enchantmentId) {
        ResourceLocation key = ResourceLocation.tryParse(enchantmentId);
        if (key == null) return null;
        return access.registry(Registries.ENCHANTMENT).map(registry -> registry.get(key)).orElse(null);
    }
}
