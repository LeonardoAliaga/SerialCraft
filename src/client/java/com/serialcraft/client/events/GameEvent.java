package com.serialcraft.client.events;

import com.serialcraft.network.TelemetryProtocol;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Catalogo de datos del juego que la pestana "Eventos" puede enviar a la
 * placa fisica.
 *
 * Cada entrada es autocontenida: su clave de cable, su categoria en la UI, si
 * es un valor que se muestrea a intervalos (PERIODIC) o un suceso puntual que
 * se detecta por flanco (EDGE), el rango [min, max] que garantiza a la placa, y
 * como leer su valor desde el jugador y el nivel del cliente. Anadir un dato
 * nuevo (por ejemplo el bioma o el modo de juego) es una entrada mas en este
 * enum: ni GameEventsTracker ni EventsPage necesitan tocarse para que aparezca
 * en la lista, se recorren con values().
 *
 * El RANGO es parte del protocolo (docs/protocol.md, seccion 10): un sketch
 * puede dimensionar sus variables y sus tablas con esos limites. Por eso
 * sample() recorta el resultado en vez de fiarse del sampler: si un dia un
 * atributo o un mod hace que la salud pase de 1024, la placa sigue recibiendo
 * un valor dentro de lo prometido. Todos los rangos caben en un int16.
 *
 * Los eventos EDGE (dano recibido, muerte) no tienen sampler: su logica de
 * deteccion vive en GameEventsTracker porque necesita comparar dos ticks
 * consecutivos, algo que un sampler sin estado no puede expresar.
 *
 * Desde 26.1, Minecraft ya no tiene un unico "day time" global: cada
 * dimension puede tener su propio "world clock" (ver Level#clockManager).
 * getDefaultClockTime() devuelve el reloj de la dimension en la que esta
 * parado el jugador, que es lo que queremos aqui; getOverworldClockTime()
 * daria siempre la hora del Overworld aunque el jugador este en el Nether.
 */
public enum GameEvent {

    GAME_TIME(Category.WORLD, Kind.PERIODIC, "mc_time", 0, 23999,
            (player, level) -> (int) Math.floorMod(level.getDefaultClockTime(), 24000L)),

    DAY_PHASE(Category.WORLD, Kind.PERIODIC, "mc_isday", 0, 1,
            (player, level) -> Math.floorMod(level.getDefaultClockTime(), 24000L) < 12000L ? 1 : 0),

    WEATHER(Category.WORLD, Kind.PERIODIC, "mc_weather", 0, 2,
            (player, level) -> level.isThundering() ? 2 : (level.isRaining() ? 1 : 0)),

    HEALTH(Category.PLAYER, Kind.PERIODIC, "mc_health", 0, 1024,
            (player, level) -> Math.max(0, Math.round(player.getHealth()))),

    HUNGER(Category.PLAYER, Kind.PERIODIC, "mc_hunger", 0, 20,
            (player, level) -> Math.max(0, player.getFoodData().getFoodLevel())),

    SATURATION(Category.PLAYER, Kind.PERIODIC, "mc_saturation", 0, 20,
            (player, level) -> Math.max(0, Math.round(player.getFoodData().getSaturationLevel()))),

    XP_LEVEL(Category.PLAYER, Kind.PERIODIC, "mc_level", 0, TelemetryProtocol.MAX_VALUE,
            (player, level) -> Math.max(0, player.experienceLevel)),

    OXYGEN(Category.PLAYER, Kind.PERIODIC, "mc_air", 0, 100,
            (player, level) -> player.getMaxAirSupply() <= 0
                    ? 100
                    : Math.clamp(Math.round(player.getAirSupply() * 100f / player.getMaxAirSupply()), 0, 100)),

    ON_FIRE(Category.COMBAT, Kind.PERIODIC, "mc_fire", 0, 1,
            (player, level) -> player.isOnFire() ? 1 : 0),

    /** Puntos de salud perdidos (2 = un corazon). Minimo 1: un golpe siempre cuenta. */
    DAMAGE_TAKEN(Category.COMBAT, Kind.EDGE, "mc_damage", 1, TelemetryProtocol.MAX_VALUE, null),

    /** Siempre vale 1: la aparicion de la linea ES el suceso. */
    DEATH(Category.COMBAT, Kind.EDGE, "mc_death", 1, 1, null);

    // ══════════════════════════════════════════════════════════════════════

    public enum Category { WORLD, PLAYER, COMBAT }

    public enum Kind { PERIODIC, EDGE }

    @FunctionalInterface
    public interface Sampler {
        int sample(LocalPlayer player, ClientLevel level);
    }

    private final Category category;
    private final Kind     kind;
    private final String   wireKey;
    private final int      min;
    private final int      max;
    private final @Nullable Sampler sampler;

    GameEvent(Category category, Kind kind, String wireKey, int min, int max,
              @Nullable Sampler sampler) {
        if (min > max) {
            throw new IllegalArgumentException(wireKey + ": min (" + min + ") > max (" + max + ")");
        }
        this.category = category;
        this.kind     = kind;
        this.wireKey  = TelemetryProtocol.requireValidKey(wireKey);
        this.min      = min;
        this.max      = max;
        this.sampler  = sampler;
    }

    /**
     * Falla al cargar la clase, no a mitad de una partida, si alguien anade un
     * evento con una clave repetida: dos casillas distintas compartirian
     * linea de cable y la placa no podria separarlas.
     */
    static {
        Set<String> seen = new HashSet<>();
        for (GameEvent event : values()) {
            if (!seen.add(event.wireKey)) {
                throw new IllegalStateException("Clave de cable duplicada: " + event.wireKey);
            }
        }
    }

    public Category category() { return category; }
    public Kind     kind()     { return kind; }
    public String   wireKey()  { return wireKey; }
    public int      min()      { return min; }
    public int      max()      { return max; }
    public boolean  isPeriodic() { return kind == Kind.PERIODIC; }

    /** Recorta un valor al rango que este evento promete a la placa. */
    public int clamp(int value) {
        return TelemetryProtocol.clamp(value, min, max);
    }

    /** Solo valido para eventos PERIODIC; los EDGE los calcula GameEventsTracker. */
    public int sample(LocalPlayer player, ClientLevel level) {
        if (sampler == null) {
            throw new IllegalStateException(name() + " no tiene sampler: es un evento EDGE");
        }
        return clamp(sampler.sample(player, level));
    }

    /** Clave de traduccion del nombre mostrado en la pestana Eventos. */
    public String labelKey() {
        return "gui.serialcraft.events.event." + name().toLowerCase(Locale.ROOT);
    }
}
