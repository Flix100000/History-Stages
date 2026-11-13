package net.bananemdnsa.historystages.fabric.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.jspecify.annotations.Nullable;

/**
 * Puts the client-only commands into the command tree the chat line reads (4df7376a on
 * fabric-1.21.X).
 *
 * <p>Fabric keeps client commands in a dispatcher of their own and copies them into the real tree
 * once the server has sent its own. When a client command shares its name with a server command,
 * and {@code /history} is both, the copy can leave the client's children out of the tree: the
 * command runs when typed in full but the chat line draws it red and offers no completions.
 *
 * <p>Only what is missing is added, so running this twice changes nothing and a client command
 * whose name is free is left to Fabric. The grafted nodes only parse and complete; typing the
 * command reaches Fabric's client dispatcher first, where the real one lives.
 */
public final class ClientCommandTree {

    private static volatile @Nullable RootCommandNode<FabricClientCommandSource> clientRoot;

    private ClientCommandTree() {}

    static void remember(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        clientRoot = dispatcher.getRoot();
    }

    /** Adds the client commands that the copy left out to {@code merged}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void graftOnto(CommandDispatcher<?> merged) {
        RootCommandNode<FabricClientCommandSource> root = clientRoot;
        if (root == null) {
            return;
        }
        for (CommandNode<FabricClientCommandSource> clientChild : root.getChildren()) {
            CommandNode target = merged.getRoot().getChild(clientChild.getName());
            if (target == null) {
                continue;
            }
            for (CommandNode<FabricClientCommandSource> grandchild : clientChild.getChildren()) {
                if (target.getChild(grandchild.getName()) != null) {
                    continue;
                }
                try {
                    target.addChild(copyForDisplay(grandchild));
                } catch (RuntimeException e) {
                    // A command that cannot be drawn still runs; losing its completions is worth
                    // less than losing the chat line it was typed into.
                }
            }
        }
    }

    /**
     * A copy that is parsed and completed, never run. No permission check and no action: both
     * would get the wrong kind of source, since the tree belongs to the server's.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CommandNode<?> copyForDisplay(CommandNode<?> source) {
        ArgumentBuilder builder = source.createBuilder();
        builder.requires(any -> true);
        if (source.getCommand() != null) {
            builder.executes(context -> 0);
        }
        if (builder instanceof RequiredArgumentBuilder<?, ?> argument) {
            // A provider from the client dispatcher would be asked with the wrong source.
            ((RequiredArgumentBuilder) argument).suggests(null);
        }
        for (CommandNode<?> child : source.getChildren()) {
            builder.then(copyForDisplay(child));
        }
        return builder.build();
    }
}
