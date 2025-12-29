package com.serialcraft.screen;

import com.serialcraft.SerialCraftClient;
import com.serialcraft.client.SerialDebugHud;
import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.block.entity.ConnectorBlockEntity;
import com.serialcraft.network.BoardInfo;
import com.serialcraft.network.BoardListRequestPayload;
import com.serialcraft.network.ConnectorConfigPayload;
import com.serialcraft.network.RemoteTogglePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ConnectorScreen extends Screen {
    private final BlockPos pos;
    private EditBox portBox;
    private Component statusText;
    private boolean isConnected;

    private int baudRate = 9600;
    private int speedMode = 2;

    private final int[] availableBaudRates = {9600, 14400, 19200, 38400, 57600, 115200};

    private List<BoardInfo> boardList = new ArrayList<>();
    private final List<Renderable> uiWidgets = new ArrayList<>();
    private final List<SolidButton> boardButtons = new ArrayList<>();

    private static final int BG_COLOR = 0xFFF2F2EC;
    private static final int HEADER_BG = 0xFFE6E6DF;
    private static final int CARD_BG = 0xFFFFFFFF;
    private static final int INPUT_BG = 0xFF1A1A1A;
    private static final int TEXT_MAIN = 0xFF333333;
    private static final int TEXT_DIM = 0xFF888888;
    private static final int TEXT_INPUT = 0xFFE0E0E0;
    private static final int ACCENT_GREEN = 0xFF2E7D32;
    private static final int ACCENT_RED = 0xFFC62828;

    public ConnectorScreen(BlockPos pos) {
        super(Component.translatable("gui.serialcraft.connector.title"));
        this.pos = pos;
        this.statusText = Component.literal("...");

        assert net.minecraft.client.Minecraft.getInstance().level != null;
        if (net.minecraft.client.Minecraft.getInstance().level.getBlockEntity(pos) instanceof ConnectorBlockEntity be) {
            this.baudRate = be.baudRate;
            this.speedMode = be.speedMode;
            SerialCraftClient.globalSerialSpeed = this.speedMode;
        }
    }

    @Override
    protected void init() {
        super.init();
        this.uiWidgets.clear();
        this.boardButtons.clear();

        this.isConnected = (SerialCraftClient.arduinoPort != null && SerialCraftClient.arduinoPort.isOpen());

        ClientPlayNetworking.send(new ConnectorConfigPayload(this.pos, this.baudRate, this.isConnected, this.speedMode));
        ClientPlayNetworking.send(new BoardListRequestPayload(true));

        int w = 340; int h = 230;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        String oldPort = (this.portBox != null) ? this.portBox.getValue() : "COM9";

        // ELIMINADO: Botón Toggle HUD que estaba aquí

        int row1Y = y + 45;

        this.portBox = new EditBox(this.font, x + 25, row1Y + 2, 100, 14, Component.translatable("gui.serialcraft.connector.port_label"));
        this.portBox.setMaxLength(32);
        this.portBox.setValue(isConnected ? SerialCraftClient.arduinoPort.getSystemPortName() : oldPort);
        this.portBox.setTextColor(TEXT_INPUT);
        this.portBox.setBordered(false);
        this.addCustomWidget(this.portBox);

        this.addCustomWidget(SolidButton.success(x + 135, row1Y, 100, 18,
                Component.translatable("gui.serialcraft.connector.btn_connect"), b -> {
                    Component res = SerialCraftClient.conectar(portBox.getValue().trim(), this.baudRate);
                    this.statusText = res;
                    this.isConnected = (SerialCraftClient.arduinoPort != null && SerialCraftClient.arduinoPort.isOpen());
                    if (isConnected) sendConfig();
                }));

        this.addCustomWidget(SolidButton.danger(x + 245, row1Y, 70, 18,
                Component.literal("X"), b -> {
                    SerialCraftClient.desconectar();
                    this.isConnected = false;
                    updateStatusText();
                    sendConfig();
                }));

        updateStatusText();

        int row3Y = y + 100;

        this.addCustomWidget(SolidButton.primary(x + 25, row3Y, 100, 20, getProfileText(), b -> {
            if (this.baudRate == 9600) this.baudRate = 115200; else this.baudRate = 9600;
            b.setMessage(getProfileText());
            applyReconnect();
        }));

        this.addCustomWidget(SolidButton.soft(x + 135, row3Y, 80, 20, Component.literal(String.valueOf(this.baudRate)), b -> {
            cycleBaudRate();
            b.setMessage(Component.literal(String.valueOf(this.baudRate)));
            applyReconnect();
        }));

        this.addCustomWidget(SolidButton.primary(x + 225, row3Y, 90, 20, getSpeedText(), b -> {
            this.speedMode = (this.speedMode + 1) % 3;
            SerialCraftClient.globalSerialSpeed = this.speedMode;
            b.setMessage(getSpeedText());
            sendConfig();
        }));

        refreshBoardListWidgets();
    }

    private void applyReconnect() {
        if (isConnected) {
            String currentPort = portBox.getValue().trim();
            SerialCraftClient.desconectar();
            Component res = SerialCraftClient.conectar(currentPort, this.baudRate);
            this.statusText = res;
            this.isConnected = (SerialCraftClient.arduinoPort != null && SerialCraftClient.arduinoPort.isOpen());
        }
        refreshWidgets();
        sendConfig();
    }

    private void refreshWidgets() {
        // CORRECCIÓN: Llamar a init() sin argumentos reconstruye la pantalla
        // usando las dimensiones y el cliente que ya tiene guardados.
        this.init();
    }
    private void cycleBaudRate() {
        int nextIndex = 0;
        for (int i = 0; i < availableBaudRates.length; i++) {
            if (availableBaudRates[i] == this.baudRate) {
                nextIndex = (i + 1) % availableBaudRates.length;
                break;
            }
        }
        this.baudRate = availableBaudRates[nextIndex];
    }

    private Component getProfileText() {
        if (this.baudRate == 9600) return Component.translatable("gui.serialcraft.profile.arduino");
        if (this.baudRate == 115200) return Component.translatable("gui.serialcraft.profile.esp32");
        return Component.translatable("gui.serialcraft.profile.custom");
    }

    private Component getSpeedText() {
        return switch (speedMode) {
            case 0 -> Component.translatable("gui.serialcraft.speed.low");
            case 1 -> Component.translatable("gui.serialcraft.speed.norm");
            default -> Component.translatable("gui.serialcraft.speed.fast");
        };
    }

    private void sendConfig() {
        ClientPlayNetworking.send(new ConnectorConfigPayload(this.pos, this.baudRate, this.isConnected, this.speedMode));
    }

    private void updateStatusText() {
        if (isConnected && SerialCraftClient.arduinoPort != null) {
            this.statusText = Component.translatable("message.serialcraft.connected", SerialCraftClient.arduinoPort.getSystemPortName());
        } else {
            this.statusText = Component.translatable("message.serialcraft.disconnected");
        }
    }

    private <T extends GuiEventListener & Renderable & NarratableEntry> void addCustomWidget(T widget) {
        this.addRenderableWidget(widget);
        this.uiWidgets.add(widget);
    }

    public void updateBoardList(List<BoardInfo> boards) {
        this.boardList = boards;
        this.refreshBoardListWidgets();
    }

    private void refreshBoardListWidgets() {
        for (SolidButton btn : boardButtons) {
            this.removeWidget(btn);
            this.uiWidgets.remove(btn);
        }
        boardButtons.clear();

        int w = 340; int h = 230;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        int listStartY = y + 140;

        for (int i = 0; i < boardList.size(); i++) {
            BoardInfo info = boardList.get(i);
            int rowY = listStartY + (i * 24);
            if (rowY > y + h - 25) break;

            SolidButton.Variant v = info.status() ? SolidButton.Variant.SUCCESS : SolidButton.Variant.DANGER;

            SolidButton toggleBtn = SolidButton.of(
                    x + 270, rowY + 3, 40, 14,
                    Component.translatable(info.status() ? "message.serialcraft.on_icon" : "message.serialcraft.off_icon"),
                    b -> {
                        ClientPlayNetworking.send(new RemoteTogglePayload(info.pos()));
                        ClientPlayNetworking.send(new BoardListRequestPayload(true));
                    },
                    v
            );

            this.addCustomWidget(toggleBtn);
            boardButtons.add(toggleBtn);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        int w = 340; int h = 230;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        gui.fill(x, y, x + w, y + h, BG_COLOR);
        // REEMPLAZO DE renderOutline POR drawBorder
        drawBorder(gui, x, y, w, h, 0xFFAAAAAA);

        gui.fill(x, y, x + w, y + 30, HEADER_BG);
        gui.drawString(this.font, this.title, x + (w/2) - (this.font.width(this.title)/2), y + 10, TEXT_MAIN, false);

        int row1Y = y + 45;
        gui.fill(x + 21, row1Y - 2, x + 20 + 109, row1Y + 16, INPUT_BG);
        gui.drawString(this.font, Component.translatable("gui.serialcraft.connector.port_label"), x + 21, row1Y - 12, TEXT_DIM, false);

        int statusColor = isConnected ? ACCENT_GREEN : ACCENT_RED;
        int statusWidth = this.font.width(statusText);
        gui.drawString(this.font, statusText, x + (w/2) - (statusWidth/2), y + 75, statusColor, false);

        gui.fill(x + 20, y + 85, x + w - 20, y + 86, 0xFFCCCCCC);

        int labelY = y + 88;
        gui.drawString(this.font, "Profile", x + 25, labelY, TEXT_DIM, false);
        gui.drawString(this.font, "Baud", x + 135, labelY, TEXT_DIM, false);
        gui.drawString(this.font, "Vel/Speed", x + 225, labelY, TEXT_DIM, false);

        gui.fill(x + 20, y + 125, x + w - 20, y + 126, 0xFFCCCCCC);
        gui.drawString(this.font, Component.translatable("gui.serialcraft.connector.boards_header"), x + 30, y + 130, TEXT_DIM, false);

        int listStartY = y + 140;
        if (boardList.isEmpty()) {
            Component noBoards = Component.translatable("gui.serialcraft.connector.no_boards");
            gui.drawString(this.font, noBoards, x + (w/2) - (this.font.width(noBoards)/2), listStartY + 20, 0xFFAAAAAA, false);
        } else {
            for (int i = 0; i < boardList.size(); i++) {
                BoardInfo info = boardList.get(i);
                int rowY = listStartY + (i * 24);
                if (rowY > y + h - 25) break;

                gui.fill(x + 25, rowY, x + w - 25, rowY + 20, CARD_BG);
                // REEMPLAZO DE renderOutline POR drawBorder
                drawBorder(gui, x + 25, rowY, w - 50, 20, 0xFFDDDDDD);

                String icon = (info.mode() == 0) ? "📤" : "📥";
                gui.drawString(this.font, icon, x + 30, rowY + 6, 0xFF000000, false);
                gui.drawString(this.font, info.id(), x + 50, rowY + 6, TEXT_MAIN, false);
                gui.drawString(this.font, "CMD: " + info.data(), x + 180, rowY + 6, TEXT_DIM, false);
            }
        }

        for (Renderable widget : this.uiWidgets) {
            widget.render(gui, mouseX, mouseY, partialTick);
        }
    }

    // MÉTODO AUXILIAR PARA BORDES
    private void drawBorder(GuiGraphics gui, int x, int y, int w, int h, int color) {
        gui.fill(x, y, x + w, y + 1, color);
        gui.fill(x, y + h - 1, x + w, y + h, color);
        gui.fill(x, y, x + 1, y + h, color);
        gui.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override public boolean isPauseScreen() { return false; }
}