package net.bananemdnsa.historystages.fabric.client;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * A vanilla command source for commands that never leave the client, built the way NeoForge
 * builds its own: the local player's position, rotation and permissions, no level and no server.
 * The platform's client-command event hands out vanilla sources, Fabric's API its own
 * {@code FabricClientCommandSource}, so client commands run against one of these.
 */
final class ClientCommandSourceStack extends CommandSourceStack {

    private ClientCommandSourceStack(CommandSource output, Vec3 position, Vec2 rotation, PermissionSet permissions,
                                     String name, Component displayName, LocalPlayer player) {
        super(output, position, rotation, null, permissions, name, displayName, null, player);
    }

    static CommandSourceStack create() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return new ClientCommandSourceStack(ChatOutput.INSTANCE, Vec3.ZERO, Vec2.ZERO, PermissionSet.NO_PERMISSIONS,
                    "", Component.empty(), null);
        }
        return new ClientCommandSourceStack(ChatOutput.INSTANCE, player.position(), player.getRotationVector(),
                player.permissions(), player.getName().getString(), player.getDisplayName(), player);
    }

    /** Never told to the admins: the base class would ask the server for them, and there is none. */
    @Override
    public void sendSuccess(Supplier<Component> message, boolean allowLogging) {
        super.sendSuccess(message, false);
    }

    private enum ChatOutput implements CommandSource {
        INSTANCE;

        @Override
        public void sendSystemMessage(Component message) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
