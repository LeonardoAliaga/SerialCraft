package com.serialcraft.client.ui;

import com.serialcraft.SerialCraft;
import com.serialcraft.client.ui.widget.NavTabButton;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Horizontal glass navigation, with two tabs on each side of the original brand. */
public class NavBar {
    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath(
            SerialCraft.MOD_ID, "textures/gui/logo-txt.png");
    private static final int LOGO_SRC_W = 779;
    private static final int LOGO_SRC_H = 261;

    private record TabStyle(SpriteIcon icon, String key, int accent) {}

    private static TabStyle styleOf(PanelUI.Tab tab) {
        return switch (tab) {
            case HOME -> new TabStyle(SpriteIcon.HOME, "gui.serialcraft.tab.home", UiTheme.ACCENT_HOME);
            case BOARDS -> new TabStyle(SpriteIcon.LIST, "gui.serialcraft.tab.boards", UiTheme.ACCENT_BOARDS);
            case EVENTS -> new TabStyle(SpriteIcon.BELL, "gui.serialcraft.tab.events", UiTheme.ACCENT_EVENTS);
            case VISUALIZE -> new TabStyle(SpriteIcon.MONITOR, "gui.serialcraft.tab.visualize", UiTheme.ACCENT_VISUALIZE);
        };
    }

    public void render(GuiGraphicsExtractor gui, int width, int height) {
        gui.fill(0, 0, width, height, UiTheme.BG_APP);
        var layout = UiTheme.navigationLayout(width, height);
        gui.fill(0, 0, width, layout.height(), UiTheme.BG_HEADER);
        gui.fill(0, layout.height() - 1, width, layout.height(), UiTheme.LINE);
        int brandHeight = layout.height() - 4;
        int slant = Math.min(10, brandHeight / 3);
        UiDraw.angledGlassSurface(gui, layout.brandX(), 2, layout.brandWidth(), brandHeight,
                slant, slant, UiTheme.HEADER_BRAND, UiTheme.HEADER_BRAND_BORDER);
        int logoWidth = Math.min(layout.brandWidth() - slant * 2 - 8,
                (brandHeight - 8) * LOGO_SRC_W / LOGO_SRC_H);
        int logoHeight = logoWidth * LOGO_SRC_H / LOGO_SRC_W;
        gui.blit(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE,
                layout.brandX() + (layout.brandWidth() - logoWidth) / 2,
                2 + (brandHeight - logoHeight) / 2,
                0, 0, logoWidth, logoHeight, LOGO_SRC_W, LOGO_SRC_H, LOGO_SRC_W, LOGO_SRC_H);
    }

    public void init(PanelUI panel, int width, int height, PanelUI.Tab activeTab) {
        var layout = UiTheme.navigationLayout(width, height);
        PanelUI.Tab[] tabs = PanelUI.Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            PanelUI.Tab tab = tabs[i];
            TabStyle style = styleOf(tab);
            panel.addWidget(new NavTabButton(layout.buttonX(i), 4, layout.buttonWidth(),
                    layout.height() - 8, style.icon(), Component.translatable(style.key()),
                    style.accent(), tab == activeTab, button -> panel.setTab(tab)));
        }
    }
}
