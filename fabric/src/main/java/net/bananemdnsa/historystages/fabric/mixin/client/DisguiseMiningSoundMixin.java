package net.bananemdnsa.historystages.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.client.disguise.ClientDisguises;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mining a disguised block sounds like the disguise. Client-only class, so no side check. */
@Mixin(MultiPlayerGameMode.class)
public class DisguiseMiningSoundMixin {

    @WrapOperation(method = "continueDestroyBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType historystages$disguiseSound(BlockState state, Operation<SoundType> original) {
        return original.call(ClientDisguises.view(state));
    }
}
