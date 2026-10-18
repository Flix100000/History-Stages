package net.bananemdnsa.historystages.commands;

import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;

import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.reload.LightReload;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * PROTOTYPE. {@code /hsreloadlab} — times the full datapack reload against the light one on a real
 * pack, and switches which of them a stage change uses. Nothing here is saved.
 */
public final class ReloadLabCommand {

    private ReloadLabCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hsreloadlab")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("full").executes(ctx -> full(ctx.getSource())))
                .then(Commands.literal("light").executes(ctx -> light(ctx.getSource(), false)))
                .then(Commands.literal("light_tags").executes(ctx -> light(ctx.getSource(), true)))
                .then(Commands.literal("resend").executes(ctx -> resend(ctx.getSource())))
                .then(Commands.literal("listeners").executes(ctx -> listeners(ctx.getSource())))
                .then(Commands.literal("mode")
                        .then(Commands.literal("full").executes(ctx -> mode(ctx.getSource(), LightReload.Mode.FULL)))
                        .then(Commands.literal("light").executes(ctx -> mode(ctx.getSource(), LightReload.Mode.LIGHT)))
                        .then(Commands.literal("light_tags").executes(ctx -> mode(ctx.getSource(), LightReload.Mode.LIGHT_TAGS)))));
    }

    private static int full(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        LightReload.requestProfile();
        long start = System.nanoTime();
        // Blocks the server thread until done when called from it, exactly as on a stage change.
        server.reloadResources(server.getPackRepository().getSelectedIds()).join();
        long millis = (System.nanoTime() - start) / 1_000_000;
        source.sendSuccess(() -> Component.translatable("command.historystages.reloadlab.full", millis), true);
        return 1;
    }

    private static int light(CommandSourceStack source, boolean tags) {
        MinecraftServer server = source.getServer();
        long start = System.nanoTime();
        LightReload.Result result = LightReload.run(server, tags);
        // Counted in, because the full reload sends the recipe list as part of its time too.
        PacketHandler.resyncRecipes(server);
        long millis = (System.nanoTime() - start) / 1_000_000;
        source.sendSuccess(() -> Component.translatable("command.historystages.reloadlab.light",
                millis, result.replayed(), result.skipped()), true);
        return 1;
    }

    private static int resend(CommandSourceStack source) {
        long start = System.nanoTime();
        PacketHandler.resyncRecipes(source.getServer());
        long millis = (System.nanoTime() - start) / 1_000_000;
        source.sendSuccess(() -> Component.translatable("command.historystages.reloadlab.resend", millis), true);
        return 1;
    }

    private static int mode(CommandSourceStack source, LightReload.Mode mode) {
        LightReload.setMode(mode);
        String name = mode.name().toLowerCase(Locale.ROOT);
        source.sendSuccess(() -> Component.translatable("command.historystages.reloadlab.mode", name), true);
        return 1;
    }

    private static int listeners(CommandSourceStack source) {
        List<LightReload.Entry> entries = LightReload.describe();
        if (entries.isEmpty()) {
            source.sendFailure(Component.translatable("command.historystages.reloadlab.listeners.none"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.historystages.reloadlab.listeners.header",
                entries.size()), false);
        for (LightReload.Entry entry : entries) {
            ChatFormatting colour = switch (entry.kind()) {
                case VANILLA -> ChatFormatting.GRAY;
                case REPLAYED -> ChatFormatting.GREEN;
                case SKIPPED -> ChatFormatting.RED;
            };
            Component kind = Component.translatable("command.historystages.reloadlab.kind."
                    + entry.kind().name().toLowerCase(Locale.ROOT)).withStyle(colour);
            source.sendSuccess(() -> Component.translatable("command.historystages.reloadlab.listeners.line",
                    kind, entry.name()), false);
        }
        return entries.size();
    }
}
