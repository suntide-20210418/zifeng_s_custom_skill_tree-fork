package org.zifeng.skilltree.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.server.SkillLinkServer;

/**
 * 客户端 → 服务端：手持宝箱标记器左键某个已被寻宝大师标记的宝箱 → 执行标记（寻宝大师扩展）。
 *
 * <p>服务端会重新校验技能状态、手持物与容器，不信任客户端自报的内容。
 */
public record SkillMarkChestC2SPacket(InteractionHand hand, BlockPos pos) implements CustomPacketPayload {
    public static final Type<SkillMarkChestC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "skill_mark_chest"));

    public static final StreamCodec<FriendlyByteBuf, SkillMarkChestC2SPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeVarInt(packet.hand().ordinal());
                        buf.writeBlockPos(packet.pos());
                    },
                    buf -> new SkillMarkChestC2SPacket(
                            InteractionHand.values()[buf.readVarInt()], buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkillMarkChestC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                SkillLinkServer.onMarkChestRequest(player, packet.hand(), packet.pos());
            }
        });
    }
}
