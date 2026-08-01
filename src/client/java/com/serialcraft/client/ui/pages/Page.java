package com.serialcraft.client.ui.pages;

import com.serialcraft.screen.PanelUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Contrato de una pagina del panel.
 */
public interface Page {

    /** Crea los widgets. Se llama en cada init() de la pantalla. */
    void init(PanelUI panel, int screenWidth, int screenHeight);

    /** Dibuja el contenido no interactivo. Los widgets se dibujan solos. */
    void render(GuiGraphics gui, int mouseX, int mouseY, Font font, int screenWidth, int screenHeight);

    /** Logica por tick. Vacio por defecto: la mayoria de paginas no lo necesita. */
    default void tick() {}

    /**
     * Libera recursos propios (hilos, timers, sockets).
     *
     * En el original esto era una fuente de fugas: PanelUI.removed() solo
     * llamaba a onClose() de HomeScreen y PlacasScreen si el estado era
     * DASHBOARD. Si el jugador se desconectaba (estado -> WELCOME) y luego
     * cerraba la pantalla, el Timer de latencia de HomeScreen quedaba vivo para
     * siempre, un hilo por cada vez que se abriera el panel.
     */
    default void onClose() {}
}
