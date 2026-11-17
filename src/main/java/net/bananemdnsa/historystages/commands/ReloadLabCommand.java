package net.bananemdnsa.historystages.commands;

import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;

import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.reload.BackgroundReload;
import net.bananemdnsa.historystages.reload.LightReload;
import net.bananemdnsa.historystages.reload.ReloadBench;
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
                .then(Commands.literal("background").executes(ctx -> background(ctx.getSource())))
                .then(Commands.literal("targeted").executes(ctx -> targeted(ctx.getSource())))
                .then(Commands.literal("resend").executes(ctx -> resend(ctx.getSource())))
                .then(Commands.literal("listeners").executes(ctx -> listeners(ctx.getSource())))
                .then(Commands.literal("bench")
                        .executes(ctx -> bench(ctx.getSource(), List.of(LightReload.Mode.FULL,
                                LightReload.Mode.BACKGROUND, LightReload.Mode.TARGETED)))
                        .then(Commands.literal("full").executes(ctx -> bench(ctx.getSource(), List.of(LightReload.Mode.FULL))))
                        .then(Commands.literal("background").executes(ctx -> bench(ctx.getSource(), List.of(LightReload.Mode.BACKGROUND))))
                        .then(Commands.literal("targeted").executes(ctx -> bench(ctx.getSource(), List.of(LightReload.Mode.TARGETED))))
                        .then(Commands.literal("stop").executes(ctx -> {
                            ReloadBench.stop();
                            return 1;
                        })))
                .then(Commands.literal("jei")
                        .then(Commands.literal("restart").executes(ctx -> jei(ctx.getSource(), true)))
                        .then(Commands.literal("diff").executes(ctx -> jei(ctx.getSource(), false))))
                .then(Commands.literal("mode")
                        .then(Commands.literal("full").executes(ctx -> mode(ctx.getSource(), LightReload.Mode.FULL)))
                        .then(Commands.literal("background").executes(ctx -> mode(ctx.getSource(), LightReload.Mode.BACKGROUND)))
                        .then(Commands.literal("targeted").executes(ctx -> mode(ctx.getSource(), LightReload.Mode.TARGETED)))));
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

    private static int background(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        LightReload.requestProfile();
        long start = System.nanoTime();
        BackgroundReload.request(server);
        // The command returns at once; the report follows when the reload is through.
        BackgroundReload.whenIdle(server, () -> {
            long millis = (System.nanoTime() - start) / 1_000_000;
            source.sendSuccess(() -> Component.translatable("command.historystages.reloadlab.background", millis), true);
        });
        return 1;
    }

    private static int targeted(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        long start = System.nanoTime();
        LightReload.start(server, true).whenComplete((result, error) -> {
            long millis = (System.nanoTime() - start) / 1_000_000;
            if (error != null) {
                source.sendFailure(Component.translatable("command.historystages.reloadlab.targeted.failed",
                        String.valueOf(error.getMessage())));
                return;
            }
            source.sendSuccess(() -> Component.translatable("command.historystages.reloadlab.targeted",
                    millis, result.replayed(), result.skipped()), true);
        });
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

    private static int bench(CommandSourceStack source, List<LightReload.Mode> modes) {
        if (ReloadBench.isRunning()) {
            source.sendFailure(Component.translatable("command.historystages.reloadlab.bench.busy"));
            return 0;
        }
        int stages = ReloadBench.start(source, modes);
        if (stages == 0) {
            source.sendFailure(Component.translatable("command.historystages.reloadlab.bench.nostages"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.historystages.reloadlab.bench.started",
                stages, modes.size()), true);
        return 1;
    }

    private static int jei(CommandSourceStack source, boolean restart) {
        LightReload.setResendRecipes(restart);
        source.sendSuccess(() -> Component.translatable(restart
                ? "command.historystages.reloadlab.jei.restart"
                : "command.historystages.reloadlab.jei.diff"), true);
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
