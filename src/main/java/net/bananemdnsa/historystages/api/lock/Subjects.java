package net.bananemdnsa.historystages.api.lock;

import java.util.List;

import net.bananemdnsa.historystages.data.lock.MerchantSubject;
import net.bananemdnsa.historystages.data.lock.TradeOfferSubject;
import net.bananemdnsa.historystages.data.lock.engine.FluidContent;
import net.bananemdnsa.historystages.data.lock.engine.LockSubjects;
import net.bananemdnsa.historystages.data.lock.engine.StackContents;
import net.bananemdnsa.historystages.data.lock.engine.StackContentsReader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the subjects the built-in categories are asked about, for {@link CategoryLocks}.
 *
 * <p>Most built-ins take a bare id string: dimensions, recipes, structures, biomes, trade levels
 * and attack locks. Those need nothing from here. The rest look at more than an id, and this is
 * the only way to hand them what they look at:
 *
 * <table>
 *   <caption>Which subject each built-in category reads</caption>
 *   <tr><th>Category</th><th>Subject</th></tr>
 *   <tr><td>items, tags, mods, fluids, enchantments, effects</td>
 *       <td>{@link #item(ItemStack)}; {@code CategoryLocks} also takes a plain {@code ItemStack},
 *       {@code Item} or item id</td></tr>
 *   <tr><td>fluids, for a fluid standing in the world</td><td>{@link #fluid(String)}</td></tr>
 *   <tr><td>enchantments, without an item</td><td>{@link #enchantment(String, int)}</td></tr>
 *   <tr><td>effects, without an item</td><td>{@link #effect(String, int)}</td></tr>
 *   <tr><td>spawnlock</td><td>{@link #spawn(String, String, String)}</td></tr>
 *   <tr><td>interactionlock</td><td>{@link #interaction(String, String, ItemStack)}</td></tr>
 *   <tr><td>trade_professions</td><td>{@link #merchant(String, int)}</td></tr>
 *   <tr><td>trades</td><td>{@link #tradeOffer(String, int, ItemStack, ItemStack, ItemStack)}</td></tr>
 * </table>
 *
 * <p>Zones gate a place rather than a thing and cannot be asked about this way.
 */
public final class Subjects {

    private Subjects() {}

    /**
     * A stack, with everything the item-shaped categories read from it: its id and mod, the fluid
     * it carries, and its enchantments and potion contents.
     */
    public static LockSubject item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new LockSubjects.ItemSubject("", "", null, null, null);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return new LockSubjects.ItemSubject(id.toString(), id.getNamespace(), stack, stack.getItem(),
                FluidContent.of(stack), StackContentsReader.of(stack));
    }

    /** An item without a stack: no fluid, no enchantments, and NBT criteria never match it. */
    public static LockSubject item(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return new LockSubjects.ItemSubject(id.toString(), id.getNamespace(), null, item, null);
    }

    /**
     * An item by id, like {@link #item(Item)}. An id the registry does not know is still asked
     * about by its text, so a stage naming it answers as it would for the real item.
     */
    public static LockSubject item(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return new LockSubjects.ItemSubject(itemId, "", null, null, null);
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return new LockSubjects.ItemSubject(id.toString(), id.getNamespace(), null, item, null);
    }

    /** A fluid by id, with no container around it — a pool, a tank's contents. */
    public static LockSubject fluid(String fluidId) {
        return new LockSubjects.ItemSubject("", "", null, null, fluidId);
    }

    /**
     * An enchantment at a level, on no particular item. Answers the way an item carrying it would
     * be answered; whether a station may apply it is {@link ItemLocks#isEnchantmentLocked}.
     */
    public static LockSubject enchantment(String enchantmentId, int level) {
        return new LockSubjects.ItemSubject("", "", null, null, null, new StackContents(
                List.of(new StackContents.Levelled(enchantmentId, level)), List.of(), List.of(), List.of()));
    }

    /** A potion effect at a level (amplifier + 1), on no particular item. */
    public static LockSubject effect(String effectId, int level) {
        return new LockSubjects.ItemSubject("", "", null, null, null, new StackContents(
                List.of(), List.of(), List.of(new StackContents.Levelled(effectId, level)), List.of()));
    }

    /**
     * A spawn attempt.
     *
     * @param source    {@code natural}, {@code spawner}, {@code structure}, {@code breeding},
     *                  {@code summon} or {@code spawn_egg}; or null to ask whether any entry covers
     *                  this entity in this dimension at all
     * @param dimension the dimension id, e.g. {@code minecraft:overworld}
     */
    public static LockSubject spawn(String entityId, @Nullable String source, String dimension) {
        return new LockSubjects.SpawnSubject(entityId, source, dimension);
    }

    /**
     * A player interacting with an entity.
     *
     * @param action {@code breed}, {@code mount}, {@code trade}, {@code leash}, {@code shear},
     *               {@code milk}, {@code name}, {@code equip} or {@code other}
     * @param held   what the player holds; {@link ItemStack#EMPTY} for an empty hand
     */
    public static LockSubject interaction(String entityId, String action, ItemStack held) {
        return new LockSubjects.InteractionSubject(entityId, action, held == null ? ItemStack.EMPTY : held);
    }

    /**
     * A merchant as a whole.
     *
     * @param professionId the villager profession, or the entity id for a merchant without one
     * @param level        the merchant's level, 1 for anything without levels
     */
    public static LockSubject merchant(String professionId, int level) {
        return new MerchantSubject(professionId, level);
    }

    /**
     * One trade offer.
     *
     * @param merchantKey the villager profession, or the entity id for a merchant without one
     * @param costB       the second cost, or {@link ItemStack#EMPTY}
     */
    public static LockSubject tradeOffer(String merchantKey, int level, ItemStack gives,
                                         ItemStack costA, ItemStack costB) {
        return new TradeOfferSubject(merchantKey, level, idOf(gives), idOf(costA), idOf(costB),
                gives == null || gives.isEmpty() ? null : gives);
    }

    @Nullable
    private static String idOf(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
