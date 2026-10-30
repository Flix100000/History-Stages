package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.bananemdnsa.historystages.block.entity.ScrollShelfBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.bananemdnsa.historystages.platform.DeferredRegister;
import net.bananemdnsa.historystages.platform.DeferredHolder;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HistoryStages.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResearchPedestalBlockEntity>> RESEARCH_PEDESTAL_BE =
            BLOCK_ENTITIES.register("research_pedestal_be", () ->
                    BlockEntityType.Builder.of(ResearchPedestalBlockEntity::new,
                            ModBlocks.RESEARCH_PEDESTAL.get(),
                            ModBlocks.RESEARCH_PEDESTAL_TIER_2.get(),
                            ModBlocks.RESEARCH_PEDESTAL_TIER_3.get(),
                            ModBlocks.RESEARCH_PEDESTAL_TIER_4.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScrollShelfBlockEntity>> SCROLL_SHELF_BE =
            BLOCK_ENTITIES.register("scroll_shelf_be", () ->
                    BlockEntityType.Builder.of(ScrollShelfBlockEntity::new,
                            ModBlocks.SCROLL_SHELVES.values().stream().map(DeferredHolder::get).toArray(Block[]::new)).build(null));

    public static void register() {
        BLOCK_ENTITIES.register();
    }
}
