package org.zifeng.skilltree.client;

import net.minecraft.core.BlockPos;
import org.zifeng.skilltree.network.SkillStructureRegionsS2CPacket.RegionEntry;
import org.zifeng.skilltree.network.StructureValuationData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DimensionTech 联动的客户端缓存（宝箱估值 + 结构区域）。
 *
 * <p>渲染层只从这里读：世界渲染与 GUI 面板不直接碰网络包，断线时统一清空（防跨服残留）。
 */
public final class SkillLinkClientState {

    /** 宝箱位置 → 估值（按 pos.asLong() 索引）。 */
    private static final Map<Long, StructureValuationData> CHEST_VALUATIONS = new HashMap<>();

    /** 最近一次结构扫描结果（半径内已生成结构 + 估值）。 */
    private static volatile List<RegionEntry> regions = List.of();

    private SkillLinkClientState() {}

    // ============ 宝箱估值 ============

    public static void onChestValuation(BlockPos pos, StructureValuationData data) {
        CHEST_VALUATIONS.put(pos.asLong(), data);
    }

    /** 该宝箱的估值；尚未收到时为 {@code null}（渲染层显示"分析中"）。 */
    public static StructureValuationData chestValuation(BlockPos pos) {
        return pos == null ? null : CHEST_VALUATIONS.get(pos.asLong());
    }

    // ============ 结构区域 ============

    public static void onStructureRegions(List<RegionEntry> newRegions) {
        regions = List.copyOf(newRegions);
    }

    public static List<RegionEntry> regions() {
        return regions;
    }

    // ============ 清理 ============

    public static void onDisconnect() {
        CHEST_VALUATIONS.clear();
        regions = List.of();
    }
}
