package com.serialcraft.client.ui.pages;

import com.serialcraft.screen.PanelUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Contrato de una pagina del panel.
 */
public interface Page {

    /** Crea los widgets. Se llama en cada init() de la pantalla. */
    void init(PanelUI panel, int screenWidth, int screenHeight);

    /** Dibuja el contenido no interactivo. Los widgets se dibujan solos. */
    void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, Font font, int screenWidth, int screenHeight);

    /** Logica por tick. Vacio por defecto: la mayoria de paginas no lo necesita. */
    default void tick() {}

    /**
     * Logica por tick mientras la pagina NO es la visible, con la Laptop abierta.
     * La necesitan las paginas que gobiernan algo en marcha (el generador de
     * Visualizar): sin ella, cambiar de pestana lo dejaria a medias. Vacio por
     * defecto.
     */
    default void backgroundTick() {}

    /**
     * Eventos de desplazamiento del raton (scroll con rueda).
     */
    default boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return false;
    }

    /**
     * Evento de clic de raton (para barras de scroll o areas interactivas personalizadas).
     */
    default boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        return false;
    }

    /**
     * Evento de liberacion de clic de raton.
     */
    default boolean mouseReleased(MouseButtonEvent event) {
        return false;
    }

    /**
     * Evento de arrastre de raton con boton presionado.
     */
    default boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return false;
    }

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
