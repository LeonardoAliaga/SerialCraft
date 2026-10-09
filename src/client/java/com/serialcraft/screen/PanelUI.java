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
import com.serialcraft.connection.ConnectionResult;
import com.serialcraft.network.ConfigResultPayload;
import com.serialcraft.connection.WifiHandler;
import com.serialcraft.network.BoardInfo;
import com.serialcraft.network.ConnectorPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Pantalla principal de la Laptop.
 */
public class PanelUI extends Screen {

    public enum AppState { WELCOME, DASHBOARD }

    public enum Tab { HOME, BOARDS, EVENTS, VISUALIZE }

    /** Descripcion del dispositivo que el jugador eligio conectar. */
    public record DeviceInfo(String name, String address, String type,
                             String platform, Supplier<ConnectionResult> connectAction) {
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
                        () -> new ConnectionResult(ConnectionManager.getWifi().isConnected(), Component.empty()));
            } else if (ConnectionManager.getSerial().isConnected()) {
                String port = ConnectionManager.getSerial().getPortName();
                selectedDevice = new DeviceInfo(
                        port, port, "USB", "Serial",
                        () -> new ConnectionResult(ConnectionManager.getSerial().isConnected(), Component.empty()));
            }
        }
        return selectedDevice;
    }

    public static void clearSelectedDevice() { selectedDevice = null; }

    // ── Estado de instancia ──────────────────────────────────────────────────

    private final @Nullable BlockPos connectorPos;
    private @Nullable BlockPos pendingBoardEditPos;

    private AppState appState  = AppState.WELCOME;
    private Tab      currentTab = Tab.HOME;

    private final NavBar navBar = new NavBar();
    private final WelcomePage welcomePage = new WelcomePage();
    private final Map<Tab, Page> pages = new EnumMap<>(Tab.class);
    private final BoardsPage boardsPage = new BoardsPage();

    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        clearFocus();

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
    protected void setInitialFocus() {
        // Screen.init otherwise replaces the editor's intended focus with the first nav button.
        if (getFocused() == null) super.setInitialFocus();
    }

    @Override
    public void tick() {
        super.tick();
        Page visible = null;
        if (appState == AppState.WELCOME) {
            welcomePage.tick();
        } else {
            visible = pages.get(currentTab);
            visible.tick();
        }
        for (Page page : pages.values()) {
            if (page != visible) page.backgroundTick();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Page page = (appState == AppState.WELCOME) ? welcomePage : pages.get(currentTab);
        if (page != null && page.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        Page page = (appState == AppState.WELCOME) ? welcomePage : pages.get(currentTab);
        if (page != null && page.mouseClicked(event, focused)) {
            return true;
        }
        boolean handled = super.mouseClicked(event, focused);
        if (page != null) page.afterKey();
        return handled;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        Page page = (appState == AppState.WELCOME) ? welcomePage : pages.get(currentTab);
        if (page != null && page.mouseReleased(event)) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        Page page = (appState == AppState.WELCOME) ? welcomePage : pages.get(currentTab);
        if (page != null && page.mouseDragged(event, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        Page page = appState == AppState.WELCOME ? welcomePage : pages.get(currentTab);
        if (page.keyPressed(event)) return true;
        boolean handled = super.keyPressed(event);
        page.afterKey();
        return handled;
    }

    @Override
    public void onClose() {
        if (appState == AppState.DASHBOARD && currentTab == Tab.BOARDS)
            boardsPage.requestClosePanel(() -> super.onClose());
        else super.onClose();
    }

    /**
     * La Laptop NO pausa el juego en un mundo de un jugador: el tiempo del mundo
     * sigue corriendo (y con el la telemetria de hora, clima y salud que llega a
     * la placa) mientras se usa. Con el valor por defecto (true) el mundo se
     * congelaba al abrirla. Ojo: con la Laptop abierta el jugador sigue en el
     * mundo y puede recibir dano.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
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
        if (appState == AppState.DASHBOARD)
            pages.get(currentTab).renderOverlay(gui, mouseX, mouseY, font, width, height);
    }

    @Override
    public void removed() {
        super.removed();
        welcomePage.onClose();
        pages.values().forEach(Page::onClose);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  API para las paginas
    // ─────────────────────────────────────────────────────────────────────────

    public Tab getCurrentTab() { return currentTab; }

    public void refresh() { this.init(); }

    public void setTab(Tab tab) {
        if (this.currentTab == tab && appState == AppState.DASHBOARD) {
            this.init();   // refresco explicito
            return;
        }
        this.currentTab = tab;
        this.init();
    }

    public void connectDevice(DeviceInfo device) {
        ConnectionResult result = device.connectAction().get();
        if (minecraft.player != null) minecraft.player.sendSystemMessage(result.message());
        if (!result.connected() || !ConnectionManager.isAnyConnected()) return;
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

    /** Widgets painted by a clipped page or a modal, while retaining native input/narration. */
    public <T extends AbstractWidget> void addInputWidget(T widget) { super.addWidget(widget); }
    public void clearUiWidgets() { clearWidgets(); }

    /** Entrega la lista de placas recibida del servidor. */
    public void updateBoardList(List<BoardInfo> boards) {
        boardsPage.acceptBoardList(boards);
    }

    public void updateConfigResult(ConfigResultPayload result) { boardsPage.acceptConfigResult(result); }
}
