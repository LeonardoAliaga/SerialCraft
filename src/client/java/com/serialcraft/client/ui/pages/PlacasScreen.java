package com.serialcraft.client.ui.pages;

import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.client.ui.NavBar;
import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.network.BoardInfo;
import com.serialcraft.network.BoardListRequestPayload;
import com.serialcraft.network.ConfigPayload;
import com.serialcraft.network.RemoteTogglePayload;
import com.serialcraft.screen.PanelUI;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PlacasScreen {

    private static final int TEXT_MAIN  = 0xFF212121;
    private static final int TEXT_DIM   = 0xFF757575;
    private static final int ACCENT     = 0xFF00838F;
    private static final int INPUT_BG   = 0xFF1A1A1A;
    private static final int INPUT_TEXT = 0xFFE0E0E0;
    private static final int BORDER_COL = 0xFFAAAAAA;
    private static final int CARD_BG    = 0xFFFFFFFF;
    private static final int CARD_LINE  = 0xFFE0E0E0;

    private final List<BoardInfo> boardList = new CopyOnWriteArrayList<>();

    private boolean   isEditing      = false;
    private BoardInfo selectedBoard  = null;
    private int       editIoMode     = 0;
    private int       editSignalType = 0;
    private int       editLogicMode  = 0;
    private boolean   editSoftOn     = true;

    private SolidButton logicBtnRef = null;
    private EditBox     editIdBox   = null;
    private EditBox     editDataBox = null;

    private PanelUI panelRef = null;

    private volatile boolean         pendingRebuild = false;
    private volatile List<BoardInfo> pendingBoards  = null;
    private boolean requestSent = false;
    private volatile boolean isLoading = false;

    public void init(PanelUI panel, int screenWidth, int screenHeight, @Nullable BlockPos directEditPos) {
        this.panelRef    = panel;
        this.logicBtnRef = null;
        this.editIdBox   = null;
        this.editDataBox = null;

        boolean isConnected = ConnectionManager.getSerial().isConnected() || ConnectionManager.getWifi().isConnected();

        // Apertura directa desde un ArduinoIOBlock en el mundo
        if (directEditPos != null && !isEditing) {
            if (isConnected) {
                prepareDirectEdit(directEditPos);
            } else {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("§c¡Debes conectar una placa (USB/Wi-Fi) para configurar el bloque!"), false
                    );
                }
            }
        }

        if (!isEditing) {
            if (!requestSent) {
                isLoading = true;
                ClientPlayNetworking.send(new BoardListRequestPayload(true));
                requestSent = true;
            }
            buildListWidgets(panel, screenWidth, screenHeight);
        } else {
            buildEditWidgets(panel, screenWidth, screenHeight);
        }
    }

    public void init(PanelUI panel, int screenWidth, int screenHeight) {
        init(panel, screenWidth, screenHeight, null);
    }

    public void tick() {
        if (pendingRebuild && panelRef != null) {
            List<BoardInfo> data = pendingBoards;
            pendingBoards  = null;
            pendingRebuild = false;
            isLoading      = false;

            if (data != null) {
                boardList.clear();
                boardList.addAll(data);
            }

            if (!isEditing) {
                panelRef.setTab(PanelUI.Tab.PLACAS);
            }
        }
    }

    public void onClose() {
        requestSent = false;
        isLoading   = false;
    }

    private void prepareDirectEdit(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        int    ioMode     = 0;
        int    signalType = 0;
        int    logicMode  = 0;
        String boardID    = "Arduino_1";
        String targetData = "cmd_1";
        boolean softOn    = true;

        if (mc.level.getBlockEntity(pos) instanceof ArduinoIOBlockEntity io) {
            ioMode     = io.ioMode;
            signalType = io.signalType;
            logicMode  = io.logicMode;
            boardID    = io.boardID;
            targetData = io.targetData;
            softOn     = io.isSoftOn;
        }

        this.selectedBoard  = new BoardInfo(pos, boardID, targetData, ioMode, softOn);
        this.editIoMode     = ioMode;
        this.editSignalType = signalType;
        this.editLogicMode  = logicMode;
        this.editSoftOn     = softOn;
        this.isEditing      = true;
    }

    private void buildListWidgets(PanelUI panel, int screenWidth, int screenHeight) {
        int navWidth  = NavBar.getNavBarWidth(screenWidth);
        int contentX  = navWidth + 20;
        int cardWidth = screenWidth - navWidth - 40;
        int cardY     = 75;

        for (BoardInfo board : boardList) {
            final BoardInfo b    = board;
            boolean         isOn = b.status();
            int             btnY = cardY + 13;

            IconTextButton toggleBtn = new IconTextButton(
                    contentX + cardWidth - 192, btnY, 88, 22,
                    (isOn ? SpriteIcon.CONNECT : SpriteIcon.DISCONNECT),
                    Component.literal(isOn ? "■  ON" : "□  OFF"),
                    (btn) -> {
                        ClientPlayNetworking.send(new RemoteTogglePayload(b.pos()));
                        requestSent = false;
                        isLoading   = true;
                        ClientPlayNetworking.send(new BoardListRequestPayload(true));
                        requestSent = true;
                    },
                    isOn ? 0xFF2E7D32 : 0xFFC62828,
                    isOn ? 0xFF1B5E20 : 0xFF8B0000,
                    0xFFFFFFFF
            );

            IconTextButton editBtn = new IconTextButton(
                    contentX + cardWidth - 100, btnY, 88, 22,
                    SpriteIcon.CODE,
                    Component.literal("Editar"),
                    (btn) -> {
                        boolean isConnected = ConnectionManager.getSerial().isConnected() || ConnectionManager.getWifi().isConnected();
                        if (isConnected) {
                            openEditor(panel, b);
                        } else {
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().player.displayClientMessage(
                                        Component.literal("§c¡Debes conectar una placa (USB/Wi-Fi) para configurar el bloque!"), false
                                );
                            }
                        }
                    },
                    0xFF00838F, 0xFF006064, 0xFFFFFFFF
            );

            panel.addWidget(toggleBtn);
            panel.addWidget(editBtn);
            cardY += 52;
        }
    }

    private void openEditor(PanelUI panel, BoardInfo board) {
        this.selectedBoard  = board;
        this.editIoMode     = board.mode();
        this.editSignalType = 0;
        this.editLogicMode  = 0;
        this.editSoftOn     = board.status();
        this.isEditing      = true;
        panel.setTab(PanelUI.Tab.PLACAS);
    }

    private void buildEditWidgets(PanelUI panel, int screenWidth, int screenHeight) {
        if (selectedBoard == null) { isEditing = false; return; }

        int navWidth = NavBar.getNavBarWidth(screenWidth);
        int cx = navWidth + 30;
        int cy = 55;
        int panelW = Math.min(320, screenWidth - navWidth - 60);

        editIdBox = new EditBox(Minecraft.getInstance().font, cx + 5, cy + 48, panelW - 90, 18, Component.literal("Board ID"));
        editIdBox.setValue(selectedBoard.id());
        editIdBox.setTextColor(INPUT_TEXT);
        editIdBox.setBordered(false);
        editIdBox.setMaxLength(32);
        panel.addWidget(editIdBox);

        SolidButton powerBtn = SolidButton.of(cx + panelW - 80, cy + 45, 70, 22, Component.literal(editSoftOn ? "■ ON" : "□ OFF"),
                b -> {
                    editSoftOn = !editSoftOn;
                    b.setMessage(Component.literal(editSoftOn ? "■ ON" : "□ OFF"));
                    b.setVariant(editSoftOn ? SolidButton.Variant.SUCCESS : SolidButton.Variant.DANGER);
                },
                editSoftOn ? SolidButton.Variant.SUCCESS : SolidButton.Variant.DANGER
        );
        panel.addWidget(powerBtn);

        int btnY = cy + 95;
        int btnW = (panelW - 20) / 3;
        int gap  = 5;

        logicBtnRef = SolidButton.primary(cx + (btnW + gap) * 2, btnY, btnW, 20, getLogicText(), b -> {
                    editLogicMode = (editLogicMode + 1) % 3;
                    b.setMessage(getLogicText());
                }
        );
        logicBtnRef.visible = (editIoMode == 1);
        panel.addWidget(logicBtnRef);

        SolidButton modeBtn = SolidButton.primary(cx, btnY, btnW, 20, getModeText(), b -> {
                    editIoMode = (editIoMode == 0) ? 1 : 0;
                    b.setMessage(getModeText());
                    if (logicBtnRef != null) logicBtnRef.visible = (editIoMode == 1);
                }
        );
        panel.addWidget(modeBtn);

        SolidButton signalBtn = SolidButton.primary(cx + btnW + gap, btnY, btnW, 20, getSignalText(), b -> {
                    editSignalType = (editSignalType == 0) ? 1 : 0;
                    b.setMessage(getSignalText());
                }
        );
        panel.addWidget(signalBtn);

        editDataBox = new EditBox(Minecraft.getInstance().font, cx + 5, cy + 157, panelW - 20, 18, Component.literal("Command"));
        editDataBox.setValue(selectedBoard.data());
        editDataBox.setTextColor(INPUT_TEXT);
        editDataBox.setBordered(false);
        editDataBox.setMaxLength(32);
        panel.addWidget(editDataBox);

        int halfW = (panelW - 20) / 2;
        panel.addWidget(SolidButton.success(cx + 5, cy + 200, halfW, 22, Component.literal("Guardar"), b -> saveAndReturn(panel)));
        panel.addWidget(SolidButton.soft(cx + halfW + 10, cy + 200, halfW, 22, Component.literal("Cancelar"), b -> cancelEdit(panel)));
    }

    private void saveAndReturn(PanelUI panel) {
        if (selectedBoard == null || editIdBox == null || editDataBox == null) return;
        ClientPlayNetworking.send(new ConfigPayload(
                selectedBoard.pos(), editIoMode, editDataBox.getValue(),
                editSignalType, editSoftOn, editIdBox.getValue(), editLogicMode
        ));
        isEditing     = false;
        selectedBoard = null;
        logicBtnRef   = null;
        requestSent   = false;
        panel.setTab(PanelUI.Tab.PLACAS);
    }

    private void cancelEdit(PanelUI panel) {
        isEditing     = false;
        selectedBoard = null;
        logicBtnRef   = null;
        panel.setTab(PanelUI.Tab.PLACAS);
    }

    public void render(GuiGraphics gui, int mouseX, int mouseY, Font font, int width, int height) {
        int navWidth = NavBar.getNavBarWidth(width);
        int cx       = navWidth + 20;

        float scale = 1.8f;
        gui.pose().pushMatrix();
        gui.pose().scale(scale, scale);
        gui.drawString(font, "PLACAS", (int)(cx / scale), (int)(22 / scale), 0xFFffc107, false);
        gui.drawString(font, "GESTOR DE IO", (int)(cx / scale) + font.width("PLACAS") + 4, (int)(22 / scale), TEXT_MAIN, false);
        gui.pose().popMatrix();

        if (isEditing) {
            renderEditView(gui, font, width, height, navWidth);
        } else {
            renderListView(gui, font, width, height, navWidth, cx);
        }
    }

    private void renderListView(GuiGraphics gui, Font font, int width, int height, int navWidth, int cx) {
        int cardWidth = width - navWidth - 40;
        int cardY     = 75;

        if (!boardList.isEmpty()) {
            String countLabel = boardList.size() + (boardList.size() == 1 ? " placa registrada" : " placas registradas");
            gui.drawString(font, countLabel, cx, 48, TEXT_DIM, false);
        }

        if (isLoading) {
            gui.drawString(font, "● Cargando placas...", cx, cardY + 10, 0xFF90CAF9, false);
            return;
        }

        if (boardList.isEmpty()) {
            gui.drawString(font, "No hay placas IO registradas en el mundo.", cx, cardY + 10, TEXT_DIM, false);
            gui.drawString(font, "Coloca un bloque IO cerca de la Laptop y vuelve a abrir este panel.", cx, cardY + 24, TEXT_DIM, false);
            return;
        }

        for (BoardInfo board : boardList) {
            gui.fill(cx + 2, cardY + 50, cx + cardWidth + 2, cardY + 52, 0x22000000);
            gui.fill(cx, cardY, cx + cardWidth, cardY + 48, CARD_BG);
            gui.fill(cx, cardY + 48, cx + cardWidth, cardY + 50, CARD_LINE);

            boolean isInput = (board.mode() == 1);
            int badgeBg  = isInput ? 0xFFE8F5E9 : 0xFFE3F2FD;
            int badgeTxt = isInput ? 0xFF2E7D32 : 0xFF1565C0;
            gui.fill(cx + 10, cardY + 10, cx + 40, cardY + 24, badgeBg);
            gui.drawString(font, isInput ? "IN" : "OUT", cx + 14, cardY + 13, badgeTxt, false);

            int ledColor = board.status() ? 0xFF4CAF50 : 0xFFf44336;
            gui.fill(cx + cardWidth - 215, cardY + 7, cx + cardWidth - 209, cardY + 13, ledColor);

            gui.drawString(font, board.id(), cx + 48, cardY + 10, TEXT_MAIN, false);
            gui.drawString(font, "CMD: " + board.data(), cx + 48, cardY + 24, TEXT_DIM,  false);
            gui.drawString(font, "Pos: " + board.pos().getX() + "  " + board.pos().getY() + "  " + board.pos().getZ(), cx + 48, cardY + 36, 0xFF9E9E9E, false);

            cardY += 52;
        }
    }

    private void renderEditView(GuiGraphics gui, Font font, int width, int height, int navWidth) {
        if (selectedBoard == null) return;

        int cx     = navWidth + 30;
        int cy     = 55;
        int panelW = Math.min(320, width - navWidth - 60);
        int panelH = 248;

        gui.fill(cx, cy, cx + panelW, cy + panelH, 0xFFF5F5F0);
        drawBorder(gui, cx, cy, panelW, panelH, BORDER_COL);

        gui.fill(cx, cy, cx + panelW, cy + 30, 0xFFE8E8E0);
        gui.drawCenteredString(font, Component.literal("Editar: " + selectedBoard.id()), cx + panelW / 2, cy + 10, TEXT_MAIN);

        gui.drawString(font, "Board ID", cx + 5, cy + 33, TEXT_DIM, false);
        gui.fill(cx + 4, cy + 42, cx + 4 + (panelW - 95), cy + 68, BORDER_COL);
        gui.fill(cx + 5, cy + 43, cx + 4 + (panelW - 96), cy + 67, INPUT_BG);

        gui.drawString(font, "Power", cx + panelW - 83, cy + 33, TEXT_DIM, false);

        gui.drawCenteredString(font, Component.literal("Modo  /  Señal" + (editIoMode == 1 ? "  /  Lógica" : "")), cx + panelW / 2, cy + 78, ACCENT);
        gui.fill(cx + 8, cy + 122, cx + panelW - 8, cy + 123, 0xFFD0D0D0);

        gui.drawString(font, "Comando (targetData)", cx + 5, cy + 130, TEXT_DIM, false);
        gui.fill(cx + 4, cy + 141, cx + 4 + (panelW - 20), cy + 177, BORDER_COL);
        gui.fill(cx + 5, cy + 142, cx + 4 + (panelW - 21), cy + 176, INPUT_BG);

        String cmdVal = (editDataBox != null) ? editDataBox.getValue() : selectedBoard.data();
        String helpText;
        if (editIoMode == 0) {
            helpText = editSignalType == 0 ? "OUT Digital → \"" + cmdVal + "\":0/1" : "OUT PWM → \"" + cmdVal + "\":0-255";
        } else {
            helpText = editSignalType == 0 ? "IN Digital → HIGH/LOW — ID \"" + cmdVal + "\"" : "IN Analog → 0-15 — ID \"" + cmdVal + "\"";
        }
        gui.drawCenteredString(font, Component.literal(helpText), cx + panelW / 2, cy + 182, 0xFF666666);
    }

    private Component getModeText() {
        return editIoMode == 0 ? Component.translatable("gui.serialcraft.mode.out") : Component.translatable("gui.serialcraft.mode.in");
    }

    private Component getSignalText() {
        return editSignalType == 0 ? Component.translatable("gui.serialcraft.signal.digital") : Component.translatable("gui.serialcraft.signal.analog");
    }

    private Component getLogicText() {
        return switch (editLogicMode) {
            case 0  -> Component.translatable("gui.serialcraft.logic.or");
            case 1  -> Component.translatable("gui.serialcraft.logic.and");
            default -> Component.translatable("gui.serialcraft.logic.xor");
        };
    }

    private static void drawBorder(GuiGraphics gui, int x, int y, int w, int h, int color) {
        gui.fill(x, y, x + w, y + 1, color);
        gui.fill(x, y + h - 1, x + w, y + h, color);
        gui.fill(x, y, x + 1, y + h, color);
        gui.fill(x + w - 1, y, x + w, y + h, color);
    }

    public void updateBoardList(List<BoardInfo> boards) {
        this.pendingBoards  = new ArrayList<>(boards);
        this.pendingRebuild = true;
    }
}