package net.bananemdnsa.historystages.fabric.mixin.event;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.bananemdnsa.historystages.fabric.events.BreakingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** The server's break asks for tool fitness before the drops; the harvest event reads the block from here. */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerDestroyBlockScopeMixin {

    @Shadow
    protected ServerLevel level;

    @WrapMethod(method = "destroyBlock")
    private boolean historystages$scope(BlockPos pos, Operation<Boolean> original) {
        return BreakingBlock.around(level, pos, () -> original.call(pos));
    }
}
