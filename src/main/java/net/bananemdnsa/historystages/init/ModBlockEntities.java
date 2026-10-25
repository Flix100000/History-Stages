package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.bananemdnsa.historystages.block.entity.ScrollShelfBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HistoryStages.MOD_ID);

    public static final RegistryObject<BlockEntityType<ResearchPedestalBlockEntity>> RESEARCH_PEDESTAL_BE =
            BLOCK_ENTITIES.register("research_pedestal_be", () ->
                    BlockEntityType.Builder.of(ResearchPedestalBlockEntity::new,
                            ModBlocks.RESEARCH_PEDESTAL.get(),
                            ModBlocks.RESEARCH_PEDESTAL_TIER_2.get(),
                            ModBlocks.RESEARCH_PEDESTAL_TIER_3.get(),
                            ModBlocks.RESEARCH_PEDESTAL_TIER_4.get()).build(null));

    public static final RegistryObject<BlockEntityType<ScrollShelfBlockEntity>> SCROLL_SHELF_BE =
            BLOCK_ENTITIES.register("scroll_shelf_be", () ->
                    BlockEntityType.Builder.of(ScrollShelfBlockEntity::new,
                            ModBlocks.SCROLL_SHELVES.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}