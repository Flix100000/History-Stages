package net.bananemdnsa.historystages.data.disguise;

import net.bananemdnsa.historystages.data.NbtMatcher;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Answers "what does this stack or block look like for this viewer", on either side.
 *
 * <p>The rule logic lives in {@link DisguiseRuleSet} and {@link DisguiseChain}; this class only
 * adds what needs Minecraft: registry lookups, tag membership, NBT and the lock check.
 */
public final class Disguises {

    private static final Map<String, TagKey<Item>> TAG_KEYS = new ConcurrentHashMap<>();

    private Disguises() {}

    /**
     * @param rule   the rule matched for the item itself; decides drops and hints
     * @param target where the chain ends; decides look, name, sounds and break behaviour
     */
    public record Resolved(DisguiseRule rule, Item target) {
        public DropsMode drops() {
            return rule.drops();
        }

        public boolean hints() {
            return rule.hints();
        }

        @Nullable
        public Block targetBlock() {
            return target instanceof BlockItem blockItem ? blockItem.getBlock() : null;
        }
    }

    // --- rule matching ---

    /** Item rule (with NBT if it has one) first, then the first tag rule by key. */
    @Nullable
    static DisguiseRule ruleFor(DisguiseRuleSet set, ItemStack stack) {
        DisguiseRule own = set.itemRule(idOf(stack.getItem()));
        if (own != null && (!own.hasNbt() || NbtMatcher.matches(stack, own.nbt()))) return own;
        return tagRuleFor(set, stack.getItem());
    }

    /**
     * Without a stack, so without NBT: an NBT rule cannot speak for a block in the world or a
     * chain step, it falls through to the tags.
     */
    @Nullable
    static DisguiseRule ruleForItem(DisguiseRuleSet set, Item item) {
        DisguiseRule own = set.itemRule(idOf(item));
        if (own != null && !own.hasNbt()) return own;
        return tagRuleFor(set, item);
    }

    @Nullable
    private static DisguiseRule tagRuleFor(DisguiseRuleSet set, Item item) {
        for (DisguiseRule rule : set.tagRules()) {
            TagKey<Item> tag = tagKey(rule.tagId());
            if (tag != null && item.builtInRegistryHolder().is(tag)) return rule;
        }
        return null;
    }

    @Nullable
    private static TagKey<Item> tagKey(String tagId) {
        TagKey<Item> cached = TAG_KEYS.get(tagId);
        if (cached != null) return cached;
        ResourceLocation rl = ResourceLocation.tryParse(tagId);
        if (rl == null) return null;
        TagKey<Item> key = TagKey.create(Registries.ITEM, rl);
        TAG_KEYS.put(tagId, key);
        return key;
    }

    // --- resolving ---

    /** The disguise of {@code stack}, or null when it has none or is not locked. */
    @Nullable
    public static Resolved resolve(ItemStack stack, Predicate<ItemStack> isLocked) {
        DisguiseRuleSet set = DisguiseData.get();
        if (set.isEmpty() || stack == null || stack.isEmpty()) return null;
        DisguiseRule first = ruleFor(set, stack);
        if (first == null) return null;
        return follow(set, idOf(stack.getItem()), first, stack, isLocked);
    }

    /** The disguise of a block standing in the world, or null. */
    @Nullable
    public static Resolved resolveBlock(Block block, Predicate<ItemStack> isLocked) {
        DisguiseRuleSet set = DisguiseData.get();
        if (set.isEmpty()) return null;
        Item item = block.asItem();
        if (item == Items.AIR) return null;
        DisguiseRule first = ruleForItem(set, item);
        if (first == null) return null;
        return follow(set, idOf(item), first, new ItemStack(item), isLocked);
    }

    @Nullable
    private static Resolved follow(DisguiseRuleSet set, String startId, DisguiseRule first,
                                   ItemStack start, Predicate<ItemStack> isLocked) {
        DisguiseChain.Result result = DisguiseChain.follow(startId,
                id -> {
                    if (id.equals(startId)) return first;
                    Item item = itemById(id);
                    return item == null ? null : ruleForItem(set, item);
                },
                id -> {
                    if (id.equals(startId)) return isLocked.test(start);
                    Item item = itemById(id);
                    return item != null && isLocked.test(new ItemStack(item));
                });
        if (result == null) return null;
        Item target = itemById(result.finalId());
        return target == null ? null : new Resolved(result.first(), target);
    }

    /** Server and client alike: the lock check that fits the side the player lives on. */
    @Nullable
    public static Resolved forPlayer(ItemStack stack, Player player) {
        return resolve(stack, lockCheckFor(player));
    }

    @Nullable
    public static Resolved forPlayer(Block block, Player player) {
        return resolveBlock(block, lockCheckFor(player));
    }

    /**
     * The disguise that takes over breaking, for {@code drops: disguise} rules whose chain ends on
     * a block. Null in every other case, which leaves breaking to the normal lock rules.
     */
    @Nullable
    public static Resolved breakingDisguise(Player player, BlockState state) {
        if (DisguiseData.get().isEmpty()) return null;
        Resolved r = forPlayer(state.getBlock(), player);
        if (r == null || r.drops() != DropsMode.DISGUISE) return null;
        Block target = r.targetBlock();
        return target == null || target == state.getBlock() ? null : r;
    }

    private static Predicate<ItemStack> lockCheckFor(Player player) {
        if (player.level().isClientSide()) return StageLockHelper::isItemLockedForClient;
        return stack -> StageLockHelper.isItemLockedForPlayer(stack, player.getUUID());
    }

    // --- helpers ---

    /**
     * The disguise block's state for {@code from}: properties with the same name and value type
     * are carried over (a furnace stays facing the same way), everything else is the default.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static BlockState mapState(BlockState from, Block to) {
        BlockState out = to.defaultBlockState();
        for (Property<?> property : from.getProperties()) {
            Property target = to.getStateDefinition().getProperty(property.getName());
            if (target == null || target.getValueClass() != property.getValueClass()) continue;
            Comparable value = from.getValue(property);
            if (target.getPossibleValues().contains(value)) out = out.setValue(target, value);
        }
        return out;
    }

    /** Rules naming an item that does not exist; such a rule simply never applies. */
    public static List<String> unknownIds() {
        List<String> out = new ArrayList<>();
        for (DisguiseRule rule : DisguiseData.get().all()) {
            if (!rule.isTag() && itemById(rule.key()) == null) {
                out.add("'" + rule.key() + "' is not a known item");
            }
            if (itemById(rule.as()) == null) {
                out.add("'" + rule.key() + "': disguise '" + rule.as() + "' is not a known item");
            }
        }
        return out;
    }

    public static String idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    @Nullable
    public static Item itemById(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        return item == Items.AIR ? null : item;
    }
}
