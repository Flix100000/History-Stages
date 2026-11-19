package net.bananemdnsa.historystages.mixin.arsnouveau;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.HsRuntimeCategories;
import net.bananemdnsa.historystages.util.lock.LootLocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps locked mob drops out of what an Ars Nouveau Drygmy collects (issue #144).
 *
 * <p>A Drygmy never kills anything. It rolls the loot table of every mob near its stone with a
 * fake player as the attacker and inserts a random pick of the result into adjacent inventories.
 * No mob dies, so the drop event our mob loot lock listens to never fires, and every staged drop
 * came out of the farm untouched.
 *
 * <p>The roll is stripped right where it comes back, before the Drygmy picks from it. There is no
 * real player behind it, so only global stages decide, the same as a mob drop nobody killed.
 *
 * <p>{@code @Pseudo} + a string target + {@code require = 0}, the same pattern as the FastSuite and
 * Better Combat hooks: absent without Ars Nouveau, and absent again rather than crashing if a
 * later version reshapes {@code generateItems}.
 */
@Pseudo
@Mixin(targets = "com.hollingsworth.arsnouveau.common.block.tile.DrygmyTile", remap = false)
public class DrygmyTileMixin {

    @ModifyExpressionValue(method = "generateItems",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems("
                            + "Lnet/minecraft/world/level/storage/loot/LootParams;"
                            + ")Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"),
            require = 0, remap = false)
    private ObjectArrayList<ItemStack> historystages$stripLockedDrops(ObjectArrayList<ItemStack> loot) {
        if (!Config.GAMEPLAY.lockMobLoot.get()) return loot;

        BlockEntity self = (BlockEntity) (Object) this;
        if (StageData.SERVER_CACHE.isEmpty() && self.getLevel() != null) {
            StageData.get(self.getLevel());
        }

        int replacedCount = LootLocks.stripLocked(loot, null);
        if (replacedCount > 0) {
            DebugLogger.runtimeThrottled(HsRuntimeCategories.MOB_LOOT_LOCK, "drygmy_" + self.getBlockPos(),
                    "Replaced " + replacedCount + " locked drop(s) rolled by a Drygmy at "
                            + self.getBlockPos().toShortString() + " [action: loot]");
        }
        return loot;
    }
}
