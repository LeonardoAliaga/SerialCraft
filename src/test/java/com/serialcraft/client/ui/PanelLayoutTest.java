package com.serialcraft.client.ui;

import com.serialcraft.client.ui.io.IoEditorLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Prevent navigation from covering content or editor controls at larger GUI scales. */
class PanelLayoutTest {
    @Test void navigationStaysCenteredAndAllFourTargetsRemainSeparate() {
        for (int width : new int[]{320, 321, 427, 640, 854, 1280, 1920}) {
            for (int height : new int[]{240, 279, 280, 360, 480, 720}) {
                var nav = UiTheme.navigationLayout(width, height);
                assertEquals(width, nav.brandX() * 2 + nav.brandWidth());
                assertTrue(nav.buttonWidth() >= 24);
                assertTrue(nav.buttonX(0) >= UiTheme.contentMargin(width));
                assertTrue(nav.buttonX(0) + nav.buttonWidth() < nav.buttonX(1));
                assertTrue(nav.buttonX(1) + nav.buttonWidth() < nav.brandX());
                assertTrue(nav.brandX() + nav.brandWidth() < nav.buttonX(2));
                assertTrue(nav.buttonX(2) + nav.buttonWidth() < nav.buttonX(3));
                assertTrue(nav.buttonX(3) + nav.buttonWidth() <= width - UiTheme.contentMargin(width));
                assertTrue(UiTheme.headerLayout(width, height).y() > nav.height());
                assertTrue(UiTheme.headerTitleY(height, 9) + Math.ceil(9 * UiTheme.TITLE_SCALE)
                        < UiTheme.contentTop(height));
                assertEquals(UiTheme.contentMargin(width), UiTheme.contentX(width));
            }
        }
    }

    @Test void editorTabsFieldsAndSaveActionsStayBelowTheGlobalNavigation() {
        for (int width : new int[]{320, 427, 640, 854, 1280}) {
            for (int height : new int[]{240, 279, 280, 360, 480}) {
                var layout = IoEditorLayout.calculate(width, height, UiTheme.contentX(width), 260, 132, 112);
                assertTrue(layout.panel().contains(layout.configurationTab()));
                assertTrue(layout.panel().contains(layout.diagnosticsTab()));
                assertTrue(layout.configurationTab().y() > UiTheme.navigationHeight(height));
                assertTrue(layout.configurationTab().right() < layout.diagnosticsTab().x());
                assertTrue(layout.viewport().y() >= layout.configurationTab().bottom() + 8);
                assertTrue(layout.viewport().height() > 0);
                assertTrue(layout.viewport().bottom() <= layout.status().y() - 6);
                assertTrue(layout.status().bottom() <= layout.save().y());
                assertTrue(layout.panel().contains(layout.save()));
                assertTrue(layout.panel().contains(layout.cancel()));
            }
        }
    }
}
