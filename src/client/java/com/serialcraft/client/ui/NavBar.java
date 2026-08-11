package com.serialcraft.client.ui;

import com.serialcraft.SerialCraft;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
            case HOME   -> new TabStyle(SpriteIcon.HOME, "gui.serialcraft.tab.home",
                                        UiTheme.ACCENT_HOME,   UiTheme.ACCENT_HOME_BORDER);
            case BOARDS -> new TabStyle(SpriteIcon.LIST, "gui.serialcraft.tab.boards",
                                        UiTheme.ACCENT_BOARDS, UiTheme.ACCENT_BOARDS_BORDER);
            case EVENTS -> new TabStyle(SpriteIcon.BELL, "gui.serialcraft.tab.events",
                                        UiTheme.ACCENT_EVENTS, UiTheme.ACCENT_EVENTS_BORDER);
        };
    }

    // ══════════════════════════════════════════════════════════════════════

    public void render(GuiGraphicsExtractor gui, int screenWidth, int screenHeight) {
        int navWidth = UiTheme.navWidth(screenWidth);

        gui.fill(0, 0, screenWidth, screenHeight, UiTheme.BG_APP);
        gui.fill(0, 0, navWidth, screenHeight, UiTheme.BG_NAV);

        int logoWidth  = (navWidth * 95) / 100;
        int logoHeight = logoWidth / 3;
        gui.blit(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE,
                navWidth * 3 / 100, (navWidth * 12) / 100, 0, 0,
                logoWidth, logoHeight, LOGO_SRC_W, LOGO_SRC_H, LOGO_SRC_W, LOGO_SRC_H);

        gui.fill(panelX(screenWidth), panelY(screenWidth),
                 panelWidth(screenWidth), panelHeight(screenHeight), UiTheme.BG_NAV_INNER);

        int catSize = (navWidth * 50) / 100;
        gui.blit(RenderPipelines.GUI_TEXTURED, CAT_TEXTURE,
                (navWidth * 25) / 100, (navWidth * 40) / 100, 0, 0,
                catSize, catSize, CAT_SRC, CAT_SRC, CAT_SRC, CAT_SRC);
    }

    public void init(PanelUI panel, int screenWidth, int screenHeight, PanelUI.Tab activeTab) {
        int x      = panelX(screenWidth);
        int y      = panelY(screenWidth);
        int width  = panelWidth(screenWidth);
        int height = panelHeight(screenHeight);

        int buttonWidth  = (width * 65) / 100;
        int buttonHeight = Math.max(20, (buttonWidth * 30) / 100);
        int spacing      = Math.max(6, (height * 4) / 100);
        int buttonX      = ((x + width) - buttonWidth) / 2;

        PanelUI.Tab[] tabs = PanelUI.Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            PanelUI.Tab tab = tabs[i];
            TabStyle style  = styleOf(tab);
            boolean active  = (tab == activeTab);

            int buttonY = y + spacing + i * (buttonHeight + spacing);

            panel.addWidget(new IconTextButton(
                    buttonX, buttonY, buttonWidth, buttonHeight,
                    style.icon(),
                    Component.translatable(style.translationKey()),
                    btn -> panel.setTab(tab),
                    active ? style.activeBg()     : UiTheme.TAB_INACTIVE_BG,
                    active ? style.activeBorder() : UiTheme.TAB_INACTIVE_BORDER,
                    UiTheme.TEXT_INVERSE
            ));
        }
    }

    // ── Metricas ──────────────────────────────────────────────────────────

    private static int panelX(int screenWidth) {
        return (UiTheme.navWidth(screenWidth) * 10) / 100;
    }

    private static int panelY(int screenWidth) {
        int navWidth  = UiTheme.navWidth(screenWidth);
        int catHeight = (navWidth * 50) / 100;
        int catY      = (navWidth * 40) / 100;
        return catY + catHeight - (catHeight * 24 / 100);
    }

    private static int panelWidth(int screenWidth) {
        return (UiTheme.navWidth(screenWidth) * 90) / 100;
    }

    private static int panelHeight(int screenHeight) {
        return (screenHeight * 95) / 100;
    }
}
