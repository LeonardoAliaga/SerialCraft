package com.serialcraft.client.ui.pages;

import com.serialcraft.block.entity.ArduinoIOBlockEntity;
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
import com.serialcraft.screen.PanelUI;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
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
    private @Nullable List<BoardInfo> incomingBoards = null;
    private @Nullable BlockPos directEditRequestPos = null;

    // ── Estado del editor ─────────────────────────────────────────────────────
    private boolean editing = false;
    private @Nullable BoardInfo editTarget = null;
    private IoMode     editMode    = IoMode.OUTPUT;
    private SignalType editSignal  = SignalType.DIGITAL;
    private LogicMode  editLogic   = LogicMode.OR;
    private boolean    editEnabled = true;

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

        if (directEditRequestPos != null) {
            BlockPos target = directEditRequestPos;
            directEditRequestPos = null;
            openEditorForPos(target, false);
            buildEditor(panelUi, screenWidth, screenHeight);
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
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof ArduinoIOBlockEntity io) {
            openEditor(io.toBoardInfo(), refreshUI);
            return;
        }
        for (BoardInfo b : boards) {
            if (b.pos().equals(pos)) {
                openEditor(b, refreshUI);
                return;
            }
        }
        BoardInfo synthetic = new BoardInfo(
                pos,
                "Board_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ(),
                ArduinoIOBlockEntity.DEFAULT_TARGET_DATA,
                IoMode.OUTPUT,
                SignalType.DIGITAL,
                LogicMode.OR,
                true
        );
        openEditor(synthetic, refreshUI);
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

        if (refreshUI && panel != null && panel.getCurrentTab() != PanelUI.Tab.BOARDS) {
            panel.setTab(PanelUI.Tab.BOARDS);
        } else if (refreshUI && panel != null) {
            panel.refresh();
        }
    }

    @Override
    public void tick() {
        if (incomingBoards == null) return;
        List<BoardInfo> incoming = incomingBoards;
        incomingBoards = null;
        awaitingResponse = false;

        if (incoming.equals(boards)) return;

        boards.clear();
        boards.addAll(incoming);
        if (!editing && panel != null) panel.refresh();
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
        if (!ClientPlayNetworking.canSend(BoardListRequestPayload.TYPE)) return;
        awaitingResponse = true;
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

        for (int i = 0; i < boards.size(); i++) {
            if (boards.get(i).pos().equals(board.pos())) {
                BoardInfo cur = boards.get(i);
                boards.set(i, new BoardInfo(
                        cur.pos(), cur.id(), cur.data(),
                        cur.mode(), cur.signalType(), cur.logicMode(),
                        !cur.enabled()));
                break;
            }
        }

        ClientPlayNetworking.send(new RemoteTogglePayload(board.pos()));
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
        int y      = Math.max(16, (screenHeight - EDITOR_H) / 2);

        idBox = new EditBox(font, x + 8, y + 44, width - 96, 20,
                Component.translatable("gui.serialcraft.editor.board_id"));
        idBox.setValue(editTarget.id());
        idBox.setMaxLength(BoardInfo.MAX_ID_LENGTH);
        panelUi.addWidget(idBox);

        panelUi.addWidget(SolidButton.of(x + width - 82, y + 44, 74, 20, powerLabel(), btn -> {
            editEnabled = !editEnabled;
            btn.setMessage(powerLabel());
            btn.setVariant(editEnabled ? SolidButton.Variant.SUCCESS : SolidButton.Variant.DANGER);
        }, editEnabled ? SolidButton.Variant.SUCCESS : SolidButton.Variant.DANGER));

        int colW = (width - 24) / 3;

        panelUi.addWidget(SolidButton.primary(x + 8, y + 94, colW, 20, modeLabel(), btn -> {
            editMode = (editMode == IoMode.OUTPUT) ? IoMode.INPUT : IoMode.OUTPUT;
            btn.setMessage(modeLabel());
            if (logicButton != null) logicButton.active = editMode.isInput();
        }));

        panelUi.addWidget(SolidButton.primary(x + 12 + colW, y + 94, colW, 20, signalLabel(), btn -> {
            editSignal = (editSignal == SignalType.DIGITAL) ? SignalType.ANALOG : SignalType.DIGITAL;
            btn.setMessage(signalLabel());
        }));

        logicButton = SolidButton.primary(x + 16 + colW * 2, y + 94, colW, 20, logicLabel(), btn -> {
            editLogic = editLogic.next();
            btn.setMessage(logicLabel());
        });
        logicButton.active = editMode.isInput();
        panelUi.addWidget(logicButton);

        dataBox = new EditBox(font, x + 8, y + 144, width - 16, 20,
                Component.translatable("gui.serialcraft.editor.command"));
        dataBox.setValue(editTarget.data());
        dataBox.setMaxLength(BoardInfo.MAX_DATA_LENGTH);
        panelUi.addWidget(dataBox);

        int halfW = (width - 20) / 2;
        panelUi.addWidget(SolidButton.success(x + 6, y + 200, halfW, 22,
                Component.translatable("gui.serialcraft.editor.save"), btn -> save()));
        panelUi.addWidget(SolidButton.soft(x + halfW + 14, y + 200, halfW, 22,
                Component.translatable("gui.serialcraft.editor.cancel"), btn -> cancel()));
    }

    private void save() {
        if (editTarget == null || idBox == null || dataBox == null) return;
        if (!ClientPlayNetworking.canSend(ConfigPayload.TYPE)) return;

        String rawId = idBox.getValue().trim();
        String rawData = dataBox.getValue().trim();
        String newId = rawId.isEmpty() ? editTarget.id() : rawId;
        String newData = rawData.isEmpty() ? editTarget.data() : rawData;

        BoardInfo updated = new BoardInfo(
                editTarget.pos(), newId, newData,
                editMode, editSignal, editLogic, editEnabled);

        for (int i = 0; i < boards.size(); i++) {
            if (boards.get(i).pos().equals(updated.pos())) {
                boards.set(i, updated);
                break;
            }
        }

        ClientPlayNetworking.send(new ConfigPayload(
                updated.pos(), updated.mode(), updated.data(),
                updated.signalType(), updated.enabled(), updated.id(), updated.logicMode()));

        closeEditor();
    }

    private void cancel() { closeEditor(); }

    private void closeEditor() {
        editing          = false;
        editTarget       = null;
        logicButton      = null;
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

            boolean inView = (currentBtnY + cw.toggleBtn().getHeight() >= viewportTop
                           && currentBtnY <= viewportBottom);
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
        int y      = Math.max(16, (screenHeight - EDITOR_H) / 2);

        gui.fill(x, y, x + width, y + EDITOR_H, UiTheme.BG_PANEL);
        gui.outline(x, y, width, EDITOR_H, UiTheme.LINE_STRONG);

        gui.centeredText(font,
                Component.translatable("gui.serialcraft.editor.title", editTarget.id()),
                x + width / 2, y + 10, UiTheme.TEXT_PRIMARY);

        gui.text(font, Component.translatable("gui.serialcraft.editor.board_id"),
                x + 8, y + 33, UiTheme.TEXT_SECONDARY, false);
        UiDraw.inputWell(gui, x + 6, y + 42, width - 92, 24);

        gui.text(font, Component.translatable("gui.serialcraft.editor.power"),
                x + width - 82, y + 33, UiTheme.TEXT_SECONDARY, false);

        gui.centeredText(font, Component.translatable(
                        editMode.isInput() ? "gui.serialcraft.editor.section_mode_logic"
                                           : "gui.serialcraft.editor.section_mode"),
                x + width / 2, y + 78, UiTheme.ACCENT_PRIMARY);
        gui.fill(x + 8, y + 88, x + width - 8, y + 89, UiTheme.LINE_SOFT);

        gui.text(font, Component.translatable("gui.serialcraft.editor.command"),
                x + 8, y + 130, UiTheme.TEXT_SECONDARY, false);
        UiDraw.inputWell(gui, x + 6, y + 141, width - 12, 26);

        String command = (dataBox != null) ? dataBox.getValue() : editTarget.data();
        gui.centeredText(font, Component.translatable(helpKey(), command, command),
                x + width / 2, y + 178, 0xFF666666);
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
