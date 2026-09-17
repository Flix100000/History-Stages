package net.bananemdnsa.historystages.util.lock;

import net.bananemdnsa.historystages.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * The two questions every loot strip asks: is this stack locked for that player, and what goes
 * in its place.
 *
 * <p>Lootr loot as it is rolled and mob drops both ask it. They differ in which player they can
 * name, but not in the answer, and two copies of the replacement ladder had already drifted.
 */
public final class LootLocks {

    private static final Random RANDOM = new Random();

    private LootLocks() {}

    /**
     * Whether the "loot" action is gated for this stack.
     *
     * <p>A null player leaves only the global stages to go on. That is the mob drop nobody
     * killed — a fall, a mob on a mob — where there is no individual view to strip against.
     */
    public static boolean isLocked(ItemStack stack, @Nullable UUID playerUuid) {
        if (StageLockHelper.isActionLockedForServer(stack, "loot")) return true;
        return playerUuid != null
                && Config.GAMEPLAY.individualLockLoot.get()
                && StageLockHelper.isActionLockedByIndividualStage(stack, playerUuid, "loot");
    }

    /**
     * What a locked stack of this size turns into: an empty stack unless the pack asked for
     * replacements.
     */
    public static ItemStack replacementFor(int count) {
        if (!Config.GAMEPLAY.useReplacements.get()) return ItemStack.EMPTY;

        // 1. Priority: random item from replacementItems list
        List<? extends String> list = Config.GAMEPLAY.replacementItems.get();
        if (list != null && !list.isEmpty()) {
            try {
                String randomId = list.get(RANDOM.nextInt(list.size()));
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(randomId));
                if (item != null && item != Items.AIR) {
                    return new ItemStack(item, count);
                }
            } catch (Exception ignored) {}
        }

        // 2. Priority: random item from replacementTags
        List<? extends String> tagList = Config.GAMEPLAY.replacementTags.get();
        if (tagList != null && !tagList.isEmpty()) {
            try {
                String tagStr = tagList.get(RANDOM.nextInt(tagList.size()));
                TagKey<Item> tagKey = ItemTags.create(new ResourceLocation(tagStr));
                List<Item> tagItems = new ArrayList<>();
                ForgeRegistries.ITEMS.tags().getTag(tagKey).forEach(tagItems::add);
                if (!tagItems.isEmpty()) {
                    return new ItemStack(tagItems.get(RANDOM.nextInt(tagItems.size())), count);
                }
            } catch (Exception ignored) {}
        }

        return new ItemStack(Items.COBBLESTONE, count);
    }
}
