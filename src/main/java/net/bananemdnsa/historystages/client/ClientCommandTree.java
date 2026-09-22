package net.bananemdnsa.historystages.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Puts the client-only commands into the command tree the chat line reads.
 *
 * <p>Client commands live in a dispatcher of their own on this loader, and are copied into the
 * real tree once the server has sent its own. That copy adds a node to the tree <em>before</em>
 * filling in its children, and brigadier keeps the node already there when the names match — so
 * everything under a client command whose name the server also uses is copied into a node that is
 * no longer part of the tree.
 *
 * <p>This mod has exactly that collision: {@code /history} is a server command and the two screens
 * are client commands under the same name. The effect is a command that runs when typed in full
 * (the client dispatcher is asked first) while the chat line draws it red and offers no
 * completions, because the tree it parses against has never heard of it. The other loader has no
 * such step — client commands are registered straight into the real tree there — which is why this
 * is a fabric-side repair rather than a difference in what the mod does.
 *
 * <p>Only what is missing is filled in, so running this twice changes nothing, and a client
 * command whose name is free is left to the normal path.
 */
@Environment(EnvType.CLIENT)
public final class ClientCommandTree {

    @Nullable
    private static volatile RootCommandNode<FabricClientCommandSource> clientRoot;

    private ClientCommandTree() {}

    /** Remembers the dispatcher the client commands were registered into. */
    public static void remember(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        clientRoot = dispatcher.getRoot();
    }

    /** Adds the client commands that the copy left out to {@code merged}. */
    public static void graftOnto(CommandDispatcher<SharedSuggestionProvider> merged) {
        RootCommandNode<FabricClientCommandSource> root = clientRoot;
        if (root == null) return;

        for (CommandNode<FabricClientCommandSource> clientChild : root.getChildren()) {
            CommandNode<SharedSuggestionProvider> target = merged.getRoot().getChild(clientChild.getName());
            // No node of that name in the tree means the copy worked; nothing to repair.
            if (target == null) continue;

            for (CommandNode<FabricClientCommandSource> grandchild : clientChild.getChildren()) {
                if (target.getChild(grandchild.getName()) != null) continue;
                try {
                    target.addChild(copyForDisplay(grandchild));
                } catch (RuntimeException e) {
                    // A command that cannot be drawn is still a command that runs. Losing its
                    // completions is worth less than losing the chat line it was typed into.
                }
            }
        }
    }

    /**
     * A copy that exists to be parsed and completed, never to be run.
     *
     * <p>Typing one of these sends it to the client dispatcher first, which is where the real
     * command lives. What is copied here therefore carries no permission check and no action —
     * both would be handed the wrong kind of command source, since the tree it is going into
     * belongs to the server's.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CommandNode<SharedSuggestionProvider> copyForDisplay(CommandNode<?> source) {
        ArgumentBuilder builder = source.createBuilder();
        builder.requires(any -> true);
        if (source.getCommand() != null) {
            builder.executes(context -> 0);
        }
        if (builder instanceof RequiredArgumentBuilder<?, ?> argument) {
            // The argument type suggests for itself; a suggestion provider carried over from the
            // client dispatcher would be asked with the wrong source and throw mid-completion.
            ((RequiredArgumentBuilder) argument).suggests(null);
        }
        for (CommandNode<?> child : source.getChildren()) {
            builder.then(copyForDisplay(child));
        }
        return builder.build();
    }
}
