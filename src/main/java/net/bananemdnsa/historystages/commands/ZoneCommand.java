package net.bananemdnsa.historystages.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bananemdnsa.historystages.data.lock.ZoneSelection;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.SyncZoneSelectionPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The {@code /history zone} subtree: marking out the two corners a zone is built from.
 *
 * <p>Its own file rather than another branch in {@code StageCommand}, which is long enough
 * already. Permission is inherited — the whole {@code history} tree sits behind level 2.
 *
 * <p>This is the half of marking that works without an item, which matters on a server where the
 * marker item is switched off, and for setting a corner you cannot stand on.
 */
public final class ZoneCommand {

    private ZoneCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("zone")
                .then(Commands.literal("mark")
                        .executes(ctx -> markHere(ctx.getSource(), 0))
                        .then(Commands.argument("point", IntegerArgumentType.integer(1, 2))
                                .executes(ctx -> markHere(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "point")))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> markAt(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "point"),
                                                BlockPosArgument.getBlockPos(ctx, "pos"))))))
                .then(Commands.literal("clear")
                        .executes(ctx -> clear(ctx.getSource())))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx.getSource())));
    }

    /** {@code point} 0 means "whichever comes next", which is what the bare command uses. */
    private static int markHere(CommandSourceStack source, int point) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return mark(source, player, point, player.blockPosition());
    }

    private static int markAt(CommandSourceStack source, int point, BlockPos pos) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return mark(source, source.getPlayerOrException(), point, pos);
    }

    private static int mark(CommandSourceStack source, ServerPlayer player, int point, BlockPos pos) {
        int corner = point == 0 ? ZoneSelection.nextPoint(player.getUUID()) : point;
        String dimension = player.level().dimension().location().toString();
        ZoneSelection.setPoint(player.getUUID(), corner, pos.getX(), pos.getY(), pos.getZ(), dimension);
        push(player);

        source.sendSuccess(() -> Component.translatable("command.historystages.zone.marked",
                corner, pos.getX(), pos.getY(), pos.getZ()), false);
        return 1;
    }

    private static int clear(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ZoneSelection.clear(player.getUUID());
        push(player);
        source.sendSuccess(() -> Component.translatable("command.historystages.zone.cleared"), false);
        return 1;
    }

    /** Keeps the preview in the world and the editor's copy in step with what was just set. */
    private static void push(ServerPlayer player) {
        PacketHandler.sendZoneSelection(
                SyncZoneSelectionPacket.of(ZoneSelection.of(player.getUUID())), player);
    }

    private static int info(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ZoneSelection.Selection selection = ZoneSelection.of(source.getPlayerOrException().getUUID());
        if (selection == null) {
            source.sendFailure(Component.translatable("command.historystages.zone.none"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("command.historystages.zone.info.header",
                selection.dimension()), false);

        if (selection.hasFirst()) {
            source.sendSuccess(() -> Component.translatable("command.historystages.zone.info.point",
                    1, selection.firstX(), selection.firstY(), selection.firstZ()), false);
        }
        if (selection.hasSecond()) {
            source.sendSuccess(() -> Component.translatable("command.historystages.zone.info.point",
                    2, selection.secondX(), selection.secondY(), selection.secondZ()), false);
        }

        if (!selection.isComplete()) {
            source.sendSuccess(() -> Component.translatable("command.historystages.zone.info.incomplete"), false);
            return 1;
        }

        // Inclusive, like the zone itself: a selection from 100 to 100 is one block wide, not zero.
        source.sendSuccess(() -> Component.translatable("command.historystages.zone.info.size",
                span(selection.firstX(), selection.secondX()),
                span(selection.firstY(), selection.secondY()),
                span(selection.firstZ(), selection.secondZ())), false);
        return 1;
    }

    private static int span(int a, int b) {
        return Math.abs(a - b) + 1;
    }
}
