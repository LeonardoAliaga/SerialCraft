package com.serialcraft.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Gestor de desplazamiento vertical (scroll) reutilizable para páginas del panel.
 *
 * Soporta rueda del ratón, arrastre del pulgar de la barra de desplazamiento,
 * fijación de límites (clamping) y renderizado de la barra.
 */
public class ScrollState {

    private double scrollAmount = 0.0;
    private int contentHeight = 0;
    private int viewportHeight = 0;

    private boolean isDragging = false;
    private double dragStartY = 0;
    private double dragStartScroll = 0;

    /**
     * Actualiza las dimensiones visibles y totales del contenido.
     */
    public void update(int viewportHeight, int contentHeight) {
        this.viewportHeight = Math.max(1, viewportHeight);
        this.contentHeight  = Math.max(0, contentHeight);
        clamp();
    }

    public void clamp() {
        double max = getMaxScroll();
        if (scrollAmount > max) scrollAmount = max;
        if (scrollAmount < 0) scrollAmount = 0;
    }

    public double getMaxScroll() {
        return Math.max(0, contentHeight - viewportHeight);
    }

    public double getScrollAmount() {
        return scrollAmount;
    }

    public void setScrollAmount(double val) {
        this.scrollAmount = val;
        clamp();
    }

    public boolean hasScroll() {
        return getMaxScroll() > 0;
    }

    /**
     * Procesa la rueda del ratón.
     * @return true si consumió el evento
     */
    public boolean mouseScrolled(double verticalAmount) {
        if (!hasScroll()) return false;
        double prev = scrollAmount;
        setScrollAmount(scrollAmount - verticalAmount * 24.0);
        return scrollAmount != prev;
    }

    /**
     * Inicia el arrastre de la barra si el clic cayó dentro de la pista.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int trackX, int trackY, int trackW, int trackH) {
        if (button != 0 || !hasScroll()) return false;
        if (mouseX >= trackX && mouseX <= trackX + trackW && mouseY >= trackY && mouseY <= trackY + trackH) {
            int thumbH = getThumbHeight(trackH);
            int thumbY = getThumbY(trackY, trackH);
            if (mouseY < thumbY) {
                // Clic por encima del pulgar: avanzar una página arriba
                setScrollAmount(scrollAmount - viewportHeight * 0.8);
            } else if (mouseY > thumbY + thumbH) {
                // Clic por debajo del pulgar: avanzar una página abajo
                setScrollAmount(scrollAmount + viewportHeight * 0.8);
            } else {
                isDragging = true;
                dragStartY = mouseY;
                dragStartScroll = scrollAmount;
            }
            return true;
        }
        return false;
    }

    public boolean mouseReleased(int button) {
        if (button == 0 && isDragging) {
            isDragging = false;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseY, int trackY, int trackH) {
        if (!isDragging || !hasScroll()) return false;
        double deltaY = mouseY - dragStartY;
        int thumbH = getThumbHeight(trackH);
        int trackAvailable = trackH - thumbH;
        if (trackAvailable > 0) {
            double scrollDelta = deltaY * (getMaxScroll() / (double) trackAvailable);
            setScrollAmount(dragStartScroll + scrollDelta);
        }
        return true;
    }

    public int getThumbHeight(int trackHeight) {
        if (contentHeight <= 0) return trackHeight;
        int h = (int) (((double) viewportHeight / contentHeight) * trackHeight);
        return Mth.clamp(h, 18, trackHeight);
    }

    public int getThumbY(int trackY, int trackHeight) {
        double max = getMaxScroll();
        if (max <= 0) return trackY;
        int thumbH = getThumbHeight(trackHeight);
        int trackAvailable = trackHeight - thumbH;
        return trackY + (int) ((scrollAmount / max) * trackAvailable);
    }

    /**
     * Dibuja la barra de desplazamiento y su pulgar.
     */
    public void renderScrollbar(GuiGraphicsExtractor gui, int x, int y, int width, int height) {
        if (!hasScroll()) return;

        // Pista sutil
        gui.fill(x, y, x + width, y + height, 0x14000000);

        // Pulgar redondeado / estilizado
        int thumbH = getThumbHeight(height);
        int thumbY = getThumbY(y, height);
        int thumbColor = isDragging ? 0x99546E7A : 0x55546E7A;
        gui.fill(x + 1, thumbY, x + width - 1, thumbY + thumbH, thumbColor);
    }
}
