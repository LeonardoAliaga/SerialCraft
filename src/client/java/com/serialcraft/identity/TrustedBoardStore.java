package com.serialcraft.identity;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Placas Wi-Fi recordadas: las que pueden reconectarse sin teclear el token.
 *
 * Se guarda en un fichero .properties (config/serialcraft-boards.properties):
 *
 *   settings.autoRemember=false
 *   settings.autoStartWifi=true
 *   settings.probeBoards=true
 *   board.ESP32-A1B2C3D4E5F6.name=ESP32-S3
 *   board.ESP32-A1B2C3D4E5F6.secret=3f9c...   (clave compartida, 128 bits)
 *   board.ESP32-A1B2C3D4E5F6.lastIp=192.168.1.60
 *   board.ESP32-A1B2C3D4E5F6.lastSeen=1790000000
 *
 * El secreto se guarda en claro porque el mod necesita la clave para calcular
 * el HMAC del reto; el fichero se crea con permisos 600 donde el sistema lo
 * permite. Olvidar una placa borra su clave y la deja fuera.
 *
 * Sin dependencias de Minecraft (ni de Gson): se puede probar sola.
 */
public final class TrustedBoardStore {

    public record Entry(String uid, String name, String secret, String lastIp, long lastSeen) {
        @Override public String toString() { return "Entry[uid=" + uid + ", name=" + name + ", secret=<redacted>]"; }
    }

    private final Path file;
    private final Map<String, Entry> boards = new LinkedHashMap<>();

    /** Recordar sin pulsar el boton "Recordar placa". Apagado: es una decision del jugador. */
    private boolean autoRemember  = false;
    /** Arrancar el servidor Wi-Fi al entrar al mundo si hay placas recordadas. */
    private boolean autoStartWifi = true;
    /** Enviar la sonda mc_who tras conectar, para que la placa diga que es. */
    private boolean probeBoards   = true;

    public TrustedBoardStore(Path file) { this.file = file; }

    // ── Ajustes ────────────────────────────────────────────────────────────

    public synchronized boolean autoRemember()  { return autoRemember; }
    public synchronized boolean autoStartWifi() { return autoStartWifi; }
    public synchronized boolean probeBoards()   { return probeBoards; }

    // ── Consulta ───────────────────────────────────────────────────────────

    public synchronized Optional<Entry> find(String uid) {
        return uid == null ? Optional.empty() : Optional.ofNullable(boards.get(uid));
    }

    public synchronized boolean hasWifiBoards() { return !boards.isEmpty(); }

    /** Mas recientes primero. */
    public synchronized List<Entry> all() {
        List<Entry> list = new ArrayList<>(boards.values());
        list.sort(Comparator.comparingLong(Entry::lastSeen).reversed());
        return list;
    }

    // ── Cambios ────────────────────────────────────────────────────────────

    /** Alta o sustitucion de la clave de una placa. @return false si no se pudo guardar. */
    public synchronized boolean remember(String uid, String name, String secret, String ip) {
        if (!BoardHello.isValidUid(uid)) throw new IllegalArgumentException("uid invalido: " + uid);
        if (secret == null || secret.length() < 16) throw new IllegalArgumentException("secreto demasiado corto");
        String cleanName = BoardHello.sanitizeModel(name);
        Entry previous = boards.put(uid, new Entry(uid, cleanName.isEmpty() ? uid : cleanName, secret,
                ip == null ? "" : ip, nowSeconds()));
        if (save()) return true;
        if (previous == null) boards.remove(uid); else boards.put(uid, previous);
        return false;
    }

    public synchronized boolean forget(String uid) {
        Entry previous = boards.remove(uid);
        if (previous == null) return false;
        if (save()) return true;
        boards.put(uid, previous);
        return false;
    }

    /** Actualiza IP y fecha de la ultima conexion (y el nombre, si cambio). */
    public synchronized void touch(String uid, String name, String ip) {
        Entry old = boards.get(uid);
        if (old == null) return;
        String cleanName = BoardHello.sanitizeModel(name);
        boards.put(uid, new Entry(uid, cleanName.isEmpty() ? old.name() : cleanName, old.secret(),
                ip == null ? old.lastIp() : ip, nowSeconds()));
        save();
    }

    // ── Persistencia ───────────────────────────────────────────────────────

    public synchronized void load() {
        boards.clear();
        if (!Files.isRegularFile(file)) return;

        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException | IllegalArgumentException e) {
            return;   // fichero ilegible: se empieza en blanco, sin romper el juego
        }

        autoRemember  = Boolean.parseBoolean(p.getProperty("settings.autoRemember",  "false"));
        autoStartWifi = Boolean.parseBoolean(p.getProperty("settings.autoStartWifi", "true"));
        probeBoards   = Boolean.parseBoolean(p.getProperty("settings.probeBoards",   "true"));

        for (String k : p.stringPropertyNames()) {
            if (!k.startsWith("board.") || !k.endsWith(".secret")) continue;
            String uid = k.substring("board.".length(), k.length() - ".secret".length());
            String secret = p.getProperty(k, "").trim();
            if (!BoardHello.isValidUid(uid) || secret.length() < 16) continue;

            String prefix = "board." + uid + ".";
            long seen;
            try { seen = Long.parseLong(p.getProperty(prefix + "lastSeen", "0").trim()); }
            catch (NumberFormatException e) { seen = 0; }

            boards.put(uid, new Entry(uid, p.getProperty(prefix + "name", uid),
                    secret, p.getProperty(prefix + "lastIp", ""), seen));
        }
    }

    /** Escritura atomica (temporal + renombrado). @return false si fallo. */
    public synchronized boolean save() {
        Properties p = new Properties();
        p.setProperty("settings.autoRemember",  String.valueOf(autoRemember));
        p.setProperty("settings.autoStartWifi", String.valueOf(autoStartWifi));
        p.setProperty("settings.probeBoards",   String.valueOf(probeBoards));
        for (Entry e : boards.values()) {
            String prefix = "board." + e.uid() + ".";
            p.setProperty(prefix + "name",     e.name());
            p.setProperty(prefix + "secret",   e.secret());
            p.setProperty(prefix + "lastIp",   e.lastIp());
            p.setProperty(prefix + "lastSeen", String.valueOf(e.lastSeen()));
        }

        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);

            // Apply permissions before the first secret byte is written.
            if (!Files.exists(tmp)) {
                try { Files.createFile(tmp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))); }
                catch (UnsupportedOperationException e) { Files.createFile(tmp); }
            }
            try { Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------")); }
            catch (UnsupportedOperationException ignored) { /* Windows inherits the config directory ACL. */ }

            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                p.store(w, "SerialCraft - placas recordadas. NO compartas este fichero: contiene claves.");
            }
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            return false;
        }
    }

    private static long nowSeconds() { return System.currentTimeMillis() / 1000L; }
}
