package com.serialcraft.screen;

import com.serialcraft.client.ui.NavBar;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.client.ui.pages.BoardsPage;
import com.serialcraft.client.ui.pages.EventsPage;
import com.serialcraft.client.ui.pages.HomePage;
import com.serialcraft.client.ui.pages.Page;
import com.serialcraft.client.ui.pages.VisualizePage;
import com.serialcraft.client.ui.pages.WelcomePage;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.connection.WifiHandler;
import com.serialcraft.network.BoardInfo;
import com.serialcraft.network.ConnectorPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Pantalla contenedora del panel.
 */
public class PanelUI extends Screen {

    public enum AppState { WELCOME, DASHBOARD }

    /** El orden del enum define el orden de los botones en la barra lateral. */
    public enum Tab { HOME, BOARDS, EVENTS, VISUALIZE }

    /** Descripcion del dispositivo que el jugador eligio conectar. */
    public record DeviceInfo(String name, String address, String type,
                             String platform, Runnable connectAction) {
        public boolean isWifi() { return "WIFI".equals(type); }
    }

    /** Dispositivo elegido en esta sesion. Se limpia al salir del mundo. */
    private static @Nullable DeviceInfo selectedDevice = null;

    public static @Nullable DeviceInfo getSelectedDevice() {
        if (selectedDevice == null && ConnectionManager.isAnyConnected()) {
            if (ConnectionManager.getWifi().isConnected()) {
                String ip = ConnectionManager.getWifi().getRemoteIp();
                selectedDevice = new DeviceInfo(
                        Component.translatable("gui.serialcraft.welcome.wifi_board", ip).getString(),
                        ip + ":" + WifiHandler.DEFAULT_PORT,
                        "WIFI", "Wi-Fi",
                        () -> {});
            } else if (ConnectionManager.getSerial().isConnected()) {
                String port = ConnectionManager.getSerial().getPortName();
                selectedDevice = new DeviceInfo(
                        port, port, "USB", "Serial",
                        () -> {});
            }
        }
        return selectedDevice;
    }

    public static void clearSelectedDevice() { selectedDevice = null; }

    // ── Estado de instancia ───────────────────────────────────────

    private final @Nullable BlockPos connectorPos;
    private @Nullable BlockPos pendingBoardEditPos;

    private AppState appState  = AppState.WELCOME;
    private Tab      currentTab = Tab.HOME;

    private final NavBar navBar = new NavBar();
    private final WelcomePage welcomePage = new WelcomePage();
    private final Map<Tab, Page> pages = new EnumMap<>(Tab.class);
    private final BoardsPage boardsPage = new BoardsPage();

    // ════════════════════════════════════════════════════════════════════════════

    public PanelUI(@Nullable BlockPos connectorPos, @Nullable BlockPos boardEditPos) {
        super(Component.translatable("gui.serialcraft.panel.title"));
        this.connectorPos        = connectorPos;
        this.pendingBoardEditPos = boardEditPos;

        pages.put(Tab.HOME,      new HomePage());
        pages.put(Tab.BOARDS,    boardsPage);
        pages.put(Tab.EVENTS,    new EventsPage());
        pages.put(Tab.VISUALIZE, new VisualizePage());

        resolveInitialState();
    }

    public PanelUI(@Nullable BlockPos connectorPos) { this(connectorPos, null); }

    public PanelUI() { this(null, null); }

    /**
     * Decide si abrir en bienvenida o en panel.
     */
    private void resolveInitialState() {
        boolean hardwareUp = ConnectionManager.isAnyConnected();

        if (pendingBoardEditPos != null) {
            this.appState   = AppState.DASHBOARD;
            this.currentTab = Tab.BOARDS;
            return;
        }
        if (hardwareUp) {
            this.appState = AppState.DASHBOARD;
            getSelectedDevice(); // Asegurar inicializacion de selectedDevice si ya hay conexion
        } else {
            this.appState = AppState.WELCOME;
            selectedDevice = null; // limpiar estado obsoleto
        }
    }

    // ════════════════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        if (appState == AppState.WELCOME) {
            welcomePage.init(this, this.width, this.height);
            return;
        }

        navBar.init(this, this.width, this.height, currentTab);

        // Consumir la posicion pendiente antes de inicializar, para que al
        // cambiar de pestana no se reabra el editor una y otra vez.
        if (currentTab == Tab.BOARDS && pendingBoardEditPos != null) {
            BlockPos editPos = pendingBoardEditPos;
            pendingBoardEditPos = null;
            boardsPage.requestDirectEdit(editPos);
        }
        pages.get(currentTab).init(this, this.width, this.height);
    }

    @Override
    public void tick() {
        super.tick();
        if (appState == AppState.WELCOME) {
            welcomePage.tick();
        } else {
            pages.get(currentTab).tick();
        }
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        gui.fill(0, 0, this.width, this.height, UiTheme.BG_APP);

        if (appState == AppState.WELCOME) {
            welcomePage.render(gui, mouseX, mouseY, this.font, this.width, this.height);
        } else {
            navBar.render(gui, this.width, this.height);
            pages.get(currentTab).render(gui, mouseX, mouseY, this.font, this.width, this.height);
        }

        super.extractRenderState(gui, mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        super.removed();
        welcomePage.onClose();
        pages.values().forEach(Page::onClose);
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  API para las paginas
    // ════════════════════════════════════════════════════════════════════════════

    public void setTab(Tab tab) {
        if (this.currentTab == tab && appState == AppState.DASHBOARD) {
            this.init();   // refresco explicito
            return;
        }
        this.currentTab = tab;
        this.init();
    }

    public void connectDevice(DeviceInfo device) {
        device.connectAction().run();
        selectedDevice  = device;
        this.appState   = AppState.DASHBOARD;
        this.currentTab = Tab.HOME;

        if (connectorPos != null) {
            ClientPlayNetworking.send(new ConnectorPayload(connectorPos, true));
        }
        this.init();
    }

    public void disconnectDevice() {
        ConnectionManager.disconnectAll();
        selectedDevice = null;
        this.appState  = AppState.WELCOME;

        if (connectorPos != null) {
            ClientPlayNetworking.send(new ConnectorPayload(connectorPos, false));
        }
        this.init();
    }

    /** Reconstruye la bienvenida sin cambiar de estado. */
    public void refreshWelcome() {
        if (appState == AppState.WELCOME) this.init();
    }

    public <T extends AbstractWidget> void addWidget(T widget) {
        this.addRenderableWidget(widget);
    }

    /** Entrega la lista de placas recibida del servidor. */
    public void updateBoardList(List<BoardInfo> boards) {
        boardsPage.acceptBoardList(boards);
    }
}
