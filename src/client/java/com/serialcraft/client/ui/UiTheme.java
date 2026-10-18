package com.serialcraft.client.ui;

/**
 * Paleta y metricas unicas de la interfaz.
 *
 * Todos los valores son ARGB con canal alfa explicito.
 */
public final class UiTheme {

    private UiTheme() {}

    // ── Superficies ───────────────────────────────────────────────────────────
    public static final int BG_APP        = 0x1808111E;
    public static final int BG_CARD       = 0xA0162934;
    public static final int BG_PANEL      = 0x701B313D;
    public static final int BG_DETAILS    = 0x88172B35;
    public static final int BG_CONSOLE    = 0xD20A1118;
    public static final int BG_CONSOLE_ALT= 0xB013222D;
    public static final int BG_NAV        = 0xBC006C89;
    public static final int BG_NAV_INNER  = 0x88304047;

    // Prueba visual de Welcome: vidrio azul grisáceo sobre el desenfoque nativo.
    // El primer byte controla la opacidad; estos colores no se usan en el panel.
    public static final int BG_WELCOME         = 0x2408111E;
    public static final int WELCOME_PANEL      = 0x50324759;
    public static final int WELCOME_CARD       = 0x90162D3D;
    public static final int WELCOME_FIELD      = 0x9A102130;
    public static final int WELCOME_BADGE      = 0x80466478;
    public static final int WELCOME_LINE       = 0xA083C6E3;
    public static final int WELCOME_TEXT       = 0xFFF1FAFF;
    public static final int WELCOME_MUTED      = 0xFFB3D2E2;
    public static final int WELCOME_CYAN       = 0xFF60D9FF;
    public static final int WELCOME_TEAL       = 0xFF27F0DA;
    public static final int WELCOME_ACTIVE     = 0xA0096265;
    public static final int WELCOME_GREEN      = 0xFF82E88C;
    public static final int WELCOME_CONNECT    = 0xB01B741D;
    public static final int WELCOME_TOKEN      = 0x90304F35;
    public static final int WELCOME_ERROR      = 0xFFFFB7AE;

    public static final int BG_HEADER = 0xA6E1EEFA;
    public static final int HEADER_BRAND = 0xB0007894;
    public static final int HEADER_BORDER = 0xA088DDF3;
    public static final int HEADER_BRAND_BORDER = 0xC443DFED;
    public static final int HEADER_GLINT = 0x30FFFFFF;
    public static final int HEADER_DETAIL = 0xA326D8EB;
    public static final int HEADER_TEXT = 0xFF10212C;
    public static final int HEADER_TEXT_MUTED = 0xFF334E5E;

    // ── Lineas y sombras ──────────────────────────────────────────────────────
    public static final int LINE          = 0x507DADC2;
    public static final int LINE_STRONG   = 0x907DB6CE;
    public static final int LINE_SOFT     = 0x304F8198;
    public static final int SHADOW        = 0x22000000;
    public static final int OVERLAY       = 0x90000000;

    // ── Texto ─────────────────────────────────────────────────────────────────
    public static final int TEXT_PRIMARY   = 0xFFF4F8FC;
    public static final int TEXT_SECONDARY = 0xFFC8D7DF;
    public static final int TEXT_MUTED     = 0xFF8FA7B6;
    public static final int TEXT_ON_DARK   = 0xFFE2EFF5;
    public static final int TEXT_INVERSE   = 0xFFFFFFFF;

    // ── Semantica de estado ───────────────────────────────────────────────────
    public static final int OK        = 0xFF39E77B;
    public static final int OK_DARK   = 0xFF83F2A6;
    public static final int OK_BG     = 0x68205336;
    public static final int WARN      = 0xFFFFC74F;
    public static final int WARN_DARK = 0xFFFFD46A;
    public static final int WARN_BG   = 0x803F2C0A;
    public static final int ERROR     = 0xFFED4770;
    public static final int ERROR_DARK= 0xFFFF88A3;
    public static final int ERROR_BG  = 0x802F1420;
    public static final int INFO      = 0xFF23CCF2;
    public static final int INFO_DARK = 0xFF74E1FA;
    public static final int INFO_BG   = 0x70214B61;
    public static final int NEUTRAL_BG= 0x603C5360;
    public static final int NEUTRAL_TX= 0xFFCCDCE6;
    public static final int CONSOLE_RX= 0xFF4DD0E1;

    // Cada pestana conserva su identidad; los colores semanticos indican estado.
    public static final int ACCENT_PRIMARY          = 0xFF26CDE1;
    public static final int ACCENT_PRIMARY_DARK     = 0xFF5DE6F0;
    public static final int ACCENT_HOME             = 0xFFEC00A6;
    public static final int ACCENT_HOME_BORDER      = 0xFFCF33A3;
    public static final int ACCENT_HOME_BG          = 0x602F142F;
    public static final int ACCENT_BOARDS           = 0xFFFFC107;
    public static final int ACCENT_BOARDS_BORDER    = 0xFFFFA000;
    public static final int ACCENT_EVENTS           = 0xFF42D76A;
    public static final int ACCENT_EVENTS_BORDER    = 0xFF259A55;
    public static final int ACCENT_VISUALIZE        = 0xFF46AAFF;
    public static final int ACCENT_VISUALIZE_BORDER = 0xFF267AC4;

    // ── Osciloscopio / Visualizador ───────────────────────────────────────────
    public static final int SCOPE_TRACE             = 0xFF39FF14;
    public static final int SCOPE_GRID              = 0xFF2A2A2A;

    public static final int TAB_INACTIVE_BG     = 0x9A111E26;
    public static final int TAB_INACTIVE_BORDER = 0x60678189;

    // ── Tokens derivados (antes eran literales sueltos en las paginas) ───────
    public static final int BG_HELP         = 0x901B303E;
    public static final int BG_FIELD        = 0xB010202D;
    public static final int BG_BADGE        = 0x70425C6C;
    public static final int HOVER_TINT      = 0x14FFFFFF;
    public static final int OK_DARK_BORDER  = 0xFF56D282;
    public static final int OK_LINE         = 0x704A9C6B;
    public static final int INFO_LINE       = 0x7066AFCA;
    public static final int TEAL            = 0xB0086D67;
    public static final int TEAL_BORDER     = 0xFF24D6C5;
    public static final int TEAL_BG         = 0x60345351;
    public static final int SLATE           = 0xB0203543;
    public static final int SLATE_BORDER    = 0x90618296;
    public static final int TEXT_ON_NAV     = 0xFFE0F7FA;

    /** Dark semantic fill keeps white action labels readable on the glass theme. */
    public static int glassTint(int color) {
        return 0xB0000000 | ((color & 0x00FEFEFE) >>> 1);
    }

    // ── Metricas ──────────────────────────────────────────────────────────────
    public static final int SECTION_GAP = 12;
    public static final int CARD_PADDING = 12;
    /** Ancho de la barra lateral, en porcentaje del ancho de pantalla. */
    public static final int NAV_WIDTH_PERCENT = 18;
    /** Margen entre la barra lateral y el contenido. */
    public static final int CONTENT_MARGIN    = 20;
    /** Altura de una fila de tarjeta, incluido el hueco inferior. */
    public static final int CARD_ROW_HEIGHT   = 52;
    public static final int CARD_HEIGHT       = 48;
    public static final float TITLE_SCALE     = 1.8f;
    /** Pagina 1.8x; tarjetas 1.2x en negrita; subtitulos 1x en negrita; datos 1x. */
    public static final float CARD_TITLE_SCALE = 1.2f;
    public static final int CARD_HEADER_HEIGHT = 36;
    public static final int CARD_TITLE_INSET = 18;

    public static final int SCROLLBAR_WIDTH   = 6;

    /** Both glass pieces and every page label use the same header geometry. */
    public record HeaderLayout(int y, int height, int slant, int brandX, int brandWidth,
                               int panelX, int panelWidth, int textX, int textRight) {
        public int bottom() { return y + height; }
    }

    public static HeaderLayout headerLayout(int screenWidth, int screenHeight) {
        int height = contentTop(screenHeight) - 14;
        int slant = Math.min(16, height / 3);
        int brandRight = navWidth(screenWidth);
        int panelX = brandRight - slant + 6;
        return new HeaderLayout(8, height, slant, 10, Math.max(1, brandRight - 10),
                panelX, Math.max(1, screenWidth - 10 - panelX),
                brandRight + Math.max(16, contentMargin(screenWidth)),
                screenWidth - (screenWidth >= 640 ? 44 : 22));
    }

    /** The full title/description block is vertically centered inside the glass. */
    public static int headerTitleY(int screenHeight, int lineHeight) {
        int titleHeight = (int) Math.ceil(lineHeight * TITLE_SCALE);
        int blockHeight = titleHeight + (compact(screenHeight) ? 0 : 5 + lineHeight);
        int height = contentTop(screenHeight) - 14;
        return 8 + (height - blockHeight) / 2;
    }

    public static int headerDescriptionY(int screenHeight, int lineHeight) {
        return headerTitleY(screenHeight, lineHeight) + (int) Math.ceil(lineHeight * TITLE_SCALE) + 5;
    }

    /** Pantallas bajas: se omite la descripcion y el contenido sube. */
    public static boolean compact(int screenHeight) { return screenHeight < 280; }

    /** Y donde empieza el contenido de cualquier pagina del panel. */
    public static int contentTop(int screenHeight) { return compact(screenHeight) ? 40 : 62; }

    /** X de la barra de desplazamiento, siempre pegada al borde derecho del contenido. */
    public static int scrollbarX(int screenWidth) {
        return screenWidth - contentMargin(screenWidth) - SCROLLBAR_WIDTH;
    }

    /** Ancho util de la barra lateral para un ancho de pantalla dado. */
    public static int navWidth(int screenWidth) {
        return (screenWidth * NAV_WIDTH_PERCENT) / 100;
    }

    /** Margen horizontal responsivo entre barra lateral y contenido o extremos. */
    public static int contentMargin(int screenWidth) {
        return screenWidth < 500 ? 10 : 18;
    }

    /** Coordenada X donde empieza el contenido de cualquier pagina del panel. */
    public static int contentX(int screenWidth) {
        return navWidth(screenWidth) + contentMargin(screenWidth);
    }

    /** Ancho maximo disponible para tarjetas y contenido en una pantalla dada. */
    public static int contentWidth(int screenWidth) {
        return Math.max(160, screenWidth - contentX(screenWidth) - contentMargin(screenWidth));
    }
}
