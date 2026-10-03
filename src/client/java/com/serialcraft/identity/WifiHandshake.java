package com.serialcraft.identity;

import java.io.IOException;
import java.util.Optional;

/**
 * Handshake del servidor Wi-Fi, con dos caminos para la primera linea:
 *
 *   1. TOKEN (el de siempre; compatible con todos los sketches existentes)
 *        placa -> mod   YXTUEA
 *        mod   -> placa OK
 *
 *   2. PLACA RECORDADA (sin token)
 *        placa -> mod   TRUST ESP32-A1B2C3D4E5F6
 *        mod   -> placa CHAL 9d1f...            (reto aleatorio)
 *        placa -> mod   <HMAC-SHA256(secreto, reto) en hex>
 *        mod   -> placa OK
 *
 *      Errores: "ERR UNKNOWN" (el mod no conoce esa placa: usa el token) y
 *      "ERR TRUST" (respuesta incorrecta: la placa debe borrar su clave).
 *
 * Esta clase solo habla en lineas, sin sockets ni Minecraft, para poder
 * probarla con flujos en memoria.
 */
public final class WifiHandshake {

    private WifiHandshake() {}

    @FunctionalInterface
    public interface LineIn  { /** @return la linea, o null si se cerro o era demasiado larga. */ String readLine() throws IOException; }

    @FunctionalInterface
    public interface LineOut { void writeLine(String line); }

    public enum Method { NONE, TOKEN, TRUSTED }

    public record Result(boolean ok, Method method, String uid, String reason) {
        static Result ok(Method m, String uid) { return new Result(true, m, uid, ""); }
        static Result fail(String reason)      { return new Result(false, Method.NONE, "", reason); }
    }

    private static final String TRUST_PREFIX = "TRUST ";

    /**
     * @param sessionToken token de esta sesion (el que muestra la Laptop); vacio = ninguno valido
     * @param store        placas recordadas, o null para desactivar el camino 2
     */
    public static Result perform(LineIn in, LineOut out, String sessionToken, TrustedBoardStore store)
            throws IOException {

        String first = in.readLine();
        first = first == null ? "" : first.trim();
        if (first.isEmpty()) {
            out.writeLine("ERR TOKEN");
            return Result.fail("primera linea vacia");
        }

        if (first.regionMatches(true, 0, TRUST_PREFIX, 0, TRUST_PREFIX.length())) {
            return trusted(first.substring(TRUST_PREFIX.length()).trim(), in, out, store);
        }

        boolean tokenOk = !sessionToken.isEmpty() && TrustAuth.verify(sessionToken, first);
        if (!tokenOk) {
            out.writeLine("ERR TOKEN");
            return Result.fail("token invalido");
        }
        out.writeLine("OK");
        return Result.ok(Method.TOKEN, "");
    }

    private static Result trusted(String uid, LineIn in, LineOut out, TrustedBoardStore store)
            throws IOException {

        Optional<TrustedBoardStore.Entry> entry =
                (store != null && BoardHello.isValidUid(uid)) ? store.find(uid) : Optional.empty();
        if (entry.isEmpty()) {
            out.writeLine("ERR UNKNOWN");
            return Result.fail("placa desconocida: " + sanitizeForLog(uid));
        }

        String nonce = TrustAuth.newNonce();
        out.writeLine("CHAL " + nonce);

        String answer = in.readLine();
        String expected = TrustAuth.response(entry.get().secret(), nonce);
        if (answer == null || !TrustAuth.verify(expected, answer)) {
            out.writeLine("ERR TRUST");
            return Result.fail("respuesta incorrecta de " + uid);
        }

        out.writeLine("OK");
        return Result.ok(Method.TRUSTED, uid);
    }

    private static String sanitizeForLog(String s) {
        String t = s.replaceAll("[^A-Za-z0-9_-]", "?");
        return t.length() > 40 ? t.substring(0, 40) : t;
    }
}
