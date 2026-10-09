package org.zifeng.skilltree.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.server.SkillLinkServer;

/**
 * 客户端 → 服务端：请求准星指向的宝箱的估值（寻宝大师扩展）。
 *
 * <p>只在"当前宝箱发生变化"时发送（见 {@code TreasureInfoOverlay}），不是每帧。
 */
public record SkillChestValuationC2SPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<SkillChestValuationC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "skill_chest_valuation"));

    public static final StreamCodec<FriendlyByteBuf, SkillChestValuationC2SPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeBlockPos(packet.pos()),
                    buf -> new SkillChestValuationC2SPacket(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkillChestValuationC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                SkillLinkServer.onChestValuationRequest(player, packet.pos());
            }
        });
    }
}
