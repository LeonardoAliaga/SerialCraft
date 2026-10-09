package com.serialcraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.serialcraft.SerialCraft;
import net.fabricmc.loader.api.FabricLoader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SerialConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("serialcraft.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static SerialConfig instance;
    public static final List<Integer> USB_BAUD_RATES = List.of(4800, 9600, 19200, 38400, 57600, 115200, 230400);

    // --- OPCIONES GUARDADAS ---
    public BoardProfile boardProfile = BoardProfile.ARDUINO_UNO;
    public int baudRate = 115200;
    public int analogUpdateRate = 1; // Legacy setting retained; IO now processes dirty inputs once per tick.
    private Map<String, Integer> usbBaudRates = new LinkedHashMap<>();

    // --- ENUM DE PERFILES ---
    public enum BoardProfile {
        ARDUINO_UNO("Arduino UNO/Nano", 115200, true),
        ESP32("ESP32 / ESP8266", 115200, false),
        GENERIC_HIGH("Genérica (Rápida)", 115200, true),
        CUSTOM("Personalizada", 115200, true);

        public final String label;
        public final int defaultBaud;
        public final boolean dtrEnabled; // true = reset al conectar (Arduino), false = no reset (ESP32)

        BoardProfile(String label, int defaultBaud, boolean dtrEnabled) {
            this.label = label;
            this.defaultBaud = defaultBaud;
            this.dtrEnabled = dtrEnabled;
        }
    }

    // --- MÉTODOS DE GESTIÓN ---
    public static void load() {
        if (CONFIG_PATH.toFile().exists()) {
            try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
                instance = GSON.fromJson(reader, SerialConfig.class);
            } catch (IOException | JsonParseException e) {
                SerialCraft.LOGGER.warn("No se pudo leer serialcraft.json; usando valores predeterminados", e);
                instance = new SerialConfig();
            }
        } else {
            instance = new SerialConfig();
            save();
        }
        if (instance == null) instance = new SerialConfig();
        if (instance.boardProfile == null) instance.boardProfile = BoardProfile.ARDUINO_UNO;
        if (instance.usbBaudRates == null) instance.usbBaudRates = new LinkedHashMap<>();
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            SerialCraft.LOGGER.warn("No se pudo guardar serialcraft.json", e);
        }
    }

    public static SerialConfig get() {
        if (instance == null) load();
        return instance;
    }

    /** Per-device settings use USB identity, never a model preset or a mutable COM number. */
    public int usbBaudRate(String uid, int fallback) {
        if (uid.isEmpty()) return fallback;
        Integer saved = usbBaudRates.get(uid);
        return saved != null && USB_BAUD_RATES.contains(saved) ? saved : fallback;
    }

    public void rememberUsbBaudRate(String uid, int baud) {
        if (uid.isEmpty() || !USB_BAUD_RATES.contains(baud)) return;
        if (!usbBaudRates.containsKey(uid) && usbBaudRates.size() >= 64)
            usbBaudRates.remove(usbBaudRates.keySet().iterator().next());
        usbBaudRates.put(uid, baud);
        save();
    }

    // Método helper para cambiar perfil y guardar defaults automáticamente
    public void setProfile(BoardProfile profile) {
        this.boardProfile = profile;
        if (profile != BoardProfile.CUSTOM) {
            this.baudRate = profile.defaultBaud;
        }
        save();
    }
}
