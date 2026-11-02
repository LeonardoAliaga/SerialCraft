package com.serialcraft.identity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Criptografia del emparejamiento persistente.
 *
 * La placa NUNCA vuelve a enviar su secreto por la red: el mod le manda un
 * reto aleatorio (nonce) y la placa responde con
 *
 *      HMAC-SHA256( clave = secreto en ASCII hex, mensaje = nonce en ASCII hex )
 *
 * en hexadecimal minuscula. Asi, quien escuche la red Wi-Fi (el canal va en
 * claro) no obtiene nada reutilizable: cada reto es distinto.
 */
public final class TrustAuth {

    private TrustAuth() {}

    private static final SecureRandom RNG = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** 128 bits, 32 caracteres hex. */
    public static String newSecret() { return hex(random(16)); }

    /** 128 bits, 32 caracteres hex. Distinto en cada intento. */
    public static String newNonce()  { return hex(random(16)); }

    public static String response(String secret, String nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal(nonce.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 no disponible", e);   // no ocurre en ninguna JVM
        }
    }

    /** Comparacion en tiempo constante, sin distinguir mayusculas. */
    public static boolean verify(String expected, String provided) {
        if (expected == null || provided == null) return false;
        return MessageDigest.isEqual(
                expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                provided.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] random(int n) {
        byte[] b = new byte[n];
        RNG.nextBytes(b);
        return b;
    }

    private static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[2 * i]     = HEX[(bytes[i] >> 4) & 0xF];
            out[2 * i + 1] = HEX[bytes[i] & 0xF];
        }
        return new String(out);
    }
}
