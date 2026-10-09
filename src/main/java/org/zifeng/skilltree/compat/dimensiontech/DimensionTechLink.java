package org.zifeng.skilltree.compat.dimensiontech;

import com.suntide_20210418.dimensiontech.api.DimensionTechApi;
import com.suntide_20210418.dimensiontech.api.StructureValuation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.zifeng.skilltree.network.StructureValuationData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 真正触碰 DimensionTech 类型的薄适配层。
 *
 * <p><b>⚠️ 本类只允许在 {@link DimensionTechBridge#isLoaded()} 为真时被调用。</b>
 * 它的方法体引用了 DimensionTech 的 {@code api} 包，一旦在未安装 DimensionTech 的环境里被加载，
 * 就会抛 {@code NoClassDefFoundError}。门闩由 {@code DimensionTechBridge} 统一把关。
 */
final class DimensionTechLink {

    private DimensionTechLink() {}

    static Component structureName(ResourceLocation id) {
        return DimensionTechApi.structureName(id);
    }

    static StructureValuationData valuateChest(ServerLevel level, BlockPos pos) {
        return DimensionTechApi.valuateChest(level, pos)
                .map(DimensionTechLink::toData)
                .orElse(StructureValuationData.UNAVAILABLE);
    }

    static CompletableFuture<StructureValuationData> valuateStructureAsync(
            MinecraftServer server, ServerLevel level, ResourceLocation structureId, BoundingBox bounds) {
        return DimensionTechApi.valuateStructureAsync(server, level, structureId, bounds)
                .thenApply(optional -> optional.map(DimensionTechLink::toData)
                        .orElse(StructureValuationData.UNAVAILABLE));
    }

    static boolean markChest(ServerLevel level, ItemStack markerStack, BlockPos pos) {
        return DimensionTechApi.markChest(level, markerStack, pos);
    }

    private static StructureValuationData toData(StructureValuation valuation) {
        List<StructureValuationData.ItemEntry> items = new ArrayList<>();
        for (StructureValuation.ItemExpectation entry : valuation.expectations()) {
            items.add(new StructureValuationData.ItemEntry(
                    entry.itemId().toString(), entry.count()));
        }
        return new StructureValuationData(
                valuation.usable(),
                valuation.status().name(),
                valuation.dimensionValue(),
                valuation.structureValue(),
                items);
    }
}
