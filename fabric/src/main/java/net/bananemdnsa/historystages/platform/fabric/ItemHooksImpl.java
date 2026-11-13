package net.bananemdnsa.historystages.platform.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;

/** Fabric side of {@code platform.ItemHooks}: vanilla behaviour, where only an empty hand never blocks. */
public final class ItemHooksImpl {

    private ItemHooksImpl() {}

    public static boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return stack.isEmpty();
    }
}
