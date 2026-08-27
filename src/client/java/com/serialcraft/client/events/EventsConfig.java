package com.serialcraft.client.events;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.serialcraft.SerialCraft;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Preferencias de la pestana "Eventos": que canales estan activos y cada
 * cuanto se muestrean los periodicos.
 *
 * Vive enteramente en el cliente y por eso no reutiliza SerialConfig (que
 * ademas no lo referencia nadie: vease la nota en esa clase). Que datos
 * mandar a la placa fisica es una preferencia de interfaz del jugador que
 * abre el panel, no un dato de la partida: no depende de ningun BlockEntity,
 * no lo necesita ver otro jugador, y no hay nada que validar en el servidor.
 * Igual que BoardsPage recuerda su ultimo estado localmente, esto solo
 * necesita un archivo junto al resto de configuracion del mod.
 */
public final class EventsConfig {

    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("serialcraft-events.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Ticks entre dos muestras de un evento periodico. 20 ticks = 1s. */
    public static final int MIN_INTERVAL_TICKS = 5;   // 4 Hz como maximo
    public static final int MAX_INTERVAL_TICKS = 200; // una vez cada 10 s como minimo

    /** Los preset que ofrece el boton de frecuencia en la UI. */
    public static final int[] INTERVAL_PRESETS_TICKS = { 5, 10, 20, 40, 60, 100, 200 };

    private static EventsConfig instance;

    // ── Opciones guardadas ────────────────────────────────────────────────
    //
    // Se guarda por nombre de constante (Set<String>), no un EnumSet ni un
    // array de ordinales: si en el futuro se anade o reordena un GameEvent,
    // un archivo antiguo con nombres desconocidos simplemente los ignora en
    // vez de desplazar que evento corresponde a que indice.
    public Set<String> enabledEvents = new LinkedHashSet<>(List.of(
            GameEvent.GAME_TIME.name(),
            GameEvent.WEATHER.name(),
            GameEvent.HEALTH.name(),
            GameEvent.HUNGER.name(),
            GameEvent.DAMAGE_TAKEN.name()
    ));
    public int intervalTicks = 20;

    // ══════════════════════════════════════════════════════════════════════

    public static synchronized EventsConfig get() {
        if (instance == null) load();
        return instance;
    }

    private static void load() {
        if (Files.exists(FILE)) {
            try (var reader = Files.newBufferedReader(FILE)) {
                instance = GSON.fromJson(reader, EventsConfig.class);
            } catch (IOException | JsonSyntaxException e) {
                SerialCraft.LOGGER.warn("No se pudo leer serialcraft-events.json, uso valores por defecto", e);
                instance = null;
            }
        }
        if (instance == null) instance = new EventsConfig();
        if (instance.enabledEvents == null) instance.enabledEvents = new LinkedHashSet<>();
        instance.intervalTicks = Math.clamp(instance.intervalTicks, MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS);
    }

    public boolean isEnabled(GameEvent event) {
        return enabledEvents.contains(event.name());
    }

    public void setEnabled(GameEvent event, boolean value) {
        boolean changed = value ? enabledEvents.add(event.name()) : enabledEvents.remove(event.name());
        if (changed) save();
    }

    public void cycleInterval() {
        int current = intervalTicks;
        int next = INTERVAL_PRESETS_TICKS[0];
        for (int i = 0; i < INTERVAL_PRESETS_TICKS.length; i++) {
            if (INTERVAL_PRESETS_TICKS[i] == current) {
                next = INTERVAL_PRESETS_TICKS[(i + 1) % INTERVAL_PRESETS_TICKS.length];
                break;
            }
        }
        this.intervalTicks = next;
        save();
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (var writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            SerialCraft.LOGGER.warn("No se pudo guardar serialcraft-events.json", e);
        }
    }
}
