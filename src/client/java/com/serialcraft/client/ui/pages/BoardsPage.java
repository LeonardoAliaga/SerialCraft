package com.serialcraft.client.ui.pages;

import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.board.IoMode;
import com.serialcraft.board.LogicMode;
import com.serialcraft.board.SignalType;
import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
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
import net.minecraft.client.gui.GuiGraphicsExtractor ;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pagina "Placas": lista y editor.
 */
public class BoardsPage implements Page {

    private static final int CARD_TOP    = 75;
    private static final int EDITOR_TOP  = 55;
    private static final int EDITOR_W    = 320;
    private static final int EDITOR_H    = 248;

    // ── Estado de la lista ────────────────────────────────────────────────
    private final List<BoardInfo> boards = new ArrayList<>();
    private volatile @Nullable List<BoardInfo> incomingBoards = null;
    private boolean awaitingResponse = false;

    // ── Estado del editor ─────────────────────────────────────────────────
    private boolean editing = false;
    private @Nullable BoardInfo editTarget;
    private IoMode     editMode   = IoMode.OUTPUT;
    private SignalType editSignal = SignalType.DIGITAL;
    private LogicMode  editLogic  = LogicMode.OR;
    private boolean    editEnabled = true;

    private @Nullable SolidButton logicButton;
    private @Nullable EditBox     idBox;
    private @Nullable EditBox     dataBox;

    private @Nullable PanelUI panel;
    private @Nullable BlockPos directEditRequest;

    // ══════════════════════════════════════════════════════════════════════

    /** La invoca PanelUI cuando el panel se abre haciendo clic en una placa. */
    public void requestDirectEdit(BlockPos pos) {
        this.directEditRequest = pos;
    }

    @Override
    public void init(PanelUI panelUi, int screenWidth, int screenHeight) {
        this.panel       = panelUi;
        this.logicButton = null;
        this.idBox       = null;
        this.dataBox     = null;

        if (directEditRequest != null && !editing) {
            BlockPos pos = directEditRequest;
            directEditRequest = null;
            if (requireConnection()) openEditorFromWorld(pos);
        }

        if (editing) {
            buildEditor(panelUi, screenWidth);
        } else {
            requestBoardList();
            buildList(panelUi, screenWidth);
        }
    }

    @Override
    public void tick() {
        List<BoardInfo> incoming = incomingBoards;
        if (incoming == null) return;
        incomingBoards   = null;
        awaitingResponse = false;

        // Solo reconstruir si la lista cambio de verdad. El original
        // reconstruia siempre, destruyendo el foco de los campos de texto.
        if (incoming.equals(boards)) return;

        boards.clear();
        boards.addAll(incoming);
        if (!editing && panel != null) panel.setTab(PanelUI.Tab.BOARDS);
    }

    @Override
    public void onClose() {
        awaitingResponse = false;
        incomingBoards   = null;
    }

    /** Punto de entrada desde la red. Se llama en el hilo del cliente. */
    public void acceptBoardList(List<BoardInfo> received) {
        this.incomingBoards = List.copyOf(received);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  LISTA
    // ══════════════════════════════════════════════════════════════════════

    private void requestBoardList() {
        if (awaitingResponse) return;
        if (!ClientPlayNetworking.canSend(BoardListRequestPayload.TYPE)) return;
        awaitingResponse = true;
        ClientPlayNetworking.send(BoardListRequestPayload.INSTANCE);
    }

    private void buildList(PanelUI panelUi, int screenWidth) {
        int contentX  = UiTheme.contentX(screenWidth);
        int cardWidth = screenWidth - contentX - UiTheme.CONTENT_MARGIN;
        int cardY     = CARD_TOP;

        for (BoardInfo board : boards) {
            final BoardInfo target = board;
            boolean on = board.enabled();
            int buttonY = cardY + 13;

            panelUi.addWidget(new IconTextButton(
                    contentX + cardWidth - 192, buttonY, 88, 22,
                    on ? SpriteIcon.CONNECT : SpriteIcon.DISCONNECT,
                    Component.translatable(on ? "gui.serialcraft.boards.on"
                                              : "gui.serialcraft.boards.off"),
                    btn -> toggleBoard(target),
                    on ? UiTheme.OK_DARK    : UiTheme.ERROR_DARK,
                    on ? 0xFF1B5E20         : 0xFF8B0000,
                    UiTheme.TEXT_INVERSE
            ));

            panelUi.addWidget(new IconTextButton(
                    contentX + cardWidth - 100, buttonY, 88, 22,
                    SpriteIcon.CODE,
                    Component.translatable("gui.serialcraft.boards.edit"),
                    btn -> { if (requireConnection()) openEditor(target); },
                    UiTheme.ACCENT_PRIMARY, UiTheme.ACCENT_PRIMARY_DARK, UiTheme.TEXT_INVERSE
            ));

            cardY += UiTheme.CARD_ROW_HEIGHT;
        }
    }

    private void toggleBoard(BoardInfo board) {
        if (!ClientPlayNetworking.canSend(RemoteTogglePayload.TYPE)) return;
        ClientPlayNetworking.send(new RemoteTogglePayload(board.pos()));
        // Una sola peticion, sin la ventana de carrera del original.
        awaitingResponse = false;
        requestBoardList();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EDITOR
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Comprueba que haya hardware conectado antes de dejar editar.
     *
     * Es una restriccion de diseno, no tecnica: una placa se puede configurar
     * sin hardware. Se mantiene el comportamiento original, pero el mensaje
     * ahora esta traducido en vez de ser una cadena con codigos de color
     * incrustados dentro del codigo Java.
     */
    private boolean requireConnection() {
        if (ConnectionManager.isAnyConnected()) return true;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(
                    Component.translatable("message.serialcraft.needs_connection")
                            .withStyle(net.minecraft.ChatFormatting.RED));
        }
        return false;
    }

    private void openEditor(BoardInfo board) {
        this.editTarget  = board;
        this.editMode    = board.mode();
        this.editEnabled = board.enabled();
        this.editSignal  = SignalType.DIGITAL;
        this.editLogic   = LogicMode.OR;
        this.editing     = true;
        if (panel != null) panel.setTab(PanelUI.Tab.BOARDS);
    }

    /** Abre el editor leyendo el estado real del BlockEntity del cliente. */
    private void openEditorFromWorld(BlockPos pos) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        if (client.level.getBlockEntity(pos) instanceof ArduinoIOBlockEntity io) {
            this.editTarget  = io.toBoardInfo();
            this.editMode    = io.getIoMode();
            this.editSignal  = io.getSignalType();
            this.editLogic   = io.getLogicMode();
            this.editEnabled = io.isEnabled();
        } else {
            this.editTarget  = new BoardInfo(pos, ArduinoIOBlockEntity.DEFAULT_BOARD_ID,
                                             ArduinoIOBlockEntity.DEFAULT_TARGET_DATA,
                                             IoMode.OUTPUT, true);
            this.editMode    = IoMode.OUTPUT;
            this.editSignal  = SignalType.DIGITAL;
            this.editLogic   = LogicMode.OR;
            this.editEnabled = true;
        }
        this.editing = true;
    }

    private void buildEditor(PanelUI panelUi, int screenWidth) {
        if (editTarget == null) { editing = false; return; }

        int x      = UiTheme.contentX(screenWidth) + 10;
        int y      = EDITOR_TOP;
        int width  = Math.min(EDITOR_W, screenWidth - UiTheme.navWidth(screenWidth) - 60);
        Font font  = Minecraft.getInstance().font;

        idBox = new EditBox(font, x + 5, y + 48, width - 90, 18,
                Component.translatable("gui.serialcraft.editor.board_id"));
        idBox.setValue(editTarget.id());
        idBox.setTextColor(UiTheme.TEXT_ON_DARK);
        idBox.setBordered(false);
        idBox.setMaxLength(BoardInfo.MAX_ID_LENGTH);
        panelUi.addWidget(idBox);

        panelUi.addWidget(SolidButton.of(x + width - 80, y + 45, 70, 22,
                powerLabel(), btn -> {
                    editEnabled = !editEnabled;
                    btn.setMessage(powerLabel());
                    btn.setVariant(editEnabled ? SolidButton.Variant.SUCCESS
                                               : SolidButton.Variant.DANGER);
                },
                editEnabled ? SolidButton.Variant.SUCCESS : SolidButton.Variant.DANGER));

        int rowY  = y + 95;
        int thirdW = (width - 20) / 3;
        int gap    = 5;

        panelUi.addWidget(SolidButton.primary(x, rowY, thirdW, 20, modeLabel(), btn -> {
            editMode = (editMode == IoMode.OUTPUT) ? IoMode.INPUT : IoMode.OUTPUT;
            btn.setMessage(modeLabel());
            if (logicButton != null) logicButton.visible = editMode.isInput();
        }));

        panelUi.addWidget(SolidButton.primary(x + thirdW + gap, rowY, thirdW, 20, signalLabel(), btn -> {
            editSignal = (editSignal == SignalType.DIGITAL) ? SignalType.ANALOG : SignalType.DIGITAL;
            btn.setMessage(signalLabel());
        }));

        logicButton = SolidButton.primary(x + (thirdW + gap) * 2, rowY, thirdW, 20, logicLabel(), btn -> {
            editLogic = LogicMode.byId((editLogic.ordinal() + 1) % LogicMode.VALUES.length);
            btn.setMessage(logicLabel());
        });
        logicButton.visible = editMode.isInput();
        panelUi.addWidget(logicButton);

        dataBox = new EditBox(font, x + 5, y + 157, width - 20, 18,
                Component.translatable("gui.serialcraft.editor.command"));
        dataBox.setValue(editTarget.data());
        dataBox.setTextColor(UiTheme.TEXT_ON_DARK);
        dataBox.setBordered(false);
        dataBox.setMaxLength(BoardInfo.MAX_DATA_LENGTH);
        panelUi.addWidget(dataBox);

        int halfW = (width - 20) / 2;
        panelUi.addWidget(SolidButton.success(x + 5, y + 200, halfW, 22,
                Component.translatable("gui.serialcraft.editor.save"), btn -> save()));
        panelUi.addWidget(SolidButton.soft(x + halfW + 10, y + 200, halfW, 22,
                Component.translatable("gui.serialcraft.editor.cancel"), btn -> cancel()));
    }

    private void save() {
        if (editTarget == null || idBox == null || dataBox == null) return;
        if (!ClientPlayNetworking.canSend(ConfigPayload.TYPE)) return;

        ClientPlayNetworking.send(new ConfigPayload(
                editTarget.pos(), editMode, dataBox.getValue(),
                editSignal, editEnabled, idBox.getValue(), editLogic));

        closeEditor();
    }

    private void cancel() { closeEditor(); }

    private void closeEditor() {
        editing          = false;
        editTarget       = null;
        logicButton      = null;
        awaitingResponse = false;
        if (panel != null) panel.setTab(PanelUI.Tab.BOARDS);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RENDER
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, Font font,
                       int screenWidth, int screenHeight) {
        int contentX = UiTheme.contentX(screenWidth);

        UiDraw.pageTitle(gui, font, contentX,
                Component.translatable("gui.serialcraft.boards.title"), UiTheme.ACCENT_BOARDS,
                Component.translatable("gui.serialcraft.boards.subtitle"));

        if (editing) renderEditor(gui, font, screenWidth);
        else         renderList(gui, font, screenWidth, contentX);
    }

    private void renderList(GuiGraphicsExtractor gui, Font font, int screenWidth, int contentX) {
        int cardWidth = screenWidth - contentX - UiTheme.CONTENT_MARGIN;
        int cardY     = CARD_TOP;

        if (awaitingResponse && boards.isEmpty()) {
            gui.text(font, Component.translatable("gui.serialcraft.boards.loading"),
                    contentX, cardY + 10, 0xFF90CAF9, false);
            return;
        }

        if (boards.isEmpty()) {
            gui.text(font, Component.translatable("gui.serialcraft.boards.empty"),
                    contentX, cardY + 10, UiTheme.TEXT_SECONDARY, false);
            gui.text(font, Component.translatable("gui.serialcraft.boards.empty_hint"),
                    contentX, cardY + 24, UiTheme.TEXT_SECONDARY, false);
            return;
        }

        gui.text(font, Component.translatable(
                        boards.size() == 1 ? "gui.serialcraft.boards.count_one"
                                           : "gui.serialcraft.boards.count_many", boards.size()),
                contentX, 48, UiTheme.TEXT_SECONDARY, false);

        for (BoardInfo board : boards) {
            UiDraw.card(gui, contentX, cardY, cardWidth, UiTheme.CARD_HEIGHT);

            boolean input = board.mode().isInput();
            UiDraw.badge(gui, font, contentX + 10, cardY + 10,
                    Component.translatable(input ? "gui.serialcraft.boards.badge_in"
                                                 : "gui.serialcraft.boards.badge_out"),
                    input ? UiTheme.OK_BG   : 0xFFE3F2FD,
                    input ? UiTheme.OK_DARK : UiTheme.INFO_DARK);

            gui.fill(contentX + cardWidth - 215, cardY + 7,
                     contentX + cardWidth - 209, cardY + 13,
                     board.enabled() ? UiTheme.OK : UiTheme.ERROR);

            gui.text(font, board.id(), contentX + 48, cardY + 10, UiTheme.TEXT_PRIMARY, false);
            gui.text(font, Component.translatable("gui.serialcraft.boards.cmd", board.data()),
                    contentX + 48, cardY + 24, UiTheme.TEXT_SECONDARY, false);
            gui.text(font, Component.translatable("gui.serialcraft.boards.pos",
                            board.pos().getX(), board.pos().getY(), board.pos().getZ()),
                    contentX + 48, cardY + 36, UiTheme.TEXT_MUTED, false);

            cardY += UiTheme.CARD_ROW_HEIGHT;
        }
    }

    private void renderEditor(GuiGraphicsExtractor  gui, Font font, int screenWidth) {
        if (editTarget == null) return;

        int x     = UiTheme.contentX(screenWidth) + 10;
        int y     = EDITOR_TOP;
        int width = Math.min(EDITOR_W, screenWidth - UiTheme.navWidth(screenWidth) - 60);

        gui.fill(x, y, x + width, y + EDITOR_H, UiTheme.BG_PANEL);
        gui.outline(x, y, width, EDITOR_H, UiTheme.LINE_STRONG);

        gui.fill(x, y, x + width, y + 30, UiTheme.BG_PANEL_HEAD);
        gui.centeredText(font,
                Component.translatable("gui.serialcraft.editor.title", editTarget.id()),
                x + width / 2, y + 10, UiTheme.TEXT_PRIMARY);

        gui.text(font, Component.translatable("gui.serialcraft.editor.board_id"),
                x + 5, y + 33, UiTheme.TEXT_SECONDARY, false);
        UiDraw.inputWell(gui, x + 4, y + 42, width - 91, 26);

        gui.text(font, Component.translatable("gui.serialcraft.editor.power"),
                x + width - 83, y + 33, UiTheme.TEXT_SECONDARY, false);

        gui.centeredText(font, Component.translatable(
                        editMode.isInput() ? "gui.serialcraft.editor.section_mode_logic"
                                           : "gui.serialcraft.editor.section_mode"),
                x + width / 2, y + 78, UiTheme.ACCENT_PRIMARY);
        gui.fill(x + 8, y + 122, x + width - 8, y + 123, UiTheme.LINE_SOFT);

        gui.text(font, Component.translatable("gui.serialcraft.editor.command"),
                x + 5, y + 130, UiTheme.TEXT_SECONDARY, false);
        UiDraw.inputWell(gui, x + 4, y + 141, width - 16, 36);

        String command = (dataBox != null) ? dataBox.getValue() : editTarget.data();
        gui.centeredText(font, Component.translatable(helpKey(), command),
                x + width / 2, y + 182, 0xFF666666);
    }

    private String helpKey() {
        boolean digital = editSignal == SignalType.DIGITAL;
        if (editMode.isOutput()) {
            return digital ? "gui.serialcraft.help.out_digital" : "gui.serialcraft.help.out_analog";
        }
        return digital ? "gui.serialcraft.help.in_digital" : "gui.serialcraft.help.in_analog";
    }

    // ── Etiquetas ─────────────────────────────────────────────────────────

    private Component powerLabel() {
        return Component.translatable(editEnabled ? "gui.serialcraft.editor.power_on"
                                                  : "gui.serialcraft.editor.power_off");
    }

    private Component modeLabel() {
        return Component.translatable("gui.serialcraft.mode." + editMode.getSerializedName());
    }

    private Component signalLabel() {
        return Component.translatable("gui.serialcraft.signal." + editSignal.getSerializedName());
    }

    private Component logicLabel() {
        return Component.translatable("gui.serialcraft.logic." + editLogic.getSerializedName());
    }
}
