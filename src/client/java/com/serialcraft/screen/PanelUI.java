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

    /**
     * Posición de un ArduinoIOBlock para abrir el editor de PlacasScreen
     * directamente al inicializar el panel. Se consume tras el primer init()
     * para no re-activar el editor al cambiar de pestaña.
     */
    @Nullable
    private BlockPos directIoEditPos;

    private AppState   appState    = AppState.WELCOME;
    private Tab        currentTab  = Tab.HOME;
    private DeviceInfo activeDevice = null;

    private final NavBar        navBar        = new NavBar();
    private final WelcomeScreen welcomeScreen = new WelcomeScreen();
    private final HomeScreen    homeScreen    = new HomeScreen();
    private final PlacasScreen  placasScreen  = new PlacasScreen();

    // ── Constructores ─────────────────────────────────────────────────────

    /**
     * Constructor completo. Permite abrir el panel desde un ConnectorBlock
     * y/o ir directamente al editor de un ArduinoIOBlock.
     *
     * @param connectorPos posición del ConnectorBlock (puede ser null).
     * @param ioEditPos    posición del ArduinoIOBlock a editar (puede ser null).
     */
    public PanelUI(@Nullable BlockPos connectorPos, @Nullable BlockPos ioEditPos) {
        super(Component.literal("PanelUI"));
        this.connectorPos   = connectorPos;
        this.directIoEditPos = ioEditPos;
        initAppState();
    }

    /**
     * Abre el panel desde un ConnectorBlock sin apuntar a ningún IO block.
     */
    public PanelUI(@Nullable BlockPos connectorPos) {
        this(connectorPos, null);
    }

    /**
     * Constructor sin BlockPos — compatibilidad con llamadas antiguas
     * y restauración de sesión cuando el bloque no es conocido.
     */
    public PanelUI() {
        this(null, null);
    }

    // ── Estado inicial ────────────────────────────────────────────────────

    /**
     * Determina el estado inicial del panel según si hay hardware activo
     * o si se abrió directamente para editar un IO block.
     */
    private void initAppState() {
        // Apertura directa desde un ArduinoIOBlock → dashboard + pestaña Placas
        if (directIoEditPos != null) {
            this.appState     = AppState.DASHBOARD;
            this.currentTab   = Tab.PLACAS;
            this.activeDevice = currentConnectedDevice; // puede ser null, es aceptable
            return;
        }

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
                case PLACAS -> {
                    // Pasa el directIoEditPos y lo consume para no re-activar el editor
                    // en siguientes llamadas a init() (p.ej. al cambiar de pestaña).
                    BlockPos editPos = this.directIoEditPos;
                    this.directIoEditPos = null;
                    placasScreen.init(this, this.width, this.height, editPos);
                }
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