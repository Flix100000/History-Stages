package net.bananemdnsa.historystages.util.lock;

import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

/** Mixed into Slot: the menu it was added to, which a slot does not otherwise know. */
public interface SlotMenuOwner {

    @Nullable AbstractContainerMenu historystages$menu();

    void historystages$setMenu(AbstractContainerMenu menu);
}
