package com.serialcraft.screen;

import com.serialcraft.SerialCraftClient;
import com.serialcraft.client.ui.NavBar;
import com.serialcraft.client.ui.pages.HomeScreen;
import com.serialcraft.client.ui.pages.PlacasScreen;
import com.serialcraft.client.ui.pages.WelcomeScreen;
import com.serialcraft.network.BoardInfo;
import com.serialcraft.network.ConnectorPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
            this.nombre = n; this.direccion = d; this.tipo = t;
            this.plataforma = plat; this.accionConectar = accion;
        }
    }

    // Memoria estática global — sobrevive al cerrar la interfaz
    public static DeviceInfo currentConnectedDevice = null;

    /**
     * Posición del ConnectorBlock que el jugador clickeó para abrir este panel.
     * Usada para enviar ConnectorPayload y actualizar el modelo LIT del bloque.
     * Null si el panel fue abierto sin un bloque asociado (restauración de sesión).
     */
    @Nullable
    private final BlockPos connectorPos;

    private AppState   appState    = AppState.WELCOME;
    private Tab        currentTab  = Tab.HOME;
    private DeviceInfo activeDevice = null;

    private final NavBar        navBar        = new NavBar();
    private final WelcomeScreen welcomeScreen = new WelcomeScreen();
    private final HomeScreen    homeScreen    = new HomeScreen();
    private final PlacasScreen  placasScreen  = new PlacasScreen();

    /**
     * Constructor principal: el jugador clickeó un ConnectorBlock en el mundo.
     * @param connectorPos posición del bloque para sincronizar el modelo LIT.
     */
    public PanelUI(@Nullable BlockPos connectorPos) {
        super(Component.literal("PanelUI"));
        this.connectorPos = connectorPos;
        initAppState();
    }

    /**
     * Constructor sin BlockPos — mantiene compatibilidad con llamadas antiguas
     * y con la restauración de sesión cuando el bloque no es conocido.
     */
    public PanelUI() {
        this(null);
    }

    /**
     * Determina el estado inicial del panel según si hay hardware activo.
     */
    private void initAppState() {
        if (currentConnectedDevice != null) {
            this.appState    = AppState.DASHBOARD;
            this.activeDevice = currentConnectedDevice;
        } else if (SerialCraftClient.arduinoPort != null && SerialCraftClient.arduinoPort.isOpen()) {
            this.appState    = AppState.DASHBOARD;
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
            navBar.init(this, this.width, this.height, currentTab);
            switch (currentTab) {
                case HOME   -> homeScreen.init(this, this.width, this.height, activeDevice);
                case PLACAS -> placasScreen.init(this, this.width, this.height);
                case EVENTS -> {}
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (appState == AppState.WELCOME) {
            welcomeScreen.tick();
        } else {
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

        super.render(gui, mouseX, mouseY, delta);
    }

    // ── Gestión de dispositivos ────────────────────────────────────────────

    /**
     * Conecta un dispositivo y actualiza el modelo del bloque en el mundo (LIT=true).
     * Envía ConnectorPayload al servidor si se conoce la posición del bloque.
     */
    public void connectDevice(DeviceInfo device) {
        device.accionConectar.run();
        currentConnectedDevice = device;
        this.appState          = AppState.DASHBOARD;
        this.activeDevice      = device;
        this.currentTab        = Tab.HOME;

        // ── Sincronizar modelo 3D del bloque: LIT → true ─────────────────
        // ConnectorPayload le dice al servidor que aplique LIT=true en el blockstate,
        // lo que activa el modelo connector_block_on (LED verde encendido).
        if (connectorPos != null) {
            ClientPlayNetworking.send(new ConnectorPayload(connectorPos, true));
        }

        this.init();
    }

    /**
     * Desconecta el hardware activo y regresa a WelcomeScreen.
     * Actualiza el modelo del bloque en el mundo (LIT=false).
     */
    public void disconnectDevice() {
        SerialCraftClient.desconectar();
        currentConnectedDevice = null;
        this.appState          = AppState.WELCOME;
        this.activeDevice      = null;

        // ── Sincronizar modelo 3D del bloque: LIT → false ────────────────
        // El bloque vuelve a su apariencia "apagada" (conector_block sin LED).
        if (connectorPos != null) {
            ClientPlayNetworking.send(new ConnectorPayload(connectorPos, false));
        }

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

    /** Reconstruye la WelcomeScreen sin cambiar de estado. */
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
        int navWidth = NavBar.getNavBarWidth(this.width);
        int cx       = navWidth + 30;

        float scale = 1.8f;
        gui.pose().pushMatrix();
        gui.pose().scale(scale, scale);
        gui.drawString(this.font, "EVENTOS",
                (int)(cx / scale), (int)(22 / scale), 0xFF4CAF50, false);
        gui.drawString(this.font, "MONITOR",
                (int)(cx / scale) + this.font.width("EVENTOS") + 4,
                (int)(22 / scale), 0xFF212121, false);
        gui.pose().popMatrix();

        gui.drawString(this.font, "Monitor de Eventos — Próximamente",
                cx, 60, 0xff757575, false);
    }
}
