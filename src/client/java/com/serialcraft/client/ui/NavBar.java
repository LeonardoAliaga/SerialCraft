package com.serialcraft.client.ui;

import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class NavBar {

    private static final Identifier GATO_TEXTURE =
            Identifier.fromNamespaceAndPath("serialcraft", "textures/gui/gato.png");
    private static final Identifier LOGO_TEXTURE =
            Identifier.fromNamespaceAndPath("serialcraft", "textures/gui/logo-txt.png");

    // Colores activos por pestaña
    private static final int COLOR_HOME_ACTIVE   = 0xffe91e63;
    private static final int COLOR_HOME_BORDER   = 0xffba184f;
    private static final int COLOR_PLACAS_ACTIVE = 0xffffc107;
    private static final int COLOR_PLACAS_BORDER = 0xffcc9a05;
    private static final int COLOR_EVENTS_ACTIVE = 0xff4caf50;
    private static final int COLOR_EVENTS_BORDER = 0xff3c8c40;

    // Colores inactivos: oscuro uniforme
    private static final int COLOR_INACTIVE_BG     = 0xff263238;
    private static final int COLOR_INACTIVE_BORDER = 0xff37474f;

    public void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        int navBarWidth = (screenWidth * 18) / 100;
        int catWidth    = (navBarWidth * 50) / 100;
        int catHeight   = (navBarWidth * 50) / 100;
        int catX        = (navBarWidth * 25) / 100;
        int catY        = (navBarWidth * 40) / 100;

        int logoWidth  = (navBarWidth * 95) / 100;
        int logoHeight = logoWidth / 3;
        int logoX      = navBarWidth * 3 / 100;
        int logoY      = (navBarWidth * 12) / 100;

        int bgBtnWidth  = (navBarWidth * 90) / 100;
        int bgBtnHeight = (screenHeight * 95) / 100;
        int bgBtnX      = (navBarWidth * 10) / 100;
        int bgBtnY      = catY + catHeight - (catHeight * 24 / 100);

        guiGraphics.fill(0, 0, screenWidth, screenHeight, 0xFFF3F3F3);
        guiGraphics.fill(0, 0, navBarWidth, screenHeight, 0xff4995b6);

        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE,
                logoX, logoY, 0, 0,
                logoWidth, logoHeight,
                779, 261, 779, 261
        );

        guiGraphics.fill(bgBtnX, bgBtnY, bgBtnWidth, bgBtnHeight, 0xfff8f4ed);

        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED, GATO_TEXTURE,
                catX, catY, 0, 0,
                catWidth, catHeight,
                1024, 1024, 1024, 1024
        );
    }

    /**
     * Inicializa los botones de navegación resaltando la pestaña activa.
     *
     * @param panel       Pantalla principal para registrar widgets y cambiar pestañas.
     * @param screenWidth Ancho de pantalla actual.
     * @param screenHeight Alto de pantalla actual.
     * @param activeTab   Pestaña actualmente seleccionada (para resaltarla).
     */
    public void init(PanelUI panel, int screenWidth, int screenHeight, PanelUI.Tab activeTab) {
        int x      = getBgButtonX(screenWidth);
        int y      = getBgButtonY(screenWidth);
        int width  = getBgButtonWidth(screenWidth);
        int height = getBgButtonHeight(screenHeight);

        int btnWidth    = (width * 65) / 100;
        // Fórmula más precisa que (btnWidth/10)*3 que pierde precisión por truncación
        int btnHeight   = Math.max(20, (btnWidth * 30) / 100);
        int btnSpacingY = Math.max(6, (height * 4) / 100);
        int btnX        = ((x + width) - btnWidth) / 2;

        boolean homeActive   = (activeTab == PanelUI.Tab.HOME);
        boolean placasActive = (activeTab == PanelUI.Tab.PLACAS);
        boolean eventsActive = (activeTab == PanelUI.Tab.EVENTS);

        // ── Botón Inicio ──────────────────────────────────────────────────
        panel.addWidget(new IconTextButton(
                btnX, y + btnSpacingY,
                btnWidth, btnHeight,
                SpriteIcon.HOME,
                Component.literal("Inicio"),
                (btn) -> panel.setTab(PanelUI.Tab.HOME),
                homeActive ? COLOR_HOME_ACTIVE   : COLOR_INACTIVE_BG,
                homeActive ? COLOR_HOME_BORDER   : COLOR_INACTIVE_BORDER,
                0xffffffff
        ));

        // ── Botón Placas ──────────────────────────────────────────────────
        panel.addWidget(new IconTextButton(
                btnX, y + btnHeight + btnSpacingY * 3 / 2,
                btnWidth, btnHeight,
                SpriteIcon.LIST,
                Component.literal("Placas"),
                (btn) -> panel.setTab(PanelUI.Tab.PLACAS),
                placasActive ? COLOR_PLACAS_ACTIVE  : COLOR_INACTIVE_BG,
                placasActive ? COLOR_PLACAS_BORDER  : COLOR_INACTIVE_BORDER,
                0xffffffff
        ));

        // ── Botón Eventos ─────────────────────────────────────────────────
        panel.addWidget(new IconTextButton(
                btnX, y + btnHeight * 2 + btnSpacingY * 2,
                btnWidth, btnHeight,
                SpriteIcon.BELL,
                Component.literal("Eventos"),
                (btn) -> panel.setTab(PanelUI.Tab.EVENTS),
                eventsActive ? COLOR_EVENTS_ACTIVE  : COLOR_INACTIVE_BG,
                eventsActive ? COLOR_EVENTS_BORDER  : COLOR_INACTIVE_BORDER,
                0xffffffff
        ));
    }

    // ── Helpers de posición ───────────────────────────────────────────────

    public int getBgButtonY(int screenWidth) {
        int navBarWidth = (screenWidth * 18) / 100;
        int catHeight   = (navBarWidth * 50) / 100;
        int catY        = (navBarWidth * 40) / 100;
        return catY + catHeight - (catHeight * 24 / 100);
    }

    public int getBgButtonX(int screenWidth) {
        return ((screenWidth * 18) / 100 * 10) / 100;
    }

    public int getBgButtonWidth(int screenWidth) {
        return ((screenWidth * 18) / 100 * 90) / 100;
    }

    public int getBgButtonHeight(int screenHeight) {
        return (screenHeight * 95) / 100;
    }

    public static int getNavBarWidth(int screenWidth) {
        return (screenWidth * 18) / 100;
    }
}
