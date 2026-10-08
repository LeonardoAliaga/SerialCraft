package com.serialcraft.client.ui.pages;

import com.serialcraft.board.SignalType;
import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.screen.PanelUI;
import com.serialcraft.signal.GeneratorController;
import com.serialcraft.signal.LanePlanner;
import com.serialcraft.signal.LaneScale;
import com.serialcraft.signal.LineEnvelope;
import com.serialcraft.signal.SignalRecorder;
import com.serialcraft.signal.SignalRecorder.Direction;
import com.serialcraft.signal.SignalRecorder.SeriesId;
import com.serialcraft.signal.SignalRecorder.Snapshot;
import com.serialcraft.signal.SignalStats;
import com.serialcraft.signal.StepEnvelope;
import com.serialcraft.signal.Waveform.Shape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pagina "Visualizar": banco de pruebas para ver lo que viaja entre el juego y
 * la placa y para probar que la placa responde.
 *
 * Tres herramientas sobre un mismo registro de senales con hora real
 * ({@link SignalRecorder}):
 *
 *  1. LINEA DE TIEMPO: varias senales en pistas que comparten el mismo eje de
 *     tiempo (sensores que llegan de la placa, valores que el juego le envia,
 *     telemetria mc_*...). Sirve para responder "¿donde se rompe la cadena?".
 *  2. SENSOR: una senal en detalle: el valor crudo 0-255 junto a la redstone
 *     0-15 que produce, el ruido y la zona muerta que conviene en el sketch.
 *  3. GENERADOR: envia una onda de prueba a una clave de la placa (rampa,
 *     triangulo, cuadrada, seno o escalera) para comprobar que responde sin
 *     tener que construir un circuito de redstone.
 *
 * Dos estilos de trazo, que se alternan con el boton "Trazo":
 *  - LINEA (por defecto): une los mensajes reales con rectas, y una onda se ve
 *    como onda. No es una curva spline: la recta nunca sale del rango de las dos
 *    muestras que une. Si entre dos mensajes pasa mas de 0,3 s no se unen (un
 *    sensor quieto no cambio gradualmente): se mantiene el valor.
 *  - ESCALON: entre dos mensajes el valor se mantiene, tal como lo vio el juego.
 */
public class VisualizePage implements Page {

    public enum View { TIMELINE, SENSOR }

    // ── Parametros ────────────────────────────────────────────────────────────
    private static final int[]    WINDOW_SECONDS = {5, 10, 30, 60};
    private static final double[] PERIODS_SEC    = {1, 2, 5, 10};
    private static final int[]    AMPLITUDES     = {100, 50, 25};

    private static final long NANOS_PER_SEC     = 1_000_000_000L;
    private static final long AUTO_ACTIVE_NANOS = 120 * NANOS_PER_SEC;
    private static final long MESSAGE_NANOS     = 4 * NANOS_PER_SEC;

    private static final int ROW1_Y = 46;
    private static final int BTN_H  = 18;
    private static final int GAP    = 4;

    private static final int MIN_LANE_H  = 22;
    private static final int MAX_LANE_H  = 64;
    private static final int MAX_LANES   = 8;
    private static final int AXIS_H      = 13;
    private static final int STAT_LINE_H = 11;

    private static final int COLOR_RX       = UiTheme.SCOPE_TRACE;   // lo que envia la placa
    private static final int COLOR_TX       = 0xFFFFA726;            // lo que envia el juego
    private static final int COLOR_REDSTONE = 0xFFFFA726;            // redstone resultante
    private static final int COLOR_LABEL    = 0xFFB0BEC5;
    private static final int COLOR_DIM      = 0xFF78909C;
    private static final int COLOR_GRID     = 0xFF1E282E;
    private static final int COLOR_GRID_MID = 0xFF2A3942;

    // ── Estado de la pagina (sobrevive a init()) ──────────────────────────────
    private View view = View.TIMELINE;
    private int windowIndex = 1;                 // 10 s
    private boolean paused = false;
    private String filterText = "";
    private boolean lineStyle = true;            // Linea (true) o Escalon (false)

    private final GeneratorController generator = new GeneratorController();
    private int generatorEpoch;
    private Shape shape = Shape.RAMP;
    private int periodIndex = 1;                 // 2 s
    private int amplitudeIndex = 0;              // 100 %
    private String genKey = "led_verde";
    private @Nullable String genMessageKey = null;
    private long genMessageUntil = 0L;

    // ── Widgets ──────────────────────────────────────────────────────────────
    private @Nullable SolidButton genToggle = null;

    // ── Disposicion (se fija en init y render) ────────────────────────────────
    private int screenW = 0;
    private int screenH = 0;

    // ── Datos ya calculados en tick (el dibujo solo los pinta) ────────────────
    private static final class LaneData {
        SeriesId id;
        StepEnvelope env;
        LaneScale.Range range;
        long[] times;
        float[] values;
        float last;
        long ageNanos;
        boolean hasAny;
    }

    private static final class SensorData {
        SeriesId id;
        StepEnvelope env;
        LaneScale.Range range;
        SignalStats stats;
        long[] times;
        float[] values;
        boolean showRedstone;
    }

    private final List<LaneData> lanes = new ArrayList<>();
    private int hiddenLanes = 0;
    private @Nullable SensorData sensor = null;
    private long viewFrom = 0L;
    private long viewTo = 0L;

    // ════════════════════════════════════════════════════════════════════════════
    //  CICLO DE VIDA
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public void init(PanelUI panel, int screenWidth, int screenHeight) {
        this.screenW = screenWidth;
        this.screenH = screenHeight;
        this.genToggle = null;

        int x = UiTheme.contentX(screenWidth);
        int width = UiTheme.contentWidth(screenWidth);
        Font font = Minecraft.getInstance().font;

        boolean compact = screenHeight < 280;
        int row1Y = compact ? 38 : ROW1_Y;
        int btnH  = compact ? 16 : BTN_H;
        int gap   = compact ? 2 : GAP;
        int row2Y = row1Y + btnH + gap;
        int row3Y = row2Y + btnH + gap;

        // ── Fila 1: vista, ventana, pausa, limpiar ────────────────────────────
        int viewW  = Math.max(68, width * 30 / 100);
        int winW   = Math.max(58, width * 27 / 100);
        int pauseW = Math.max(46, width * 20 / 100);
        int clearW = Math.max(36, width - viewW - winW - pauseW - 3 * gap);

        panel.addWidget(SolidButton.primary(x, row1Y, viewW, btnH, viewLabel(), btn -> {
            view = (view == View.TIMELINE) ? View.SENSOR : View.TIMELINE;
            panel.setTab(PanelUI.Tab.VISUALIZE);       // reconstruir: cambia el texto de ayuda del campo
        }));

        panel.addWidget(SolidButton.primary(x + viewW + gap, row1Y, winW, btnH, windowLabel(), btn -> {
            windowIndex = (windowIndex + 1) % WINDOW_SECONDS.length;
            btn.setMessage(windowLabel());
        }));

        panel.addWidget(SolidButton.of(x + viewW + winW + 2 * gap, row1Y, pauseW, btnH, pauseLabel(), btn -> {
            paused = !paused;
            if (paused) {
                viewTo = System.nanoTime();             // se congela lo que se esta viendo
                viewFrom = viewTo - windowNanos();
                refreshData();
            }
            btn.setVariant(paused ? SolidButton.Variant.DANGER : SolidButton.Variant.SUCCESS);
            btn.setMessage(pauseLabel());
        }, paused ? SolidButton.Variant.DANGER : SolidButton.Variant.SUCCESS));

        panel.addWidget(SolidButton.soft(x + viewW + winW + pauseW + 3 * gap, row1Y, clearW, btnH,
                Component.translatable("gui.serialcraft.visualize.clear"), btn -> {
                    SignalRecorder.INSTANCE.clear();
                    lanes.clear();
                    sensor = null;
                    hiddenLanes = 0;
                }));

        // ── Fila 2: canales (linea de tiempo) o clave (sensor) ────────────────
        String hintKey = (view == View.TIMELINE)
                ? "gui.serialcraft.visualize.timeline_filter_hint"
                : "gui.serialcraft.visualize.sensor_filter_hint";
        int styleW  = Math.max(62, width * 26 / 100);
        int filterW = width - styleW - gap;
        EditBox filterBox = new EditBox(font, x, row2Y, filterW, btnH, Component.translatable(hintKey));
        filterBox.setMaxLength(96);
        filterBox.setValue(filterText);
        filterBox.setHint(Component.translatable(hintKey));
        filterBox.setTextColor(UiTheme.TEXT_INVERSE);
        filterBox.setResponder(val -> this.filterText = val);
        panel.addWidget(filterBox);

        panel.addWidget(SolidButton.soft(x + filterW + gap, row2Y, styleW, btnH, traceLabel(), btn -> {
            lineStyle = !lineStyle;
            btn.setMessage(traceLabel());
            refreshData();                          // tambien en pausa: cambia el dibujo, no los datos
        }));

        // ── Fila 3: generador ─────────────────────────────────────────────────
        int toggleW = Math.max(50, width * 24 / 100);
        int shapeW  = Math.max(46, width * 20 / 100);
        int perW    = Math.max(34, width * 14 / 100);
        int ampW    = Math.max(34, width * 14 / 100);
        int keyW    = Math.max(44, width - toggleW - shapeW - perW - ampW - 4 * gap);
        int gx = x;

        genToggle = SolidButton.of(gx, row3Y, toggleW, btnH, genToggleLabel(), btn -> toggleGenerator(),
                generator.isRunning() ? SolidButton.Variant.DANGER : SolidButton.Variant.SUCCESS);
        panel.addWidget(genToggle);
        gx += toggleW + gap;

        panel.addWidget(SolidButton.soft(gx, row3Y, shapeW, btnH, shapeLabel(), btn -> {
            shape = shape.next();
            btn.setMessage(shapeLabel());
        }));
        gx += shapeW + gap;

        panel.addWidget(SolidButton.soft(gx, row3Y, perW, btnH, periodLabel(), btn -> {
            periodIndex = (periodIndex + 1) % PERIODS_SEC.length;
            btn.setMessage(periodLabel());
        }));
        gx += perW + gap;

        panel.addWidget(SolidButton.soft(gx, row3Y, ampW, btnH, amplitudeLabel(), btn -> {
            amplitudeIndex = (amplitudeIndex + 1) % AMPLITUDES.length;
            btn.setMessage(amplitudeLabel());
        }));
        gx += ampW + gap;

        EditBox genKeyBox = new EditBox(font, gx, row3Y, keyW, btnH,
                Component.translatable("gui.serialcraft.visualize.gen_key_hint"));
        genKeyBox.setMaxLength(SignalRecorder.MAX_KEY_LENGTH);
        genKeyBox.setValue(genKey);
        genKeyBox.setHint(Component.translatable("gui.serialcraft.visualize.gen_key_hint"));
        genKeyBox.setTextColor(UiTheme.TEXT_INVERSE);
        genKeyBox.setResponder(val -> this.genKey = val);
        panel.addWidget(genKeyBox);
    }

    @Override
    public void tick() {
        long now = System.nanoTime();
        runGenerator(now);

        if (paused) return;                 // el dibujo se queda congelado; el generador sigue
        viewTo = now;
        viewFrom = viewTo - windowNanos();
        refreshData();
    }

    @Override
    public void backgroundTick() {
        runGenerator(System.nanoTime());
    }

    @Override
    public void onClose() {
        applyAction(generator.stop());
    }

    private void runGenerator(long now) {
        if (!generator.isRunning()) return;
        if (generatorEpoch != ConnectionManager.sessionEpoch()) {
            generator.stop();
            ConnectionManager.discardPending(genKey.trim());
            showMessage("gui.serialcraft.visualize.gen_lost");
            refreshGenToggle();
            return;
        }
        GeneratorController.Action action = generator.tick(now, shape,
                PERIODS_SEC[periodIndex], AMPLITUDES[amplitudeIndex]);
        applyAction(action);
        if (!generator.isRunning()) refreshGenToggle();
    }

    private void applyAction(GeneratorController.Action action) {
        if (!action.send()) return;
        boolean delivered = ConnectionManager.sendSignal(genKey.trim() + ":" + action.value(), action.stopped());
        if (!delivered && !action.stopped()) {
            generator.stop();
            showMessage("gui.serialcraft.visualize.gen_lost");
            refreshGenToggle();
        }
    }

    private void refreshGenToggle() {
        if (genToggle == null) return;
        genToggle.setMessage(genToggleLabel());
        genToggle.setVariant(generator.isRunning() ? SolidButton.Variant.DANGER : SolidButton.Variant.SUCCESS);
    }

    private void toggleGenerator() {
        if (generator.isRunning()) {
            applyAction(generator.stop());
        } else {
            String key = genKey.trim();
            if (!com.serialcraft.network.SignalProtocol.isValidChannel(key)) { showMessage("gui.serialcraft.visualize.gen_bad_key"); return; }
            if (!ConnectionManager.isAnyConnected()) { showMessage("gui.serialcraft.visualize.gen_no_board"); return; }
            generator.start(System.nanoTime());
            generatorEpoch = ConnectionManager.sessionEpoch();
        }
        refreshGenToggle();
    }

    private void showMessage(String key) {
        genMessageKey = key;
        genMessageUntil = System.nanoTime() + MESSAGE_NANOS;
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  CALCULO DE DATOS (20 Hz)
    // ════════════════════════════════════════════════════════════════════════════

    private long windowNanos() { return WINDOW_SECONDS[windowIndex] * NANOS_PER_SEC; }

    /** Envolvente de dibujo segun el estilo elegido (Linea o Escalon). */
    private StepEnvelope envelope(Snapshot snap, int columns) {
        return lineStyle
                ? LineEnvelope.compute(snap, viewFrom, viewTo, columns)
                : StepEnvelope.compute(snap, viewFrom, viewTo, columns);
    }

    private void refreshData() {
        if (screenW == 0) return;
        if (view == View.TIMELINE) { sensor = null; refreshTimeline(); }
        else                       { lanes.clear(); hiddenLanes = 0; refreshSensor(); }
    }

    private void refreshTimeline() {
        Geometry g = geometry();
        SignalRecorder rec = SignalRecorder.INSTANCE;

        List<SeriesId> recent = rec.activeSeries(viewTo, AUTO_ACTIVE_NANOS);
        List<SeriesId> known  = rec.activeSeries(viewTo, Long.MAX_VALUE / 4);
        LanePlanner.Plan plan = LanePlanner.plan(recent, known, filterText, g.maxLanes);

        lanes.clear();
        hiddenLanes = plan.hidden();
        for (SeriesId id : plan.lanes()) {
            Snapshot snap = rec.snapshot(id, viewFrom, viewTo);
            LaneData d = new LaneData();
            d.id = id;
            if (snap == null || snap.size() == 0) { d.hasAny = false; lanes.add(d); continue; }

            float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
            for (float v : snap.values()) { min = Math.min(min, v); max = Math.max(max, v); }

            d.hasAny = true;
            d.times = snap.times();
            d.values = snap.values();
            d.last = snap.values()[snap.size() - 1];
            d.ageNanos = viewTo - snap.times()[snap.size() - 1];
            d.range = LaneScale.of(id.key(), min, max);
            d.env = envelope(snap, g.traceW);
            lanes.add(d);
        }
    }

    private void refreshSensor() {
        SignalRecorder rec = SignalRecorder.INSTANCE;
        SeriesId id = pickSensorSeries(rec);
        if (id == null) { sensor = null; return; }

        Snapshot snap = rec.snapshot(id, viewFrom, viewTo);
        if (snap == null || snap.size() == 0) { sensor = null; return; }

        Geometry g = geometry();
        SensorData d = new SensorData();
        d.id = id;
        d.times = snap.times();
        d.values = snap.values();
        d.stats = SignalStats.compute(snap, viewFrom, viewTo, SignalType.ANALOG::wireToRedstone);
        d.range = LaneScale.of(id.key(), d.stats.min(), d.stats.max());
        // La conversion a redstone solo tiene sentido en la escala del cable (0-255).
        d.showRedstone = d.range.lo() >= 0f && d.range.hi() <= 255f && !id.key().startsWith("mc_");
        d.env = envelope(snap, g.sensorGridW);
        sensor = d;
    }

    /** La clave escrita (RX primero) o, si esta vacia, el sensor que hablo mas recientemente. */
    private @Nullable SeriesId pickSensorSeries(SignalRecorder rec) {
        String key = filterText.trim();
        if (!key.isEmpty()) {
            SeriesId rx = new SeriesId(Direction.RX, key);
            if (rec.has(rx)) return rx;
            SeriesId tx = new SeriesId(Direction.TX, key);
            return rec.has(tx) ? tx : null;
        }
        List<SeriesId> recent = rec.activeSeries(viewTo, AUTO_ACTIVE_NANOS);
        for (SeriesId id : recent) if (id.dir() == Direction.RX) return id;
        return recent.isEmpty() ? null : recent.get(0);
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  DISPOSICION
    // ════════════════════════════════════════════════════════════════════════════

    private static final class Geometry {
        int x, width;
        int statusY;
        int plotTop, plotBottom;
        int labelW, traceX, traceW;
        int maxLanes;
        int statLines;
        int sensorPlotBottom;
        int sensorGridX, sensorGridY, sensorGridW, sensorGridH;
    }

    private Geometry geometry() {
        Geometry g = new Geometry();
        g.x = UiTheme.contentX(screenW);
        g.width = UiTheme.contentWidth(screenW);

        boolean compact = screenH < 280;
        int row1Y   = compact ? 38 : ROW1_Y;
        int btnH    = compact ? 16 : BTN_H;
        int gap     = compact ? 2 : GAP;
        int row3Y   = row1Y + (btnH + gap) * 2;
        g.statusY   = row3Y + btnH + (compact ? 2 : 4);
        g.plotTop   = g.statusY + 12;

        int bottomLimit = screenH - UiTheme.contentMargin(screenW);

        // Linea de tiempo: pistas a la derecha de una columna de etiquetas
        g.labelW = Math.max(60, Math.min(112, g.width / 4));
        g.traceX = g.x + g.labelW;
        g.traceW = Math.max(10, g.width - g.labelW - 6);
        g.plotBottom = bottomLimit - 12;
        int laneArea = g.plotBottom - g.plotTop - AXIS_H - 4;
        g.maxLanes = Math.max(1, Math.min(MAX_LANES, laneArea / MIN_LANE_H));

        // Sensor: grafica arriba y estadisticas debajo
        int avail = bottomLimit - g.plotTop;
        g.statLines = avail >= 175 ? 6 : (avail >= 150 ? 5 : 3);
        int statsH = g.statLines * STAT_LINE_H + 2;
        g.sensorPlotBottom = Math.max(g.plotTop + 40, bottomLimit - statsH - 4);
        int plotH = g.sensorPlotBottom - g.plotTop;
        g.sensorGridX = g.x + 36;
        g.sensorGridY = g.plotTop + 6;
        g.sensorGridW = Math.max(10, g.width - 36 - 36);
        g.sensorGridH = Math.max(10, plotH - AXIS_H - 10);
        return g;
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  DIBUJO
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, Font font,
                       int screenWidth, int screenHeight) {
        this.screenW = screenWidth;
        this.screenH = screenHeight;

        int x = UiTheme.contentX(screenWidth);
        UiDraw.pageTitle(gui, font, x,
                Component.translatable("gui.serialcraft.visualize.title"), UiTheme.ACCENT_VISUALIZE,
                Component.translatable("gui.serialcraft.visualize.subtitle"));

        Geometry g = geometry();
        if (view == View.TIMELINE) renderTimeline(gui, font, g);
        else                       renderSensor(gui, font, g);

        renderStatusLine(gui, font, g);
    }

    // ── Linea de tiempo ───────────────────────────────────────────────────────

    private void renderTimeline(GuiGraphicsExtractor gui, Font font, Geometry g) {
        int plotH = g.plotBottom - g.plotTop;

        if (lanes.isEmpty()) {
            UiDraw.inputWell(gui, g.x, g.plotTop, g.width, plotH);
            int cy = g.plotTop + plotH / 2 - 8;
            gui.centeredText(font, Component.translatable("gui.serialcraft.visualize.timeline_empty_1"),
                    g.x + g.width / 2, cy, UiTheme.TEXT_SECONDARY);
            gui.centeredText(font, Component.translatable("gui.serialcraft.visualize.timeline_empty_2"),
                    g.x + g.width / 2, cy + 12, UiTheme.TEXT_MUTED);
            return;
        }

        int count = lanes.size();
        int laneH = Math.clamp((plotH - AXIS_H - 4) / count, MIN_LANE_H, MAX_LANE_H);

        for (int i = 0; i < count; i++) {
            renderLane(gui, font, lanes.get(i), g.x, g.plotTop + i * laneH,
                    g.labelW, g.traceW, laneH, i == count - 1);
        }

        int axisY = g.plotTop + count * laneH + 2;
        renderTimeAxis(gui, font, g.traceX, axisY, g.traceW);

        if (hiddenLanes > 0) {
            String note = Component.translatable("gui.serialcraft.visualize.lane_hidden", hiddenLanes).getString();
            gui.text(font, note, g.x, g.plotBottom + 1, UiTheme.TEXT_MUTED, false);
        }
    }

    private void renderLane(GuiGraphicsExtractor gui, Font font, LaneData lane,
                            int x, int y, int labelW, int traceW, int h, boolean last) {
        int traceX = x + labelW;
        int color = (lane.id.dir() == Direction.RX) ? COLOR_RX : COLOR_TX;

        // Fondo de la pista
        gui.fill(traceX, y, traceX + traceW, y + h, UiTheme.BG_CONSOLE);
        gui.fill(traceX, y, traceX + traceW, y + 1, COLOR_GRID);
        if (last) gui.fill(traceX, y + h - 1, traceX + traceW, y + h, COLOR_GRID);

        // Guia central suave (50 % de la escala)
        int midY = y + h / 2;
        gui.fill(traceX, midY, traceX + traceW, midY + 1, COLOR_GRID_MID);

        // Columna de etiquetas
        int dirBadgeColor = (lane.id.dir() == Direction.RX) ? UiTheme.OK_DARK : COLOR_TX;
        String dirText = (lane.id.dir() == Direction.RX) ? "RX" : "TX";
        int badgeW = font.width(dirText) + 4;
        gui.fill(x, y + 2, x + badgeW, y + 11, dirBadgeColor);
        gui.text(font, dirText, x + 2, y + 3, UiTheme.TEXT_INVERSE, false);

        int keyX = x + badgeW + 3;
        int keyW = labelW - badgeW - 6;
        gui.text(font, fit(font, lane.id.key(), keyW), keyX, y + 3, UiTheme.TEXT_PRIMARY, false);

        if (lane.hasAny) {
            String valStr = LaneScale.format(lane.last);
            gui.text(font, fit(font, valStr, labelW - 6), x, y + 13, color, false);

            if (h >= 32) {
                String age = Component.translatable("gui.serialcraft.visualize.lane_age",
                        formatAge(lane.ageNanos)).getString();
                gui.text(font, fit(font, age, labelW - 6), x, y + 23, UiTheme.TEXT_MUTED, false);
            }
        } else {
            gui.text(font, Component.translatable("gui.serialcraft.visualize.lane_nodata"),
                    x, y + 13, UiTheme.TEXT_MUTED, false);
        }

        if (lane.hasAny) {
            if (LaneScale.isEvent(lane.id.key())) {
                renderEventBars(gui, lane, traceX, y, traceW, h, color);
            } else {
                renderStepBand(gui, lane.env, lane.range, traceX, y, traceW, h, color);
                renderSampleDots(gui, lane, traceX, y, traceW, h);
            }
        }
    }

    private void renderStepBand(GuiGraphicsExtractor gui, StepEnvelope env, LaneScale.Range r,
                                int x, int y, int w, int h, int color) {
        int usableH = h - 2;
        int base = y + h - 2;

        int fill = (color & 0x00FFFFFF) | 0x22000000;
        for (int c = 0; c < w; c++) {
            if (!env.known[c]) continue;
            float nLo = LaneScale.normalize(r, env.lo[c]);
            float nHi = LaneScale.normalize(r, env.hi[c]);
            int yHi = base - Math.round(nHi * usableH);
            int yLo = base - Math.round(nLo * usableH);
            if (yLo < yHi) { int tmp = yLo; yLo = yHi; yHi = tmp; }

            gui.fill(x + c, yLo, x + c + 1, base + 1, fill);
            gui.fill(x + c, yHi, x + c + 1, Math.max(yHi + 1, yLo + 1), color);
        }
    }

    private void renderSampleDots(GuiGraphicsExtractor gui, LaneData lane,
                                  int x, int y, int w, int h) {
        if (lane.times == null) return;
        int n = lane.times.length;
        if (n <= 0 || n > 60) return;

        double span = (double) (viewTo - viewFrom);
        int usableH = h - 2;
        int base = y + h - 2;

        for (int i = 0; i < n; i++) {
            long t = lane.times[i];
            if (t < viewFrom || t > viewTo) continue;
            int px = x + (int) Math.round((t - viewFrom) / span * (w - 1));
            float norm = LaneScale.normalize(lane.range, lane.values[i]);
            int py = base - Math.round(norm * usableH);
            gui.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFFFFFF);
        }
    }

    private void renderEventBars(GuiGraphicsExtractor gui, LaneData lane,
                                 int x, int y, int w, int h, int color) {
        if (lane.times == null) return;
        double span = (double) (viewTo - viewFrom);
        for (int i = 0; i < lane.times.length; i++) {
            long t = lane.times[i];
            if (t < viewFrom || t > viewTo) continue;
            int px = x + (int) Math.round((t - viewFrom) / span * (w - 1));
            gui.fill(px - 1, y + 2, px + 2, y + h - 2, color);
        }
    }

    private void renderTimeAxis(GuiGraphicsExtractor gui, Font font, int x, int y, int w) {
        gui.fill(x, y, x + w, y + 1, COLOR_DIM);

        int secs = WINDOW_SECONDS[windowIndex];
        int major = (secs <= 10) ? 2 : (secs <= 30 ? 5 : 10);

        for (int s = 0; s <= secs; s += major) {
            int tx = x + (int) Math.round((double) s / secs * (w - 1));
            gui.fill(tx, y, tx + 1, y + 3, COLOR_DIM);
            String label = "-" + (secs - s) + "s";
            if (s == secs) label = "0s";
            int lw = font.width(label);
            int lx = Math.clamp(tx - lw / 2, x, x + w - lw);
            gui.text(font, label, lx, y + 4, COLOR_DIM, false);
        }
    }

    // ── Vista Sensor ──────────────────────────────────────────────────────────

    private void renderSensor(GuiGraphicsExtractor gui, Font font, Geometry g) {
        if (sensor == null) {
            int plotH = g.sensorPlotBottom - g.plotTop;
            UiDraw.inputWell(gui, g.x, g.plotTop, g.width, plotH);
            gui.centeredText(font, Component.translatable("gui.serialcraft.visualize.sensor_none"),
                    g.x + g.width / 2, g.plotTop + plotH / 2 - 4, UiTheme.TEXT_SECONDARY);
            return;
        }

        renderSensorGrid(gui, font, g, sensor);

        int statsY = g.sensorPlotBottom + 4;
        renderSensorStats(gui, font, g, sensor, statsY);
    }

    private void renderSensorGrid(GuiGraphicsExtractor gui, Font font, Geometry g, SensorData d) {
        int x = g.sensorGridX, y = g.sensorGridY, w = g.sensorGridW, h = g.sensorGridH;

        gui.fill(x, y, x + w, y + h, UiTheme.BG_CONSOLE);
        gui.outline(x, y, w, h, COLOR_GRID);

        // 4 lineas horizontales (0 %, 33 %, 66 %, 100 %)
        for (int i = 0; i <= 3; i++) {
            int gy = y + (h - 1) * i / 3;
            gui.fill(x, gy, x + w, gy + 1, (i == 0 || i == 3) ? COLOR_GRID : COLOR_GRID_MID);

            float val = d.range.hi() - (d.range.span() * i / 3f);
            String label = LaneScale.format(val);
            gui.text(font, label, g.x, gy - 4, COLOR_DIM, false);

            if (d.showRedstone) {
                int rs = (int) Math.round((1.0 - (double) i / 3.0) * 15.0);
                String rsStr = "RS " + rs;
                gui.text(font, rsStr, x + w + 4, gy - 4, COLOR_REDSTONE, false);
            }
        }

        renderStepBand(gui, d.env, d.range, x, y, w, h, COLOR_RX);

        // Trazo de redstone (escalonado, naranja) si aplica
        if (d.showRedstone && d.stats != null && d.times != null) {
            renderRedstoneSteps(gui, d, x, y, w, h);
        }

        // Eje de tiempo abajo
        renderTimeAxis(gui, font, x, y + h + 2, w);
    }

    private void renderRedstoneSteps(GuiGraphicsExtractor gui, SensorData d,
                                     int x, int y, int w, int h) {
        int usableH = h - 2;
        int base = y + h - 2;
        double span = (double) (viewTo - viewFrom);

        int lastRs = -1;
        int prevPx = x;
        int prevPy = base;

        int n = d.times.length;
        for (int i = 0; i < n; i++) {
            long t = d.times[i];
            int rs = SignalType.ANALOG.wireToRedstone(Math.round(d.values[i]));
            int px = (t <= viewFrom) ? x : x + (int) Math.round((t - viewFrom) / span * (w - 1));
            px = Math.clamp(px, x, x + w - 1);
            int py = base - Math.round((rs / 15f) * usableH);

            if (lastRs != -1) {
                gui.fill(prevPx, prevPy, px, prevPy + 1, COLOR_REDSTONE);
                int yMin = Math.min(prevPy, py);
                int yMax = Math.max(prevPy, py);
                gui.fill(px, yMin, px + 1, yMax + 1, COLOR_REDSTONE);
            }
            prevPx = px;
            prevPy = py;
            lastRs = rs;
        }
        if (lastRs != -1 && prevPx < x + w) {
            gui.fill(prevPx, prevPy, x + w, prevPy + 1, COLOR_REDSTONE);
        }
    }

    private void renderSensorStats(GuiGraphicsExtractor gui, Font font, Geometry g,
                                   SensorData d, int y0) {
        SignalStats s = d.stats;
        int line = 0;

        int rsCurrent = SignalType.ANALOG.wireToRedstone(Math.round(s.last()));
        String valStr = d.showRedstone
                ? Component.translatable("gui.serialcraft.visualize.stat_value_fmt",
                        LaneScale.format(s.last()), rsCurrent).getString()
                : LaneScale.format(s.last());
        statRow(gui, font, g, y0 + (line++) * STAT_LINE_H, "stat_value", valStr, COLOR_RX);

        statRow(gui, font, g, y0 + (line++) * STAT_LINE_H, "stat_rate",
                String.format(Locale.ROOT, "%.1f Hz", s.rateHz()), COLOR_LABEL);

        if (g.statLines >= 5) {
            String rangeStr = Component.translatable("gui.serialcraft.visualize.stat_range_fmt",
                    LaneScale.format(s.min()), LaneScale.format(s.max()),
                    LaneScale.format((float) s.mean())).getString();
            statRow(gui, font, g, y0 + (line++) * STAT_LINE_H, "stat_range", rangeStr, COLOR_LABEL);

            String noiseStr = Component.translatable("gui.serialcraft.visualize.stat_noise_fmt",
                    LaneScale.format(s.variation()), s.suggestedDeadband()).getString();
            int noiseColor = (s.variation() >= 3f) ? UiTheme.WARN_DARK : UiTheme.OK_DARK;
            statRow(gui, font, g, y0 + (line++) * STAT_LINE_H, "stat_noise", noiseStr, noiseColor);

            if (d.showRedstone) {
                String rsChanges = Component.translatable("gui.serialcraft.visualize.stat_redstone_fmt",
                        s.mappedChanges()).getString();
                int rsColor = (s.mappedChanges() > 2) ? UiTheme.WARN_DARK : UiTheme.OK_DARK;
                statRow(gui, font, g, y0 + (line++) * STAT_LINE_H, "stat_redstone", rsChanges, rsColor);
            } else {
                statRow(gui, font, g, y0 + (line++) * STAT_LINE_H, "stat_redstone",
                        Component.translatable("gui.serialcraft.visualize.stat_scale_note").getString(),
                        UiTheme.TEXT_MUTED);
            }
        }

        if (g.statLines >= 6) {
            boolean flicker = d.showRedstone && s.mappedChanges() > 2;
            String tipKey = flicker ? "stat_redstone_tip" : "stat_quiet_tip";
            gui.text(font, fit(font, Component.translatable("gui.serialcraft.visualize." + tipKey).getString(), g.width),
                    g.x, y0 + line * STAT_LINE_H, flicker ? UiTheme.WARN_DARK : UiTheme.TEXT_SECONDARY, false);
        }
    }

    private void statRow(GuiGraphicsExtractor gui, Font font, Geometry g, int y, String key, String value, int color) {
        gui.text(font, Component.translatable("gui.serialcraft.visualize." + key), g.x, y, UiTheme.TEXT_SECONDARY, false);
        gui.text(font, fit(font, value, g.width - UiDraw.LABEL_COLUMN_WIDTH),
                g.x + UiDraw.LABEL_COLUMN_WIDTH, y, color, false);
    }

    // ── Linea de estado ───────────────────────────────────────────────────────

    /**
     * Una linea propia entre los controles y la grafica: asi un aviso nunca
     * tapa datos. Izquierda: generador o aviso; derecha: pausa.
     */
    private void renderStatusLine(GuiGraphicsExtractor gui, Font font, Geometry g) {
        String left = null;
        int leftColor = UiTheme.OK_DARK;
        if (generator.isRunning()) {
            left = Component.translatable("gui.serialcraft.visualize.gen_running",
                    shapeLabel().getString(), genKey.trim()).getString();
        } else if (genMessageKey != null && System.nanoTime() < genMessageUntil) {
            left = Component.translatable(genMessageKey).getString();
            leftColor = UiTheme.WARN_DARK;
        }

        int rightLimit = g.x + g.width;
        if (paused) {
            String p = Component.translatable("gui.serialcraft.visualize.paused").getString();
            int w = font.width(p);
            gui.text(font, p, rightLimit - w, g.statusY, UiTheme.ERROR_DARK, false);
            rightLimit -= w + 8;
        }
        if (left != null) {
            gui.text(font, fit(font, left, rightLimit - g.x), g.x, g.statusY, leftColor, false);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  UTILIDADES
    // ════════════════════════════════════════════════════════════════════════════

    /** Recorta el texto con ".." para que quepa en {@code maxWidth} pixeles. */
    private static String fit(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String s = text;
        while (s.length() > 1 && font.width(s + "..") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    private static String formatAge(long nanos) {
        long s = nanos / NANOS_PER_SEC;
        return s < 60 ? s + " s" : (s / 60) + " min";
    }

    // ── Etiquetas ─────────────────────────────────────────────────────────────

    private Component viewLabel() {
        return Component.translatable(view == View.TIMELINE
                ? "gui.serialcraft.visualize.view_timeline"
                : "gui.serialcraft.visualize.view_sensor");
    }

    private Component traceLabel() {
        return Component.translatable(lineStyle
                ? "gui.serialcraft.visualize.trace_line"
                : "gui.serialcraft.visualize.trace_step");
    }

    private Component windowLabel() {
        return Component.translatable("gui.serialcraft.visualize.window_s", WINDOW_SECONDS[windowIndex]);
    }

    private Component pauseLabel() {
        return Component.translatable(paused
                ? "gui.serialcraft.visualize.resume"
                : "gui.serialcraft.visualize.pause");
    }

    private Component genToggleLabel() {
        return Component.translatable(generator.isRunning()
                ? "gui.serialcraft.visualize.gen_stop"
                : "gui.serialcraft.visualize.gen_start");
    }

    private Component shapeLabel() {
        return Component.translatable(switch (shape) {
            case RAMP     -> "gui.serialcraft.visualize.shape_ramp";
            case TRIANGLE -> "gui.serialcraft.visualize.shape_triangle";
            case SQUARE   -> "gui.serialcraft.visualize.shape_square";
            case SINE     -> "gui.serialcraft.visualize.shape_sine";
            case STAIRS   -> "gui.serialcraft.visualize.shape_stairs";
        });
    }

    private Component periodLabel() {
        double p = PERIODS_SEC[periodIndex];
        return Component.translatable("gui.serialcraft.visualize.gen_period",
                p == Math.floor(p) ? String.valueOf((int) p) : String.valueOf(p));
    }

    private Component amplitudeLabel() {
        return Component.translatable("gui.serialcraft.visualize.gen_amp", AMPLITUDES[amplitudeIndex]);
    }
}
