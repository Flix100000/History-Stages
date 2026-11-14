package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.network.RecipeListSync;
import net.bananemdnsa.historystages.platform.IPayloadContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** The editor opened on a client without its own server and wants the recipe list. */
public record RequestRecipeListPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestRecipeListPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(HistoryStages.MOD_ID, "request_recipe_list"));

    public static final StreamCodec<FriendlyByteBuf, RequestRecipeListPacket> STREAM_CODEC =
            StreamCodec.of((buffer, msg) -> {}, buffer -> new RequestRecipeListPacket());

    public static void handle(RequestRecipeListPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // Same check as the editor command; anyone else gets nothing.
            if (ctx.player() instanceof ServerPlayer player) RecipeListSync.sendTo(player);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
