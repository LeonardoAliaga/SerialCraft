package com.serialcraft.client;

import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.connection.WifiHandler;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * HUD de diagnostico (F7).
 *
 * El texto sigue sin traducirse a proposito: es una herramienta de depuracion
 * dirigida a quien desarrolla el sketch, no parte de la interfaz de usuario.
 * Lo que si cambia es que ahora esta declarado como tal en vez de ser una
 * mezcla de cadenas en espanol y en ingles sin criterio.
 */
public class SerialDebugHud {

    private static final int MAX_LOGS   = 8;
    private static final int PANEL_W    = 240;
    private static final int LINE_H     = 10;

    private static volatile boolean enabled = false;
    private static final Deque<String> LOG = new ArrayDeque<>(MAX_LOGS);

    public static void toggle() { enabled = !enabled; }
    public static boolean isEnabled() { return enabled; }

    public static void addLog(String message) {
        synchronized (LOG) {
            if (LOG.size() >= MAX_LOGS) LOG.removeLast();
            LOG.addFirst(message);
        }
    }

    private static List<String> snapshot() {
        synchronized (LOG) { return new ArrayList<>(LOG); }
    }

    // ══════════════════════════════════════════════════════════════════════

    public static void render(GuiGraphicsExtractor gui, DeltaTracker deltaTracker) {
        if (!enabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client.gui.hud.isHidden() || client.level == null) return;

        Font font = client.font;
        renderConnectionPanel(gui, font);
        renderTargetedBoard(gui, font, client);
    }

    private static void renderConnectionPanel(GuiGraphicsExtractor gui, Font font) {
        var serial = ConnectionManager.getSerial();
        var wifi   = ConnectionManager.getWifi();

        String transport;
        String detail;
        if (serial.isConnected()) {
            transport = "USB " + serial.getPortName();
            detail    = serial.getBaudRate() + " bps";
        } else if (wifi.isConnected()) {
            transport = "WiFi " + wifi.getRemoteIp();
            detail    = "TCP";
        } else {
            transport = "offline";
            detail    = "--";
        }

        List<String> logs = snapshot();
        int x = 5, y = 5;
        int panelH = logs.size() * LINE_H + 20;

        gui.fill(x - 2, y - 2, x + PANEL_W, y + panelH, UiTheme.OVERLAY);
        gui.text(font, "[SerialCraft] " + transport + " | " + detail,
                x, y, ConnectionManager.isAnyConnected() ? UiTheme.OK : UiTheme.ERROR, true);

        y += 12;
        for (String entry : logs) {
            gui.text(font, "> " + font.plainSubstrByWidth(entry, PANEL_W - 16),
                    x, y, entry.startsWith("RX") ? 0xFFFFAA00 : 0xFFAAAAFF, true);
            y += LINE_H;
        }
    }

    private static void renderTargetedBoard(GuiGraphicsExtractor gui, Font font, Minecraft client) {
        HitResult hit = client.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        if (!(client.level.getBlockEntity(pos) instanceof ArduinoIOBlockEntity io)) return;

        int x = (client.getWindow().getGuiScaledWidth()  / 2) + 15;
        int y = (client.getWindow().getGuiScaledHeight() / 2) - 15;

        gui.fill(x - 5, y - 5, x + 145, y + 65, UiTheme.OVERLAY);

        drawPair(gui, font, x, y,           "ID",   io.getBoardId(),                       UiTheme.TEXT_INVERSE);
        drawPair(gui, font, x, y + LINE_H,  "Mode", io.getIoMode().getSerializedName(),    0xFF55FFFF);
        drawPair(gui, font, x, y + LINE_H*2,"Sig",  io.getSignalType().getSerializedName(),0xFFFF55FF);
        drawPair(gui, font, x, y + LINE_H*3,"Cmd",  io.getTargetData(),                    0xFFFFFF55);
        drawPair(gui, font, x, y + LINE_H*4,"On",   String.valueOf(io.isEnabled()),
                io.isEnabled() ? UiTheme.OK : UiTheme.ERROR);
        drawPair(gui, font, x, y + LINE_H*5,"RS",   String.valueOf(io.getRedstoneSignal()),
                io.getRedstoneSignal() > 0 ? UiTheme.ERROR : UiTheme.TEXT_MUTED);
    }

    /** Columna de valores alineada, en vez de los desplazamientos a ojo del original. */
    private static void drawPair(GuiGraphicsExtractor gui, Font font, int x, int y,
                                 String label, String value, int valueColor) {
        gui.text(font, label, x, y, 0xFFAAAAAA, true);
        gui.text(font, value, x + 34, y, valueColor, true);
    }
}