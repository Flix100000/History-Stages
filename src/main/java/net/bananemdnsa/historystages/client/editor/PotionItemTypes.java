package net.bananemdnsa.historystages.client.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Every item type that can carry a potion: the ones that come with potion contents as a default
 * component. That is how the game itself sets up potions, splash and lingering potions and tipped
 * arrows, and how a mod following the same pattern sets up its own.
 *
 * <p>An item that only gains potion contents at runtime is not listed, so it cannot be spared on
 * its own. It is still locked: the lock reads the stack, not this list.
 */
public final class PotionItemTypes {

    private PotionItemTypes() {}

    public static List<String> all() {
        List<String> ids = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!item.components().has(DataComponents.POTION_CONTENTS)) continue;
            ids.add(BuiltInRegistries.ITEM.getKey(item).toString());
        }
        ids.sort(String::compareToIgnoreCase);
        return ids;
    }

    public static String displayName(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return itemId;
        return BuiltInRegistries.ITEM.get(key).getDescription().getString();
    }
}
