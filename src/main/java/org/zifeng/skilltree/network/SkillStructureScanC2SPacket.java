package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.server.SkillLinkServer;

/** 客户端 → 服务端：请求一次「半径内已生成结构」扫描（结构剖析）。 */
public record SkillStructureScanC2SPacket() implements CustomPacketPayload {
    public static final Type<SkillStructureScanC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "skill_structure_scan"));

    public static final StreamCodec<FriendlyByteBuf, SkillStructureScanC2SPacket> STREAM_CODEC =
            StreamCodec.unit(new SkillStructureScanC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkillStructureScanC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                SkillLinkServer.onStructureScanRequest(player);
            }
        });
    }
}
