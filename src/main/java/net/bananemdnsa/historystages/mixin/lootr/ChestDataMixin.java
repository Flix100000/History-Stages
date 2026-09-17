package net.bananemdnsa.historystages.mixin.lootr;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.bananemdnsa.historystages.compat.lootr.StageLootFilter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import noobanidus.mods.lootr.api.LootFiller;
import noobanidus.mods.lootr.data.SpecialChestInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Where Lootr rolls one player's copy of a container's loot.
 *
 * <p>Lootr on 1.20.1 has no filter to register with — that arrived with the 1.21 rewrite — so the
 * gate is hung on the three methods that build a fresh copy and fill it. All three do the same
 * three things in a row: make an empty inventory, hand it to a loot filler, and file it under the
 * player. At {@code RETURN} the copy exists, holds nothing but that roll, and belongs to exactly
 * one player.
 *
 * <p>That is what makes this the right place rather than the container window. An inventory being
 * opened may hold what the player put there themselves, and Lootr keeps those in the same list as
 * the loot — stripping on open deleted them (#124). It also reaches the containers that never open
 * a window at all: a decorated pot drops its contents straight on the ground, a brushed suspicious
 * block likewise (#120).
 *
 * <p>{@code @Pseudo} and {@code require = 0} because these are Lootr's own internals rather than
 * its API, and an internal class is allowed to move. It should not, however, move quietly:
 * {@code LootrLootFilterTests} fails loudly when none of the three applied.
 */
@Pseudo
@Mixin(targets = "noobanidus.mods.lootr.data.ChestData", remap = false)
public class ChestDataMixin {

    @Inject(method = "createInventory("
                    + "Lnet/minecraft/server/level/ServerPlayer;"
                    + "Lnoobanidus/mods/lootr/api/LootFiller;"
                    + "Ljava/util/function/IntSupplier;"
                    + "Ljava/util/function/Supplier;"
                    + "Ljava/util/function/Supplier;"
                    + "Ljava/util/function/LongSupplier;"
                    + ")Lnoobanidus/mods/lootr/data/SpecialChestInventory;",
            at = @At("RETURN"), require = 0)
    private void historystages$stripSizedRoll(ServerPlayer player, LootFiller filler,
                                              IntSupplier size, Supplier<Component> name,
                                              Supplier<ResourceLocation> table, LongSupplier seed,
                                              CallbackInfoReturnable<SpecialChestInventory> cir) {
        historystages$strip(cir.getReturnValue(), player);
    }

    @Inject(method = "createInventory("
                    + "Lnet/minecraft/server/level/ServerPlayer;"
                    + "Lnoobanidus/mods/lootr/api/LootFiller;"
                    + "Lnet/minecraft/world/level/block/entity/BaseContainerBlockEntity;"
                    + "Ljava/util/function/Supplier;"
                    + "Ljava/util/function/LongSupplier;"
                    + ")Lnoobanidus/mods/lootr/data/SpecialChestInventory;",
            at = @At("RETURN"), require = 0)
    private void historystages$stripContainerRoll(ServerPlayer player, LootFiller filler,
                                                  BaseContainerBlockEntity blockEntity,
                                                  Supplier<ResourceLocation> table,
                                                  LongSupplier seed,
                                                  CallbackInfoReturnable<SpecialChestInventory> cir) {
        historystages$strip(cir.getReturnValue(), player);
    }

    @Inject(method = "createInventory("
                    + "Lnet/minecraft/server/level/ServerPlayer;"
                    + "Lnoobanidus/mods/lootr/api/LootFiller;"
                    + "Lnet/minecraft/world/level/block/entity/RandomizableContainerBlockEntity;"
                    + ")Lnoobanidus/mods/lootr/data/SpecialChestInventory;",
            at = @At("RETURN"), require = 0)
    private void historystages$stripChestRoll(ServerPlayer player, LootFiller filler,
                                              RandomizableContainerBlockEntity blockEntity,
                                              CallbackInfoReturnable<SpecialChestInventory> cir) {
        historystages$strip(cir.getReturnValue(), player);
    }

    /** Null when Lootr decided against building a copy at all, which is not ours to second-guess. */
    private static void historystages$strip(SpecialChestInventory rolled, ServerPlayer player) {
        if (rolled != null) StageLootFilter.strip(rolled, player);
    }
}
