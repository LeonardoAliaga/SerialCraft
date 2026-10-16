package com.serialcraft.client.ui;

import com.serialcraft.SerialCraft;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Barra lateral de navegacion.
 */
public class NavBar {

    private static final Identifier CAT_TEXTURE =
            Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "textures/gui/gato.png");
    private static final Identifier LOGO_TEXTURE =
            Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "textures/gui/logo-txt.png");

    private static final int LOGO_SRC_W = 779;
    private static final int LOGO_SRC_H = 261;
    private static final int CAT_SRC    = 1024;

    /** Descripcion declarativa de una pestana. Fuente unica para UI y colores. */
    private record TabStyle(SpriteIcon icon, String translationKey, int activeBg, int activeBorder) {}

    private static TabStyle styleOf(PanelUI.Tab tab) {
        return switch (tab) {
            case HOME      -> new TabStyle(SpriteIcon.HOME, "gui.serialcraft.tab.home",
                                        UiTheme.ACCENT_HOME,      UiTheme.ACCENT_HOME_BORDER);
            case BOARDS    -> new TabStyle(SpriteIcon.LIST, "gui.serialcraft.tab.boards",
                                        UiTheme.ACCENT_BOARDS,    UiTheme.ACCENT_BOARDS_BORDER);
            case EVENTS    -> new TabStyle(SpriteIcon.BELL, "gui.serialcraft.tab.events",
                                        UiTheme.ACCENT_EVENTS,    UiTheme.ACCENT_EVENTS_BORDER);
            case VISUALIZE -> new TabStyle(SpriteIcon.MONITOR, "gui.serialcraft.tab.visualize",
                                        UiTheme.ACCENT_VISUALIZE, UiTheme.ACCENT_VISUALIZE_BORDER);
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════

    /** Rectangulo real del panel claro (x, y, ancho, alto) en pixeles GUI. */
    private record Box(int x, int y, int width, int height) {
        int right()  { return x + width; }
        int bottom() { return y + height; }
    }

    /**
     * El panel deja el mismo margen a izquierda, derecha y abajo (10 % del ancho de la barra).
     * Antes se pasaba el ANCHO a fill() como si fuese la coordenada derecha, de modo que el
     * panel medía 80 % y los botones (calculados sobre 90 %) quedaban pegados a su borde.
     */
    private static Box panelBox(int screenWidth, int screenHeight) {
        int nav = UiTheme.navWidth(screenWidth);
        int margin = (nav * 10) / 100;
        int catHeight = (nav * 50) / 100;
        int catY = (nav * 40) / 100;
        int y = catY + catHeight - (catHeight * 24 / 100);
        return new Box(margin, y, nav - margin * 2, Math.max(40, screenHeight - margin - y));
    }

    private static int inset(Box panel) { return Math.max(6, panel.width() * 12 / 100); }

    public void render(GuiGraphicsExtractor gui, int screenWidth, int screenHeight) {
        int navWidth = UiTheme.navWidth(screenWidth);

        gui.fill(0, 0, screenWidth, screenHeight, UiTheme.BG_APP);
        gui.fill(0, 0, navWidth, screenHeight, UiTheme.BG_NAV);

        int logoWidth  = (navWidth * 95) / 100;
        int logoHeight = logoWidth / 3;
        gui.blit(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE,
                navWidth * 3 / 100, (navWidth * 12) / 100, 0, 0,
                logoWidth, logoHeight, LOGO_SRC_W, LOGO_SRC_H, LOGO_SRC_W, LOGO_SRC_H);

        Box panel = panelBox(screenWidth, screenHeight);
        gui.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), UiTheme.BG_NAV_INNER);

        int catSize = (navWidth * 50) / 100;
        gui.blit(RenderPipelines.GUI_TEXTURED, CAT_TEXTURE,
                (navWidth * 25) / 100, (navWidth * 40) / 100, 0, 0,
                catSize, catSize, CAT_SRC, CAT_SRC, CAT_SRC, CAT_SRC);

    }

    public void init(PanelUI panel, int screenWidth, int screenHeight, PanelUI.Tab activeTab) {
        Box box = panelBox(screenWidth, screenHeight);

        int inset        = inset(box);
        int buttonWidth  = Math.min(104, Math.max(1, box.width() - inset * 2));
        int spacing      = Math.clamp(screenHeight / 50, 6, 10);
        int buttonX      = box.x() + (box.width() - buttonWidth) / 2;
        int navWidth     = UiTheme.navWidth(screenWidth);
        // Leave room for the cat's paws, then keep a compact, centered button stack.
        int buttonsTop   = Math.max(box.y() + inset, navWidth * 40 / 100 + navWidth * 50 / 100 + 6);

        PanelUI.Tab[] tabs = PanelUI.Tab.values();
        int availableHeight = box.bottom() - inset - buttonsTop - (tabs.length - 1) * spacing;
        int buttonHeight = Math.clamp(availableHeight / tabs.length, 20, 28);
        for (int i = 0; i < tabs.length; i++) {
            PanelUI.Tab tab = tabs[i];
            TabStyle style  = styleOf(tab);
            boolean active  = (tab == activeTab);

            int buttonY = buttonsTop + i * (buttonHeight + spacing);

            var button = new IconTextButton(
                    buttonX, buttonY, buttonWidth, buttonHeight,
                    style.icon(),
                    Component.translatable(style.translationKey()),
                    btn -> panel.setTab(tab),
                    active ? style.activeBg()     : UiTheme.TAB_INACTIVE_BG,
                    active ? style.activeBorder() : UiTheme.TAB_INACTIVE_BORDER,
                    UiTheme.TEXT_INVERSE
            );
            // Con escalas de GUI altas el texto puede acortarse; el tooltip conserva la etiqueta completa.
            button.setTooltip(Tooltip.create(Component.translatable(style.translationKey())));
            panel.addWidget(button);
        }
    }
}
