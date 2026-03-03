package com.serialcraft.client.ui.pages;

import com.serialcraft.SerialCraftClient;
import com.serialcraft.client.ui.NavBar;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

public class HomeScreen {

    private PanelUI.DeviceInfo activeDevice;
    private int layoutStartY;

    private volatile int currentPingMs = -1;
    private Timer pingTimer;

    public void init(PanelUI panel, int screenWidth, int screenHeight, PanelUI.DeviceInfo device) {
        this.activeDevice = device;
        this.currentPingMs = -1;

        int navWidth = NavBar.getNavBarWidth(screenWidth);
        int leftX = navWidth + 30;
        int rightX = leftX + 270; // Posición de la Columna Derecha (ahora más limpia)
        this.layoutStartY = 70;

        // Botón rojo grande de desconexión (Lo subimos ya que es el único botón ahora)
        IconTextButton disconnectBtn = new IconTextButton(
                rightX, layoutStartY, 140, 26, SpriteIcon.DISCONNECT,
                Component.literal("Desconectar Placa"),
                (btn) -> panel.disconnectDevice(),
                0xffe91e63, 0xffba184f, 0xffffffff
        );
        panel.addWidget(disconnectBtn);

        // Iniciamos el ping en segundo plano si es WIFI
        if (activeDevice != null && activeDevice.tipo.equals("WIFI")) {
            iniciarMedidorLatencia();
        }
    }

    private void iniciarMedidorLatencia() {
        detenerPing();
        pingTimer = new Timer(true);

        String[] partes = activeDevice.direccion.split(":");
        if (partes.length < 2) return;

        String ip = partes[0];
        int puerto = Integer.parseInt(partes[1]);

        pingTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                long tiempoInicio = System.currentTimeMillis();
                try {
                    if (InetAddress.getByName(ip).isReachable(1000)) {
                        currentPingMs = (int) (System.currentTimeMillis() - tiempoInicio);
                        return;
                    }
                } catch (Exception ignored) {}

                tiempoInicio = System.currentTimeMillis();
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(ip, puerto), 1000);
                    currentPingMs = (int) (System.currentTimeMillis() - tiempoInicio);
                } catch (java.net.ConnectException e) {
                    currentPingMs = (int) (System.currentTimeMillis() - tiempoInicio);
                } catch (Exception e) {
                    currentPingMs = 999;
                }
            }
        }, 0, 2000);
    }

    private void detenerPing() {
        if (pingTimer != null) {
            pingTimer.cancel();
            pingTimer = null;
        }
    }

    public void onClose() {
        detenerPing();
    }

    public void render(GuiGraphics gui, int mouseX, int mouseY, Font font, int width, int height) {
        int navWidth = NavBar.getNavBarWidth(width);
        int x = navWidth + 30;

        float scale = 1.8f;
        gui.pose().pushMatrix();
        gui.pose().scale(scale, scale);
        gui.drawString(font, "INICIO", (int) (x / scale), (int) (22 / scale), 0xFFE91E63, false);
        gui.drawString(font, "PANEL DE CONTROL", (int) (x / scale) + font.width("INICIO") + 4, (int) (22 / scale), 0xFF000000, false);
        gui.pose().popMatrix();

        if (activeDevice != null) {
            // ==========================================
            // TARJETA DE ESTADO (Limpia)
            // ==========================================
            gui.fill(x, layoutStartY, x + 250, layoutStartY + 80, 0xffffffff);
            gui.fill(x, layoutStartY + 80, x + 250, layoutStartY + 82, 0xffe0e0e0);

            // Indicador LED verde
            gui.fill(x + 12, layoutStartY + 14, x + 18, layoutStartY + 20, 0xff4caf50);

            gui.drawString(font, "Hardware Activo:", x + 25, layoutStartY + 15, 0xff757575, false);
            gui.drawString(font, activeDevice.nombre, x + 25, layoutStartY + 28, 0xff212121, false);

            // Badge de Plataforma
            gui.fill(x + 160, layoutStartY + 26, x + 240, layoutStartY + 38, 0xffe3f2fd);
            gui.drawString(font, activeDevice.plataforma, x + 165, layoutStartY + 29, 0xff1565c0, false);

            gui.drawString(font, "Vía: " + activeDevice.tipo + " | " + activeDevice.direccion, x + 25, layoutStartY + 42, 0xff1976d2, false);

            // Latencia
            if (activeDevice.tipo.equals("WIFI")) {
                String textoPing = currentPingMs == -1 ? "Midiendo red..." : (currentPingMs == 999 ? "Desconectado" : currentPingMs + " ms");
                int colorPing = currentPingMs <= 50 ? 0xff4caf50 : (currentPingMs <= 150 ? 0xffffc107 : 0xffff5252);
                gui.drawString(font, "Ping: " + textoPing, x + 25, layoutStartY + 58, colorPing, false);
            } else {
                gui.drawString(font, "Ping: < 1 ms (Local)", x + 25, layoutStartY + 58, 0xff4caf50, false);
            }
        }
    }
}