package org.zifeng.skilltree.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.network.ModNetwork;
import org.zifeng.skilltree.network.SkillChestValuationC2SPacket;
import org.zifeng.skilltree.network.StructureValuationData;
import org.zifeng.skilltree.skill.Skills;

import java.util.List;

/**
 * 寻宝大师扩展：准星对准宝箱时，在宝箱标记框右侧显示「产出期望 / 宝箱价值 / 维度价值」。
 *
 * <p>实现分两步（世界→屏幕投影与绘制不在同一阶段）：
 * <ol>
 *   <li>{@link RenderLevelStageEvent}（AFTER_BLOCK_ENTITIES）：判定准星命中哪个已标记容器，用相机矩阵把
 *       锚点投影成屏幕坐标；命中变化时才向服务端请求估值。</li>
 *   <li>GUI 层：在投影出的坐标旁绘制信息面板（数值来自 {@link SkillLinkClientState} 缓存）。</li>
 * </ol>
 *
 * <p>未安装 DimensionTech 时服务端回 {@code available=false}，这里显示"需要 DimensionTech"占位而不画数字。
 */
public final class TreasureInfoOverlay {

    /** 准星拾取距离（与项目既有 pick 口径一致：20 格）。 */
    private static final int PICK_DISTANCE = 20;
    /** 每帧最多显示几行物品期望。 */
    private static final int MAX_ITEM_LINES = 6;

    private static final int COLOR_TITLE = 0xFFFFD24A;
    private static final int COLOR_TEXT = 0xFFE8E8E8;
    private static final int COLOR_DIM = 0xFF9A9A9A;
    private static final int COLOR_BG = 0xC0101018;
    private static final int COLOR_BORDER = 0xFF55FF55;

    private static BlockPos aimedPos;
    private static BlockPos lastRequested;
    private static boolean visible;
    private static float screenX;
    private static float screenY;

    private TreasureInfoOverlay() {}

    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "treasure_info_overlay");

    /** MOD 总线：把浮层注册为一个 GUI 层（与技能点 HUD 同层机制）。 */
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.EXPERIENCE_LEVEL, LAYER_ID, (gui, delta) -> renderGui(gui));
    }

    /** 世界阶段：判定命中 + 投影 + 变化时请求。 */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        visible = false;
        aimedPos = null;
        if (!SkillKeyBinds.isTreasureInfoVisible()) {
            lastRequested = null;
            return;
        }
        if (!ModKeyBindingEvents.isSkillEnabledClient(Skills.TREASURE_HUNTER)) {
            lastRequested = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            lastRequested = null;
            return;
        }
        BlockPos pos = aimedTreasure(mc);
        if (pos == null) {
            lastRequested = null;
            return;
        }
        aimedPos = pos;
        if (!pos.equals(lastRequested)) {
            lastRequested = pos;
            ModNetwork.sendToServer(new SkillChestValuationC2SPacket(pos));
        }
        // 锚点：箱子 +X 面外侧、腰部高度 → 屏幕上出现在框的右侧
        double wx = pos.getX() + 1.15D;
        double wy = pos.getY() + 0.5D;
        double wz = pos.getZ() + 0.5D;
        var cam = event.getCamera().getPosition();
        Matrix4f mvp = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        Vector4f clip = mvp.transform(new Vector4f(
                (float) (wx - cam.x), (float) (wy - cam.y), (float) (wz - cam.z), 1.0F));
        if (clip.w() <= 0.0001F) {
            return; // 在相机背后
        }
        float ndcX = clip.x() / clip.w();
        float ndcY = clip.y() / clip.w();
        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int guiHeight = mc.getWindow().getGuiScaledHeight();
        screenX = (ndcX * 0.5F + 0.5F) * guiWidth;
        screenY = (1.0F - (ndcY * 0.5F + 0.5F)) * guiHeight;
        visible = true;
    }

    /** GUI 层：绘制面板。 */
    public static void renderGui(GuiGraphics gui) {
        if (!visible || aimedPos == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.font == null) {
            return;
        }
        StructureValuationData data = SkillLinkClientState.chestValuation(aimedPos);

        List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable("ui.zifeng_s_custom_skill_tree.link_chest_title"));
        if (data == null) {
            lines.add(Component.translatable("ui.zifeng_s_custom_skill_tree.link_analyzing"));
        } else if (!data.available()) {
            lines.add(Component.translatable("ui.zifeng_s_custom_skill_tree.need_dimension_tech"));
            lines.add(Component.translatable(
                    "ui.zifeng_s_custom_skill_tree.link_status", data.status()));
        } else {
            lines.add(Component.translatable(
                    "ui.zifeng_s_custom_skill_tree.link_chest_value", format(data.structureValue())));
            lines.add(Component.translatable(
                    "ui.zifeng_s_custom_skill_tree.link_dimension_value", format(data.dimensionValue())));
            lines.add(Component.translatable("ui.zifeng_s_custom_skill_tree.link_expectation"));
            int shown = 0;
            for (StructureValuationData.ItemEntry entry : data.items()) {
                if (shown >= MAX_ITEM_LINES) {
                    lines.add(Component.translatable(
                            "ui.zifeng_s_custom_skill_tree.link_more",
                            data.items().size() - shown));
                    break;
                }
                lines.add(Component.literal("  ")
                        .append(itemName(entry.itemId()))
                        .append(" §7x" + format(entry.count())));
                shown++;
            }
        }

        int lineHeight = 10;
        int padding = 4;
        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, mc.font.width(line));
        }
        int panelWidth = width + padding * 2;
        int panelHeight = lines.size() * lineHeight + padding * 2;
        int panelX = Math.round(screenX + 6.0F);
        int panelY = Math.round(screenY - panelHeight / 2.0F);
        // 贴边修正
        panelX = Math.max(2, Math.min(gui.guiWidth() - panelWidth - 2, panelX));
        panelY = Math.max(2, Math.min(gui.guiHeight() - panelHeight - 2, panelY));

        gui.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_BG);
        gui.fill(panelX, panelY, panelX + panelWidth, panelY + 1, COLOR_BORDER);
        gui.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, COLOR_BORDER);
        int textY = panelY + padding;
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? COLOR_TITLE : (i >= 2 ? COLOR_DIM : COLOR_TEXT);
            gui.drawString(mc.font, lines.get(i), panelX + padding, textY, color, false);
            textY += lineHeight;
        }
    }

    /** 准星命中的已标记容器；不是箱子或未被标记则 null。 */
    private static BlockPos aimedTreasure(Minecraft mc) {
        LocalPlayer player = mc.player;
        HitResult hit = player.pick(PICK_DISTANCE, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            return null;
        }
        BlockPos pos = blockHit.getBlockPos();
        return ClientTreasureEvents.isCachedTreasure(pos) ? pos : null;
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

    public static void onDisconnect() {
        aimedPos = null;
        lastRequested = null;
        visible = false;
    }
}
