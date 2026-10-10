package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
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
        for (Holder<Item> item : enchantment.getSupportedItems()) {
            item.unwrapKey().ifPresent(key -> {
                String id = key.location().toString();
                if (!ids.contains(id)) ids.add(id);
            });
        }
        ids.subList(1, ids.size()).sort(String::compareToIgnoreCase);
        return ids;
    }

    /**
     * Every item type that can carry a potion: the ones that come with potion contents as a
     * default component. That is how the game sets up potions, splash and lingering potions and
     * tipped arrows, and how a mod following the same pattern sets up its own. An item that only
     * gains potion contents at runtime is not listed; it is still locked, because the lock reads
     * the stack.
     */
    public static List<String> forEffect() {
        List<String> ids = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!item.components().has(DataComponents.POTION_CONTENTS)) continue;
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
                String id = effect.getEffect().unwrapKey().map(key -> key.location().toString()).orElse("");
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
