package com.serialcraft.identity;

import com.serialcraft.identity.BoardIdentity.Bridge;
import com.serialcraft.identity.BoardIdentity.Confidence;
import com.serialcraft.identity.BoardIdentity.Family;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Identificacion por descriptores USB (VID:PID).
 *
 * Tres niveles, del mas al menos preciso:
 *  1. VID:PID exacto de una placa concreta (Arduino UNO Q, UNO R3, Nano ESP32...).
 *     Los PID de Arduino salen de los boards.txt oficiales (ArduinoCore-avr,
 *     -renesas, -samd, -megaavr, -zephyr) y de arduino-esp32.
 *  2. Solo el fabricante (Espressif, Raspberry Pi, Adafruit...).
 *  3. Solo el chip puente (CH340, CP210x, FTDI...): la placa que hay detras
 *     NO se puede saber por USB. Aqui se queda en BRIDGE y lo resuelve el
 *     anuncio de la placa (mc_id) o su banner de arranque.
 */
public final class UsbBoardCatalog {

    private UsbBoardCatalog() {}

    private static final int ARDUINO_LLC = 0x2341;
    private static final int ARDUINO_SRL = 0x2A03;

    private record Model(String name, Family family) {}

    private static final Map<Long, Model> EXACT = new HashMap<>();

    private static long key(int vid, int pid) { return ((long) vid << 16) | pid; }

    /** Registra el PID para los dos VID de Arduino. */
    private static void arduino(String name, int... pids) {
        for (int pid : pids) {
            EXACT.put(key(ARDUINO_LLC, pid), new Model(name, Family.ARDUINO));
            EXACT.put(key(ARDUINO_SRL, pid), new Model(name, Family.ARDUINO));
        }
    }

    private static void exact(int vid, int pid, String name, Family family) {
        EXACT.put(key(vid, pid), new Model(name, family));
    }

    static {
        // AVR
        arduino("Arduino UNO R3",    0x0001, 0x0043, 0x0243, 0x006a);
        arduino("Arduino UNO Mini",  0x0062);
        arduino("Arduino Mega 2560", 0x0010, 0x0042, 0x0210, 0x0242);
        arduino("Arduino Mega ADK",  0x003f, 0x0044);
        arduino("Arduino Leonardo",  0x0036, 0x8036);
        arduino("Arduino Micro",     0x0037, 0x0237, 0x8037, 0x8237);
        arduino("Arduino Nano Every", 0x0058);
        // SAMD
        arduino("Arduino Zero",         0x004d, 0x024d, 0x804d, 0x824d);
        arduino("Arduino MKR 1000 WiFi", 0x004e, 0x024e, 0x804e, 0x824e);
        arduino("Arduino MKR Zero",     0x004f, 0x804f);
        arduino("Arduino MKR WiFi 1010", 0x0054, 0x8054);
        arduino("Arduino MKR WAN 1310", 0x0059, 0x8059);
        arduino("Arduino Nano 33 IoT",  0x0057, 0x8057);
        // Renesas RA4M1 / RA4M4
        arduino("Arduino UNO R4 Minima", 0x0069, 0x0369);
        arduino("Arduino UNO R4 WiFi",   0x006d, 0x1002);
        arduino("Arduino Nano R4",       0x0074, 0x0374);
        arduino("Arduino Portenta C33",  0x0068, 0x0368);
        arduino("Arduino Opta",          0x0064, 0x0664, 0x006e, 0x016e, 0x0071, 0x0171);
        // Zephyr / Linux
        arduino("Arduino UNO Q",         0x0078);
        arduino("Arduino Ventuno Q",     0x007a);
        exact(0x1209, 0xca01, "Arduino Ventuno Q", Family.ARDUINO);
        arduino("Arduino Nano Matter",   0x0072);
        arduino("Arduino Nano 33 BLE",   0x065a);
        arduino("Arduino Nano RP2040 Connect", 0x065e);
        arduino("Arduino Giga R1",       0x0666);
        // Una placa Arduino con chip ESP: la marca engana, la familia es ESP.
        EXACT.put(key(ARDUINO_LLC, 0x0070), new Model("Arduino Nano ESP32", Family.ESP));

        // Espressif, USB nativo del propio chip (sin puente)
        exact(0x303a, 0x1001, "ESP32 (S3/C3/C6)", Family.ESP); // USB-Serial/JTAG integrado
        exact(0x303a, 0x0002, "ESP32-S2", Family.ESP);
        exact(0x303a, 0x0003, "ESP32-S2", Family.ESP);
        exact(0x303a, 0x4001, "ESP32-S3", Family.ESP);
        // Teensy (el VID 0x16C0 es compartido: solo vale con este PID)
        exact(0x16c0, 0x0483, "Teensy", Family.TEENSY);
    }

    // ══════════════════════════════════════════════════════════════════════

    /**
     * @param serial numero de serie USB, o null. Si existe se usa como
     *               identificador estable ("usb-VVVV-PPPP-serie").
     * @param text   descripcion / producto del puerto, o null; solo se usa
     *               como ultimo recurso.
     */
    public static BoardIdentity identify(int vid, int pid, String serial, String text) {
        String uid = usbUid(vid, pid, serial);
        String source = String.format(Locale.ROOT, "usb %04x:%04x", vid & 0xFFFF, pid & 0xFFFF);

        Model exact = EXACT.get(key(vid, pid));
        if (exact != null) {
            return new BoardIdentity(exact.name, exact.family, Confidence.MODEL, Bridge.NONE, uid, source);
        }

        BoardIdentity vendor = byVendor(vid, uid, source);
        if (vendor != null) return vendor;

        Bridge bridge = bridgeOf(vid, pid);
        if (bridge != Bridge.NONE) {
            // El texto puede delatar la placa aunque el chip no lo haga.
            BoardIdentity fromText = fromText(text, uid, source);
            if (fromText != null) return new BoardIdentity(fromText.model(), fromText.family(),
                    Confidence.VENDOR, bridge, uid, source);
            return new BoardIdentity("", Family.UNKNOWN, Confidence.BRIDGE, bridge, uid, source);
        }

        BoardIdentity fromText = fromText(text, uid, source);
        return fromText != null ? fromText
                : new BoardIdentity("", Family.UNKNOWN, Confidence.UNKNOWN, Bridge.NONE, uid, source);
    }

    private static BoardIdentity byVendor(int vid, String uid, String source) {
        return switch (vid) {
            case ARDUINO_LLC, ARDUINO_SRL ->
                    new BoardIdentity("Arduino", Family.ARDUINO, Confidence.VENDOR, Bridge.NONE, uid, source);
            case 0x303a ->
                    new BoardIdentity("ESP32", Family.ESP, Confidence.VENDOR, Bridge.NONE, uid, source);
            case 0x2e8a ->
                    new BoardIdentity("Raspberry Pi Pico", Family.RASPBERRY_PI, Confidence.VENDOR, Bridge.NONE, uid, source);
            case 0x0483 ->
                    new BoardIdentity("STM32", Family.STM32, Confidence.VENDOR, Bridge.NONE, uid, source);
            case 0x239a ->
                    new BoardIdentity("Adafruit", Family.OTHER, Confidence.VENDOR, Bridge.NONE, uid, source);
            case 0x1b4f ->
                    new BoardIdentity("SparkFun", Family.OTHER, Confidence.VENDOR, Bridge.NONE, uid, source);
            case 0x2886 ->
                    new BoardIdentity("Seeed Studio", Family.OTHER, Confidence.VENDOR, Bridge.NONE, uid, source);
            default -> null;
        };
    }

    private static Bridge bridgeOf(int vid) {
        return switch (vid) {
            case 0x1a86 -> Bridge.CH340;   // afinado por PID en bridgeOf(vid, pid)
            case 0x10c4 -> Bridge.CP210X;
            case 0x0403 -> Bridge.FTDI;
            case 0x067b -> Bridge.PL2303;
            default     -> Bridge.NONE;
        };
    }

    /** Distingue CH340 de CH9102 (mismo fabricante, VID 0x1A86). */
    public static Bridge bridgeOf(int vid, int pid) {
        if (vid == 0x1a86 && (pid == 0x55d4 || pid == 0x55d3)) return Bridge.CH9102;
        return bridgeOf(vid);
    }

    private static BoardIdentity fromText(String text, String uid, String source) {
        if (text == null || text.isBlank()) return null;
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("esp32"))  return new BoardIdentity("ESP32",  Family.ESP, Confidence.VENDOR, Bridge.NONE, uid, source + " txt");
        if (t.contains("esp8266")) return new BoardIdentity("ESP8266", Family.ESP, Confidence.VENDOR, Bridge.NONE, uid, source + " txt");
        if (t.contains("arduino")) return new BoardIdentity("Arduino", Family.ARDUINO, Confidence.VENDOR, Bridge.NONE, uid, source + " txt");
        return null;
    }

    /** Identificador estable a partir del numero de serie USB; vacio si no hay. */
    static String usbUid(int vid, int pid, String serial) {
        if (serial == null) return "";
        String clean = serial.replaceAll("[^A-Za-z0-9]", "");
        if (clean.length() < 6) return "";            // series tipicas de clones ("0001"): no distinguen entre placas
        if (clean.length() > 16) clean = clean.substring(0, 16);
        return String.format(Locale.ROOT, "usb-%04x-%04x-%s", vid & 0xFFFF, pid & 0xFFFF, clean);
    }
}
