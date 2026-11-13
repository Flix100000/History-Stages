package net.bananemdnsa.historystages.client.disguise;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.data.disguise.DisguiseData;
import net.bananemdnsa.historystages.data.disguise.Disguises;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The local player's view of the disguises.
 *
 * <p>World blocks go through a finished table, because the lookup runs for every block of every
 * chunk mesh, on the chunk-build threads. The table is built on the client thread and only ever
 * replaced as a whole, never changed in place, so a worker reads either the old or the new one.
 *
 * <p>Items are resolved per stack on the client thread: NBT rules need the stack, and the item
 * surfaces (inventory, recipe viewers) already ask per stack.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public final class ClientDisguises {

    private static volatile Map<BlockState, BlockState> table = Map.of();

    /** Rule set, global and individual stage versions the table was built from. -1 = never. */
    private static int builtRules = -1;
    private static int builtGlobal = -1;
    private static int builtIndividual = -1;

    private ClientDisguises() {}

    /** What the local player sees standing at a position holding {@code state}. Any thread. */
    public static BlockState view(BlockState state) {
        Map<BlockState, BlockState> t = table;
        if (t.isEmpty()) return state;
        BlockState disguised = t.get(state);
        return disguised != null ? disguised : state;
    }

    public static boolean isDisguised(BlockState state) {
        return view(state) != state;
    }

    /** The disguise of a stack for the local player. Client thread only. */
    @Nullable
    public static Disguises.Resolved forStack(ItemStack stack) {
        if (DisguiseData.get().isEmpty()) return null;
        if (inEditor()) return null;
        return Disguises.resolve(stack, StageLockHelper::isItemLockedForClient);
    }

    private static final String EDITOR_PACKAGE = "net.bananemdnsa.historystages.client.editor.";

    /**
     * The editor shows what things really are. An admin who has not unlocked the stage would
     * otherwise see "stone" in the very list where they locked emerald ore.
     */
    private static boolean inEditor() {
        net.minecraft.client.gui.screens.Screen screen = Minecraft.getInstance().screen;
        return screen != null && screen.getClass().getName().startsWith(EDITOR_PACKAGE);
    }

    /** True when a disguise asks for this stack's lock icon, border and stage lines to go. */
    public static boolean hidesHints(ItemStack stack) {
        Disguises.Resolved r = forStack(stack);
        return r != null && !r.hints();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        int rules = DisguiseData.version();
        int global = ClientStageCache.version();
        int individual = ClientIndividualStageCache.version();
        if (rules == builtRules && global == builtGlobal && individual == builtIndividual) return;
        builtRules = rules;
        builtGlobal = global;
        builtIndividual = individual;
        rebuild();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        table = Map.of();
        builtRules = builtGlobal = builtIndividual = -1;
    }

    private static void rebuild() {
        Map<BlockState, BlockState> next = build();
        if (next.equals(table)) return;
        table = next;
        Minecraft mc = Minecraft.getInstance();
        // Chunk meshes hold the old look; without this the ore stays stone (or stays ore) until
        // the chunk happens to be rebuilt for some other reason.
        if (mc.level != null) mc.levelRenderer.allChanged();
    }

    private static Map<BlockState, BlockState> build() {
        if (DisguiseData.get().isEmpty()) return Map.of();
        Map<BlockState, BlockState> out = new IdentityHashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Disguises.Resolved r = Disguises.resolveBlock(block, StageLockHelper::isItemLockedForClient);
            if (r == null) continue;
            Block target = r.targetBlock();
            if (target == null || target == block) continue;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                out.put(state, Disguises.mapState(state, target));
            }
        }
        return out.isEmpty() ? Map.of() : Collections.unmodifiableMap(out);
    }
}
