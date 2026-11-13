package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.block.MultiBlockTier3Block;
import net.bananemdnsa.historystages.block.MultiBlockTier4Block;
import net.bananemdnsa.historystages.block.ResearchPedestalBlock;
import net.bananemdnsa.historystages.block.ResearchPedestalTier2Block;
import net.bananemdnsa.historystages.block.ScrollShelfBlock;
import net.bananemdnsa.historystages.block.ScrollShelfWood;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, HistoryStages.MOD_ID);

    private static BlockBehaviour.Properties pedestalProps() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                .strength(5.0f)
                .requiresCorrectToolForDrops();
    }

    public static final DeferredHolder<Block, Block> RESEARCH_PEDESTAL = BLOCKS.register("research_pedestal",
            () -> new ResearchPedestalBlock(pedestalProps()));

    public static final DeferredHolder<Block, Block> RESEARCH_PEDESTAL_TIER_2 = BLOCKS.register("research_pedestal_tier_2",
            () -> new ResearchPedestalTier2Block(pedestalProps()));

    public static final DeferredHolder<Block, Block> RESEARCH_PEDESTAL_TIER_3 = BLOCKS.register("research_pedestal_tier_3",
            () -> new MultiBlockTier3Block(pedestalProps()));

    public static final DeferredHolder<Block, Block> RESEARCH_PEDESTAL_TIER_4 = BLOCKS.register("research_pedestal_tier_4",
            () -> new MultiBlockTier4Block(pedestalProps()));

    public static final Map<ScrollShelfWood, DeferredHolder<Block, ScrollShelfBlock>> SCROLL_SHELVES = registerScrollShelves();

    private static Map<ScrollShelfWood, DeferredHolder<Block, ScrollShelfBlock>> registerScrollShelves() {
        Map<ScrollShelfWood, DeferredHolder<Block, ScrollShelfBlock>> shelves = new EnumMap<>(ScrollShelfWood.class);
        for (ScrollShelfWood wood : ScrollShelfWood.values()) {
            shelves.put(wood, BLOCKS.register(wood.blockId(), () -> new ScrollShelfBlock(scrollShelfProps(wood))));
        }
        return Collections.unmodifiableMap(shelves);
    }

    // The chiseled bookshelf's properties, coloured on the map like the wood it's made of. The
    // last three would follow from a full-cube collision shape, which the shelf doesn't have.
    private static BlockBehaviour.Properties scrollShelfProps(ScrollShelfWood wood) {
        BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                .mapColor(wood.planks().defaultMapColor())
                .instrument(NoteBlockInstrument.BASS)
                .strength(1.5F)
                .sound(SoundType.CHISELED_BOOKSHELF)
                .isRedstoneConductor((state, level, pos) -> true)
                .isSuffocating((state, level, pos) -> true)
                .isViewBlocking((state, level, pos) -> true);
        return wood.flammable() ? props.ignitedByLava() : props;
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
