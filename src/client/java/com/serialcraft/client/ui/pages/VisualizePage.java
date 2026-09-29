package com.serialcraft.client.ui.pages;

import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pagina "Visualizar": osciloscopio en tiempo real de alta precision con soporte
 * para filtrado de claves especificas (ej. 'pot_val', 'temp'), interpolacion
 * Catmull-Rom continua ultra-fluida y graduacion completa con unidades en los ejes X e Y.
 */
public class VisualizePage implements Page {

    public enum Source {
        SERIAL,
        MANUAL
    }

    public enum ScaleMode {
        AUTO,
        PWM_8BIT,
        ANALOG_10BIT
    }

    private static final int BUFFER_CAPACITY = 240;
    private static final int[] WINDOW_SIZES = {60, 120, 240}; // 3s, 6s, 12s a 20 Hz

    /** Regex para extraer valores numericos de punto flotante o enteros con signo. */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)");

    // ── Estado de captura y buffer ────────────────────────────────
    private final float[] buffer = new float[BUFFER_CAPACITY];
    private int head = 0;
    private int sampleCount = 0;

    private Source source = Source.SERIAL;
    private ScaleMode scaleMode = ScaleMode.AUTO;
    private int windowIndex = 1; // 120 muestras (6 segundos) por defecto
    private boolean paused = false;

    private float lastSerialValue = 0.0f;
    private String keyFilter = "pot_val";
    private String manualValueText = "128";

    // ── Widgets ───────────────────────────────────────────────────
    private @Nullable EditBox inputBox = null;

    // ════════════════════════════════════════════════════════════════════════════
    //  CICLO DE VIDA DE LA PAGINA
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public void init(PanelUI panel, int screenWidth, int screenHeight) {
        this.inputBox = null;

        int x = UiTheme.contentX(screenWidth);
        int width = screenWidth - x - UiTheme.CONTENT_MARGIN;

        int row1Y = 46;
        int row2Y = 69;
        int btnH  = 18;
        int gap   = 4;

        // ── Fila 1 de controles: Fuente, Escala y Ventana ─────────────
        int thirdW = (width - 2 * gap) / 3;

        panel.addWidget(SolidButton.primary(x, row1Y, thirdW, btnH, sourceLabel(), btn -> {
            source = (source == Source.SERIAL) ? Source.MANUAL : Source.SERIAL;
            panel.setTab(PanelUI.Tab.VISUALIZE); // Reconstruir para actualizar el EditBox
        }));

        panel.addWidget(SolidButton.primary(x + thirdW + gap, row1Y, thirdW, btnH, scaleLabel(), btn -> {
            scaleMode = switch (scaleMode) {
                case AUTO -> ScaleMode.PWM_8BIT;
                case PWM_8BIT -> ScaleMode.ANALOG_10BIT;
                case ANALOG_10BIT -> ScaleMode.AUTO;
            };
            btn.setMessage(scaleLabel());
        }));

        panel.addWidget(SolidButton.primary(x + 2 * (thirdW + gap), row1Y, width - 2 * (thirdW + gap), btnH, windowLabel(), btn -> {
            windowIndex = (windowIndex + 1) % WINDOW_SIZES.length;
            btn.setMessage(windowLabel());
        }));

        // ── Fila 2 de controles: Pausar/Reanudar, Limpiar y Campo de Clave / Entrada ──
        int pauseW = 68;
        int clearW = 54;

        panel.addWidget(SolidButton.of(x, row2Y, pauseW, btnH, pauseLabel(), btn -> {
            paused = !paused;
            btn.setVariant(paused ? SolidButton.Variant.DANGER : SolidButton.Variant.SUCCESS);
            btn.setMessage(pauseLabel());
        }, paused ? SolidButton.Variant.DANGER : SolidButton.Variant.SUCCESS));

        panel.addWidget(SolidButton.soft(x + pauseW + gap, row2Y, clearW, btnH,
                Component.translatable("gui.serialcraft.visualize.clear"), btn -> clearBuffer()));

        int editX = x + pauseW + clearW + 2 * gap;
        int editW = width - (pauseW + clearW + 2 * gap);

        Font font = Minecraft.getInstance().font;
        if (source == Source.SERIAL) {
            inputBox = new EditBox(font, editX, row2Y, editW, btnH,
                    Component.translatable("gui.serialcraft.visualize.key_filter_hint"));
            inputBox.setValue(keyFilter);
            inputBox.setHint(Component.translatable("gui.serialcraft.visualize.key_filter_hint"));
            inputBox.setTextColor(UiTheme.TEXT_INVERSE);
            inputBox.setResponder(val -> this.keyFilter = val);
            panel.addWidget(inputBox);
        } else {
            inputBox = new EditBox(font, editX, row2Y, editW, btnH,
                    Component.translatable("gui.serialcraft.visualize.manual_value"));
            inputBox.setValue(manualValueText);
            inputBox.setTextColor(UiTheme.TEXT_INVERSE);
            inputBox.setResponder(val -> this.manualValueText = val);
            panel.addWidget(inputBox);
        }
    }

    @Override
    public void tick() {
        if (paused) return;

        float currentVal;
        if (source == Source.SERIAL) {
            lastSerialValue = extractLastSerialValue(keyFilter, lastSerialValue);
            currentVal = lastSerialValue;
        } else {
            currentVal = parseManualValue(manualValueText);
        }

        pushSample(currentVal);
    }

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, Font font,
                       int screenWidth, int screenHeight) {
        int x = UiTheme.contentX(screenWidth);
        int width = screenWidth - x - UiTheme.CONTENT_MARGIN;

        UiDraw.pageTitle(gui, font, x,
                Component.translatable("gui.serialcraft.visualize.title"), UiTheme.ACCENT_VISUALIZE,
                Component.translatable("gui.serialcraft.visualize.subtitle"));

        renderOscilloscope(gui, font, x, width, screenHeight);
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  RENDERIZADO DEL OSCILOSCOPIO CON EJES Y UNIDADES
    // ════════════════════════════════════════════════════════════════════════════

    private void renderOscilloscope(GuiGraphicsExtractor gui, Font font, int x, int width, int screenHeight) {
        int scopeY = 93;
        int readoutHeight = 38;
        int availableH = screenHeight - scopeY - UiTheme.CONTENT_MARGIN - readoutHeight;
        int scopeH = Math.max(70, availableH);

        // Fondo oscuro y contorno general del panel
        gui.fill(x, scopeY, x + width, scopeY + scopeH, UiTheme.BG_CONSOLE);
        gui.outline(x, scopeY, width, scopeH, UiTheme.LINE_STRONG);

        // Geometría interna de la retícula con márgenes para los ejes
        int axisYWidth = 36;
        int axisXHeight = 13;
        int gridX = x + axisYWidth;
        int gridY = scopeY + 8;
        int gridW = width - axisYWidth - 8;
        int gridH = scopeH - axisXHeight - 12;

        if (gridW <= 10 || gridH <= 10) return;

        // Fondo de la cuadrícula
        gui.fill(gridX, gridY, gridX + gridW, gridY + gridH, 0xFF14181B);
        gui.outline(gridX, gridY, gridW, gridH, 0xFF37474F);

        // 4 divisiones horizontales (líneas guía de nivel 25%, 50%, 75%)
        for (int i = 1; i <= 3; i++) {
            int gy = gridY + (gridH * i) / 4;
            int color = (i == 2) ? 0xFF2A3942 : 0xFF1E282E;
            gui.fill(gridX + 1, gy, gridX + gridW - 1, gy + 1, color);
            // Pequeña marca de graduación en el eje Y
            gui.fill(gridX - 3, gy, gridX, gy + 1, 0xFF78909C);
        }

        // 6 divisiones verticales (base de tiempo)
        for (int i = 1; i <= 5; i++) {
            int gx = gridX + (gridW * i) / 6;
            int color = (i == 3) ? 0xFF2A3942 : 0xFF1E282E;
            gui.fill(gx, gridY + 1, gx + 1, gridY + gridH - 1, color);
            // Pequeña marca de graduación en el eje X
            gui.fill(gx, gridY + gridH, gx + 1, gridY + gridH + 3, 0xFF78909C);
        }

        // Extracción y análisis de muestras visibles
        int windowSize = WINDOW_SIZES[windowIndex];
        int count = Math.min(sampleCount, windowSize);

        float current = 0.0f;
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        float sum = 0.0f;

        if (count > 0) {
            float[] raw = new float[count];
            for (int i = 0; i < count; i++) {
                int idx = (head - count + i + BUFFER_CAPACITY) % BUFFER_CAPACITY;
                float val = buffer[idx];
                raw[i] = val;
                if (val < min) min = val;
                if (val > max) max = val;
                sum += val;
            }

            current = raw[count - 1];

            // Determinación de límites de escala para los ejes
            float scaleMin;
            float scaleMax;
            switch (scaleMode) {
                case PWM_8BIT -> {
                    scaleMin = 0.0f;
                    scaleMax = 255.0f;
                }
                case ANALOG_10BIT -> {
                    scaleMin = 0.0f;
                    scaleMax = 1023.0f;
                }
                default -> {
                    float dynamicMin = min;
                    float dynamicMax = max;
                    if (dynamicMax - dynamicMin < 1e-3f) {
                        dynamicMin -= 1.0f;
                        dynamicMax += 1.0f;
                    }
                    scaleMin = dynamicMin;
                    scaleMax = dynamicMax;
                }
            }

            float range = scaleMax - scaleMin;
            float[] normalized = new float[count];
            for (int i = 0; i < count; i++) {
                if (range < 1e-4f) {
                    normalized[i] = 0.5f;
                } else {
                    normalized[i] = (raw[i] - scaleMin) / range;
                }
            }

            // Trazado continuo suave (Catmull-Rom spline + halo de brillo y relleno)
            UiDraw.smoothPolyline(gui, gridX + 1, gridY + 1, gridW - 2, gridH - 2,
                    normalized, count, UiTheme.SCOPE_TRACE, true);

            // ── Unidades y etiquetas del Eje Y (Amplitud) ─────────────────
            renderAxisYLabels(gui, font, x, gridX, gridY, gridH, scaleMin, scaleMax);
        } else {
            // Escala por defecto sin datos
            float defaultMax = (scaleMode == ScaleMode.PWM_8BIT) ? 255.0f : (scaleMode == ScaleMode.ANALOG_10BIT ? 1023.0f : 100.0f);
            renderAxisYLabels(gui, font, x, gridX, gridY, gridH, 0.0f, defaultMax);
        }

        // ── Unidades y etiquetas del Eje X (Tiempo) ───────────────────────
        renderAxisXLabels(gui, font, gridX, gridY + gridH + 3, gridW, windowSize);

        // Indicador de variable o clave activa en la esquina superior izquierda
        String keyLabel = (source == Source.SERIAL && !keyFilter.trim().isEmpty())
                ? "[" + keyFilter.trim() + "]"
                : (source == Source.MANUAL ? "[MANUAL]" : "[RAW]");
        gui.text(font, keyLabel, gridX + 4, gridY + 3, 0xFF4DD0E1, false);

        // Indicador de estado pausado
        if (paused) {
            Component pausedComp = Component.translatable("gui.serialcraft.visualize.paused");
            int badgeW = font.width(pausedComp) + 8;
            UiDraw.badge(gui, font, gridX + gridW - badgeW - 4, gridY + 3,
                    pausedComp, UiTheme.ERROR_DARK, UiTheme.TEXT_INVERSE);
        }

        // ── Lectura numérica inferior ─────────────────────────────────────
        int readoutY = scopeY + scopeH + 4;
        String noDataStr = Component.translatable("gui.serialcraft.visualize.no_data").getString();

        String currentStr = (count > 0) ? formatNumber(current) : noDataStr;
        String minMaxStr  = (count > 0) ? (formatNumber(min) + " / " + formatNumber(max)) : noDataStr;
        String avgStr     = (count > 0) ? formatNumber(sum / count) : noDataStr;

        UiDraw.labelledRow(gui, font, x, readoutY,
                Component.translatable("gui.serialcraft.visualize.current"), currentStr, UiTheme.SCOPE_TRACE);
        UiDraw.labelledRow(gui, font, x + 120, readoutY,
                Component.translatable("gui.serialcraft.visualize.minmax"), minMaxStr, UiTheme.TEXT_PRIMARY);
        UiDraw.labelledRow(gui, font, x + 250, readoutY,
                Component.translatable("gui.serialcraft.visualize.average"), avgStr, UiTheme.TEXT_PRIMARY);

        // Etiqueta de la clave rastreada
        Component trackComp = (source == Source.SERIAL && !keyFilter.trim().isEmpty())
                ? Component.translatable("gui.serialcraft.visualize.tracking_key", keyFilter.trim())
                : Component.translatable("gui.serialcraft.visualize.tracking_all");
        gui.text(font, trackComp, x, readoutY + 13, UiTheme.TEXT_SECONDARY, false);
    }

    private void renderAxisYLabels(GuiGraphicsExtractor gui, Font font, int x, int gridX, int gridY, int gridH,
                                   float scaleMin, float scaleMax) {
        float range = scaleMax - scaleMin;

        // Top (100%)
        String topStr = formatNumber(scaleMax);
        gui.text(font, topStr, gridX - font.width(topStr) - 4, gridY - 3, 0xFF90A4AE, false);

        // Mid (50%)
        String midStr = formatNumber(scaleMin + range * 0.5f);
        gui.text(font, midStr, gridX - font.width(midStr) - 4, gridY + gridH / 2 - 4, 0xFF78909C, false);

        // Bottom (0%)
        String botStr = formatNumber(scaleMin);
        gui.text(font, botStr, gridX - font.width(botStr) - 4, gridY + gridH - 5, 0xFF90A4AE, false);
    }

    private void renderAxisXLabels(GuiGraphicsExtractor gui, Font font, int gridX, int y, int gridW, int windowSize) {
        float totalSec = (float) windowSize * 0.05f; // 20 Hz -> 50 ms por muestra

        String tLeft = String.format(Locale.ROOT, "-%.1fs", totalSec);
        String tMid1 = String.format(Locale.ROOT, "-%.1fs", totalSec * 0.66f);
        String tMid2 = String.format(Locale.ROOT, "-%.1fs", totalSec * 0.33f);
        String tNow  = "0.0s";

        gui.text(font, tLeft, gridX, y, 0xFF78909C, false);
        gui.text(font, tMid1, gridX + (gridW * 2) / 6 - font.width(tMid1) / 2, y, 0xFF607D8B, false);
        gui.text(font, tMid2, gridX + (gridW * 4) / 6 - font.width(tMid2) / 2, y, 0xFF607D8B, false);
        gui.text(font, tNow, gridX + gridW - font.width(tNow), y, 0xFF90A4AE, false);

        // Leyenda del eje de tiempo a la derecha
        Component baseComp = Component.translatable("gui.serialcraft.visualize.timebase",
                String.format(Locale.ROOT, "%.1f", totalSec));
        int baseW = font.width(baseComp);
        gui.text(font, baseComp, gridX + gridW - baseW, y + 10, UiTheme.TEXT_MUTED, false);
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  OPERACIONES CON EL BUFFER Y FUENTES DE DATOS
    // ════════════════════════════════════════════════════════════════════════════

    private void pushSample(float val) {
        buffer[head] = val;
        head = (head + 1) % BUFFER_CAPACITY;
        if (sampleCount < BUFFER_CAPACITY) {
            sampleCount++;
        }
    }

    private void clearBuffer() {
        head = 0;
        sampleCount = 0;
    }

    /**
     * Parsea el valor numerico asociado a una clave especifica (ej. 'pot_val', 'temp')
     * o extrae el ultimo numero si la clave esta vacia.
     */
    public static float extractLastSerialValue(@Nullable String filter, float fallback) {
        List<String> history = ConnectionManager.recentHistory(16);
        boolean hasFilter = filter != null && !filter.trim().isEmpty() && !filter.trim().equals("*");
        Pattern keyPattern = null;

        if (hasFilter) {
            String quoted = Pattern.quote(filter.trim());
            // Soporta: pot_val: 512, pot_val=1023, pot_val 450, {"pot_val": 123}, pot_val:12.34
            keyPattern = Pattern.compile("(?i)(?:^|[\\s,;{\"\\[])" + quoted + "(?:[\"\\s]*[:=]|\\s+)+([-+]?(?:\\d+\\.?\\d*|\\.\\d+))");
        }

        for (int i = history.size() - 1; i >= 0; i--) {
            String line = history.get(i);
            if (!line.startsWith("RX:")) continue;
            String payload = line.substring(3).trim();

            if (hasFilter) {
                Matcher m = keyPattern.matcher(payload);
                String lastMatch = null;
                while (m.find()) {
                    lastMatch = m.group(1);
                }
                if (lastMatch != null) {
                    try {
                        return Float.parseFloat(lastMatch);
                    } catch (NumberFormatException ignored) {}
                }
            } else {
                Matcher m = NUMBER_PATTERN.matcher(payload);
                String lastMatch = null;
                while (m.find()) {
                    lastMatch = m.group();
                }
                if (lastMatch != null) {
                    try {
                        return Float.parseFloat(lastMatch);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return fallback;
    }

    private static float parseManualValue(String text) {
        if (text == null) return 0.0f;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return 0.0f;
        try {
            return Float.parseFloat(trimmed);
        } catch (NumberFormatException ignored) {
            return 0.0f;
        }
    }

    private static String formatNumber(float val) {
        if (Float.isNaN(val) || Float.isInfinite(val)) return "0";
        if (val == (long) val) {
            return String.format(Locale.ROOT, "%d", (long) val);
        }
        return String.format(Locale.ROOT, "%.1f", val);
    }

    // ── Componentes de etiquetas de botones ───────────────────────
    private Component sourceLabel() {
        return Component.translatable(source == Source.SERIAL
                ? "gui.serialcraft.visualize.source_serial"
                : "gui.serialcraft.visualize.source_manual");
    }

    private Component scaleLabel() {
        return switch (scaleMode) {
            case PWM_8BIT -> Component.translatable("gui.serialcraft.visualize.scale_fixed_pwm");
            case ANALOG_10BIT -> Component.translatable("gui.serialcraft.visualize.scale_fixed_adc");
            case AUTO -> Component.translatable("gui.serialcraft.visualize.scale_auto");
        };
    }

    private Component windowLabel() {
        int samples = WINDOW_SIZES[windowIndex];
        float sec = (float) samples * 0.05f;
        return Component.translatable("gui.serialcraft.visualize.window", samples, String.format(Locale.ROOT, "%.0f", sec));
    }

    private Component pauseLabel() {
        return Component.translatable(paused
                ? "gui.serialcraft.visualize.resume"
                : "gui.serialcraft.visualize.pause");
    }
}
