package net.bananemdnsa.historystages.mixin;

import com.google.common.collect.Multimap;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The ungated by-type index, for walking past a locked recipe to the next one that matches. */
@Mixin(RecipeMap.class)
public interface RecipeMapAccessor {

    @Accessor("byType")
    Multimap<RecipeType<?>, RecipeHolder<?>> historystages$byType();
}
