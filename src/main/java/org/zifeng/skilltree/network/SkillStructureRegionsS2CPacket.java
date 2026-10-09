package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：半径内「已生成结构」的区域列表 + 各自估值（结构剖析）。
 *
 * <p>区域包围盒用 6 个 int 传输（而非序列化 {@code BoundingBox}/{@code AABB}），保持包的载荷是纯 JDK 类型，
 * 客户端再自行构造渲染与射线求交要用的盒。
 */
public record SkillStructureRegionsS2CPacket(List<RegionEntry> regions) implements CustomPacketPayload {
    public static final Type<SkillStructureRegionsS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "skill_structure_regions"));

    public static final StreamCodec<FriendlyByteBuf, SkillStructureRegionsS2CPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeVarInt(packet.regions().size());
                        for (RegionEntry entry : packet.regions()) {
                            buf.writeUtf(entry.structureId().toString());
                            buf.writeVarInt(entry.minX());
                            buf.writeVarInt(entry.minY());
                            buf.writeVarInt(entry.minZ());
                            buf.writeVarInt(entry.maxX());
                            buf.writeVarInt(entry.maxY());
                            buf.writeVarInt(entry.maxZ());
                            entry.data().write(buf);
                        }
                    },
                    buf -> {
                        int size = buf.readVarInt();
                        List<RegionEntry> regions = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            ResourceLocation id = ResourceLocation.parse(buf.readUtf());
                            int minX = buf.readVarInt();
                            int minY = buf.readVarInt();
                            int minZ = buf.readVarInt();
                            int maxX = buf.readVarInt();
                            int maxY = buf.readVarInt();
                            int maxZ = buf.readVarInt();
                            regions.add(new RegionEntry(
                                    id, minX, minY, minZ, maxX, maxY, maxZ, StructureValuationData.read(buf)));
                        }
                        return new SkillStructureRegionsS2CPacket(regions);
                    });

    public SkillStructureRegionsS2CPacket {
        regions = List.copyOf(regions);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkillStructureRegionsS2CPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isClientbound() && FMLEnvironment.dist.isClient()) {
                org.zifeng.skilltree.client.SkillLinkClientState.onStructureRegions(packet.regions());
            }
        });
    }

    /** 一个结构区域：id + 包围盒（含界）+ 估值。 */
    public record RegionEntry(
            ResourceLocation structureId,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            StructureValuationData data) {}
}
