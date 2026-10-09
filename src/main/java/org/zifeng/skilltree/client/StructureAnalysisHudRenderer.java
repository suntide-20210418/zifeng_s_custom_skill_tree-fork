package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.compat.dimensiontech.DimensionTechBridge;
import org.zifeng.skilltree.network.SkillStructureRegionsS2CPacket.RegionEntry;
import org.zifeng.skilltree.network.StructureValuationData;
import org.zifeng.skilltree.skill.Skills;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构剖析面板：在可调整的固定屏幕位置显示"当前结构"的结构名 / 结构价值 / 维度价值 / 产出期望。
 *
 * <p>"当前结构" = 玩家视线命中的那个结构区域（{@link StructureRegionRenderer#aimedRegion}）。
 *
 * <p>位置与显示开关、期望列表条数都来自 {@link SkillKeyBinds}（在 HUD 调整子界面里配置）。
 *
 * <p>未安装 DimensionTech：结构名照显（走本地兜底名），数值区显示"需要 DimensionTech"。
 */
public final class StructureAnalysisHudRenderer {

    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "structure_analysis_hud");

    /** 默认基准位置（左上角）——可被 SkillKeyBinds 的偏移改写。 */
    private static final int BASE_X = 6;
    private static final int BASE_Y = 6;

    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 4;
    private static final int MIN_PANEL_WIDTH = 180;

    /** 视线长度：足够覆盖 16 区块扫描半径内的结构。 */
    private static final double RAY_LENGTH = 256.0D;

    private static final int COLOR_TITLE = 0xFF7FD8FF;
    private static final int COLOR_TEXT = 0xFFE8E8E8;
    private static final int COLOR_DIM = 0xFF9A9A9A;
    private static final int COLOR_BG = 0xC0101018;
    private static final int COLOR_BORDER = 0xFF3A7A9A;

    private StructureAnalysisHudRenderer() {}

    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.EXPERIENCE_LEVEL, LAYER_ID, (gui, delta) -> render(gui));
    }

    public static void render(GuiGraphics gui) {
        if (!SkillKeyBinds.isAnalysisHudVisible()) {
            return;
        }
        if (!ModKeyBindingEvents.isSkillEnabledClient(Skills.STRUCTURE_ANALYSIS)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.player.isSpectator() || mc.font == null) {
            return;
        }

        List<Component> lines = buildLines(mc);
        int width = MIN_PANEL_WIDTH;
        for (Component line : lines) {
            width = Math.max(width, mc.font.width(line) + PADDING * 2);
        }
        int panelX = BASE_X + SkillKeyBinds.getAnalysisHudOffsetX();
        int panelY = BASE_Y + SkillKeyBinds.getAnalysisHudOffsetY();
        int panelWidth = width;
        int panelHeight = lines.size() * LINE_HEIGHT + PADDING * 2;
        panelX = Math.max(2, Math.min(gui.guiWidth() - panelWidth - 2, panelX));
        panelY = Math.max(2, Math.min(gui.guiHeight() - panelHeight - 2, panelY));

        gui.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_BG);
        gui.fill(panelX, panelY, panelX + panelWidth, panelY + 1, COLOR_BORDER);
        gui.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, COLOR_BORDER);

        int textY = panelY + PADDING;
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? COLOR_TITLE : (i == 1 ? COLOR_TEXT : COLOR_DIM);
            gui.drawString(mc.font, lines.get(i), panelX + PADDING, textY, color, false);
            textY += LINE_HEIGHT;
        }
    }

    private static List<Component> buildLines(Minecraft mc) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("ui.zifeng_s_custom_skill_tree.link_structure_title"));
        if (!DimensionTechBridge.isLoaded()) {
            lines.add(Component.translatable("ui.zifeng_s_custom_skill_tree.need_dimension_tech"));
        }
        RegionEntry hit = aimedStructure(mc);
        if (hit == null) {
            lines.add(Component.translatable("ui.zifeng_s_custom_skill_tree.link_no_structure"));
            return lines;
        }
        lines.add(Component.translatable(
                "ui.zifeng_s_custom_skill_tree.link_structure_name",
                DimensionTechBridge.structureName(hit.structureId())));
        StructureValuationData data = hit.data();
        if (!data.available()) {
            lines.add(Component.translatable("ui.zifeng_s_custom_skill_tree.link_analyzing"));
            if (!"MISSING".equals(data.status())) {
                lines.add(Component.translatable(
                        "ui.zifeng_s_custom_skill_tree.link_status", data.status()));
            }
            return lines;
        }
        lines.add(Component.translatable(
                "ui.zifeng_s_custom_skill_tree.link_structure_value", format(data.structureValue())));
        lines.add(Component.translatable(
                "ui.zifeng_s_custom_skill_tree.link_dimension_value", format(data.dimensionValue())));
        lines.add(Component.translatable("ui.zifeng_s_custom_skill_tree.link_expectation"));
        int limit = SkillKeyBinds.getAnalysisHudItemLimit();
        int shown = 0;
        for (StructureValuationData.ItemEntry entry : data.items()) {
            if (shown >= limit) {
                lines.add(Component.translatable(
                        "ui.zifeng_s_custom_skill_tree.link_more", data.items().size() - shown));
                break;
            }
            lines.add(Component.literal("  ")
                    .append(itemName(entry.itemId()))
                    .append(" §7x" + format(entry.count())));
            shown++;
        }
        return lines;
    }

    private static RegionEntry aimedStructure(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition(1.0F);
        Vec3 look = mc.player.getLookAngle();
        return StructureRegionRenderer.aimedRegion(eye, eye.add(look.scale(RAY_LENGTH)));
    }

    private static Component itemName(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return Component.literal(itemId);
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item.getDefaultInstance().getHoverName();
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
