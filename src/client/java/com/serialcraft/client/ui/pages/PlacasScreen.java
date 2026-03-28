package com.serialcraft.client.ui.pages;

import com.serialcraft.client.ui.NavBar;
import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.widget.IconTextButton;
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
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Página "Placas" del PanelUI.
 * Sigue el mismo patrón que HomeScreen / WelcomeScreen:
 * NO extiende Screen — clase helper con init / render / tick / onClose.
 *
 * Modos internos:
 *  - Lista  : tarjeta por cada ArduinoIOBlock con Toggle + Editar.
 *  - Editor : formulario equivalente a IOScreen (id, comando, modo, señal, lógica, power).
 */
public class PlacasScreen {

    // ── Colores ────────────────────────────────────────────────────────────
    private static final int TEXT_MAIN  = 0xFF212121;
    private static final int TEXT_DIM   = 0xFF757575;
    private static final int ACCENT     = 0xFF00838F;
    private static final int INPUT_BG   = 0xFF1A1A1A;
    private static final int INPUT_TEXT = 0xFFE0E0E0;
    private static final int BORDER_COL = 0xFFAAAAAA;
    private static final int CARD_BG    = 0xFFFFFFFF;
    private static final int CARD_LINE  = 0xFFE0E0E0;

    // ── Estado de la lista ─────────────────────────────────────────────────
    private final List<BoardInfo> boardList = new CopyOnWriteArrayList<>();

    // ── Estado del editor ──────────────────────────────────────────────────
    private boolean   isEditing      = false;
    private BoardInfo selectedBoard  = null;
    private int       editIoMode     = 0;
    private int       editSignalType = 0;
    private int       editLogicMode  = 0;
    private boolean   editSoftOn     = true;

    // Referencia al logicBtn para controlar visibilidad dinámicamente sin re-init
    private SolidButton logicBtnRef = null;

    // EditBoxes para leer valores al guardar
    private EditBox editIdBox   = null;
    private EditBox editDataBox = null;

    // ── Referencias para re-init ───────────────────────────────────────────
    private PanelUI panelRef       = null;

    // ── Flags anti-loop (FIX DEL PARPADEO) ────────────────────────────────
    // updateBoardList() escribe pendingBoards desde el hilo de red.
    // tick() consume pendingBoards en el hilo principal UNA sola vez.
    private volatile boolean         pendingRebuild = false;
    private volatile List<BoardInfo> pendingBoards  = null;

    // Evita enviar BoardListRequestPayload en bucle al reconstruir
    private boolean requestSent = false;

    // ══════════════════════════════════════════════════════════════════════
    //  CICLO DE VIDA
    // ══════════════════════════════════════════════════════════════════════

    public void init(PanelUI panel, int screenWidth, int screenHeight) {
        this.panelRef    = panel;
        this.logicBtnRef = null;
        this.editIdBox   = null;
        this.editDataBox = null;

        if (!isEditing) {
            // Pedir la lista al servidor solo si no se pidió ya en esta sesión
            if (!requestSent) {
                ClientPlayNetworking.send(new BoardListRequestPayload(true));
                requestSent = true;
            }
            buildListWidgets(panel, screenWidth, screenHeight);
        } else {
            buildEditWidgets(panel, screenWidth, screenHeight);
        }
    }

    /**
     * tick() es el ÚNICO lugar donde se aplica el rebuild tras recibir datos
     * del servidor. Nunca se llama setTab() desde render().
     * Así se rompe el loop: updateBoardList → tick (una vez) → setTab → init
     * → requestSent==true → NO envía otra request → se detiene.
     */
    public void tick() {
        if (pendingRebuild && panelRef != null) {
            List<BoardInfo> data = pendingBoards;
            pendingBoards  = null;
            pendingRebuild = false;

            if (data != null) {
                boardList.clear();
                boardList.addAll(data);
            }

            if (!isEditing) {
                // requestSent sigue true → init() no enviará otra request
                panelRef.setTab(PanelUI.Tab.PLACAS);
            }
        }
    }

    public void onClose() {
        // Al cerrar la pestaña o la UI, permitir nueva request la próxima vez
        requestSent = false;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MODO LISTA — construcción de widgets
    // ══════════════════════════════════════════════════════════════════════

    private void buildListWidgets(PanelUI panel, int screenWidth, int screenHeight) {
        int navWidth  = NavBar.getNavBarWidth(screenWidth);
        int contentX  = navWidth + 20;
        int cardWidth = screenWidth - navWidth - 40;
        int cardY     = 70;

        for (BoardInfo board : boardList) {
            final BoardInfo b    = board;
            boolean         isOn = b.status();
            int             btnY = cardY + 12;

            // Botón Toggle ON/OFF
            IconTextButton toggleBtn = new IconTextButton(
                    contentX + cardWidth - 192, btnY, 88, 24,
                    (isOn ? SpriteIcon.CONNECT : SpriteIcon.DISCONNECT),
                    Component.literal(isOn ? "■  ON" : "□  OFF"),
                    (btn) -> {
                        ClientPlayNetworking.send(new RemoteTogglePayload(b.pos()));
                        // Forzar refresh de lista al volver
                        requestSent = false;
                        ClientPlayNetworking.send(new BoardListRequestPayload(true));
                        requestSent = true;
                    },
                    isOn ? 0xFF2E7D32 : 0xFFC62828,
                    isOn ? 0xFF1B5E20 : 0xFF8B0000,
                    0xFFFFFFFF
            );

            // Botón Editar → entra al editor
            IconTextButton editBtn = new IconTextButton(
                    contentX + cardWidth - 100, btnY, 88, 24,
                    SpriteIcon.CODE,
                    Component.literal("Editar"),
                    (btn) -> openEditor(panel, b),
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
        // requestSent ya está en true → el init del editor no enviará request
        panel.setTab(PanelUI.Tab.PLACAS);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MODO EDITOR — construcción de widgets
    // ══════════════════════════════════════════════════════════════════════

    private void buildEditWidgets(PanelUI panel, int screenWidth, int screenHeight) {
        if (selectedBoard == null) {
            isEditing = false;
            return;
        }

        int navWidth = NavBar.getNavBarWidth(screenWidth);
        int cx = navWidth + 30;
        int cy = 55;

        // ── EditBox Board ID ──────────────────────────────────────────────
        editIdBox = new EditBox(
                Minecraft.getInstance().font,
                cx + 5, cy + 48, 160, 18,
                Component.literal("Board ID")
        );
        editIdBox.setValue(selectedBoard.id());
        editIdBox.setTextColor(INPUT_TEXT);
        editIdBox.setBordered(false);
        editIdBox.setMaxLength(32);
        panel.addWidget(editIdBox);

        // ── Botón Power ───────────────────────────────────────────────────
        SolidButton powerBtn = SolidButton.of(
                cx + 175, cy + 45, 60, 20,
                Component.literal(editSoftOn ? "■ ON" : "□ OFF"),
                b -> {
                    editSoftOn = !editSoftOn;
                    b.setMessage(Component.literal(editSoftOn ? "■ ON" : "□ OFF"));
                    b.setVariant(editSoftOn ? SolidButton.Variant.SUCCESS : SolidButton.Variant.DANGER);
                },
                editSoftOn ? SolidButton.Variant.SUCCESS : SolidButton.Variant.DANGER
        );
        panel.addWidget(powerBtn);

        // ── Botones Mode / Signal / Logic ─────────────────────────────────
        int btnY = cy + 95;
        int btnW = 70;
        int gap  = 5;

        // logicBtn se crea ANTES que modeBtn para tener la referencia en su lambda
        logicBtnRef = SolidButton.primary(
                cx + (btnW + gap) * 2, btnY, btnW, 20,
                getLogicText(),
                b -> {
                    editLogicMode = (editLogicMode + 1) % 3;
                    b.setMessage(getLogicText());
                }
        );
        // Visible solo en modo IN, igual que IOScreen de main
        logicBtnRef.visible = (editIoMode == 1);
        panel.addWidget(logicBtnRef);

        SolidButton modeBtn = SolidButton.primary(
                cx, btnY, btnW, 20,
                getModeText(),
                b -> {
                    editIoMode = (editIoMode == 0) ? 1 : 0;
                    b.setMessage(getModeText());
                    if (logicBtnRef != null) {
                        logicBtnRef.visible = (editIoMode == 1);
                    }
                }
        );
        panel.addWidget(modeBtn);

        SolidButton signalBtn = SolidButton.primary(
                cx + btnW + gap, btnY, btnW, 20,
                getSignalText(),
                b -> {
                    editSignalType = (editSignalType == 0) ? 1 : 0;
                    b.setMessage(getSignalText());
                }
        );
        panel.addWidget(signalBtn);

        // ── EditBox Comando ───────────────────────────────────────────────
        editDataBox = new EditBox(
                Minecraft.getInstance().font,
                cx + 5, cy + 157, 200, 18,
                Component.literal("Command")
        );
        editDataBox.setValue(selectedBoard.data());
        editDataBox.setTextColor(INPUT_TEXT);
        editDataBox.setBordered(false);
        editDataBox.setMaxLength(32);
        panel.addWidget(editDataBox);

        // ── Guardar / Cancelar ────────────────────────────────────────────
        SolidButton saveBtn = SolidButton.success(
                cx + 30, cy + 198, 100, 20,
                Component.literal("Guardar"),
                b -> saveAndReturn(panel)
        );
        panel.addWidget(saveBtn);

        SolidButton cancelBtn = SolidButton.neutral(
                cx + 140, cy + 198, 100, 20,
                Component.literal("Cancelar"),
                b -> cancelEdit(panel)
        );
        panel.addWidget(cancelBtn);
    }

    private void saveAndReturn(PanelUI panel) {
        if (selectedBoard == null || editIdBox == null || editDataBox == null) return;
        ClientPlayNetworking.send(new ConfigPayload(
                selectedBoard.pos(),
                editIoMode,
                editDataBox.getValue(),
                editSignalType,
                editSoftOn,
                editIdBox.getValue(),
                editLogicMode
        ));
        isEditing     = false;
        selectedBoard = null;
        logicBtnRef   = null;
        // Forzar refresh de lista al volver
        requestSent = false;
        panel.setTab(PanelUI.Tab.PLACAS);
    }

    private void cancelEdit(PanelUI panel) {
        isEditing     = false;
        selectedBoard = null;
        logicBtnRef   = null;
        panel.setTab(PanelUI.Tab.PLACAS);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RENDER
    // ══════════════════════════════════════════════════════════════════════

    /** Llamado desde PanelUI.render() antes de super.render() (que dibuja widgets). */
    public void render(GuiGraphics gui, int mouseX, int mouseY, Font font, int width, int height) {
        // NUNCA llamar setTab() aquí — eso causaría el loop de parpadeo.
        // tick() es el responsable de aplicar los datos del servidor.

        int navWidth = NavBar.getNavBarWidth(width);
        int cx       = navWidth + 20;

        // Título de sección
        float scale = 1.8f;
        gui.pose().pushMatrix();
        gui.pose().scale(scale, scale);
        gui.drawString(font, "PLACAS",
                (int)(cx / scale), (int)(22 / scale), 0xFFffc107, false);
        gui.drawString(font, "GESTOR DE IO",
                (int)(cx / scale) + font.width("PLACAS") + 4,
                (int)(22 / scale), TEXT_MAIN, false);
        gui.pose().popMatrix();

        if (isEditing) {
            renderEditView(gui, font, width, height, navWidth);
        } else {
            renderListView(gui, font, width, height, navWidth, cx);
        }
    }

    // ── Vista lista ───────────────────────────────────────────────────────
    private void renderListView(GuiGraphics gui, Font font,
                                int width, int height, int navWidth, int cx) {
        int cardWidth = width - navWidth - 40;
        int cardY     = 70;

        if (boardList.isEmpty()) {
            gui.drawString(font,
                    "No hay placas IO registradas en el mundo.",
                    cx, cardY + 10, TEXT_DIM, false);
            gui.drawString(font,
                    "Coloca un bloque IO cerca de la Laptop y vuelve a abrir este panel.",
                    cx, cardY + 24, TEXT_DIM, false);
            return;
        }

        for (BoardInfo board : boardList) {
            gui.fill(cx, cardY, cx + cardWidth, cardY + 48, CARD_BG);
            gui.fill(cx, cardY + 48, cx + cardWidth, cardY + 50, CARD_LINE);

            // Badge IN / OUT
            boolean isInput = (board.mode() == 1);
            int badgeBg  = isInput ? 0xFFE8F5E9 : 0xFFE3F2FD;
            int badgeTxt = isInput ? 0xFF2E7D32 : 0xFF1565C0;
            gui.fill(cx + 10, cardY + 10, cx + 36, cardY + 22, badgeBg);
            gui.drawString(font, isInput ? "IN" : "OUT", cx + 14, cardY + 13, badgeTxt, false);

            // LED de estado
            int ledColor = board.status() ? 0xFF4CAF50 : 0xFFf44336;
            gui.fill(cx + cardWidth - 215, cardY + 6,
                    cx + cardWidth - 209, cardY + 12, ledColor);

            gui.drawString(font, board.id(),            cx + 44, cardY + 10, TEXT_MAIN, false);
            gui.drawString(font, "CMD: " + board.data(), cx + 44, cardY + 24, TEXT_DIM, false);
            gui.drawString(font,
                    "Pos: " + board.pos().getX() + "  "
                            + board.pos().getY() + "  "
                            + board.pos().getZ(),
                    cx + 44, cardY + 36, 0xFF9E9E9E, false);

            cardY += 52;
        }
    }

    // ── Vista editor ──────────────────────────────────────────────────────
    private void renderEditView(GuiGraphics gui, Font font,
                                int width, int height, int navWidth) {
        if (selectedBoard == null) return;

        int cx     = navWidth + 30;
        int cy     = 55;
        int panelW = 265;
        int panelH = 240;

        gui.fill(cx, cy, cx + panelW, cy + panelH, 0xFFF2F2EC);
        drawBorder(gui, cx, cy, panelW, panelH, BORDER_COL);

        // Cabecera
        gui.fill(cx, cy, cx + panelW, cy + 30, 0xFFE6E6DF);
        gui.drawCenteredString(font,
                Component.literal("Editar: " + selectedBoard.id()),
                cx + panelW / 2, cy + 10, TEXT_MAIN);

        // Board ID
        gui.drawString(font, "Board ID", cx + 5, cy + 33, TEXT_DIM, false);
        gui.fill(cx + 4, cy + 42, cx + 4 + 172, cy + 68, BORDER_COL);
        gui.fill(cx + 5, cy + 43, cx + 4 + 171, cy + 67, INPUT_BG);

        // Separador de sección (muestra "Lógica" solo cuando mode == IN)
        gui.drawCenteredString(font,
                Component.literal("Modo  /  Señal" + (editIoMode == 1 ? "  /  Lógica" : "")),
                cx + panelW / 2, cy + 78, ACCENT);

        // Comando
        gui.drawString(font, "Comando (targetData)", cx + 5, cy + 140, TEXT_DIM, false);
        gui.fill(cx + 4, cy + 151, cx + 4 + 212, cy + 177, BORDER_COL);
        gui.fill(cx + 5, cy + 152, cx + 4 + 211, cy + 176, INPUT_BG);

        // Texto de ayuda dinámico (como IOScreen de main)
        String cmdVal = (editDataBox != null) ? editDataBox.getValue() : selectedBoard.data();
        String helpText;
        if (editIoMode == 0) {
            helpText = editSignalType == 0
                    ? "OUT Digital → \"" + cmdVal + "\":0/1"
                    : "OUT PWM → \"" + cmdVal + "\":0-255";
        } else {
            helpText = editSignalType == 0
                    ? "IN Digital → HIGH/LOW con ID \"" + cmdVal + "\""
                    : "IN Analog → 0-15 con ID \"" + cmdVal + "\"";
        }
        gui.drawCenteredString(font, Component.literal(helpText),
                cx + panelW / 2, cy + 182, 0xFF666666);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HELPERS DE TEXTO
    // ══════════════════════════════════════════════════════════════════════

    private Component getModeText() {
        return editIoMode == 0
                ? Component.translatable("gui.serialcraft.mode.out")
                : Component.translatable("gui.serialcraft.mode.in");
    }

    private Component getSignalText() {
        return editSignalType == 0
                ? Component.translatable("gui.serialcraft.signal.digital")
                : Component.translatable("gui.serialcraft.signal.analog");
    }

    private Component getLogicText() {
        return switch (editLogicMode) {
            case 0 -> Component.translatable("gui.serialcraft.logic.or");
            case 1 -> Component.translatable("gui.serialcraft.logic.and");
            default -> Component.translatable("gui.serialcraft.logic.xor");
        };
    }

    private static void drawBorder(GuiGraphics gui, int x, int y, int w, int h, int color) {
        gui.fill(x,         y,         x + w, y + 1,     color);
        gui.fill(x,         y + h - 1, x + w, y + h,     color);
        gui.fill(x,         y,         x + 1, y + h,     color);
        gui.fill(x + w - 1, y,         x + w, y + h,     color);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  API PÚBLICA — llamada desde SerialCraftClient (hilo de red)
    // ══════════════════════════════════════════════════════════════════════

    public void updateBoardList(List<BoardInfo> boards) {
        this.pendingBoards  = new ArrayList<>(boards);
        this.pendingRebuild = true;
        // tick() lo procesará en el hilo principal en el próximo ciclo
    }
}