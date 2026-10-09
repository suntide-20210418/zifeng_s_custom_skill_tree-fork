package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构/宝箱估值的中立传输 DTO（不引用 DimensionTech 类型）。
 *
 * <p>为什么要有这一层：技能树对 DimensionTech 只有 compileOnly 依赖，且要求"未安装时功能不进入感"。
 * 如果直接把 {@code com.suntide_20210418.dimensiontech.api.StructureValuation} 塞进网络包，包的类加载就
 * 会牵动 DimensionTech 的类 —— 未安装即崩。这里用纯 JDK 类型复刻同样的数据，网络层与渲染层只认本类型。
 *
 * <p>{@link #available()} 为 {@code false} 表示"该对象本来就没有可显示的数值"（不是宝箱 / DimensionTech
 * 未安装 / 分析 UNSUPPORTED），此时数值项不可信，调用方必须显式降级显示，而不是画一个 0。
 */
public record StructureValuationData(
        boolean available,
        String status,
        double dimensionValue,
        double structureValue,
        List<ItemEntry> items) {

    /** DimensionTech 未安装 / 该位置无战利品来源时的占位。 */
    public static final StructureValuationData UNAVAILABLE =
            new StructureValuationData(false, "MISSING", 0.0D, 0.0D, List.of());

    public StructureValuationData {
        items = List.copyOf(items);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(available);
        buf.writeUtf(status);
        buf.writeDouble(dimensionValue);
        buf.writeDouble(structureValue);
        buf.writeVarInt(items.size());
        for (ItemEntry entry : items) {
            buf.writeUtf(entry.itemId());
            buf.writeDouble(entry.count());
        }
    }

    public static StructureValuationData read(FriendlyByteBuf buf) {
        boolean available = buf.readBoolean();
        String status = buf.readUtf();
        double dimensionValue = buf.readDouble();
        double structureValue = buf.readDouble();
        int size = buf.readVarInt();
        List<ItemEntry> items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            items.add(new ItemEntry(buf.readUtf(), buf.readDouble()));
        }
        return new StructureValuationData(available, status, dimensionValue, structureValue, items);
    }

    /** 单个物品的期望产出：物品 id + 平均个数。 */
    public record ItemEntry(String itemId, double count) {}
}
