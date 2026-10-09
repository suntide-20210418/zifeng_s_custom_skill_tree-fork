package org.zifeng.skilltree.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.zifeng.skilltree.compat.dimensiontech.DimensionTechBridge;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.network.ModNetwork;
import org.zifeng.skilltree.network.SkillChestValuationS2CPacket;
import org.zifeng.skilltree.network.SkillStructureRegionsS2CPacket;
import org.zifeng.skilltree.network.SkillStructureRegionsS2CPacket.RegionEntry;
import org.zifeng.skilltree.network.StructureValuationData;
import org.zifeng.skilltree.skill.Skills;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 联动功能的服务端逻辑（寻宝大师扩展 + 结构剖析）。
 *
 * <p>原则：
 * <ul>
 *   <li><b>不信任客户端</b>：每个请求都重新校验玩家是否点亮了对应技能；标记宝箱还要复验手持物与容器。</li>
 *   <li><b>结构枚举只用原版 API</b>（{@link LevelChunk#getAllStarts()}）：DimensionTech 未安装时结构框与结构名照常，
 *       只有数值区退化为不可用。</li>
 *   <li><b>有界</b>：只遍历已加载区块（{@code getChunkNow} 返回 null 直接跳过，不触发生成）；估值结果按
 *       (维度,结构,包围盒) 缓存，重复扫描是纯查表。</li>
 * </ul>
 */
public final class SkillLinkServer {

    /** 结构剖析的搜索半径（区块）。实际结果以服务端已加载区块为界。 */
    public static final int SCAN_RADIUS_CHUNKS = 16;

    /** 宝箱估值缓存：key = 维度|pos.asLong()。 */
    private static final Map<String, StructureValuationData> CHEST_CACHE = new ConcurrentHashMap<>();

    /** 缓存规模上限：超过即整体清空（避免长会话无限增长；重算是纯本地计算，代价可接受）。 */
    private static final int CACHE_CAP = 8192;

    /** 结构估值缓存：key = 维度|结构id|包围盒。 */
    private static final Map<String, StructureValuationData> STRUCTURE_CACHE = new ConcurrentHashMap<>();

    /** 正在异步计算中的结构 key（防止重复提交）。 */
    private static final Set<String> STRUCTURE_PENDING = ConcurrentHashMap.newKeySet();

    private SkillLinkServer() {}

    // ============ 寻宝大师扩展：宝箱估值 ============

    public static void onChestValuationRequest(ServerPlayer player, BlockPos pos) {
        if (!hasSkill(player, Skills.TREASURE_HUNTER)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos immutable = pos.immutable();
        String key = chestKey(level, immutable);
        StructureValuationData data = CHEST_CACHE.get(key);
        if (data == null) {
            data = DimensionTechBridge.valuateChest(level, immutable);
            if (CHEST_CACHE.size() >= CACHE_CAP) {
                CHEST_CACHE.clear();
            }
            CHEST_CACHE.put(key, data);
        }
        ModNetwork.sendToPlayer(player, new SkillChestValuationS2CPacket(immutable, data));
    }

    // ============ 寻宝大师扩展：手持宝箱标记器左键标记 ============

    public static void onMarkChestRequest(ServerPlayer player, InteractionHand hand, BlockPos pos) {
        if (!hasSkill(player, Skills.TREASURE_HUNTER)) {
            return;
        }
        if (!DimensionTechBridge.isLoaded()) {
            // 未装 DimensionTech：宝箱标记器根本不该存在，这里只是兜底提示。
            player.displayClientMessage(
                    Component.translatable("message.zifeng_s_custom_skill_tree.need_dimension_tech"), true);
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!DimensionTechBridge.isChestMarker(stack)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos immutable = pos.immutable();
        if (!level.isLoaded(immutable)) {
            return;
        }
        if (DimensionTechBridge.markChest(level, stack, immutable)) {
            // 标记写入物品数据组件后需要同步给客户端（tooltip 才会立刻更新）。
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
            player.displayClientMessage(
                    Component.translatable("message.zifeng_s_custom_skill_tree.chest_marked"), true);
        }
    }

    // ============ 结构剖析：半径内已生成结构 ============

    public static void onStructureScanRequest(ServerPlayer player) {
        if (!hasSkill(player, Skills.STRUCTURE_ANALYSIS)) {
            ModNetwork.sendToPlayer(player, new SkillStructureRegionsS2CPacket(List.of()));
            return;
        }
        scanAndSend(player);
    }

    private static void scanAndSend(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = player.blockPosition();
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        int baseChunkX = center.getX() >> 4;
        int baseChunkZ = center.getZ() >> 4;
        String dimension = level.dimension().location().toString();

        List<RegionEntry> regions = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int chunkX = baseChunkX - SCAN_RADIUS_CHUNKS;
                chunkX <= baseChunkX + SCAN_RADIUS_CHUNKS;
                chunkX++) {
            for (int chunkZ = baseChunkZ - SCAN_RADIUS_CHUNKS;
                    chunkZ <= baseChunkZ + SCAN_RADIUS_CHUNKS;
                    chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue; // 未加载 → 不枚举、不生成
                }
                for (StructureStart start : chunk.getAllStarts().values()) {
                    if (!start.isValid()) {
                        continue;
                    }
                    ResourceLocation structureId = registry.getKey(start.getStructure());
                    if (structureId == null) {
                        continue;
                    }
                    BoundingBox box = start.getBoundingBox();
                    String key = structureKey(dimension, structureId, box);
                    if (!seen.add(key)) {
                        continue;
                    }
                    StructureValuationData data = STRUCTURE_CACHE.get(key);
                    if (data == null) {
                        data = StructureValuationData.UNAVAILABLE;
                        requestStructureValue(server, level, structureId, box, key);
                    }
                    regions.add(
                            new RegionEntry(
                                    structureId,
                                    box.minX(),
                                    box.minY(),
                                    box.minZ(),
                                    box.maxX(),
                                    box.maxY(),
                                    box.maxZ(),
                                    data));
                }
            }
        }
        ModNetwork.sendToPlayer(player, new SkillStructureRegionsS2CPacket(regions));
    }

    private static void requestStructureValue(
            MinecraftServer server,
            ServerLevel level,
            ResourceLocation structureId,
            BoundingBox box,
            String key) {
        if (!STRUCTURE_PENDING.add(key)) {
            return;
        }
        DimensionTechBridge.valuateStructureAsync(server, level, structureId, box)
                .whenComplete(
                        (data, error) -> {
                            STRUCTURE_PENDING.remove(key);
                            if (data != null) {
                                STRUCTURE_CACHE.put(key, data);
                            }
                        });
    }

    // ============ 生命周期 ============

    /** 服务器停止时清空缓存（防跨存档残留）。 */
    public static void onServerStopped() {
        CHEST_CACHE.clear();
        STRUCTURE_CACHE.clear();
        STRUCTURE_PENDING.clear();
    }

    // ============ 工具 ============

    private static boolean hasSkill(ServerPlayer player, String skillId) {
        PlayerSkillRecord record =
                PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
        return record.getLearnedPoints(skillId) > 0;
    }

    private static String chestKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    private static String structureKey(String dimension, ResourceLocation id, BoundingBox box) {
        return dimension
                + "|"
                + id
                + "|"
                + box.minX()
                + ","
                + box.minY()
                + ","
                + box.minZ()
                + ","
                + box.maxX()
                + ","
                + box.maxY()
                + ","
                + box.maxZ();
    }
}
