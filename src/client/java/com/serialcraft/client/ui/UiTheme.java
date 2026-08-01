package com.serialcraft.client.ui;

/**
 * Paleta y metricas unicas de la interfaz.
 *
 * MOTIVO: en la version original habia literales de color repartidos por cinco
 * archivos. 0xFF212121 aparecia nueve veces, 0xFFE0E0E0 siete, 0xFF4CAF50 seis,
 * cada uno escrito a mano. Cambiar el gris de los bordes obligaba a buscar y
 * reemplazar en todo el proyecto, y bastaba olvidar uno para que la interfaz
 * quedara desalineada visualmente sin que nada fallara.
 *
 * Todos los valores son ARGB. El canal alfa va siempre explicito: omitirlo
 * produce transparencia total, que es el error mas comun al escribir colores
 * de GUI en Minecraft a mano.
 */
public final class UiTheme {

    private UiTheme() {}

    // ── Superficies ───────────────────────────────────────────────────────
    public static final int BG_APP        = 0xFFF3F3F3;
    public static final int BG_CARD       = 0xFFFFFFFF;
    public static final int BG_PANEL      = 0xFFF5F5F0;
    public static final int BG_PANEL_HEAD = 0xFFE8E8E0;
    public static final int BG_CONSOLE    = 0xFF1A1A1A;
    public static final int BG_NAV        = 0xFF4995B6;
    public static final int BG_NAV_INNER  = 0xFFF8F4ED;

    // ── Lineas y sombras ──────────────────────────────────────────────────
    public static final int LINE          = 0xFFE0E0E0;
    public static final int LINE_STRONG   = 0xFFAAAAAA;
    public static final int LINE_SOFT     = 0xFFD0D0D0;
    public static final int SHADOW        = 0x22000000;
    public static final int OVERLAY       = 0x90000000;

    // ── Texto ─────────────────────────────────────────────────────────────
    public static final int TEXT_PRIMARY   = 0xFF212121;
    public static final int TEXT_SECONDARY = 0xFF757575;
    public static final int TEXT_MUTED     = 0xFF9E9E9E;
    public static final int TEXT_ON_DARK   = 0xFFE0E0E0;
    public static final int TEXT_INVERSE   = 0xFFFFFFFF;

    // ── Semantica de estado ───────────────────────────────────────────────
    public static final int OK        = 0xFF4CAF50;
    public static final int OK_DARK   = 0xFF2E7D32;
    public static final int OK_BG     = 0xFFE8F5E9;
    public static final int WARN      = 0xFFFFC107;
    public static final int WARN_DARK = 0xFF827717;
    public static final int WARN_BG   = 0xFFFFF9C4;
    public static final int ERROR     = 0xFFF44336;
    public static final int ERROR_DARK= 0xFFC62828;
    public static final int INFO      = 0xFF2196F3;
    public static final int INFO_DARK = 0xFF1565C0;
    public static final int INFO_BG   = 0xFFBBDEFB;
    public static final int NEUTRAL_BG= 0xFFF5F5F5;
    public static final int NEUTRAL_TX= 0xFF424242;

    // ── Acentos por pestana ───────────────────────────────────────────────
    public static final int ACCENT_HOME          = 0xFFE91E63;
    public static final int ACCENT_HOME_BORDER   = 0xFFBA184F;
    public static final int ACCENT_BOARDS        = 0xFFFFC107;
    public static final int ACCENT_BOARDS_BORDER = 0xFFCC9A05;
    public static final int ACCENT_EVENTS        = 0xFF4CAF50;
    public static final int ACCENT_EVENTS_BORDER = 0xFF3C8C40;
    public static final int ACCENT_PRIMARY       = 0xFF00838F;
    public static final int ACCENT_PRIMARY_DARK  = 0xFF006064;

    public static final int TAB_INACTIVE_BG     = 0xFF263238;
    public static final int TAB_INACTIVE_BORDER = 0xFF37474F;

    // ── Metricas ──────────────────────────────────────────────────────────
    /** Ancho de la barra lateral, en porcentaje del ancho de pantalla. */
    public static final int NAV_WIDTH_PERCENT = 18;
    /** Margen entre la barra lateral y el contenido. */
    public static final int CONTENT_MARGIN    = 20;
    /** Altura de una fila de tarjeta, incluido el hueco inferior. */
    public static final int CARD_ROW_HEIGHT   = 52;
    public static final int CARD_HEIGHT       = 48;
    public static final int TITLE_Y           = 22;
    public static final float TITLE_SCALE     = 1.8f;

    /** Ancho util de la barra lateral para un ancho de pantalla dado. */
    public static int navWidth(int screenWidth) {
        return (screenWidth * NAV_WIDTH_PERCENT) / 100;
    }

    /** Coordenada X donde empieza el contenido de cualquier pagina. */
    public static int contentX(int screenWidth) {
        return navWidth(screenWidth) + CONTENT_MARGIN;
    }
}
