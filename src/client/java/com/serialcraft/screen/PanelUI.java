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
        public String plataforma; // ¡NUEVO! Identificador de hardware
        public Runnable accionConectar;

        public DeviceInfo(String n, String d, String t, String plat, Runnable accion) {
            this.nombre = n; this.direccion = d; this.tipo = t; this.plataforma = plat; this.accionConectar = accion;
        }
    }

    // ¡MEMORIA ESTÁTICA GLOBAL! Sobrevive al cerrar la interfaz
    public static DeviceInfo currentConnectedDevice = null;

    private AppState appState = AppState.WELCOME;
    private Tab currentTab = Tab.HOME;
    private DeviceInfo activeDevice = null;

    private final NavBar navBar = new NavBar();
    private final WelcomeScreen welcomeScreen = new WelcomeScreen();
    private final HomeScreen homeScreen = new HomeScreen();

    public PanelUI() {
        super(Component.literal("PanelUI"));

        // Recuperar conexión activa de Wi-Fi o USB
        if (currentConnectedDevice != null) {
            this.appState = AppState.DASHBOARD;
            this.activeDevice = currentConnectedDevice;
        } else if (SerialCraftClient.arduinoPort != null && SerialCraftClient.arduinoPort.isOpen()) {
            // Rescate por defecto para USB antiguo
            this.appState = AppState.DASHBOARD;
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
            if (currentTab == Tab.HOME) {
                homeScreen.init(this, this.width, this.height, activeDevice);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (appState == AppState.WELCOME) {
            welcomeScreen.tick();
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

        currentConnectedDevice = device; // Guardamos en la memoria estática
        this.appState = AppState.DASHBOARD;
        this.activeDevice = device;
        this.currentTab = Tab.HOME;

        this.init();
    }

    public void disconnectDevice() {
        SerialCraftClient.desconectar();
        currentConnectedDevice = null; // Borramos la memoria
        this.appState = AppState.WELCOME;
        this.activeDevice = null;
        this.init();
    }

    @Override
    public void removed() {
        super.removed();
        welcomeScreen.onClose();
        if (appState == AppState.DASHBOARD) {
            homeScreen.onClose(); // Detener el ping al cerrar UI para no gastar recursos
        }
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