package com.serialcraft.screen;

import com.serialcraft.client.ui.NavBar; // <--- Importamos tu nueva clase
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.widget.IconTextButton;

public class PanelUI extends Screen {

    private final NavBar navBar = new NavBar();

    public PanelUI() {
        super(Component.literal("PanelUI"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {

        navBar.render(guiGraphics, this.width, this.height);

        super.render(guiGraphics, mouseX, mouseY, delta);
    }
    @Override
    protected void init() {
        super.init();

        //int x = navBar.getButtonX(this.width);
        //int y = navBar.getFirstButtonY(this.height);

        int x = navBar.getBgButtonX(this.width);
        int y = navBar.getBgButtonY(this.width);

        IconTextButton homeButton = new IconTextButton(
                ((x + navBar.getBgButtonWidth(this.width)) - 80)/2,
                y + 15,
                80,
                24,
                SpriteIcon.HOME,
                Component.literal("Inicio"),
                0xffe91e63,
                0xffba184f
        );
        IconTextButton placasButton = new IconTextButton(
                ((x + navBar.getBgButtonWidth(this.width)) - 80)/2,
                y + 45,
                80,
                24,
                SpriteIcon.LIST,
                Component.literal("Placas"),
                0xffffc107,
                0xffcc9a05
        );

        addRenderableWidget(homeButton);
        addRenderableWidget(placasButton);
    }

}