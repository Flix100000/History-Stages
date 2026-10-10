package net.bananemdnsa.historystages.data.lock.engine;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a stack carries that a lock can name: its enchantments and its potion contents.
 *
 * <p>Plain strings and ints on purpose. The unit tests run without Minecraft, and this travels on
 * {@link LockSubjects.ItemSubject}, which they build; reading the components is
 * {@code StackContentsReader}'s job.
 *
 * <p>Enchantments are kept in two lists because the mod lock treats them differently: a book is
 * "from" the mod whose enchantment it stores, a sword is not "from" the mod whose enchantment it
 * happens to carry.
 *
 * @param enchantments       what the stack is enchanted with (gear)
 * @param storedEnchantments what an enchanted book stores
 * @param effects            potion effects, level = amplifier + 1
 * @param potionIds          the base potion's id, if any
 */
public record StackContents(List<Levelled> enchantments, List<Levelled> storedEnchantments,
                            List<Levelled> effects, List<String> potionIds) {

    public static final StackContents EMPTY = new StackContents(List.of(), List.of(), List.of(), List.of());

    /** One enchantment or effect at one level. */
    public record Levelled(String id, int level) {}

    public boolean isEmpty() {
        return enchantments.isEmpty() && storedEnchantments.isEmpty()
                && effects.isEmpty() && potionIds.isEmpty();
    }

    /** Both enchantment lists together — what an enchantment entry checks the item against. */
    public List<Levelled> allEnchantments() {
        if (storedEnchantments.isEmpty()) return enchantments;
        if (enchantments.isEmpty()) return storedEnchantments;
        List<Levelled> all = new java.util.ArrayList<>(enchantments);
        all.addAll(storedEnchantments);
        return all;
    }

    /** The namespaces a mod lock should treat this stack as coming from, besides its own id's. */
    public Set<String> namespacesForModLock() {
        Set<String> namespaces = new LinkedHashSet<>();
        for (Levelled stored : storedEnchantments) namespaces.add(namespaceOf(stored.id()));
        for (Levelled effect : effects) namespaces.add(namespaceOf(effect.id()));
        for (String potion : potionIds) namespaces.add(namespaceOf(potion));
        return namespaces;
    }

    private static String namespaceOf(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }
}
