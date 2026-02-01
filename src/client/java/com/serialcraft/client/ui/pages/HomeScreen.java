package com.serialcraft.client.ui.pages;

import com.fazecast.jSerialComm.SerialPort;
import com.serialcraft.SerialCraftClient;
import com.serialcraft.client.ui.NavBar;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class HomeScreen {

    public enum ConnectionMode {
        USB,
        WIFI
    }

    // =========================
    // ESTADO
    // =========================

    private ConnectionMode mode = ConnectionMode.USB;

    // Referencias a los widgets principales
    private IconTextButton usbButton;
    private IconTextButton wifiButton;

    // --- Variables para el Dropdown y Acciones ---
    private boolean isDropdownOpen = false;
    private String selectedPort = "Seleccionar...";

    private IconTextButton dropdownMainBtn;
    private IconTextButton connectBtn;
    private IconTextButton disconnectBtn; // Nuevo botón

    // Pool de botones para la lista desplegable
    private final List<IconTextButton> portListButtons = new ArrayList<>();
    private final int MAX_VISIBLE_PORTS = 5;

    // --- Variables de Layout (Calculadas en init) ---
    private int layoutLabelY;    // Donde va el texto "Puerto:"
    private int layoutControlsY; // Donde va el botón de selección
    private int layoutActionsY;  // Donde van los botones Conectar/Desconectar
    // -----------------------------------------

    public void setMode(ConnectionMode mode) {
        this.mode = mode;
        updateVisibility();
    }

    public ConnectionMode getMode() {
        return mode;
    }

    // =========================
    // INIT
    // =========================

    public void init(PanelUI panel, int screenWidth, int screenHeight) {

        // 1. Botones de Modo (USB/WIFI) - Tu código original intacto
        int btnModeWidth = (screenWidth * 7) / 100;
        int btnModeHeight = (btnModeWidth / 10) * 4;
        int gap = 6;
        int modeY = 22; // Alineado con el título visualmente

        int xWifi = screenWidth - btnModeWidth - 18;
        int xUsb = xWifi - btnModeWidth - gap;

        this.usbButton = new IconTextButton(
                xUsb, modeY, btnModeWidth, btnModeHeight,
                SpriteIcon.USB, Component.literal("USB"),
                (btn) -> setMode(ConnectionMode.USB),
                0xff424242, 0xff212121, 0xffffffff
        );

        this.wifiButton = new IconTextButton(
                xWifi, modeY, btnModeWidth, btnModeHeight,
                SpriteIcon.WIFI, Component.literal("WIFI"),
                (btn) -> setMode(ConnectionMode.WIFI),
                0xff424242, 0xff212121, 0xffffffff
        );

        panel.addWidget(this.usbButton);
        panel.addWidget(this.wifiButton);


        // 2. CÁLCULO DE LAYOUT VERTICAL
        // -----------------------------------------------------
        // Título Y: 22
        // Altura Título (Escala 1.8): 9px * 1.8 ≈ 16px.
        // Margen deseado: 22px.

        int titleY = 22;
        int titleHeight = 16;
        int margin = 22;

        // Aquí empieza "Puerto:"
        this.layoutLabelY = titleY + titleHeight + margin;

        // El Dropdown empieza un poco más abajo del texto "Puerto:"
        this.layoutControlsY = layoutLabelY + 12;

        // Los botones de acción (Conectar/Desconectar) van debajo del Dropdown
        // Altura del dropdown (27) + un margen (10)
        this.layoutActionsY = layoutControlsY + 27 + 10;
        // -----------------------------------------------------

        // 3. Inicializar widgets del contenido USB
        initUsbWidgets(panel, screenWidth);

        // Ajustar visibilidad inicial
        setVisible(true);
    }

    private void initUsbWidgets(PanelUI panel, int width) {
        int navWidth = NavBar.getNavBarWidth(width);
        int x = navWidth + 22; // Margen izquierdo estándar

        // --- 1. Dropdown (Selector de Puerto) ---
        this.dropdownMainBtn = new IconTextButton(
                x, layoutControlsY, 220, 27,
                null,
                Component.literal("Click aquí para seleccionar"),
                (btn) -> toggleDropdown(),
                0xffededed, 0xffdddddd, 0xff8d8d8d
        );
        panel.addWidget(this.dropdownMainBtn);

        // --- 2. Botón Conectar ---
        this.connectBtn = new IconTextButton(
                x, layoutActionsY, 85, 24,
                SpriteIcon.CONNECT,
                Component.literal("Conectar"),
                (btn) -> handleConnect(),
                0xff4bad00, 0xff1e9400, 0xffffffff
        );
        panel.addWidget(this.connectBtn);

        // --- 3. Botón Desconectar (AL LADO) ---
        int disconnectX = x + 85 + 6; // x + ancho_boton_anterior + gap

        this.disconnectBtn = new IconTextButton(
                disconnectX, layoutActionsY, 100, 24,
                SpriteIcon.DISCONNECT,
                Component.literal("Desconectar"),
                (btn) -> handleDisconnect(),
                0xffe91e63, 0xffba184f, // Rojo para desconectar
                0xffffffff
        );
        panel.addWidget(this.disconnectBtn);

        // --- 4. Pool de botones ocultos para la lista (Dropdown Items) ---
        // Se renderizan visualmente SOBRE los otros botones cuando se activan
        for (int i = 0; i < MAX_VISIBLE_PORTS; i++) {
            IconTextButton btn = new IconTextButton(
                    x, layoutControlsY + 26 + (i * 22), 220, 25,
                    null,
                    Component.literal(""),
                    (b) -> selectPort(b.getMessage().getString()),
                    0xffededed, 0xffdddddd, 0xff9e9e9e
            );
            btn.visible = false;
            portListButtons.add(btn);
            panel.addWidget(btn);
        }
    }

    // =========================
    // LÓGICA
    // =========================

    private void toggleDropdown() {
        isDropdownOpen = !isDropdownOpen;
        if (isDropdownOpen) refreshPortList();
        updateVisibility();
    }

    private void refreshPortList() {
        SerialPort[] ports = SerialPort.getCommPorts();
        for (int i = 0; i < MAX_VISIBLE_PORTS; i++) {
            IconTextButton btn = portListButtons.get(i);
            if (i < ports.length) {
                btn.setMessage(Component.literal(ports[i].getSystemPortName()));
            } else {
                btn.setMessage(Component.literal("---"));
            }
        }
    }

    private void selectPort(String port) {
        if (port.equals("---")) return;
        this.selectedPort = port;
        this.dropdownMainBtn.setMessage(Component.literal("Puerto seleccionado: " + port));
        this.isDropdownOpen = false;
        updateVisibility();
    }

    private void handleConnect() {
        if (!selectedPort.equals("Seleccionar...") && !selectedPort.equals("---")) {
            // Asumiendo 9600 baudios por defecto
            SerialCraftClient.conectar(selectedPort, 9600);
        }
    }

    private void handleDisconnect() {
        SerialCraftClient.desconectar();
    }

    // =========================
    // VISIBILIDAD
    // =========================

    public void setVisible(boolean visible) {
        if (usbButton != null) usbButton.visible = visible;
        if (wifiButton != null) wifiButton.visible = visible;

        if (visible) {
            updateVisibility();
        } else {
            setUsbControlsVisible(false, false);
        }
    }

    private void updateVisibility() {
        boolean showUsbControls = (mode == ConnectionMode.USB);
        setUsbControlsVisible(showUsbControls, isDropdownOpen);
    }

    private void setUsbControlsVisible(boolean showControls, boolean showList) {
        if (dropdownMainBtn != null) dropdownMainBtn.visible = showControls;
        if (connectBtn != null) connectBtn.visible = showControls;
        if (disconnectBtn != null) disconnectBtn.visible = showControls;

        // Lógica de la lista desplegable
        boolean showListFinal = showControls && showList;
        SerialPort[] ports = SerialPort.getCommPorts();

        for (int i = 0; i < portListButtons.size(); i++) {
            boolean hasPort = i < ports.length;
            portListButtons.get(i).visible = showListFinal && hasPort;
        }
    }

    // =========================
    // RENDER
    // =========================

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, Font font, int width, int height) {
        renderTitle(guiGraphics, font, width);

        if (mode == ConnectionMode.USB) {
            renderUsbContent(guiGraphics, font, width, height);
        } else {
            renderWifiContent(guiGraphics, font, width, height);
        }

    }

    private void renderTitle(GuiGraphics guiGraphics, Font font, int width) {
        int navWidth = NavBar.getNavBarWidth(width);
        int baseX = navWidth + 22;
        int baseY = 22;

        float scale = 1.8f;
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(scale, scale);

        int x = (int) (baseX / scale);
        int y = (int) (baseY / scale);

        String left = "INICIO";
        String right = "CONECTA TU HARDWARE";

        guiGraphics.drawString(font, left, x, y, 0xFFE91E63, false);
        guiGraphics.drawString(font, right, x + font.width(left) + 4, y, 0xFF000000, false);

        guiGraphics.pose().popMatrix();
    }

    private void renderUsbContent(GuiGraphics gui, Font font, int w, int h) {
        int navWidth = NavBar.getNavBarWidth(w);
        int x = navWidth + 22;

        // 1. Etiqueta "Puerto:" (Usando la coordenada calculada)
        gui.drawString(font, "Puerto:", x, this.layoutLabelY, 0xff212121, false);

        // 2. Estado (Al lado de los botones de acción)
        // Botón conectar ancho ~85, Desconectar ~95, Gap ~6 -> Total ~186
        // Empezamos a dibujar el estado después del botón desconectar
        int statusX = x + 85 + 6 + 95 + 12;

        // Centrado verticalmente con los botones (altura botón 24, altura fuente 9)
        // 24 / 2 = 12 (centro botón)
        // 9 / 2 = 4.5 (centro texto)
        // Offset = 12 - 4.5 ≈ 7-8px
        int statusY = this.layoutActionsY + 8;

        if (SerialCraftClient.arduinoPort != null && SerialCraftClient.arduinoPort.isOpen()) {
            gui.drawString(font, "Conexión establecida al puerto seleccionado.", statusX, statusY, 0xff4caf50, false);
        } else {
            gui.drawString(font, "Conexión no establecida, esperando selección.", statusX, statusY, 0xffff5252, false);
        }
        gui.fill(x,0, 2, 20, 0xff272727);
    }

    private void renderWifiContent(GuiGraphics gui, Font font, int w, int h) {
        int navWidth = NavBar.getNavBarWidth(w);
        int x = navWidth + 22;

        // Usamos las mismas coordenadas base para consistencia
        gui.drawString(font, "Dirección IP:", x, this.layoutLabelY, 0xff212121, false);
        gui.drawString(font, "Conectado vía WIFI (Próximamente)", x, this.layoutControlsY, 0xff2196f3, false);
    }
}