package com.serialcraft.screen;

import com.serialcraft.SerialCraftClient;
import com.serialcraft.client.ui.NavBar;
import com.serialcraft.client.ui.pages.HomeScreen;
import com.serialcraft.client.ui.pages.PlacasScreen;
import com.serialcraft.client.ui.pages.WelcomeScreen;
import com.serialcraft.network.BoardInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PanelUI extends Screen {

    public enum AppState { WELCOME, DASHBOARD }
    public enum Tab { HOME, PLACAS, EVENTS }

    public static class DeviceInfo {
        public String nombre;
        public String direccion;
        public String tipo;
        public String plataforma;
        public Runnable accionConectar;

        public DeviceInfo(String n, String d, String t, String plat, Runnable accion) {
            this.nombre = n; this.direccion = d; this.tipo = t; this.plataforma = plat; this.accionConectar = accion;
        }
    }

    // Memoria estática global — sobrevive al cerrar la interfaz
    public static DeviceInfo currentConnectedDevice = null;

    private AppState   appState   = AppState.WELCOME;
    private Tab        currentTab = Tab.HOME;
    private DeviceInfo activeDevice = null;

    private final NavBar        navBar        = new NavBar();
    private final WelcomeScreen welcomeScreen = new WelcomeScreen();
    private final HomeScreen    homeScreen    = new HomeScreen();
    private final PlacasScreen  placasScreen  = new PlacasScreen();

    public PanelUI() {
        super(Component.literal("PanelUI"));

        if (currentConnectedDevice != null) {
            this.appState     = AppState.DASHBOARD;
            this.activeDevice = currentConnectedDevice;
        } else if (SerialCraftClient.arduinoPort != null && SerialCraftClient.arduinoPort.isOpen()) {
            this.appState     = AppState.DASHBOARD;
            this.activeDevice = new DeviceInfo(
                    "Arduino (Conexión Activa)",
                    SerialCraftClient.arduinoPort.getSystemPortName(),
                    "USB", "Arduino", () -> {}
            );
            currentConnectedDevice = this.activeDevice;
        }
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        if (appState == AppState.WELCOME) {
            welcomeScreen.init(this, this.width, this.height);
        } else {
            navBar.init(this, this.width, this.height);
            switch (currentTab) {
                case HOME   -> homeScreen.init(this, this.width, this.height, activeDevice);
                case PLACAS -> placasScreen.init(this, this.width, this.height);
                case EVENTS -> {} // sin widgets por ahora
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (appState == AppState.WELCOME) {
            welcomeScreen.tick();
        } else {
            // tick del dashboard — placasScreen necesita tick() para procesar
            // la respuesta del servidor SIN causar loops desde render()
            placasScreen.tick();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics gui, int mouseX, int mouseY, float delta) {
        gui.fill(0, 0, this.width, this.height, 0xFFF3F3F3);

        if (appState == AppState.WELCOME) {
            welcomeScreen.render(gui, mouseX, mouseY, this.font, this.width, this.height);
        } else {
            navBar.render(gui, this.width, this.height);
            switch (currentTab) {
                case HOME   -> homeScreen.render(gui, mouseX, mouseY, this.font, this.width, this.height);
                case PLACAS -> placasScreen.render(gui, mouseX, mouseY, this.font, this.width, this.height);
                case EVENTS -> renderEventsContent(gui);
            }
        }

        // super.render dibuja todos los widgets registrados (EditBox, SolidButton, etc.)
        super.render(gui, mouseX, mouseY, delta);
    }

    // ── Gestión de dispositivos ────────────────────────────────────────────

    public void connectDevice(DeviceInfo device) {
        device.accionConectar.run();
        currentConnectedDevice = device;
        this.appState          = AppState.DASHBOARD;
        this.activeDevice      = device;
        this.currentTab        = Tab.HOME;
        this.init();
    }

    public void disconnectDevice() {
        SerialCraftClient.desconectar();
        currentConnectedDevice = null;
        this.appState          = AppState.WELCOME;
        this.activeDevice      = null;
        this.init();
    }

    @Override
    public void removed() {
        super.removed();
        welcomeScreen.onClose();
        if (appState == AppState.DASHBOARD) {
            homeScreen.onClose();
            placasScreen.onClose();
        }
    }

    // ── API para páginas ──────────────────────────────────────────────────

    public void setTab(Tab tab) {
        this.currentTab = tab;
        this.init();
    }

    /** Reconstruye la WelcomeScreen (botón Wi-Fi, tarjetas USB) sin cambiar de estado. */
    public void rebuildWelcome() {
        if (appState == AppState.WELCOME) this.init();
    }

    public <T extends AbstractWidget> void addWidget(T widget) {
        this.addRenderableWidget(widget);
    }

    /**
     * Recibe la lista de placas IO desde SerialCraftClient y la delega a PlacasScreen.
     * PlacasScreen la procesará en su próximo tick(), en el hilo principal.
     */
    public void updatePlacasList(List<BoardInfo> boards) {
        placasScreen.updateBoardList(boards);
    }

    // ── Contenido placeholder ─────────────────────────────────────────────

    private void renderEventsContent(GuiGraphics gui) {
        gui.drawString(this.font, "Monitor de Eventos (Próximamente)", 300, 50, 0xff212121, false);
    }
}