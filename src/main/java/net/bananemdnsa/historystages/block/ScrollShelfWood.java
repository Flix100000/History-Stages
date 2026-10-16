package net.bananemdnsa.historystages.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Supplier;

/** The wood types a scroll shelf comes in. The id is the prefix of the block id, as in vanilla. */
public enum ScrollShelfWood {
    OAK("oak", () -> Blocks.OAK_PLANKS, true),
    SPRUCE("spruce", () -> Blocks.SPRUCE_PLANKS, true),
    BIRCH("birch", () -> Blocks.BIRCH_PLANKS, true),
    JUNGLE("jungle", () -> Blocks.JUNGLE_PLANKS, true),
    ACACIA("acacia", () -> Blocks.ACACIA_PLANKS, true),
    DARK_OAK("dark_oak", () -> Blocks.DARK_OAK_PLANKS, true),
    MANGROVE("mangrove", () -> Blocks.MANGROVE_PLANKS, true),
    CHERRY("cherry", () -> Blocks.CHERRY_PLANKS, true),
    BAMBOO("bamboo", () -> Blocks.BAMBOO_PLANKS, true),
    CRIMSON("crimson", () -> Blocks.CRIMSON_PLANKS, false),
    WARPED("warped", () -> Blocks.WARPED_PLANKS, false);

    private final String id;
    private final Supplier<Block> planks;
    private final boolean flammable;

    ScrollShelfWood(String id, Supplier<Block> planks, boolean flammable) {
        this.id = id;
        this.planks = planks;
        this.flammable = flammable;
    }

    public String id() {
        return id;
    }

    public String blockId() {
        return id + "_scroll_shelf";
    }

    public Block planks() {
        return planks.get();
    }

    /** Nether wood neither burns nor fuels a furnace, same as its planks. */
    public boolean flammable() {
        return flammable;
    }
}
