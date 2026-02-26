package com.serialcraft.client.ui.pages;

import com.serialcraft.client.ui.NavBar;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class HomeScreen {

    private PanelUI.DeviceInfo activeDevice;
    private int layoutStartY;

    public void init(PanelUI panel, int screenWidth, int screenHeight, PanelUI.DeviceInfo device) {
        this.activeDevice = device;

        int navWidth = NavBar.getNavBarWidth(screenWidth);
        int x = navWidth + 30; // Margen izquierdo después del NavBar
        this.layoutStartY = 70; // Espacio debajo del título

        // Botón rojo grande de desconexión
        IconTextButton disconnectBtn = new IconTextButton(
                x, layoutStartY + 80, 130, 26, SpriteIcon.DISCONNECT,
                Component.literal("Desconectar Placa"),
                (btn) -> panel.disconnectDevice(), // ¡Le dice a PanelUI que vuelva a WelcomeScreen!
                0xffe91e63, 0xffba184f, 0xffffffff
        );
        panel.addWidget(disconnectBtn);
    }

    public void render(GuiGraphics gui, int mouseX, int mouseY, Font font, int width, int height) {
        int navWidth = NavBar.getNavBarWidth(width);
        int x = navWidth + 30;

        // Título del Dashboard
        float scale = 1.8f;
        gui.pose().pushMatrix();
        gui.pose().scale(scale, scale);
        gui.drawString(font, "INICIO", (int) (x / scale), (int) (22 / scale), 0xFFE91E63, false);
        gui.drawString(font, "PANEL DE CONTROL", (int) (x / scale) + font.width("INICIO") + 4, (int) (22 / scale), 0xFF000000, false);
        gui.pose().popMatrix();

        // Tarjeta de estado (Dashboard Card)
        if (activeDevice != null) {
            gui.fill(x, layoutStartY, x + 350, layoutStartY + 60, 0xffffffff); // Tarjeta blanca
            gui.fill(x, layoutStartY + 60, x + 350, layoutStartY + 62, 0xffe0e0e0); // Sombra inferior

            // Indicador LED verde de conexión
            gui.fill(x + 15, layoutStartY + 25, x + 25, layoutStartY + 35, 0xff4caf50);

            gui.drawString(font, "Dispositivo Conectado Exitosamente:", x + 35, layoutStartY + 15, 0xff757575, false);
            gui.drawString(font, activeDevice.nombre, x + 35, layoutStartY + 28, 0xff212121, false);
            gui.drawString(font, "Vía: " + activeDevice.tipo + " | Dirección: " + activeDevice.direccion, x + 35, layoutStartY + 42, 0xff1976d2, false);
        }
    }
}