package net.bananemdnsa.historystages.fabric.mixin.slotlock;

import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.fabricmc.fabric.impl.transfer.item.ContainerStorageImpl")
public interface ContainerStorageImplAccessor {

    @Accessor("container")
    Container historystages$container();
}
