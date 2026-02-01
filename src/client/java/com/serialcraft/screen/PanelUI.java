package com.serialcraft.screen;

import com.serialcraft.client.ui.NavBar;
import com.serialcraft.client.ui.pages.HomeScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class PanelUI extends Screen {

    public enum Tab {
        HOME,
        PLACAS,
        EVENTS,
    }

    private final NavBar navBar = new NavBar();
    private final HomeScreen homeScreen = new HomeScreen();

    private Tab currentTab = Tab.HOME;

    public PanelUI() {
        super(Component.literal("PanelUI"));
    }

    @Override
    protected void init() {
        super.init();

        navBar.init(this, this.width, this.height);
        homeScreen.init(this, this.width, this.height);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {

        navBar.render(guiGraphics, this.width, this.height);

        if (currentTab == Tab.HOME) {
            homeScreen.render(guiGraphics, mouseX, mouseY, this.font, this.width, this.height);
        } else if (currentTab == Tab.PLACAS) {
            renderPlacasContent(guiGraphics);
        } else if (currentTab == Tab.EVENTS) {
            renderEventsContent(guiGraphics);
        }

        super.render(guiGraphics, mouseX, mouseY, delta);
    }

    private void renderPlacasContent(GuiGraphics gui) {
        gui.drawString(this.font, "Estás en PLACAS", 300, 50, 0xff212121);
    }

    private void renderEventsContent(GuiGraphics gui) {
        gui.drawString(this.font, "Estás en EVENTOS", 300, 50, 0xff212121);
    }

    // =========================
    // API para componentes
    // =========================

    public void setTab(Tab tab) {
        this.currentTab = tab;

        // ⬇️ Solo HOME muestra botones USB/WIFI
        homeScreen.setVisible(tab == Tab.HOME);
    }

    public <T extends AbstractWidget> void addWidget(T widget) {
        this.addRenderableWidget(widget);
    }
}
