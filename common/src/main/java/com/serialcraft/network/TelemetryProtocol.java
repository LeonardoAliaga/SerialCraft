package com.serialcraft.network;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Reglas del protocolo de telemetria (Minecraft -> placa). Clase sin
 * dependencias de Minecraft a proposito: la usan el cliente (para formatear y
 * validar lo que envia) y el servidor (para impedir colisiones con el
 * "Target Data" de los Bloques IO), y asi se puede probar aisladamente.
 *
 * Formato de cable, una linea por dato:
 *
 *      mc_<canal>:<entero>\n
 *
 *  - El prefijo "mc_" esta RESERVADO. Ningun Bloque IO puede usarlo como
 *    Target Data (vease ModNetworking). Sin esa regla, una placa no podria
 *    distinguir un dato de telemetria de una orden de redstone que casualmente
 *    se llamara igual.
 *  - El valor es un entero decimal con signo dentro de [MIN_VALUE, MAX_VALUE],
 *    es decir, cabe en un {@code int} de 16 bits (el de un Arduino Uno). Cada
 *    canal declara ademas su propio rango, mas estrecho (vease GameEvent).
 *  - La clave cumple {@code mc_[a-z0-9_]{1,29}}: 32 caracteres como maximo, el
 *    mismo limite que el Target Data.
 */
public final class TelemetryProtocol {

    private TelemetryProtocol() {}

    public static final String RESERVED_PREFIX = "mc_";

    /** Igual que BoardInfo.MAX_DATA_LENGTH; se repite aqui para no acoplar ambas clases. */
    public static final int MAX_KEY_LENGTH = 32;

    /** Limites absolutos del valor: un int16 con signo. */
    public static final int MIN_VALUE = Short.MIN_VALUE;
    public static final int MAX_VALUE = Short.MAX_VALUE;

    private static final Pattern VALID_KEY = Pattern.compile("mc_[a-z0-9_]{1,29}");

    /**
     * @return true si la clave empieza por el prefijo reservado, sin distinguir
     *         mayusculas: "MC_HEALTH" tambien se rechaza, porque en un sketch
     *         con comparaciones sin distincion de caso seria indistinguible.
     */
    public static boolean isReservedKey(@Nullable String key) {
        return key != null
                && key.regionMatches(true, 0, RESERVED_PREFIX, 0, RESERVED_PREFIX.length());
    }

    /** @return true si la clave es una clave de telemetria bien formada. */
    public static boolean isValidKey(@Nullable String key) {
        return key != null && VALID_KEY.matcher(key).matches();
    }

    /** Version que falla en voz alta: para validar constantes al cargar la clase. */
    public static String requireValidKey(String key) {
        if (!isValidKey(key)) {
            throw new IllegalArgumentException(
                    "Clave de telemetria invalida: '" + key + "' (debe cumplir mc_[a-z0-9_]{1,29})");
        }
        return key;
    }

    /** Recorta un valor al rango pedido y, ademas, al rango absoluto del protocolo. */
    public static int clamp(int value, int min, int max) {
        int lo = Math.max(min, MIN_VALUE);
        int hi = Math.min(max, MAX_VALUE);
        return Math.clamp(value, lo, hi);
    }

    /** Linea de cable, SIN terminador: cada BoardLink anade el suyo. */
    public static String format(String key, int value) {
        return key + ':' + value;
    }
}
