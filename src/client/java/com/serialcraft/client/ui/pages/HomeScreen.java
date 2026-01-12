package com.serialcraft.client.ui.pages;

import com.serialcraft.client.ui.NavBar;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class HomeScreen {

    public enum ConnectionMode {
        USB,
        WIFI
    }

    // =========================
    // ESTADO
    // =========================

    private ConnectionMode mode = ConnectionMode.USB;

    // Referencias reales a los widgets
    private IconTextButton usbButton;
    private IconTextButton wifiButton;

    public void setMode(ConnectionMode mode) {
        this.mode = mode;
    }

    public ConnectionMode getMode() {
        return mode;
    }

    // =========================
    // INIT
    // =========================

    public void init(PanelUI panel, int screenWidth, int screenHeight) {

        int btnModeWidth = (screenWidth * 7) / 100;
        int btnModeHeight = (btnModeWidth / 10) * 4;
        int gap = 6;
        int y = 18;

        int xWifi = screenWidth - btnModeWidth - 18;
        int xUsb = xWifi - btnModeWidth - gap;

        this.usbButton = new IconTextButton(
                xUsb,
                y,
                btnModeWidth,
                btnModeHeight,
                SpriteIcon.USB,
                Component.literal("USB"),
                (btn) -> setMode(ConnectionMode.USB),
                0xff424242,
                0xff212121
        );

        this.wifiButton = new IconTextButton(
                xWifi,
                y,
                btnModeWidth,
                btnModeHeight,
                SpriteIcon.WIFI,
                Component.literal("WIFI"),
                (btn) -> setMode(ConnectionMode.WIFI),
                0xff424242,
                0xff212121
        );

        panel.addWidget(this.usbButton);
        panel.addWidget(this.wifiButton);

        // HOME por defecto
        setVisible(true);
    }

    // =========================
    // VISIBILIDAD
    // =========================

    public void setVisible(boolean visible) {
        if (usbButton != null) usbButton.visible = visible;
        if (wifiButton != null) wifiButton.visible = visible;
    }

    // =========================
    // RENDER
    // =========================

    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            Font font,
            int width,
            int height
    ) {

        renderTitle(guiGraphics, font, width);

        if (mode == ConnectionMode.USB) {
            renderUsbContent(guiGraphics, font, width, height);
        } else {
            renderWifiContent(guiGraphics, font, width, height);
        }
    }

    // =========================
    // SECCIONES
    // =========================

    private void renderTitle(GuiGraphics guiGraphics, Font font, int width) {

        int navWidth = NavBar.getNavBarWidth(width);
        int baseX = navWidth + 18;
        int baseY = 18;

        int contentWidth = width - navWidth;
        float scale = Math.min(1.8f, Math.max(1.6f, contentWidth / 500f));

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(scale, scale);

        int x = (int) (baseX / scale);
        int y = (int) (baseY / scale);

        String left = "INICIO";
        String right = "CONECTA TU HARDWARE";

        guiGraphics.drawString(font, left, x, y, 0xFFE91E63, false);
        guiGraphics.drawString(
                font,
                right,
                x + font.width(left) + 4,
                y,
                0xFF000000,
                false
        );

        guiGraphics.pose().popMatrix();
    }

    private void renderUsbContent(GuiGraphics gui, Font font, int w, int h) {
        int navWidth = NavBar.getNavBarWidth(w);
        int x = navWidth + 18;

        gui.drawString(font, "Puerto:", x, 80, 0xff212121);
        gui.drawString(font, "Conectado vía USB", x, 110, 0xff4caf50);
    }

    private void renderWifiContent(GuiGraphics gui, Font font, int w, int h) {
        int navWidth = NavBar.getNavBarWidth(w);
        int x = navWidth + 18;

        gui.drawString(font, "Dirección IP:", x, 80, 0xff212121);
        gui.drawString(font, "Conectado vía WIFI", x, 110, 0xff2196f3);
    }
}
