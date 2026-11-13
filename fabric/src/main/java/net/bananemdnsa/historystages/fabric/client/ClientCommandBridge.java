package net.bananemdnsa.historystages.fabric.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.client.RegisterClientCommandsEvent;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandSourceStack;

/**
 * Feeds client commands written against vanilla's {@link CommandSourceStack} into Fabric's client
 * dispatcher, which runs on {@link FabricClientCommandSource}.
 *
 * <p>The platform event fills a dispatcher of our own. Its tree is then copied into Fabric's, node
 * for node, so the chat line still parses and completes it; every node that can run hands the full
 * input back to our dispatcher with a {@link ClientCommandSourceStack}. Permission checks are asked
 * of that source as well. What does not carry over: custom suggestion providers (they expect the
 * vanilla source, so the argument type suggests for itself) and redirects. Main's two client
 * commands use neither.
 */
final class ClientCommandBridge {

    private ClientCommandBridge() {}

    static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            // The whole platform event at once: one registration has no other mod to order against.
            if (Phases.wanted(RegisterClientCommandsEvent.class)) {
                CommandDispatcher<CommandSourceStack> ours = new CommandDispatcher<>();
                EventBus.post(new RegisterClientCommandsEvent(ours));
                for (CommandNode<CommandSourceStack> child : ours.getRoot().getChildren()) {
                    dispatcher.getRoot().addChild(forwarding(child, ours));
                }
            }
            // Kept so the command tree can be repaired once the server's own arrives.
            ClientCommandTree.remember(dispatcher);
        });
    }

    private static CommandNode<FabricClientCommandSource> forwarding(CommandNode<CommandSourceStack> node,
                                                                    CommandDispatcher<CommandSourceStack> ours) {
        ArgumentBuilder<FabricClientCommandSource, ?> builder;
        if (node instanceof LiteralCommandNode<CommandSourceStack> literal) {
            builder = LiteralArgumentBuilder.literal(literal.getLiteral());
        } else if (node instanceof ArgumentCommandNode<CommandSourceStack, ?> argument) {
            builder = RequiredArgumentBuilder.argument(argument.getName(), argument.getType());
        } else {
            throw new IllegalStateException("unexpected command node " + node);
        }
        builder.requires(source -> node.canUse(ClientCommandSourceStack.create()));
        if (node.getCommand() != null) {
            builder.executes(context -> ours.execute(stripSlash(context.getInput()), ClientCommandSourceStack.create()));
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            builder.then(forwarding(child, ours));
        }
        return builder.build();
    }

    private static String stripSlash(String input) {
        return input.startsWith("/") ? input.substring(1) : input;
    }
}
