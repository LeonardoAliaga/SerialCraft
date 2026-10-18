package com.serialcraft.client.ui;

import com.serialcraft.SerialCraft;
import com.serialcraft.client.ui.widget.NavTabButton;
import com.serialcraft.screen.PanelUI;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Glass app header and sidebar. One mascot is selected for each PanelUI instance. */
public class NavBar {
    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath(
            SerialCraft.MOD_ID, "textures/gui/logo-txt.png");
    private static final int LOGO_SRC_W = 779;
    private static final int LOGO_SRC_H = 261;
    private static final String VERSION = FabricLoader.getInstance().getModContainer(SerialCraft.MOD_ID)
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
    private final CatSprite cat = CatSprite.random();

    private record TabStyle(SpriteIcon icon, String key, int accent) {}
    private record Box(int x, int y, int width, int height) {
        int bottom() { return y + height; }
    }
    private record Layout(Box sidebar, int buttonsTop, int buttonHeight, int gap, Box cat, int versionY) {}

    private static TabStyle styleOf(PanelUI.Tab tab) {
        return switch (tab) {
            case HOME -> new TabStyle(SpriteIcon.HOME, "gui.serialcraft.tab.home", UiTheme.ACCENT_HOME);
            case BOARDS -> new TabStyle(SpriteIcon.LIST, "gui.serialcraft.tab.boards", UiTheme.ACCENT_BOARDS);
            case EVENTS -> new TabStyle(SpriteIcon.BELL, "gui.serialcraft.tab.events", UiTheme.ACCENT_EVENTS);
            case VISUALIZE -> new TabStyle(SpriteIcon.MONITOR, "gui.serialcraft.tab.visualize", UiTheme.ACCENT_VISUALIZE);
        };
    }

    private static Layout layout(int width, int height) {
        int margin = Math.min(10, Math.max(6, UiTheme.navWidth(width) / 10));
        int top = UiTheme.contentTop(height) + 4;
        Box sidebar = new Box(margin, top, UiTheme.navWidth(width) - margin,
                Math.max(100, height - top - margin));
        int gap = Math.clamp(height / 55, 4, 8);
        int preferredCat = Math.min(104, Math.max(24, sidebar.width() - 16));
        int count = PanelUI.Tab.values().length;
        int buttonHeight = Math.clamp((sidebar.height() - preferredCat - 38 - gap * (count - 1)) / count, 20, 28);
        int buttonsTop = top + 8;
        int buttonsBottom = buttonsTop + count * buttonHeight + (count - 1) * gap;
        int versionY = sidebar.bottom() - 14;
        int catHeight = Math.min(preferredCat, Math.max(0, versionY - buttonsBottom - 16));
        Box cat = new Box(sidebar.x() + 8, versionY - 8 - catHeight, sidebar.width() - 16, catHeight);
        return new Layout(sidebar, buttonsTop, buttonHeight, gap, cat, versionY);
    }

    public void render(GuiGraphicsExtractor gui, int width, int height) {
        int headerBottom = UiTheme.contentTop(height) - 6;
        gui.fill(0, 0, width, height, UiTheme.BG_APP);
        UiDraw.glassSurface(gui, 10, 8, width - 20, headerBottom - 8,
                UiTheme.BG_HEADER, UiTheme.LINE_STRONG, false);
        int brandWidth = Math.max(20, UiTheme.navWidth(width) - 10);
        UiDraw.glassSurface(gui, 10, 8, brandWidth, headerBottom - 8, UiTheme.BG_NAV, 0, false);
        int logoWidth = Math.min(brandWidth - 8, (headerBottom - 16) * LOGO_SRC_W / LOGO_SRC_H);
        int logoHeight = logoWidth * LOGO_SRC_H / LOGO_SRC_W;
        gui.blit(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE,
                10 + (brandWidth - logoWidth) / 2, 8 + (headerBottom - 8 - logoHeight) / 2,
                0, 0, logoWidth, logoHeight, LOGO_SRC_W, LOGO_SRC_H, LOGO_SRC_W, LOGO_SRC_H);

        Layout layout = layout(width, height);
        Box sidebar = layout.sidebar();
        UiDraw.glassSurface(gui, sidebar.x(), sidebar.y(), sidebar.width(), sidebar.height(),
                UiTheme.BG_NAV_INNER, UiTheme.LINE_STRONG, false);
        Box catBox = layout.cat();
        cat.render(gui, catBox.x(), catBox.y(), catBox.width(), catBox.height());
        var font = Minecraft.getInstance().font;
        String version = sidebar.width() >= 125 ? "SerialCraft v" + VERSION : "v" + VERSION;
        version = font.plainSubstrByWidth(version, Math.max(1, sidebar.width() - 10));
        gui.text(font, version, sidebar.x() + (sidebar.width() - font.width(version)) / 2,
                layout.versionY(), UiTheme.TEXT_ON_DARK, true);
    }

    public void init(PanelUI panel, int width, int height, PanelUI.Tab activeTab) {
        Layout layout = layout(width, height);
        Box sidebar = layout.sidebar();
        int buttonWidth = Math.max(1, sidebar.width() - 12);
        PanelUI.Tab[] tabs = PanelUI.Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            PanelUI.Tab tab = tabs[i];
            TabStyle style = styleOf(tab);
            panel.addWidget(new NavTabButton(sidebar.x() + 6,
                    layout.buttonsTop() + i * (layout.buttonHeight() + layout.gap()), buttonWidth,
                    layout.buttonHeight(), style.icon(), Component.translatable(style.key()),
                    style.accent(), tab == activeTab, button -> panel.setTab(tab)));
        }
    }
}
