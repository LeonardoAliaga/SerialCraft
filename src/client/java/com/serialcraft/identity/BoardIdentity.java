package com.serialcraft.identity;

/**
 * Lo que se sabe de la placa conectada y CUANTO se fia el mod de ello.
 *
 * Clase sin dependencias de Minecraft, para poder probarla sola.
 *
 * Un chip puente USB-serie (CH340, CP2102...) no dice que placa hay detras:
 * el mismo CH340 va en un Nano clon, en un ESP32 y en un ESP8266. Por eso la
 * identidad no es una cadena, sino una cadena mas un nivel de confianza, y
 * varias fuentes (USB, banner de arranque, anuncio de la propia placa) se
 * combinan quedandose con la mas fiable ({@link #best}).
 */
public record BoardIdentity(String model, Family family, Confidence confidence,
                            Bridge bridge, String uid, String source) {

    public enum Family { UNKNOWN, ARDUINO, ESP, RASPBERRY_PI, STM32, TEENSY, OTHER }

    /** Orden ascendente: cuanto mas abajo, mas fiable. */
    public enum Confidence {
        /** No se sabe nada. */
        UNKNOWN,
        /** Solo se ve el chip puente USB-serie. */
        BRIDGE,
        /** Se conoce el fabricante (VID) pero no el modelo. */
        VENDOR,
        /** Modelo o chip concreto: VID:PID exacto o banner de arranque. */
        MODEL,
        /** La propia placa dijo lo que es (mc_id). */
        DECLARED
    }

    /** Chip puente USB-serie detectado (solo informativo, para la etiqueta). */
    public enum Bridge { NONE, CH340, CH9102, CP210X, FTDI, PL2303 }

    private static final BoardIdentity UNKNOWN_INSTANCE =
            new BoardIdentity("", Family.UNKNOWN, Confidence.UNKNOWN, Bridge.NONE, "", "");

    public BoardIdentity {
        model  = model  == null ? "" : model;
        uid    = uid    == null ? "" : uid;
        source = source == null ? "" : source;
        if (family == null)     family = Family.UNKNOWN;
        if (confidence == null) confidence = Confidence.UNKNOWN;
        if (bridge == null)     bridge = Bridge.NONE;
    }

    public static BoardIdentity unknown() { return UNKNOWN_INSTANCE; }

    public boolean isKnown()  { return confidence != Confidence.UNKNOWN; }
    public boolean hasModel() { return !model.isEmpty(); }
    public boolean hasUid()   { return !uid.isEmpty(); }

    public BoardIdentity withUid(String newUid) {
        return new BoardIdentity(model, family, confidence, bridge, newUid, source);
    }

    /** Plataforma para la insignia de la interfaz; vacia si no se conoce. */
    public String platformLabel() {
        return switch (family) {
            case ARDUINO      -> "Arduino";
            case ESP          -> model.toLowerCase().contains("8266") ? "ESP8266" : "ESP32";
            case RASPBERRY_PI -> "Raspberry Pi";
            case STM32        -> "STM32";
            case TEENSY       -> "Teensy";
            default           -> "";
        };
    }

    /**
     * Combina dos identidades: gana la de mayor confianza; en empate, la
     * primera. El identificador unico se conserva aunque la ganadora no lo
     * traiga.
     */
    public static BoardIdentity best(BoardIdentity a, BoardIdentity b) {
        BoardIdentity winner = b.confidence.ordinal() > a.confidence.ordinal() ? b : a;
        BoardIdentity other  = winner == a ? b : a;
        return (winner.hasUid() || !other.hasUid()) ? winner : winner.withUid(other.uid);
    }
}
