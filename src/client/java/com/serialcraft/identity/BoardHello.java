package com.serialcraft.identity;

import com.serialcraft.identity.BoardIdentity.Bridge;
import com.serialcraft.identity.BoardIdentity.Confidence;
import com.serialcraft.identity.BoardIdentity.Family;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lineas de identificacion entre placa y mod. Comparten el canal de siempre
 * (USB o TCP) y el prefijo reservado "mc_":
 *
 *   mod   -> placa   mc_who:1                        "¿quien eres?" (sonda)
 *   placa -> mod     mc_id:model=ESP32-S3;uid=AABBCC "soy esto"
 *   mod   -> placa   mc_key:<secreto>                clave para reconectar sin token
 *
 * Una placa antigua ignora "mc_who" y "mc_key" (claves desconocidas, ya se
 * documento asi), y el mod ignora que una placa no responda: todo es opcional.
 * Las lineas mc_id nunca llegan al servidor: las consume el cliente.
 *
 * Todo lo que viene de la red es NO CONFIABLE: se filtra y se acota antes de
 * mostrarlo en la interfaz o usarlo como clave de un fichero.
 */
public final class BoardHello {

    private BoardHello() {}

    public static final String PROBE   = "mc_who:1";
    public static final String ID_PREFIX  = "mc_id:";
    public static final String KEY_PREFIX = "mc_key:";

    public static final int MAX_MODEL_LENGTH = 32;
    private static final int MAX_FIELDS = 8;

    private static final Pattern UID_OK = Pattern.compile("[A-Za-z0-9_-]{4,32}");
    private static final Pattern MODEL_BAD_CHARS = Pattern.compile("[^A-Za-z0-9 .+\\-_/()]");
    private static final Pattern ESP32_CHIP = Pattern.compile("esp32([sch]\\d)?");

    public static boolean isHello(String line) {
        return line != null && line.startsWith(ID_PREFIX);
    }

    public static boolean isValidUid(String uid) {
        return uid != null && UID_OK.matcher(uid).matches();
    }

    /** @return la identidad declarada, o vacio si la linea no aporta nada utilizable. */
    public static Optional<BoardIdentity> parse(String line) {
        if (!isHello(line)) return Optional.empty();
        String body = line.substring(ID_PREFIX.length()).trim();
        if (body.isEmpty()) return Optional.empty();

        String model = "";
        String uid = "";

        if (body.indexOf('=') < 0) {
            model = body;                                   // forma corta: mc_id:esp32-s3
        } else {
            String[] fields = body.split(";", MAX_FIELDS);
            for (String field : fields) {
                int eq = field.indexOf('=');
                if (eq <= 0) continue;
                String k = field.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String v = field.substring(eq + 1).trim();
                switch (k) {
                    case "model" -> model = v;
                    case "uid"   -> uid = v;
                    default      -> { /* claves futuras: se ignoran */ }
                }
            }
        }

        model = normalizeModel(sanitizeModel(model));
        if (!isValidUid(uid)) uid = "";
        if (model.isEmpty() && uid.isEmpty()) return Optional.empty();

        return Optional.of(new BoardIdentity(model, familyOf(model), Confidence.DECLARED,
                Bridge.NONE, uid, "mc_id"));
    }

    /** Quita caracteres raros y acota la longitud: el texto acaba en pantalla. */
    static String sanitizeModel(String raw) {
        if (raw == null) return "";
        String s = MODEL_BAD_CHARS.matcher(raw).replaceAll("").trim();
        return s.length() > MAX_MODEL_LENGTH ? s.substring(0, MAX_MODEL_LENGTH).trim() : s;
    }

    /** "esp32s3" / "ESP32_S3" -> "ESP32-S3"; "uno-q" -> "Arduino UNO Q". */
    public static String normalizeModel(String model) {
        if (model == null || model.isBlank()) return "";
        String flat = model.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");

        if (flat.equals("unoq") || flat.equals("arduinounoq")) return "Arduino UNO Q";
        if (flat.equals("esp8266")) return "ESP8266";
        var m = ESP32_CHIP.matcher(flat);
        if (m.matches()) {
            return m.group(1) == null ? "ESP32" : "ESP32-" + m.group(1).toUpperCase(Locale.ROOT);
        }
        return model;
    }

    /** Identidad de una placa recordada, antes de que vuelva a presentarse. */
    public static BoardIdentity remembered(String name, String uid) {
        String model = normalizeModel(sanitizeModel(name));
        return new BoardIdentity(model, familyOf(model), Confidence.MODEL, Bridge.NONE,
                isValidUid(uid) ? uid : "", "recordada");
    }

    static Family familyOf(String model) {
        String m = model.toLowerCase(Locale.ROOT);
        if (m.contains("esp32") || m.contains("esp8266") || m.startsWith("esp")) return Family.ESP;
        if (m.contains("arduino") || m.contains("uno") || m.contains("nano")
                || m.contains("mega") || m.contains("mkr") || m.contains("leonardo")) return Family.ARDUINO;
        if (m.contains("pico") || m.contains("rp2040") || m.contains("rp2350") || m.contains("raspberry"))
            return Family.RASPBERRY_PI;
        if (m.contains("stm32")) return Family.STM32;
        if (m.contains("teensy")) return Family.TEENSY;
        return m.isEmpty() ? Family.UNKNOWN : Family.OTHER;
    }
}
