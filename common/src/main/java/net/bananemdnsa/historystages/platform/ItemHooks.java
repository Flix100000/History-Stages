package net.bananemdnsa.historystages.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;

/** Item questions that only a loader extension answers. */
public final class ItemHooks {

    private ItemHooks() {}

    /**
     * NeoForge's {@code IItemStackExtension.doesSneakBypassUse}: whether sneaking with this stack
     * still uses the block. Vanilla, and so Fabric, knows no such item and answers false.
     */
    @ExpectPlatform
    public static boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        throw new AssertionError();
    }
}
