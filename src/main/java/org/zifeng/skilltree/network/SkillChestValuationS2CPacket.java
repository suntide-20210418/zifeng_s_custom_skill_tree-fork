package org.zifeng.skilltree.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;

/** 服务端 → 客户端：某个宝箱的估值结果（寻宝大师扩展）。 */
public record SkillChestValuationS2CPacket(BlockPos pos, StructureValuationData data)
        implements CustomPacketPayload {
    public static final Type<SkillChestValuationS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "skill_chest_valuation_result"));

    public static final StreamCodec<FriendlyByteBuf, SkillChestValuationS2CPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeBlockPos(packet.pos());
                        packet.data().write(buf);
                    },
                    buf -> new SkillChestValuationS2CPacket(buf.readBlockPos(), StructureValuationData.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkillChestValuationS2CPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isClientbound() && FMLEnvironment.dist.isClient()) {
                org.zifeng.skilltree.client.SkillLinkClientState.onChestValuation(packet.pos(), packet.data());
            }
        });
    }
}
