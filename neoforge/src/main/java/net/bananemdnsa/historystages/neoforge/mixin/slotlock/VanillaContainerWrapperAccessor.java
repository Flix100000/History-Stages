package net.bananemdnsa.historystages.neoforge.mixin.slotlock;

import net.minecraft.world.Container;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VanillaContainerWrapper.class)
public interface VanillaContainerWrapperAccessor {

    @Accessor("container")
    Container historystages$container();
}
