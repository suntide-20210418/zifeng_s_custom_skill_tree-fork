package org.zifeng.skilltree.compat.dimensiontech;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.fml.ModList;
import org.zifeng.skilltree.network.StructureValuationData;

import java.util.concurrent.CompletableFuture;

/**
 * DimensionTech 联动的<b>唯一对外入口</b>（本类不引用任何 DimensionTech 类型）。
 *
 * <p>分层原因（务必保持）：DimensionTech 是 compileOnly 依赖，未安装时必须能正常加载技能树。JVM 加载一个
 * 类时，其方法体里引用的类型要到<b>首次执行</b>才会解析 —— 所以这里把 DT 引用全部隔离到
 * {@link DimensionTechLink}，本类只做门闩与转发。调用方先 {@link #isLoaded()}，通过后才触达 Link，
 * 未安装 DimensionTech 时 Link 永远不会被加载。
 *
 * <p>与 DimensionTech 自身 {@code integration/ae2/Ae2Integration} + {@code ModList.isLoaded} 的写法同构。
 */
public final class DimensionTechBridge {

    /** DimensionTech 的 modid。 */
    public static final String MOD_ID = "dimension_tech";

    /** 宝箱标记器物品 id（纯注册表比对，无需加载 DT 类）。 */
    private static final ResourceLocation CHEST_MARKER_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "chest_marker");

    private static Boolean loaded;

    private DimensionTechBridge() {}

    /** DimensionTech 是否已安装（结果缓存）。 */
    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(MOD_ID);
        }
        return loaded;
    }

    /**
     * 物品是否是 DimensionTech 的「宝箱标记器」。
     *
     * <p>按注册表 id 比对，<b>不加载 DimensionTech 类</b>：即使未安装 DimensionTech，本方法也只是恒 false，
     * 绝不会因缺失类而抛错。
     */
    public static boolean isChestMarker(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return CHEST_MARKER_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** 结构本地化名；未安装 DimensionTech 时退回"可读化的 id"。 */
    public static Component structureName(ResourceLocation id) {
        if (!isLoaded()) {
            return Component.literal(readable(id));
        }
        return DimensionTechLink.structureName(id);
    }

    /** 宝箱估值（同步，单次用户动作；DimensionTech 侧与宝箱标记器同一条路径）。 */
    public static StructureValuationData valuateChest(ServerLevel level, BlockPos pos) {
        if (!isLoaded()) {
            return StructureValuationData.UNAVAILABLE;
        }
        return DimensionTechLink.valuateChest(level, pos);
    }

    /** 结构估值（异步，复用 DimensionTech 分析缓存）。 */
    public static CompletableFuture<StructureValuationData> valuateStructureAsync(
            MinecraftServer server, ServerLevel level, ResourceLocation structureId, BoundingBox bounds) {
        if (!isLoaded()) {
            return CompletableFuture.completedFuture(StructureValuationData.UNAVAILABLE);
        }
        return DimensionTechLink.valuateStructureAsync(server, level, structureId, bounds);
    }

    /** 用宝箱标记器标记指定位置的宝箱；未安装 DimensionTech 或位置无战利品来源时返回 false。 */
    public static boolean markChest(ServerLevel level, ItemStack markerStack, BlockPos pos) {
        if (!isLoaded()) {
            return false;
        }
        return DimensionTechLink.markChest(level, markerStack, pos);
    }

    /** id → 可读文本（namespace 去掉下划线、path 用空格分词），无 DimensionTech 时的兜底显示名。 */
    private static String readable(ResourceLocation id) {
        String[] words = id.getPath().replace('/', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.length() == 0 ? id.toString() : result.toString();
    }
}
