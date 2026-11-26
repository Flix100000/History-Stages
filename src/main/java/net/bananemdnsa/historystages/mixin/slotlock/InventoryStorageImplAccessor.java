package net.bananemdnsa.historystages.mixin.slotlock;

import net.fabricmc.fabric.impl.transfer.item.InventoryStorageImpl;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The container behind Fabric's transfer-API wrapper, which the API itself does not hand out. */
@Mixin(value = InventoryStorageImpl.class, remap = false)
public interface InventoryStorageImplAccessor {

    @Accessor("inventory")
    Container historystages$inventory();
}
