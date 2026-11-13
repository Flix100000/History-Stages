package net.bananemdnsa.historystages.platform.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;

/** NeoForge side of {@code platform.ItemHooks}. */
public final class ItemHooksImpl {

    private ItemHooksImpl() {}

    public static boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return stack.doesSneakBypassUse(level, pos, player);
    }
}
