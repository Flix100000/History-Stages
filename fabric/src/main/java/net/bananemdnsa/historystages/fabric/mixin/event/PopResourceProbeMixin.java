package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.PopResourceProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The seam {@code PopResourceProbe} counts at; does nothing unless a GameTest is watching. */
@Mixin(Block.class)
public abstract class PopResourceProbeMixin {

    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"))
    private static void historystages$probe(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
        PopResourceProbe.hit();
    }
}
