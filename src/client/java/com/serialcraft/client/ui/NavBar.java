package com.serialcraft.client.ui;

import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class NavBar {

    // Textura
    private static final Identifier GATO_TEXTURE =
            Identifier.fromNamespaceAndPath(
                    "serialcraft",
                    "textures/gui/gato.png"
            );
    private static final Identifier LOGO_TEXTURE =
            Identifier.fromNamespaceAndPath(
                    "serialcraft",
                    "textures/gui/logo-txt.png"
            );

    // (Omití las constantes no usadas para limpiar, pero puedes dejarlas si las usas en otro lado)

    public void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        // Variables de renderizado (sin cambios en la lógica visual)
        int navBarWidth = (screenWidth * 18) / 100;
        int catWidth = (navBarWidth * 50) / 100;
        int catHeight = (navBarWidth * 50) / 100;
        int catX = (navBarWidth * 25) / 100;
        int catY = (navBarWidth * 40) / 100;

        int logoWidth = (navBarWidth * 95) / 100;
        int logoHeight = logoWidth / 3;
        int logoX = navBarWidth * 3 / 100;
        int logoY = (navBarWidth * 12) / 100;

        int bgBtnWidth = (navBarWidth * 90) / 100;
        int bgBtnHeight = (screenHeight * 95) / 100;
        int bgBtnX = (navBarWidth * 10) / 100;
        int bgBtnY = catY + catHeight - (catHeight * 24/100);

        // Fondo
        guiGraphics.fill(0,0, screenWidth, screenHeight, 0xFFF3F3F3);
        guiGraphics.fill(0,0, navBarWidth, screenHeight, 0xff4995b6);

        // Logo
        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                LOGO_TEXTURE,
                logoX, logoY,
                0, 0,
                logoWidth, logoHeight,
                779, 261,
                779, 261
        );

        // Panel de botones (fondo crema)
        guiGraphics.fill(bgBtnX,bgBtnY, bgBtnWidth, bgBtnHeight, 0xfff8f4ed);

        // Gato
        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                GATO_TEXTURE,
                catX, catY,
                0, 0,
                catWidth, catHeight,
                1024, 1024,
                1024, 1024
        );
    }

    /**
     * Inicializa los botones de la barra de navegación.
     * @param panel La pantalla principal (para registrar los widgets y cambiar pestañas).
     * @param screenWidth El ancho actual de la pantalla para cálculos.
     */
    public void init(PanelUI panel, int screenWidth, int  screenHeight) {
        int x = getBgButtonX(screenWidth);
        int y = getBgButtonY(screenWidth);
        int width = getBgButtonWidth(screenWidth);
        int height = getBgButtonHeight(screenHeight);

        int btnWidth = (getBgButtonWidth(screenWidth) * 65)/100;
        int btnHeight = (btnWidth / 10) * 3;

        int btnPaddingY = (height * 4)/100;

        // Botón Inicio
        IconTextButton homeButton = new IconTextButton(
                ((x + width) - btnWidth)/2,
                y + btnPaddingY,
                btnWidth,
                btnHeight,
                SpriteIcon.HOME,
                Component.literal("Inicio"),
                // Acción: Cambiar a pestaña HOME
                (btn) -> panel.setTab(PanelUI.Tab.HOME),
                0xffe91e63,
                0xffba184f
        );

        // Botón Placas
        IconTextButton placasButton = new IconTextButton(
                ((x + width) - btnWidth)/2,
                y + btnHeight + btnPaddingY*15/10,
                btnWidth,
                btnHeight,
                SpriteIcon.LIST,
                Component.literal("Placas"),
                // Acción: Cambiar a pestaña PLACAS
                (btn) -> panel.setTab(PanelUI.Tab.PLACAS),
                0xffffc107,
                0xffcc9a05
        );

        // Botón Placas
        IconTextButton eventosButton = new IconTextButton(
                ((x + width) - btnWidth)/2,
                y + btnHeight*2 + btnPaddingY*2,
                btnWidth,
                btnHeight,
                SpriteIcon.BELL,
                Component.literal("Eventos"),
                // Acción: Cambiar a pestaña PLACAS
                (btn) -> panel.setTab(PanelUI.Tab.EVENTS),
                0xff4caf50,
                0xff3c8c40
        );

        // Registramos los botones en la pantalla principal
        panel.addWidget(homeButton);
        panel.addWidget(placasButton);
        panel.addWidget(eventosButton);
    }

    // --- Helpers de Posición (Manteniendo tu lógica original) ---

    public int getBgButtonY(int screenWidth) {
        int navBarWidth = (screenWidth * 18) / 100;
        int catHeight = (navBarWidth * 50) / 100;
        int catY = (navBarWidth * 40) / 100;
        return catY + catHeight - (catHeight * 24/100);
    }

    public int getBgButtonX(int screenWidth) {
        int navBarWidth = (screenWidth * 18) / 100;
        return (navBarWidth * 10) / 100;
    }

    public int getBgButtonWidth(int screenWidth) {
        int navBarWidth = (screenWidth * 18) / 100;
        return (navBarWidth * 90) / 100;
    }
    public int getBgButtonHeight(int screenHeight) {
        return (screenHeight * 95) / 100;
    }
    public static int getNavBarWidth(int screenWidth) {
        return (screenWidth * 18) / 100;
    }
}