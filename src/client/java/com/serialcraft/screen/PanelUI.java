package com.serialcraft.screen;

import com.serialcraft.SerialCraftClient;
import com.serialcraft.client.ui.NavBar;
import com.serialcraft.client.ui.pages.HomeScreen;
import com.serialcraft.client.ui.pages.WelcomeScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class PanelUI extends Screen {

    public enum AppState { WELCOME, DASHBOARD }
    public enum Tab { HOME, PLACAS, EVENTS }

    public static class DeviceInfo {
        public String nombre;
        public String direccion;
        public String tipo;
        public Runnable accionConectar;

        public DeviceInfo(String n, String d, String t, Runnable accion) {
            this.nombre = n; this.direccion = d; this.tipo = t; this.accionConectar = accion;
        }
    }

    private AppState appState = AppState.WELCOME;
    private Tab currentTab = Tab.HOME;
    private DeviceInfo activeDevice = null;

    private final NavBar navBar = new NavBar();
    private final WelcomeScreen welcomeScreen = new WelcomeScreen();
    private final HomeScreen homeScreen = new HomeScreen();

    public PanelUI() {
        super(Component.literal("PanelUI"));

        if (SerialCraftClient.arduinoPort != null && SerialCraftClient.arduinoPort.isOpen()) {
            this.appState = AppState.DASHBOARD;
            this.activeDevice = new DeviceInfo(
                    "Arduino (Conexión Activa)",
                    SerialCraftClient.arduinoPort.getSystemPortName(),
                    "USB", () -> {}
            );
        }
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets(); // LIMPIEZA TOTAL: Asegura que no queden botones fantasmas

        if (appState == AppState.WELCOME) {
            welcomeScreen.init(this, this.width, this.height);
        } else {
            navBar.init(this, this.width, this.height);

            if (currentTab == Tab.HOME) {
                homeScreen.init(this, this.width, this.height, activeDevice);
            }
        }
    }

    @Override
    public void render(@NotNull GuiGraphics gui, int mouseX, int mouseY, float delta) {
        gui.fill(0, 0, this.width, this.height, 0xFFF3F3F3);

        if (appState == AppState.WELCOME) {
            welcomeScreen.render(gui, mouseX, mouseY, this.font, this.width, this.height);
        } else {
            navBar.render(gui, this.width, this.height);

            if (currentTab == Tab.HOME) {
                homeScreen.render(gui, mouseX, mouseY, this.font, this.width, this.height);
            } else if (currentTab == Tab.PLACAS) {
                renderPlacasContent(gui);
            } else if (currentTab == Tab.EVENTS) {
                renderEventsContent(gui);
            }
        }

        super.render(gui, mouseX, mouseY, delta);
    }

    public void connectDevice(DeviceInfo device) {
        device.accionConectar.run();

        this.appState = AppState.DASHBOARD;
        this.activeDevice = device;
        this.currentTab = Tab.HOME;

        this.init(); // Recargamos la interfaz para mostrar el Dashboard
    }

    public void disconnectDevice() {
        SerialCraftClient.desconectar();
        this.appState = AppState.WELCOME;
        this.activeDevice = null;
        this.init();
    }

    @Override
    public void removed() {
        super.removed();
        welcomeScreen.onClose();
    }

    public void setTab(Tab tab) {
        this.currentTab = tab;
        this.init();
    }

    public <T extends AbstractWidget> void addWidget(T widget) {
        this.addRenderableWidget(widget);
    }

    private void renderPlacasContent(GuiGraphics gui) {
        gui.drawString(this.font, "Gestor de Placas (Próximamente)", 300, 50, 0xff212121, false);
    }
    private void renderEventsContent(GuiGraphics gui) {
        gui.drawString(this.font, "Monitor de Eventos (Próximamente)", 300, 50, 0xff212121, false);
    }
}