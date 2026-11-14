package net.bananemdnsa.historystages.platform.event.level;

import java.util.List;

import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A block's loot and experience, before they enter the world (NeoForge's {@code BlockDropsEvent}).
 * Server only. The list is what spawns; cancelling drops nothing and gives no experience.
 */
public class BlockDropsEvent extends BlockEvent implements ICancellableEvent {

    private final @Nullable BlockEntity blockEntity;
    private final List<ItemEntity> drops;
    private final @Nullable Entity breaker;
    private final ItemStack tool;
    private int experience;

    public BlockDropsEvent(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
                           List<ItemEntity> drops, @Nullable Entity breaker, ItemStack tool, int experience) {
        super(level, pos, state);
        this.blockEntity = blockEntity;
        this.drops = drops;
        this.breaker = breaker;
        this.tool = tool;
        this.experience = experience;
    }

    @Override
    public ServerLevel getLevel() {
        return (ServerLevel) super.getLevel();
    }

    public @Nullable BlockEntity getBlockEntity() {
        return blockEntity;
    }

    public List<ItemEntity> getDrops() {
        return drops;
    }

    public @Nullable Entity getBreaker() {
        return breaker;
    }

    public ItemStack getTool() {
        return tool;
    }

    public int getDroppedExperience() {
        return experience;
    }

    public void setDroppedExperience(int experience) {
        this.experience = experience;
    }
}
