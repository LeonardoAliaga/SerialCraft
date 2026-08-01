package com.serialcraft.board;

import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Indice de placas IO activas, por dimension y por dueno.
 *
 * Reemplaza a {@code SerialCraft.activeIOBlocks}, que era un
 * {@code Set<ArduinoIOBlockEntity>} estatico y global. Ese set tenia cuatro
 * problemas, tres de ellos funcionales y no cosmeticos:
 *
 *  1. FUGA DE MEMORIA. Solo se anadia en setPlacedBy y solo se quitaba en
 *     playerWillDestroy. Una placa destruida por TNT, empujada por un piston,
 *     borrada con /fill o /setblock, o simplemente descargada con su chunk,
 *     quedaba en el set para siempre. Como un BlockEntity referencia su Level,
 *     cada entrada muerta mantenia viva una dimension entera.
 *
 *  2. NO SOBREVIVIA AL REINICIO. Al arrancar el servidor el set esta vacio y
 *     nada lo repuebla: las placas cargadas de disco jamas se registraban. Tras
 *     un reinicio la pestana "Placas" salia vacia y el modo INPUT dejaba de
 *     funcionar hasta volver a colocar cada bloque a mano. Este es el bug mas
 *     grave del proyecto y es invisible en singleplayer si nunca reinicias.
 *
 *  3. IGNORABA LA DIMENSION. Se comparaba solo el UUID del dueno, asi que una
 *     placa en el Nether reaccionaba a un comando serial emitido desde el
 *     Overworld.
 *
 *  4. RECORRIDO LINEAL. Cada mensaje serial iteraba TODAS las placas del
 *     servidor para filtrar por dueno. Aqui el indice ya esta particionado por
 *     dimension+dueno, asi que el coste es proporcional a las placas de ese
 *     jugador en esa dimension.
 *
 * El ciclo de vida se engancha a ServerBlockEntityEvents, que es lo que
 * realmente cubre carga y descarga de chunks; setPlacedBy no basta.
 */
public final class BoardRegistry {

    private BoardRegistry() {}

    /** dimension -> dueno -> placas. */
    private static final Map<ResourceKey<Level>, Map<UUID, List<ArduinoIOBlockEntity>>> INDEX =
            new ConcurrentHashMap<>();

    /** Placas sin dueno asignado, por dimension. Se consultan para reclamar. */
    private static final Map<ResourceKey<Level>, List<ArduinoIOBlockEntity>> UNOWNED =
            new ConcurrentHashMap<>();

    // ── Registro de eventos ───────────────────────────────────────────────

    public static void initialize() {
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((be, level) -> {
            if (be instanceof ArduinoIOBlockEntity io) add(level, io);
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((be, level) -> {
            if (be instanceof ArduinoIOBlockEntity io) remove(level, io);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
    }

    // ── Mutacion ──────────────────────────────────────────────────────────

    private static void add(ServerLevel level, ArduinoIOBlockEntity io) {
        UUID owner = io.getOwnerUUID();
        if (owner == null) {
            UNOWNED.computeIfAbsent(level.dimension(), k -> Collections.synchronizedList(new ArrayList<>()))
                   .add(io);
        } else {
            INDEX.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>())
                 .computeIfAbsent(owner, k -> Collections.synchronizedList(new ArrayList<>()))
                 .add(io);
        }
    }

    private static void remove(ServerLevel level, ArduinoIOBlockEntity io) {
        var byOwner = INDEX.get(level.dimension());
        if (byOwner != null) {
            byOwner.values().forEach(list -> list.remove(io));
            byOwner.entrySet().removeIf(e -> e.getValue().isEmpty());
        }
        var unowned = UNOWNED.get(level.dimension());
        if (unowned != null) unowned.remove(io);
    }

    /**
     * Reindexa una placa cuyo dueno acaba de cambiar (colocacion o reclamo).
     * Debe llamarse SIEMPRE que se toque ownerUUID, o el indice queda mentiroso.
     */
    public static void reindex(ServerLevel level, ArduinoIOBlockEntity io) {
        remove(level, io);
        add(level, io);
    }

    public static void clear() {
        INDEX.clear();
        UNOWNED.clear();
    }

    // ── Consulta ──────────────────────────────────────────────────────────

    /**
     * Placas del jugador en la dimension donde el jugador esta AHORA.
     * Devuelve una copia: el llamador puede iterarla sin bloquear ni arriesgar
     * ConcurrentModificationException si un chunk se descarga a mitad.
     */
    public static List<ArduinoIOBlockEntity> boardsOf(ServerPlayer player) {
        var byOwner = INDEX.get(player.level().dimension());
        if (byOwner == null) return List.of();
        var list = byOwner.get(player.getUUID());
        if (list == null) return List.of();
        synchronized (list) {
            return List.copyOf(list);
        }
    }

    /** Numero total de placas indexadas. Util para diagnostico y limites. */
    public static int totalBoards() {
        int total = 0;
        for (var byOwner : INDEX.values()) {
            for (var list : byOwner.values()) total += list.size();
        }
        return total;
    }

    /** Numero de placas de un jugador en una dimension concreta. */
    public static int countFor(ResourceKey<Level> dimension, UUID owner) {
        var byOwner = INDEX.get(dimension);
        if (byOwner == null) return 0;
        var list = byOwner.get(owner);
        return list == null ? 0 : list.size();
    }
}
