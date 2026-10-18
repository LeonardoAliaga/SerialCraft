package com.serialcraft.client.ui;

import com.serialcraft.SerialCraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.concurrent.ThreadLocalRandom;

/** Four independently addressable poses in the original transparent atlas. */
public enum CatSprite {
    STRETCHING(101, 11, 528, 640),
    STANDING(674, 88, 510, 505),
    FRONT(45, 649, 557, 513),
    GROOMING(712, 695, 500, 472);

    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            SerialCraft.MOD_ID, "textures/gui/cats.png");
    public static final int SHEET_WIDTH = 1254;
    public static final int SHEET_HEIGHT = 1254;
    private static final CatSprite[] POSES = values();

    private final int u, v, sourceWidth, sourceHeight;

    CatSprite(int u, int v, int sourceWidth, int sourceHeight) {
        this.u = u;
        this.v = v;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
    }

    public static CatSprite random() {
        return POSES[ThreadLocalRandom.current().nextInt(POSES.length)];
    }

    /** Fits without stretching; all poses share a centered baseline. */
    public void render(GuiGraphicsExtractor gui, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        float scale = Math.min((float) width / sourceWidth, (float) height / sourceHeight);
        int drawWidth = Math.max(1, Math.round(sourceWidth * scale));
        int drawHeight = Math.max(1, Math.round(sourceHeight * scale));
        gui.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x + (width - drawWidth) / 2, y + height - drawHeight, u, v, drawWidth, drawHeight,
                sourceWidth, sourceHeight, SHEET_WIDTH, SHEET_HEIGHT);
    }
}
