package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.compat.jei.LockedJeiRefresher;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The JEI hide diff on its own, against a stand-in for JEI that behaves like the real one where it
 * matters: an item taken out is gone from its list, and every list it hands out is new stacks.
 */
public class JeiRefresherTests {

    public JeiRefresherTests() {}

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void anUnlockShowsOnlyWhatUnlockedAndKeepsTheRestHidden(GameTestHelper helper) {
        FakeJei jei = new FakeJei(Items.DIAMOND, Items.EMERALD, Items.STICK);
        LockedJeiRefresher refresher = new LockedJeiRefresher(jei);

        Set<Item> locked = new HashSet<>(Set.of(Items.DIAMOND, Items.EMERALD));
        Predicate<ItemStack> isLocked = stack -> locked.contains(stack.getItem());

        refresher.applyInitial(true, jei.listed(), isLocked);
        helper.assertTrue(jei.items.equals(Set.of(Items.STICK)),
                "after the first pass JEI should list only the stick, but lists " + jei.items);

        locked.remove(Items.DIAMOND);
        jei.calls = 0;
        refresher.applyDiff(true, jei.listed(), isLocked);

        helper.assertTrue(jei.items.equals(Set.of(Items.STICK, Items.DIAMOND)),
                "after unlocking the diamond JEI should list stick and diamond, but lists " + jei.items
                        + " — the emerald coming back means a hidden item counted as unlocked because "
                        + "JEI no longer listed it");
        helper.assertTrue(jei.calls == 1, "the unlock took " + jei.calls + " JEI calls instead of one");

        jei.calls = 0;
        refresher.applyDiff(true, jei.listed(), isLocked);
        helper.assertTrue(jei.calls == 0, "a pass with nothing changed still called JEI " + jei.calls
                + " times, so stacks are being compared by identity");
        helper.succeed();
    }

    /** JEI's item list, reduced to the set of items in it. */
    private static final class FakeJei implements LockedJeiRefresher.JeiOps {
        final Set<Item> items = new HashSet<>();
        int calls;

        FakeJei(Item... initial) {
            items.addAll(List.of(initial));
        }

        /** Fresh stacks every time, the way JEI hands them out. */
        List<ItemStack> listed() {
            List<ItemStack> out = new ArrayList<>();
            for (Item item : items) out.add(new ItemStack(item));
            return out;
        }

        @Override
        public void removeItems(Set<ItemStack> stacks) {
            calls++;
            stacks.forEach(stack -> items.remove(stack.getItem()));
        }

        @Override
        public void addItems(Set<ItemStack> stacks) {
            calls++;
            stacks.forEach(stack -> items.add(stack.getItem()));
        }
    }
}
