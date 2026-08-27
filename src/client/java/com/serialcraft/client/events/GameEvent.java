package com.serialcraft.client.events;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Catalogo de datos del juego que la pestana "Eventos" puede enviar a la
 * placa fisica.
 *
 * Cada entrada es autocontenida: su clave de cable, su categoria en la UI, si
 * es un valor que se muestrea a intervalos (PERIODIC) o un suceso puntual que
 * se detecta por flanco (EDGE), y como leer su valor desde el jugador y el
 * nivel del cliente. Anadir un dato nuevo (por ejemplo el bioma o el modo de
 * juego) es una entrada mas en este enum: ni GameEventsTracker ni EventsPage
 * necesitan tocarse para que aparezca en la lista, se recorren con values().
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

    GAME_TIME(Category.WORLD, Kind.PERIODIC, "mc_time",
            (player, level) -> (int) (level.getDefaultClockTime() % 24000L)),

    DAY_PHASE(Category.WORLD, Kind.PERIODIC, "mc_isday",
            (player, level) -> (level.getDefaultClockTime() % 24000L) < 12000L ? 1 : 0),

    WEATHER(Category.WORLD, Kind.PERIODIC, "mc_weather",
            (player, level) -> level.isThundering() ? 2 : (level.isRaining() ? 1 : 0)),

    HEALTH(Category.PLAYER, Kind.PERIODIC, "mc_health",
            (player, level) -> Math.round(player.getHealth())),

    HUNGER(Category.PLAYER, Kind.PERIODIC, "mc_hunger",
            (player, level) -> player.getFoodData().getFoodLevel()),

    SATURATION(Category.PLAYER, Kind.PERIODIC, "mc_saturation",
            (player, level) -> Math.round(player.getFoodData().getSaturationLevel())),

    XP_LEVEL(Category.PLAYER, Kind.PERIODIC, "mc_level",
            (player, level) -> player.experienceLevel),

    OXYGEN(Category.PLAYER, Kind.PERIODIC, "mc_air",
            (player, level) -> player.getMaxAirSupply() <= 0
                    ? 100
                    : Math.round(player.getAirSupply() * 100f / player.getMaxAirSupply())),

    ON_FIRE(Category.COMBAT, Kind.PERIODIC, "mc_fire",
            (player, level) -> player.isOnFire() ? 1 : 0),

    DAMAGE_TAKEN(Category.COMBAT, Kind.EDGE, "mc_damage", null),

    DEATH(Category.COMBAT, Kind.EDGE, "mc_death", null);

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
    private final @Nullable Sampler sampler;

    GameEvent(Category category, Kind kind, String wireKey, @Nullable Sampler sampler) {
        this.category = category;
        this.kind     = kind;
        this.wireKey  = wireKey;
        this.sampler  = sampler;
    }

    public Category category() { return category; }
    public Kind     kind()     { return kind; }
    public String   wireKey()  { return wireKey; }
    public boolean  isPeriodic() { return kind == Kind.PERIODIC; }

    /** Solo valido para eventos PERIODIC; los EDGE los calcula GameEventsTracker. */
    public int sample(LocalPlayer player, ClientLevel level) {
        if (sampler == null) {
            throw new IllegalStateException(name() + " no tiene sampler: es un evento EDGE");
        }
        return sampler.sample(player, level);
    }

    /** Clave de traduccion del nombre mostrado en la pestana Eventos. */
    public String labelKey() {
        return "gui.serialcraft.events.event." + name().toLowerCase(Locale.ROOT);
    }
}