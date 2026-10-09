package org.zifeng.skilltree.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * HUD 调整子界面（2026-09-01 子界面系统）：
 * <p>悬浮在玩家屏幕中间的不透明面板，用于调整技能点 HUD 的显示与位置。
 * <p>行1：HUD 开关；行2：X 位置（左/右箭头 + 滚轮）；行3：Y 位置；
 * 行4：真实血量数字开关；行5：伤害吸收数字开关（2026-09-11）；
 * 行6：护甲数字开关；行7：满值数字开关（三数字共用，默认关）；
 * 行8：结构剖析面板开关；行9/10：结构剖析面板 X/Y；行11：期望列表条数；行12：宝箱信息浮层开关；
 * 行13：重置位置（同时归零技能点 HUD 与结构剖析面板）。
 * <p>行8~12 为 DimensionTech 联动新增（未装 DimensionTech 时仍可调，只是面板数值区显示占位）。
 * <p>步进与技能点切换一致：普通=1，Shift=10，Ctrl=100。
 */
public class HudAdjustSubScreen extends SkillSubScreen {
    /** 面板宽 */
    private static final int PANEL_W = 220;
    /** 面板高 */
    private static final int PANEL_H = 330; // 2026-10-09：8 行 → 13 行（+5 行 DimensionTech 联动设置）
    /** 行高 */
    private static final int ROW_H = 18;
    /** 行起始 y（标题栏下方） */
    private static final int ROWS_TOP = TITLE_BAR_H + 8;
    /** 行间距 */
    private static final int ROW_GAP = 4;
    /** 行内容左/右留白 */
    private static final int PAD = 12;

    public HudAdjustSubScreen(SkillTreeScreen parent) {
        super(parent);
        this.posKey = "hud_adjust";
    }

    @Override
    public void init(int screenWidth, int screenHeight) {
        // 屏幕中间居中
        panelW = PANEL_W;
        panelH = PANEL_H;
        panelX = (screenWidth - panelW) / 2;
        panelY = (screenHeight - panelH) / 2;
        // 恢复上次拖动的位置（有保存值则覆盖默认）
        super.init(screenWidth, screenHeight);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY) {
        renderPanelBase(gui, t("hud_title"), mouseX, mouseY);
        renderRows(gui, mouseX, mouseY);
    }

    /** 渲染 13 行控制 */
    private void renderRows(GuiGraphics gui, int mouseX, int mouseY) {
        var overlay = net.minecraft.client.renderer.RenderType.guiOverlay();
        // 行1：开关
        boolean visible = org.zifeng.skilltree.client.SkillPointHudRenderer.isVisible();
        drawRow(gui, 0,
                visible ? t("hud_on") : t("hud_off"),
                visible ? 0xFF55FF55 : 0xFFFF5555,
                visible ? 0xFF2A4A2A : 0xFF4A2A2A,
                visible ? 0xFF55FF55 : 0xFFFF5555,
                mouseX, mouseY);
        // 行2：X 位置
        drawPosRow(gui, 1, t("hud_x"), org.zifeng.skilltree.client.SkillPointHudRenderer.getHudOffsetX(), mouseX, mouseY);
        // 行3：Y 位置
        drawPosRow(gui, 2, t("hud_y"), org.zifeng.skilltree.client.SkillPointHudRenderer.getHudOffsetY(), mouseX, mouseY);
        // 行4：真实血量数字开关（2026-09-11）
        boolean hpNum = org.zifeng.skilltree.client.SkillKeyBinds.isHudHealthNumber();
        drawRow(gui, 3,
                t("hud_health_number") + ": " + (hpNum ? t("hud_state_on") : t("hud_state_off")),
                hpNum ? 0xFF55FF55 : 0xFFFF5555,
                hpNum ? 0xFF2A4A2A : 0xFF4A2A2A,
                hpNum ? 0xFF55FF55 : 0xFFFF5555,
                mouseX, mouseY);
        // 行5：伤害吸收数字开关（2026-09-11）
        boolean absNum = org.zifeng.skilltree.client.SkillKeyBinds.isHudAbsorptionNumber();
        drawRow(gui, 4,
                t("hud_absorption_number") + ": " + (absNum ? t("hud_state_on") : t("hud_state_off")),
                absNum ? 0xFF55FF55 : 0xFFFF5555,
                absNum ? 0xFF2A4A2A : 0xFF4A2A2A,
                absNum ? 0xFF55FF55 : 0xFFFF5555,
                mouseX, mouseY);
        // 行6：护甲数字开关（2026-09-11）
        boolean armorNum = org.zifeng.skilltree.client.SkillKeyBinds.isHudArmorNumber();
        drawRow(gui, 5,
                t("hud_armor_number") + ": " + (armorNum ? t("hud_state_on") : t("hud_state_off")),
                armorNum ? 0xFF55FF55 : 0xFFFF5555,
                armorNum ? 0xFF2A4A2A : 0xFF4A2A2A,
                armorNum ? 0xFF55FF55 : 0xFFFF5555,
                mouseX, mouseY);
        // 行7：满值数字开关（三数字共用，默认关）
        boolean showMax = org.zifeng.skilltree.client.SkillKeyBinds.isHudShowMaxValue();
        drawRow(gui, 6,
                t("hud_show_max") + ": " + (showMax ? t("hud_state_on") : t("hud_state_off")),
                showMax ? 0xFF55FF55 : 0xFFFF5555,
                showMax ? 0xFF2A4A2A : 0xFF4A2A2A,
                showMax ? 0xFF55FF55 : 0xFFFF5555,
                mouseX, mouseY);
        // 行8：结构剖析面板开关（DimensionTech 联动）
        boolean structHud = org.zifeng.skilltree.client.SkillKeyBinds.isAnalysisHudVisible();
        drawRow(gui, 7,
                t("hud_structure_hud") + ": " + (structHud ? t("hud_state_on") : t("hud_state_off")),
                structHud ? 0xFF55FF55 : 0xFFFF5555,
                structHud ? 0xFF2A4A2A : 0xFF4A2A2A,
                structHud ? 0xFF55FF55 : 0xFFFF5555,
                mouseX, mouseY);
        // 行9：结构剖析面板 X 位置
        drawPosRow(gui, 8, t("hud_structure_x"),
                org.zifeng.skilltree.client.SkillKeyBinds.getAnalysisHudOffsetX(), mouseX, mouseY);
        // 行10：结构剖析面板 Y 位置
        drawPosRow(gui, 9, t("hud_structure_y"),
                org.zifeng.skilltree.client.SkillKeyBinds.getAnalysisHudOffsetY(), mouseX, mouseY);
        // 行11：期望物品列表显示条数
        drawPosRow(gui, 10, t("hud_item_limit"),
                org.zifeng.skilltree.client.SkillKeyBinds.getAnalysisHudItemLimit(), mouseX, mouseY);
        // 行12：宝箱信息浮层开关
        boolean chestOverlay = org.zifeng.skilltree.client.SkillKeyBinds.isTreasureInfoVisible();
        drawRow(gui, 11,
                t("hud_treasure_overlay") + ": " + (chestOverlay ? t("hud_state_on") : t("hud_state_off")),
                chestOverlay ? 0xFF55FF55 : 0xFFFF5555,
                chestOverlay ? 0xFF2A4A2A : 0xFF4A2A2A,
                chestOverlay ? 0xFF55FF55 : 0xFFFF5555,
                mouseX, mouseY);
        // 行13：重置按钮（红色系）
        drawRow(gui, 12, t("hud_reset"), 0xFFFF5555, 0xFF3A2A2A, 0xFFDD5555, mouseX, mouseY);
        // 步进提示（右下角小字）
        gui.drawString(parent.font(), t("hud_step_hint"), panelX + PAD, panelY + panelH - 10, 0xFF888888, false);
    }

    /** 行矩形（屏幕坐标） */
    private int rowY(int index) {
        return panelY + ROWS_TOP + index * (ROW_H + ROW_GAP);
    }

    /** 整行宽 */
    private int rowW() {
        return panelW - PAD * 2;
    }

    /** 绘制普通行（无左右箭头），返回是否命中 */
    private void drawRow(GuiGraphics gui, int index, String text, int textColor, int bg, int border, int mouseX, int mouseY) {
        int x = panelX + PAD;
        int y = rowY(index);
        int w = rowW();
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + ROW_H;
        int fill = hovered ? (bg + 0x00FFFFFF) : bg;
        var overlay = net.minecraft.client.renderer.RenderType.guiOverlay();
        gui.fill(overlay, x, y, x + w, y + ROW_H, fill);
        gui.fill(overlay, x, y, x + w, y + 1, border);
        gui.fill(overlay, x, y + ROW_H - 1, x + w, y + ROW_H, border);
        // ★ 2026-09-21：全部文字去掉投影（drawCenteredString 内部固定带阴影 → 自己算居中）
        var rowFont = parent.font();
        gui.drawString(rowFont, text, x + (w - rowFont.width(text)) / 2, y + 4, textColor, false);
    }

    /** 绘制位置行（左箭头 / 文字 / 右箭头），返回是否命中 */
    private void drawPosRow(GuiGraphics gui, int index, String label, int value, int mouseX, int mouseY) {
        int x = panelX + PAD;
        int y = rowY(index);
        int w = rowW();
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + ROW_H;
        var overlay = net.minecraft.client.renderer.RenderType.guiOverlay();
        gui.fill(overlay, x, y, x + w, y + ROW_H, hovered ? 0xFF3A3A4A : 0xFF2A2A3A);
        gui.fill(overlay, x, y, x + w, y + 1, 0xFF555566);
        gui.fill(overlay, x, y + ROW_H - 1, x + w, y + ROW_H, 0xFF555566);
        // 左箭头（◀）/ 文字 / 右箭头（▶）—— ★ 2026-09-21：均改为无投影绘制
        var posFont = parent.font();
        gui.drawString(posFont, "◀", x + 14 - posFont.width("◀") / 2, y + 4, 0xFF87CEEB, false);
        String centerText = label + ":" + value;
        gui.drawString(posFont, centerText, x + (w - posFont.width(centerText)) / 2, y + 4, 0xFFE0B6C8, false);
        gui.drawString(posFont, "▶", x + w - 14 - posFont.width("▶") / 2, y + 4, 0xFF87CEEB, false);
    }

    /** 位置行的箭头区域（左箭头 0~28px，右箭头 w-28~w） */
    private int arrowZone(double mouseX, double x, double w) {
        if (mouseX < x + 28) {
            return -1; // 左箭头
        } else if (mouseX > x + w - 28) {
            return 1; // 右箭头
        }
        return 0; // 中间文字区
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 基类：左键点中标题栏 → 标记拖动（排除 ✕）
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && isCloseBtnHit(mouseX, mouseY)) {
            parent.closeSubScreen();
            return true;
        }
        if (button != 0) {
            return true; // 拦截右键等
        }
        int x = panelX + PAD;
        int w = rowW();
        // 行1：开关
        if (rowHit(mouseX, mouseY, 0, x, w)) {
            org.zifeng.skilltree.client.SkillPointHudRenderer.setVisible(
                    !org.zifeng.skilltree.client.SkillPointHudRenderer.isVisible());
            return true;
        }
        // 行2/行3：X/Y 位置（步进：普通=1，Shift=10，Ctrl=100）
        int step = Screen.hasControlDown() ? 100 : (Screen.hasShiftDown() ? 10 : 1);
        if (rowHit(mouseX, mouseY, 1, x, w)) {
            int dir = arrowZone(mouseX, x, w);
            if (dir != 0) {
                org.zifeng.skilltree.client.SkillPointHudRenderer.adjustOffsetX(dir * step);
            }
            return true;
        }
        if (rowHit(mouseX, mouseY, 2, x, w)) {
            int dir = arrowZone(mouseX, x, w);
            if (dir != 0) {
                org.zifeng.skilltree.client.SkillPointHudRenderer.adjustOffsetY(dir * step);
            }
            return true;
        }
        // 行4：血量数字开关
        if (rowHit(mouseX, mouseY, 3, x, w)) {
            org.zifeng.skilltree.client.SkillKeyBinds.setHudHealthNumber(
                    !org.zifeng.skilltree.client.SkillKeyBinds.isHudHealthNumber());
            return true;
        }
        // 行5：吸收数字开关
        if (rowHit(mouseX, mouseY, 4, x, w)) {
            org.zifeng.skilltree.client.SkillKeyBinds.setHudAbsorptionNumber(
                    !org.zifeng.skilltree.client.SkillKeyBinds.isHudAbsorptionNumber());
            return true;
        }
        // 行6：护甲数字开关
        if (rowHit(mouseX, mouseY, 5, x, w)) {
            org.zifeng.skilltree.client.SkillKeyBinds.setHudArmorNumber(
                    !org.zifeng.skilltree.client.SkillKeyBinds.isHudArmorNumber());
            return true;
        }
        // 行7：满值数字开关（三数字共用）
        if (rowHit(mouseX, mouseY, 6, x, w)) {
            org.zifeng.skilltree.client.SkillKeyBinds.setHudShowMaxValue(
                    !org.zifeng.skilltree.client.SkillKeyBinds.isHudShowMaxValue());
            return true;
        }
        // 行8：结构剖析面板开关（DimensionTech 联动）
        if (rowHit(mouseX, mouseY, 7, x, w)) {
            org.zifeng.skilltree.client.SkillKeyBinds.setAnalysisHudVisible(
                    !org.zifeng.skilltree.client.SkillKeyBinds.isAnalysisHudVisible());
            return true;
        }
        // 行9/行10：结构剖析面板 X/Y
        if (rowHit(mouseX, mouseY, 8, x, w)) {
            int dir = arrowZone(mouseX, x, w);
            if (dir != 0) {
                org.zifeng.skilltree.client.SkillKeyBinds.adjustAnalysisHudOffsetX(dir * step);
            }
            return true;
        }
        if (rowHit(mouseX, mouseY, 9, x, w)) {
            int dir = arrowZone(mouseX, x, w);
            if (dir != 0) {
                org.zifeng.skilltree.client.SkillKeyBinds.adjustAnalysisHudOffsetY(dir * step);
            }
            return true;
        }
        // 行11：期望物品列表条数
        if (rowHit(mouseX, mouseY, 10, x, w)) {
            int dir = arrowZone(mouseX, x, w);
            if (dir != 0) {
                org.zifeng.skilltree.client.SkillKeyBinds.setAnalysisHudItemLimit(
                        org.zifeng.skilltree.client.SkillKeyBinds.getAnalysisHudItemLimit() + dir);
            }
            return true;
        }
        // 行12：宝箱信息浮层开关
        if (rowHit(mouseX, mouseY, 11, x, w)) {
            org.zifeng.skilltree.client.SkillKeyBinds.setTreasureInfoVisible(
                    !org.zifeng.skilltree.client.SkillKeyBinds.isTreasureInfoVisible());
            return true;
        }
        // 行13：重置（技能点 HUD + 结构剖析面板位置归零）
        if (rowHit(mouseX, mouseY, 12, x, w)) {
            org.zifeng.skilltree.client.SkillPointHudRenderer.resetOffset();
            org.zifeng.skilltree.client.SkillKeyBinds.setAnalysisHudOffset(0, 0);
            return true;
        }
        return true; // 面板内点击一律拦截
    }

    /** 行区域命中 */
    private boolean rowHit(double mouseX, double mouseY, int index, int x, int w) {
        int y = rowY(index);
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + ROW_H;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 悬停在 X/Y 行上：滚轮调位置（步进与点击一致）
        int step = Screen.hasControlDown() ? 100 : (Screen.hasShiftDown() ? 10 : 1);
        int x = panelX + PAD;
        int w = rowW();
        int dir = delta > 0 ? 1 : -1;
        if (rowHit(mouseX, mouseY, 1, x, w)) {
            org.zifeng.skilltree.client.SkillPointHudRenderer.adjustOffsetX(dir * step);
            return true;
        }
        if (rowHit(mouseX, mouseY, 2, x, w)) {
            org.zifeng.skilltree.client.SkillPointHudRenderer.adjustOffsetY(dir * step);
            return true;
        }
        // 结构剖析面板 X/Y（DimensionTech 联动）
        if (rowHit(mouseX, mouseY, 8, x, w)) {
            org.zifeng.skilltree.client.SkillKeyBinds.adjustAnalysisHudOffsetX(dir * step);
            return true;
        }
        if (rowHit(mouseX, mouseY, 9, x, w)) {
            org.zifeng.skilltree.client.SkillKeyBinds.adjustAnalysisHudOffsetY(dir * step);
            return true;
        }
        // 期望物品列表条数
        if (rowHit(mouseX, mouseY, 10, x, w)) {
            org.zifeng.skilltree.client.SkillKeyBinds.setAnalysisHudItemLimit(
                    org.zifeng.skilltree.client.SkillKeyBinds.getAnalysisHudItemLimit() + dir);
            return true;
        }
        return true;
    }
}
