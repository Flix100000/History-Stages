package net.bananemdnsa.historystages.util.lock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.bananemdnsa.historystages.events.RecipeHandler;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

/**
 * The stonecutter recipes a player may use. 26.2 hands the client a list of stonecutter recipes
 * and the server reads clicks back as indices into its own copy, so both sides filter with the
 * same rule: the client's copy when it is sent, the server's when the menu builds its buttons.
 */
public final class StonecutterGate {

    private StonecutterGate() {}

    public static SelectableRecipe.SingleInputSet<StonecutterRecipe> forPlayer(
            ServerPlayer player, SelectableRecipe.SingleInputSet<StonecutterRecipe> set) {
        List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> kept = new ArrayList<>(set.size());
        boolean removed = RecipeCraftContext.with(player.getUUID(), () -> {
            boolean any = false;
            for (SelectableRecipe.SingleInputEntry<StonecutterRecipe> entry : set.entries()) {
                Optional<RecipeHolder<StonecutterRecipe>> recipe = entry.recipe().recipe();
                if (recipe.isPresent() && RecipeHandler.isLockedForResolution(recipe.get(), false)) {
                    any = true;
                } else {
                    kept.add(entry);
                }
            }
            return any;
        });
        return removed ? new SelectableRecipe.SingleInputSet<>(kept) : set;
    }

    /** The recipe packet as this player gets it. */
    public static ClientboundUpdateRecipesPacket forPlayer(ServerPlayer player, ClientboundUpdateRecipesPacket packet) {
        SelectableRecipe.SingleInputSet<StonecutterRecipe> filtered = forPlayer(player, packet.stonecutterRecipes());
        return filtered == packet.stonecutterRecipes() ? packet
                : new ClientboundUpdateRecipesPacket(packet.itemSets(), filtered);
    }
}
