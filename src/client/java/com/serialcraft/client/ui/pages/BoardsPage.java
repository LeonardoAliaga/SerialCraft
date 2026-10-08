package com.serialcraft.client.ui.pages;

import com.serialcraft.block.entity.HardwareIOBlockEntity;
import com.serialcraft.block.IOSide;
import com.serialcraft.board.IoMode;
import com.serialcraft.board.LogicMode;
import com.serialcraft.board.SignalType;
import com.serialcraft.client.ui.ScrollState;
import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.network.BoardInfo;
import com.serialcraft.network.BoardListRequestPayload;
import com.serialcraft.network.ConfigPayload;
import com.serialcraft.network.RemoteTogglePayload;
import com.serialcraft.network.IoSnapshot;
import com.serialcraft.network.SignalProtocol;
import com.serialcraft.network.ConfigResultPayload;
import com.serialcraft.screen.PanelUI;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pagina "Placas": gestion de todos los Bloques IO del jugador.
 *
 * Incluye barra de desplazamiento (scroll) con rueda y arrastre de raton,
 * adaptabilidad completa a diferentes resoluciones de pantalla y centrado
 * dinamico del editor.
 */
public class BoardsPage implements Page {

    private static final int CARD_TOP   = 56;
    private static final int EDITOR_W   = 280;
    private static final int EDITOR_H   = 232;

    private final List<BoardInfo> boards = new ArrayList<>();
    private final ScrollState scroll = new ScrollState();

    private record BoardCardWidgets(IconTextButton toggleBtn, IconTextButton editBtn, int baseButtonY) {}
    private final List<BoardCardWidgets> cardWidgets = new ArrayList<>();

    private PanelUI panel;
    private int screenWidth;
    private int screenHeight;
    private boolean awaitingResponse = false;
    private long listRequestedAt;
    private String listMessage = "";
    private String listDimension = "";
    private @Nullable List<BoardInfo> incomingBoards = null;
    private @Nullable BlockPos directEditRequestPos = null;

    // ── Estado del editor ─────────────────────────────────────────────────────
    private boolean editing = false;
    private @Nullable BoardInfo editTarget = null;
    private IoMode     editMode    = IoMode.OUTPUT;
    private SignalType editSignal  = SignalType.DIGITAL;
    private LogicMode  editLogic   = LogicMode.OR;
    private boolean    editEnabled = true;
    private String editId = "";
    private String editData = "";
    private String editDimension = "";
    private int editSides;
    private boolean diagnostics;
    private boolean saving;
    private long saveStarted;
    private String editorMessage = "";
    private static int nextRequestId;
    private int pendingRequestId;

    private @Nullable EditBox     idBox;
    private @Nullable EditBox     dataBox;
    private @Nullable SolidButton logicButton;

    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void init(PanelUI panelUi, int screenWidth, int screenHeight) {
        this.panel        = panelUi;
        this.screenWidth  = screenWidth;
        this.screenHeight = screenHeight;
        this.cardWidgets.clear();
        updateDimension();

        if (directEditRequestPos != null) {
            BlockPos target = directEditRequestPos;
            directEditRequestPos = null;
            openEditorForPos(target, false);
            if (editing) buildEditor(panelUi, screenWidth, screenHeight);
            else buildList(panelUi, screenWidth);
            return;
        }

        if (editing) {
            buildEditor(panelUi, screenWidth, screenHeight);
            return;
        }

        requestBoardList();
        buildList(panelUi, screenWidth);
    }

    public void requestDirectEdit(BlockPos pos) {
        this.directEditRequestPos = pos;
    }

    private void openEditorForPos(BlockPos pos, boolean refreshUI) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof HardwareIOBlockEntity io) {
            openEditor(io.toBoardInfo(), refreshUI);
            return;
        }
        for (BoardInfo b : boards) {
            if (b.pos().equals(pos)) {
                openEditor(b, refreshUI);
                return;
            }
        }
        listMessage = "gui.serialcraft.editor.unavailable";
    }

    private void openEditor(BoardInfo board) {
        openEditor(board, true);
    }

    private void openEditor(BoardInfo board, boolean refreshUI) {
        this.editing     = true;
        this.editTarget  = board;
        this.editMode    = board.mode();
        this.editSignal  = board.signalType();
        this.editLogic   = board.logicMode();
        this.editEnabled = board.enabled();
        this.editId = board.id();
        this.editData = board.data();
        this.editSides = board.snapshot().sides();
        this.diagnostics = false;
        this.saving = false;
        this.editorMessage = "";
        var level = Minecraft.getInstance().level;
        this.editDimension = level == null ? "" : level.dimension().identifier().toString();

        if (refreshUI && panel != null && panel.getCurrentTab() != PanelUI.Tab.BOARDS) {
            panel.setTab(PanelUI.Tab.BOARDS);
        } else if (refreshUI && panel != null) {
            panel.refresh();
        }
    }

    @Override
    public void tick() {
        if (updateDimension() && panel != null) panel.refresh();
        if (awaitingResponse && System.nanoTime() - listRequestedAt > 5_000_000_000L) {
            awaitingResponse = false;
            listMessage = "gui.serialcraft.boards.timeout";
        }
        if (saving && System.nanoTime() - saveStarted > 5_000_000_000L) {
            saving = false;
            editorMessage = "gui.serialcraft.editor.timeout";
            if (panel != null) panel.refresh();
        }
        if (incomingBoards == null) return;
        List<BoardInfo> incoming = incomingBoards;
        incomingBoards = null;
        awaitingResponse = false;
        listMessage = "";

        if (incoming.equals(boards)) return;

        boards.clear();
        boards.addAll(incoming);
        if (!editing && panel != null) panel.refresh();
    }

    private boolean updateDimension() {
        var level = Minecraft.getInstance().level;
        String dimension = level == null ? "" : level.dimension().identifier().toString();
        if (listDimension.equals(dimension)) return false;
        boolean wasInitialized = !listDimension.isEmpty();
        listDimension = dimension;
        boards.clear(); incomingBoards = null; awaitingResponse = false;
        editing = saving = false; editTarget = null;
        if (wasInitialized) directEditRequestPos = null;
        return wasInitialized;
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

    // ── LISTA CON SCROLL ──────────────────────────────────────────────────────

    private void requestBoardList() {
        if (awaitingResponse) return;
        if (!ClientPlayNetworking.canSend(BoardListRequestPayload.TYPE)) {
            listMessage = "gui.serialcraft.editor.incompatible";
            return;
        }
        awaitingResponse = true;
        listRequestedAt = System.nanoTime();
        listMessage = "";
        ClientPlayNetworking.send(BoardListRequestPayload.INSTANCE);
    }

    private void buildList(PanelUI panelUi, int screenWidth) {
        int contentX  = UiTheme.contentX(screenWidth);
        int cardWidth = Math.max(160, UiTheme.contentWidth(screenWidth) - (scroll.hasScroll() ? 10 : 0));
        int cardY     = CARD_TOP;

        cardWidgets.clear();

        for (BoardInfo board : boards) {
            final BoardInfo target = board;
            boolean on = board.enabled();
            int buttonY = cardY + 13;

            int btnW = Math.clamp((cardWidth - 110) / 2, 60, 88);
            int editBtnX = contentX + cardWidth - btnW - 8;
            int toggleBtnX = editBtnX - btnW - 6;

            IconTextButton toggleBtn = new IconTextButton(
                    toggleBtnX, buttonY, btnW, 22,
                    on ? SpriteIcon.CONNECT : SpriteIcon.DISCONNECT,
                    Component.translatable(on ? "gui.serialcraft.boards.on"
                                              : "gui.serialcraft.boards.off"),
                    btn -> toggleBoard(target),
                    on ? UiTheme.OK_DARK    : UiTheme.ERROR_DARK,
                    on ? 0xFF1B5E20         : 0xFF8B0000,
                    UiTheme.TEXT_INVERSE
            );
            panelUi.addWidget(toggleBtn);

            IconTextButton editBtn = new IconTextButton(
                    editBtnX, buttonY, btnW, 22,
                    SpriteIcon.CODE,
                    Component.translatable("gui.serialcraft.boards.edit"),
                    btn -> openEditor(target),
                    UiTheme.ACCENT_PRIMARY, UiTheme.ACCENT_PRIMARY_DARK, UiTheme.TEXT_INVERSE
            );
            panelUi.addWidget(editBtn);

            cardWidgets.add(new BoardCardWidgets(toggleBtn, editBtn, buttonY));
            cardY += UiTheme.CARD_ROW_HEIGHT;
        }
    }

    private void toggleBoard(BoardInfo board) {
        if (!ClientPlayNetworking.canSend(RemoteTogglePayload.TYPE)) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        ClientPlayNetworking.send(new RemoteTogglePayload(board.pos(), level.dimension().identifier().toString()));
        awaitingResponse = false;
        requestBoardList();
        if (panel != null) panel.refresh();
    }

    // ── EVENTOS DE RATÓN Y DESPLAZAMIENTO ─────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (editing) return false;
        return scroll.mouseScrolled(verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        if (editing) return false;
        int contentX = UiTheme.contentX(screenWidth);
        int cardWidth = Math.max(160, UiTheme.contentWidth(screenWidth) - (scroll.hasScroll() ? 10 : 0));
        int viewportTop = CARD_TOP;
        int viewportBottom = screenHeight - UiTheme.contentMargin(screenWidth);
        int viewportHeight = viewportBottom - viewportTop;
        return scroll.mouseClicked(event.x(), event.y(), event.button(),
                contentX + cardWidth + 2, viewportTop, 8, viewportHeight);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return scroll.mouseReleased(event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (editing) return false;
        int viewportTop = CARD_TOP;
        int viewportBottom = screenHeight - UiTheme.contentMargin(screenWidth);
        int viewportHeight = viewportBottom - viewportTop;
        return scroll.mouseDragged(event.y(), viewportTop, viewportHeight);
    }

    // ── EDITOR ────────────────────────────────────────────────────────────────

    private void buildEditor(PanelUI panelUi, int screenWidth, int screenHeight) {
        if (editTarget == null) return;

        Font font = Minecraft.getInstance().font;
        int availW = UiTheme.contentWidth(screenWidth);
        int width  = Math.min(EDITOR_W, availW);
        int x      = UiTheme.contentX(screenWidth) + Math.max(0, (availW - width) / 2);
        int y      = Math.max(4, (screenHeight - EDITOR_H) / 2);

        if (!diagnostics) {
            idBox = new EditBox(font, x + 8, y + 38, width - 96, 20,
                    Component.translatable("gui.serialcraft.editor.board_id"));
            idBox.setMaxLength(BoardInfo.MAX_ID_LENGTH);
            idBox.setValue(editId);
            idBox.setResponder(value -> editId = value);
            idBox.active = !saving;
            panelUi.addWidget(idBox);
            var power = SolidButton.of(x + width - 82, y + 38, 74, 20, powerLabel(), btn -> {
                editEnabled = !editEnabled;
                btn.setMessage(powerLabel());
                btn.setVariant(editEnabled ? SolidButton.Variant.SUCCESS : SolidButton.Variant.DANGER);
            }, editEnabled ? SolidButton.Variant.SUCCESS : SolidButton.Variant.DANGER);
            power.active = !saving;
            panelUi.addWidget(power);

            dataBox = new EditBox(font, x + 8, y + 78, width - 16, 20,
                    Component.translatable("gui.serialcraft.editor.command"));
            dataBox.setMaxLength(BoardInfo.MAX_DATA_LENGTH);
            dataBox.setValue(editData);
            dataBox.setResponder(value -> editData = value);
            dataBox.setTooltip(Tooltip.create(Component.translatable("gui.serialcraft.editor.channel_help")));
            dataBox.active = !saving;
            panelUi.addWidget(dataBox);

            var direction = SolidButton.primary(x + 8, y + 104, width - 16, 20, modeLabel(), btn -> {
                editMode = editMode.isOutput() ? IoMode.INPUT : IoMode.OUTPUT;
                btn.setMessage(modeLabel());
            });
            direction.active = !saving;
            direction.setTooltip(Tooltip.create(Component.translatable("gui.serialcraft.editor.directions_help")));
            panelUi.addWidget(direction);
            int half = (width - 20) / 2;
            var signal = SolidButton.primary(x + 8, y + 130, half, 20, signalLabel(), btn -> {
                editSignal = editSignal == SignalType.DIGITAL ? SignalType.ANALOG : SignalType.DIGITAL;
                btn.setMessage(signalLabel());
            });
            signal.active = !saving;
            panelUi.addWidget(signal);
            logicButton = SolidButton.primary(x + 12 + half, y + 130, half, 20, logicLabel(), btn -> {
                editLogic = editLogic.next();
                btn.setMessage(logicLabel());
            });
            logicButton.active = !saving;
            logicButton.setTooltip(Tooltip.create(Component.translatable("gui.serialcraft.editor.logic_help")));
            panelUi.addWidget(logicButton);
        } else {
            String[] faces = {"north", "south", "east", "west", "down"};
            for (int i = 0; i < faces.length; i++) {
                final int index = i;
                var button = SolidButton.primary(x + 8, y + 34 + i * 22, width - 16, 20,
                        sideLabel(faces[i], IOSide.at(editSides, i)), btn -> {
                            editSides = IOSide.with(editSides, index, IOSide.at(editSides, index).next());
                            btn.setMessage(sideLabel(faces[index], IOSide.at(editSides, index)));
                        });
                button.active = !saving;
                button.setTooltip(Tooltip.create(Component.translatable("gui.serialcraft.editor.connectors_help")));
                panelUi.addWidget(button);
            }
        }
        var detail = SolidButton.soft(x + 8, y + 174, width - 16, 20,
                Component.translatable(diagnostics ? "gui.serialcraft.editor.basic" : "gui.serialcraft.editor.diagnostics"), btn -> {
                    diagnostics = !diagnostics;
                    panelUi.refresh();
                });
        detail.active = !saving;
        panelUi.addWidget(detail);
        int halfW = (width - 20) / 2;
        var save = SolidButton.success(x + 6, y + 200, halfW, 22,
                Component.translatable(saving ? "gui.serialcraft.editor.saving" : "gui.serialcraft.editor.save"), btn -> save());
        save.active = !saving;
        panelUi.addWidget(save);
        panelUi.addWidget(SolidButton.soft(x + halfW + 14, y + 200, halfW, 22,
                Component.translatable("gui.serialcraft.editor.cancel"), btn -> cancel()));
    }

    private Component sideLabel(String face, IOSide side) {
        return Component.translatable("gui.serialcraft.editor.side",
                Component.translatable("gui.serialcraft.face." + face),
                Component.translatable(side == IOSide.OUTPUT && editMode.isOutput()
                        ? "gui.serialcraft.side.output_inactive" : "gui.serialcraft.side." + side.getSerializedName()));
    }

    private void save() {
        if (editTarget == null || saving) return;
        if (!ClientPlayNetworking.canSend(ConfigPayload.TYPE)) {
            editorMessage = "gui.serialcraft.editor.incompatible";
            return;
        }
        String channel = editData.trim();
        if (!SignalProtocol.isValidChannel(channel)) {
            editorMessage = "gui.serialcraft.editor.invalid_channel";
            return;
        }
        saving = true;
        saveStarted = System.nanoTime();
        pendingRequestId = ++nextRequestId;
        editorMessage = "";
        ClientPlayNetworking.send(new ConfigPayload(editTarget.pos(), editMode, channel, editSignal,
                editEnabled, editId.trim(), editLogic, editSides, editDimension, pendingRequestId));
        if (panel != null) panel.refresh();
    }

    public void acceptConfigResult(ConfigResultPayload result) {
        if (!saving || editTarget == null || !editTarget.pos().equals(result.pos()) || pendingRequestId != result.requestId()) return;
        saving = false;
        if (result.accepted()) closeEditor();
        else {
            editorMessage = result.reason();
            if (panel != null) panel.refresh();
        }
    }

    private void cancel() { closeEditor(); }

    private void closeEditor() {
        editing          = false;
        editTarget       = null;
        logicButton      = null;
        saving = false;
        awaitingResponse = false;
        if (panel != null) panel.refresh();
    }

    // ── RENDER ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, Font font,
                       int screenWidth, int screenHeight) {
        int contentX = UiTheme.contentX(screenWidth);

        UiDraw.pageTitle(gui, font, contentX,
                Component.translatable("gui.serialcraft.boards.title"), UiTheme.ACCENT_BOARDS,
                Component.translatable("gui.serialcraft.boards.subtitle"));

        if (editing) renderEditor(gui, font, screenWidth, screenHeight);
        else         renderList(gui, font, screenWidth, screenHeight, contentX);
    }

    private void renderList(GuiGraphicsExtractor gui, Font font, int screenWidth, int screenHeight, int contentX) {
        int cardWidth = Math.max(160, UiTheme.contentWidth(screenWidth) - (scroll.hasScroll() ? 10 : 0));
        int viewportTop = CARD_TOP;
        int viewportBottom = screenHeight - UiTheme.contentMargin(screenWidth);
        int viewportHeight = Math.max(10, viewportBottom - viewportTop);
        int totalHeight = boards.size() * UiTheme.CARD_ROW_HEIGHT;

        scroll.update(viewportHeight, totalHeight);
        int scrollY = (int) scroll.getScrollAmount();

        if (awaitingResponse && boards.isEmpty()) {
            gui.text(font, Component.translatable("gui.serialcraft.boards.loading"),
                    contentX, viewportTop + 10, 0xFF90CAF9, false);
            return;
        }

        if (boards.isEmpty()) {
            if (!listMessage.isEmpty()) {
                gui.text(font, font.plainSubstrByWidth(Component.translatable(listMessage).getString(), cardWidth),
                        contentX, viewportTop + 10, UiTheme.ERROR_DARK, false);
                return;
            }
            gui.text(font, Component.translatable("gui.serialcraft.boards.empty"),
                    contentX, viewportTop + 10, UiTheme.TEXT_SECONDARY, false);
            gui.text(font, Component.translatable("gui.serialcraft.boards.empty_hint"),
                    contentX, viewportTop + 24, UiTheme.TEXT_SECONDARY, false);
            return;
        }

        gui.text(font, Component.translatable(
                        boards.size() == 1 ? "gui.serialcraft.boards.count_one"
                                           : "gui.serialcraft.boards.count_many", boards.size()),
                contentX, 42, UiTheme.TEXT_SECONDARY, false);

        // Actualizar posiciones y visibilidad de los widgets segun el scroll
        for (BoardCardWidgets cw : cardWidgets) {
            int currentBtnY = cw.baseButtonY - scrollY;
            cw.toggleBtn().setY(currentBtnY);
            cw.editBtn().setY(currentBtnY);

            boolean inView = (currentBtnY >= viewportTop
                           && currentBtnY + cw.toggleBtn().getHeight() <= viewportBottom);
            cw.toggleBtn().visible = inView;
            cw.toggleBtn().active  = inView;
            cw.editBtn().visible   = inView;
            cw.editBtn().active    = inView;
        }

        // Renderizado recortado dentro del viewport
        gui.enableScissor(contentX - 2, viewportTop, screenWidth, viewportBottom);

        int cardY = CARD_TOP - scrollY;
        for (BoardInfo board : boards) {
            if (cardY + UiTheme.CARD_HEIGHT >= viewportTop && cardY <= viewportBottom) {
                UiDraw.card(gui, contentX, cardY, cardWidth, UiTheme.CARD_HEIGHT);

                boolean input = board.mode().isInput();
                UiDraw.badge(gui, font, contentX + 10, cardY + 10,
                        Component.translatable(input ? "gui.serialcraft.boards.badge_in"
                                                     : "gui.serialcraft.boards.badge_out"),
                        input ? UiTheme.OK_BG   : 0xFFE3F2FD,
                        input ? UiTheme.OK_DARK : UiTheme.INFO_DARK);

                int maxTextW = Math.max(40, cardWidth - 210);

                gui.text(font, font.plainSubstrByWidth(board.id(), maxTextW),
                        contentX + 48, cardY + 10, UiTheme.TEXT_PRIMARY, false);
                gui.text(font, font.plainSubstrByWidth(
                                Component.translatable("gui.serialcraft.boards.cmd", board.data()).getString(), maxTextW),
                        contentX + 48, cardY + 24, UiTheme.TEXT_SECONDARY, false);
                gui.text(font, Component.translatable("gui.serialcraft.boards.pos",
                                board.pos().getX(), board.pos().getY(), board.pos().getZ()),
                        contentX + 48, cardY + 36, UiTheme.TEXT_MUTED, false);
            }
            cardY += UiTheme.CARD_ROW_HEIGHT;
        }

        gui.disableScissor();

        // Barra de desplazamiento
        if (scroll.hasScroll()) {
            scroll.renderScrollbar(gui, contentX + cardWidth + 3, viewportTop, 6, viewportHeight);
        }
    }

    private void renderEditor(GuiGraphicsExtractor gui, Font font, int screenWidth, int screenHeight) {
        if (editTarget == null) return;

        int availW = UiTheme.contentWidth(screenWidth);
        int width  = Math.min(EDITOR_W, availW);
        int x      = UiTheme.contentX(screenWidth) + Math.max(0, (availW - width) / 2);
        int y      = Math.max(4, (screenHeight - EDITOR_H) / 2);

        gui.fill(x, y, x + width, y + EDITOR_H, UiTheme.BG_PANEL);
        gui.outline(x, y, width, EDITOR_H, UiTheme.LINE_STRONG);

        gui.centeredText(font,
                Component.translatable("gui.serialcraft.editor.title", editTarget.id()),
                x + width / 2, y + 10, UiTheme.TEXT_PRIMARY);

        if (!diagnostics) {
            gui.text(font, Component.translatable("gui.serialcraft.editor.board_id"), x + 8, y + 26, UiTheme.TEXT_SECONDARY, false);
            gui.text(font, Component.translatable("gui.serialcraft.editor.command"), x + 8, y + 66, UiTheme.TEXT_SECONDARY, false);
            gui.text(font, font.plainSubstrByWidth(Component.translatable(helpKey(), editData, editData).getString(), width - 16),
                    x + 8, y + 157, UiTheme.TEXT_SECONDARY, false);
        } else {
            IoSnapshot current = editTarget.snapshot();
            var level = Minecraft.getInstance().level;
            if (level != null && level.getBlockEntity(editTarget.pos()) instanceof HardwareIOBlockEntity io) current = io.snapshot();
            String values = Component.translatable("gui.serialcraft.editor.values", current.received(), current.lastSent(),
                    current.read(), current.emitted()).getString();
            gui.text(font, font.plainSubstrByWidth(values, width - 16), x + 8, y + 147, UiTheme.TEXT_SECONDARY, false);
            gui.text(font, Component.translatable(current.connected() ? "gui.serialcraft.editor.connected" : "gui.serialcraft.editor.disconnected"),
                    x + 8, y + 159, UiTheme.TEXT_SECONDARY, false);
        }
        if (!editorMessage.isEmpty()) {
            gui.text(font, font.plainSubstrByWidth(Component.translatable(editorMessage).getString(), width - 16),
                    x + 8, y + 224, UiTheme.ERROR_DARK, false);
        }
    }

    private String helpKey() {
        boolean digital = editSignal == SignalType.DIGITAL;
        if (editMode.isOutput()) {
            return digital ? "gui.serialcraft.help.out_digital" : "gui.serialcraft.help.out_analog";
        }
        return digital ? "gui.serialcraft.help.in_digital" : "gui.serialcraft.help.in_analog";
    }

    // ── Etiquetas ─────────────────────────────────────────────────────────────

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
