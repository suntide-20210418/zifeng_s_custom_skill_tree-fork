package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.zifeng.skilltree.compat.dimensiontech.DimensionTechBridge;
import org.zifeng.skilltree.network.ModNetwork;
import org.zifeng.skilltree.network.SkillMarkChestC2SPacket;
import org.zifeng.skilltree.skill.Skills;

/**
 * 寻宝大师扩展：手持「宝箱标记器」左键点击已被寻宝大师标记的宝箱 → 执行标记。
 *
 * <p>为什么走 {@link InputEvent.InteractionKeyMappingTriggered} 而不是右键：右键开箱子会被原版优先处理，
 * 且语义不符（这里是"标记"而非"打开"）；左键才对应"点击标记框"。
 *
 * <p>⚠️ 激活期<b>无条件</b> {@code setCanceled(true)}：长按左键时本事件每 tick 触发一次，只在"通过去重"时
 * 才取消的话，未通过去重的那几次会照常挖方块 —— 结果是"一边标记一边把箱子挖了"。与
 * {@code MagnetExclusionInputHandler} 的既有踩坑结论一致。
 */
public final class ChestMarkInputHandler {

    private static final int PICK_DISTANCE = 20;

    private static int lastFireTick = Integer.MIN_VALUE;

    private ChestMarkInputHandler() {}

    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null) {
            return;
        }
        if (!ModKeyBindingEvents.isSkillEnabledClient(Skills.TREASURE_HUNTER)) {
            return;
        }
        InteractionHand hand = markerHand(player);
        if (hand == null) {
            return;
        }
        BlockPos pos = aimedTreasure(player);
        if (pos == null) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(true);
        if (player.tickCount == lastFireTick) {
            return; // 同 tick 去重（长按只响应一次）
        }
        lastFireTick = player.tickCount;
        ModNetwork.sendToServer(new SkillMarkChestC2SPacket(hand, pos));
    }

    /** 手持宝箱标记器的那只手；两只手都没有则 null。 */
    private static InteractionHand markerHand(LocalPlayer player) {
        if (DimensionTechBridge.isChestMarker(player.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        if (DimensionTechBridge.isChestMarker(player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private static BlockPos aimedTreasure(LocalPlayer player) {
        HitResult hit = player.pick(PICK_DISTANCE, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            return null;
        }
        BlockPos pos = blockHit.getBlockPos();
        return ClientTreasureEvents.isCachedTreasure(pos) ? pos : null;
    }

    public static void onDisconnect() {
        lastFireTick = Integer.MIN_VALUE;
    }
}
