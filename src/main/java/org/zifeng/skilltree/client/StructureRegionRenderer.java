package org.zifeng.skilltree.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.zifeng.skilltree.network.ModNetwork;
import org.zifeng.skilltree.network.SkillStructureScanC2SPacket;
import org.zifeng.skilltree.network.SkillStructureRegionsS2CPacket.RegionEntry;
import org.zifeng.skilltree.skill.Skills;

/**
 * 结构剖析：渲染半径内「已生成结构」的区域框，并负责周期性向服务端拉取扫描结果。
 *
 * <p>渲染管线与 {@link ClientTreasureEvents} / 机械共鸣选区渲染完全一致
 * （{@code AFTER_BLOCK_ENTITIES} + {@code DEBUG_LINES} + 位置色做着色器 + 关深度），
 * 只在颜色上区分，属于"复用项目现有选区渲染代码"。
 *
 * <p>命中判定复用项目既有的 {@link ZoneRayUtil#rayFace}（射线 × 盒求交），供 GUI 面板取"当前结构"。
 */
public final class StructureRegionRenderer {

    /** 扫描请求间隔（tick）。服务端有缓存，重复扫描是纯查表。 */
    private static final int SCAN_INTERVAL_TICKS = 40;

    // 双线框：外深青 + 内亮青（与寻宝金/考古青区分度足够，且与选区渲染同款"外粗内亮"手法）
    private static final float OUTER_R = 0.10F, OUTER_G = 0.55F, OUTER_B = 0.75F;
    private static final float INNER_R = 0.55F, INNER_G = 0.90F, INNER_B = 1.0F;
    private static final float ALPHA = 1.0F;

    private static int scanTimer;

    private StructureRegionRenderer() {}

    /** 技能开启时周期性请求一次扫描。 */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!ModKeyBindingEvents.isSkillEnabledClient(Skills.STRUCTURE_ANALYSIS)) {
            scanTimer = 0;
            return;
        }
        scanTimer++;
        if (scanTimer < SCAN_INTERVAL_TICKS) {
            return;
        }
        scanTimer = 0;
        ModNetwork.sendToServer(new SkillStructureScanC2SPacket());
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        if (IrisCompat.isShadowPass()) {
            return;
        }
        java.util.List<RegionEntry> regions = SkillLinkClientState.regions();
        if (regions.isEmpty()) {
            return;
        }
        if (!ModKeyBindingEvents.isSkillEnabledClient(Skills.STRUCTURE_ANALYSIS)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        var cam = event.getCamera().getPosition();
        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(() -> GameRenderer.getPositionColorShader());

        BufferBuilder buffer =
                Tesselator.getInstance()
                        .begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (RegionEntry region : regions) {
            float x0 = region.minX();
            float y0 = region.minY();
            float z0 = region.minZ();
            float x1 = region.maxX() + 1.0F;
            float y1 = region.maxY() + 1.0F;
            float z1 = region.maxZ() + 1.0F;
            LevelRenderer.renderLineBox(
                    poseStack, buffer, x0, y0, z0, x1, y1, z1, OUTER_R, OUTER_G, OUTER_B, ALPHA);
            LevelRenderer.renderLineBox(
                    poseStack, buffer, x0, y0, z0, x1, y1, z1, INNER_R, INNER_G, INNER_B, ALPHA);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    /** 视线命中的最近结构区域；未命中返回 null。 */
    public static RegionEntry aimedRegion(Vec3 from, Vec3 to) {
        RegionEntry best = null;
        double bestDistance = Double.MAX_VALUE;
        for (RegionEntry region : SkillLinkClientState.regions()) {
            AABB box = boxOf(region);
            if (ZoneRayUtil.rayFace(box, from, to) == null) {
                continue; // 未命中
            }
            double distance = box.getCenter().distanceToSqr(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = region;
            }
        }
        return best;
    }

    /** 区域包围盒（含界 → 上界 +1，与渲染/交互口径一致）。 */
    public static AABB boxOf(RegionEntry region) {
        return new AABB(
                region.minX(),
                region.minY(),
                region.minZ(),
                region.maxX() + 1.0D,
                region.maxY() + 1.0D,
                region.maxZ() + 1.0D);
    }
}
