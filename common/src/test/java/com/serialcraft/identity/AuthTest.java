package com.serialcraft.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class AuthTest {
    @TempDir Path folder;
    @Test void hmacMatchesPublishedSha256Vector() {
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                TrustAuth.response("key", "The quick brown fox jumps over the lazy dog"));
        assertTrue(TrustAuth.verify("abcdef", "ABCDEF"));
        assertFalse(TrustAuth.verify("abcdef", "abcdeg"));
    }
    @Test void trustPersistsAndRevocationRejectsReconnect() throws Exception {
        var file = folder.resolve("boards.properties");
        var store = new TrustedBoardStore(file);
        String secret = TrustAuth.newSecret();
        assertTrue(store.remember("ESP32-1234", "ESP32", secret, "192.168.1.2"));
        var restored = new TrustedBoardStore(file); restored.load();
        assertEquals(secret, restored.find("ESP32-1234").orElseThrow().secret());
        var input = new ArrayDeque<String>(); input.add("TRUST ESP32-1234");
        var output = new ArrayList<String>();
        var result = WifiHandshake.perform(input::poll, line -> {
            output.add(line);
            if (line.startsWith("CHAL ")) input.add(TrustAuth.response(secret, line.substring(5)));
        }, "", restored);
        assertTrue(result.ok()); assertEquals(WifiHandshake.Method.TRUSTED, result.method());
        assertTrue(restored.forget("ESP32-1234"));
        var denied = WifiHandshake.perform(() -> "TRUST ESP32-1234", output::add, "", restored);
        assertFalse(denied.ok());
    }
    @Test void replayAndEmptyTokenAreRejected() throws Exception {
        var store = new TrustedBoardStore(folder.resolve("boards.properties"));
        String secret = TrustAuth.newSecret();
        store.remember("ESP32-1234", "ESP32", secret, "");
        var input = new ArrayDeque<String>();
        input.add("TRUST ESP32-1234"); input.add(TrustAuth.response(secret, "oldnonce"));
        assertFalse(WifiHandshake.perform(input::poll, line -> {}, "", store).ok());
        assertFalse(WifiHandshake.perform(() -> "TOKEN", line -> {}, "", store).ok());
        assertTrue(WifiHandshake.perform(() -> "TOKEN", line -> {}, "TOKEN", store).ok());
    }
    @Test void failedSaveDoesNotClaimToRememberAndSecretsAreRedacted() throws Exception {
        var parent = folder.resolve("file"); java.nio.file.Files.writeString(parent, "occupied");
        var store = new TrustedBoardStore(parent.resolve("boards.properties"));
        assertFalse(store.remember("ESP32-1234", "ESP32", "a".repeat(32), ""));
        assertTrue(store.all().isEmpty());
        assertFalse(new TrustedBoardStore.Entry("ESP32-1234", "ESP32", "supersecret", "", 0).toString().contains("supersecret"));
    }
}
