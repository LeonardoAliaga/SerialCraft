package com.serialcraft.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
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
    private static final Identifier ICONS_TEXTURE =
            Identifier.fromNamespaceAndPath(
                    "serialcraft",
                    "textures/gui/icons.png"
            );
    // Datos del sprite "home" (desde tu stylesheet)
    private static final int ICON_SIZE = 64;
    private static final int HOME_U = 153;
    private static final int HOME_V = 79;

    // Tamaño total del sheet (ajusta si cambia)
    private static final int SHEET_WIDTH = 296;
    private static final int SHEET_HEIGHT = 222;


    public void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {

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


        guiGraphics.fill(0,0, screenWidth, screenHeight, 0xFFF3F3F3);
        guiGraphics.fill(0,0, navBarWidth, screenHeight, 0xff4995b6);

        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                LOGO_TEXTURE,
                logoX, logoY,
                0, 0,
                logoWidth, logoHeight,
                779, 261,
                779, 261
        );


        guiGraphics.fill(bgBtnX,bgBtnY, bgBtnWidth, bgBtnHeight, 0xfff8f4ed);

        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                GATO_TEXTURE,
                catX, catY,
                0, 0,
                catWidth, catHeight,
                1024, 1024,
                1024, 1024
        );
        // Dibujar icono HOME
        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                ICONS_TEXTURE,
                0, 0,
                HOME_U, HOME_V,
                64, 64,
                ICON_SIZE,ICON_SIZE,
                SHEET_WIDTH, SHEET_HEIGHT
        );
    }
}