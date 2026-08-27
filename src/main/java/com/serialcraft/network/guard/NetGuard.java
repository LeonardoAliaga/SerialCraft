package com.serialcraft.network.guard;

import com.serialcraft.SerialCraft;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Validaciones obligatorias para TODO paquete C2S.
 *
 * Regla de oro: un BlockPos que llega del cliente es un dato hostil, no una
 * coordenada. El codigo original hacia directamente
 * {@code context.player().level().getBlockEntity(payload.pos())} en los cuatro
 * receptores, lo que abria tres agujeros a la vez:
 *
 *  1. CARGA FORZADA DE CHUNKS. getBlockState/getBlockEntity sobre un chunk no
 *     cargado lo genera. Un cliente modificado que mande posiciones aleatorias
 *     a 20 paquetes/segundo obliga al servidor a generar terreno sin limite:
 *     es un DoS de un solo jugador, sin permisos especiales.
 *  2. ALCANCE INFINITO. Se podia configurar o apagar una placa a 10 millones
 *     de bloques de distancia, sin verla nunca.
 *  3. SIN DUENO. RemoteTogglePayload y ConnectorConfigPayload no comprobaban
 *     ownerUUID en absoluto: cualquiera podia apagar las placas de cualquiera.
 *
 * Estos helpers cierran los tres. Se usan SIEMPRE dentro de server.execute(),
 * es decir ya en el hilo del servidor.
 */
public final class NetGuard {

    private NetGuard() {}

    /** Alcance maximo de interaccion, en bloques. Generoso pero finito. */
    public static final double MAX_INTERACT_DISTANCE    = 12.0D;
    /** Alcance para operar placas desde la laptop (mismo chunk-radius util). */
    public static final double MAX_MANAGEMENT_DISTANCE  = 64.0D;

    private static final String BYPASS_PERMISSION = "serialcraft.admin.bypass";
    private static final int    BYPASS_OP_LEVEL   = 2;

    /** Patron precompilado para filtrar caracteres de control sin instanciar regex por llamada. */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}]");

    /**
     * Resuelve un BlockEntity a partir de una posicion enviada por el cliente.
     *
     * @return el BlockEntity del tipo pedido, o null si la peticion no supera
     *         alguna de las validaciones (chunk no cargado, fuera de alcance,
     *         tipo incorrecto, altura invalida).
     */
    public static <T extends BlockEntity> @Nullable T resolve(
            ServerPlayer player, BlockPos pos, Class<T> type, double maxDistance) {

        if (pos == null) {
            SerialCraft.LOGGER.debug("BlockPos nulo recibido de {}", player.getGameProfile().name());
            return null;
        }

        // FIX: En los mapeos recientes, usamos level() y casteamos a ServerLevel.
        ServerLevel level = (ServerLevel) player.level();

        // Altura valida del mundo. Descarta enteros absurdos de entrada.
        if (level.isOutsideBuildHeight(pos)) {
            SerialCraft.LOGGER.debug("Posicion {} fuera de altura valida para {}", pos, player.getGameProfile().name());
            return null;
        }

        // Alcance. Se compara al cuadrado para evitar la raiz cuadrada.
        double distanceSq = player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (distanceSq > maxDistance * maxDistance) {
            SerialCraft.LOGGER.debug("Posicion {} fuera de alcance ({:.2f}m > {:.2f}m) para {}",
                    pos, Math.sqrt(distanceSq), maxDistance, player.getGameProfile().name());
            return null;
        }

        // CLAVE: isLoaded() NO genera el chunk. Cierra el DoS de carga forzada.
        if (!level.isLoaded(pos)) {
            SerialCraft.LOGGER.debug("Chunk en {} no cargado para {}", pos, player.getGameProfile().name());
            return null;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            SerialCraft.LOGGER.debug("No hay BlockEntity en {} para {}", pos, player.getGameProfile().name());
            return null;
        }

        if (!type.isInstance(be)) {
            SerialCraft.LOGGER.debug("BlockEntity en {} no es del tipo esperado {} (actual: {}) para {}",
                    pos, type.getSimpleName(), be.getClass().getSimpleName(), player.getGameProfile().name());
            return null;
        }

        return type.cast(be);
    }

    public static <T extends BlockEntity> @Nullable T resolve(
            ServerPlayer player, BlockPos pos, Class<T> type) {
        return resolve(player, pos, type, MAX_INTERACT_DISTANCE);
    }

    /**
     * Comprueba propiedad.
     *
     * Diferencia importante frente al original: alli la condicion era
     * {@code ownerUUID == null || ownerUUID.equals(...)}, de modo que una placa
     * sin dueno (colocada por comando, dispensador o generada por estructura)
     * quedaba abierta a cualquiera. Aqui una placa sin dueno se RECLAMA para el
     * primer jugador que la opere, en vez de quedar publica para siempre.
     */
    public static boolean canOperate(ServerPlayer player, @Nullable java.util.UUID ownerUUID) {
        if (ownerUUID == null) return true;                 // se reclamara arriba
        if (ownerUUID.equals(player.getUUID())) return true;
        return hasBypass(player);
    }

    public static boolean hasBypass(ServerPlayer player) {
        return Permissions.check(player, BYPASS_PERMISSION, BYPASS_OP_LEVEL);
    }

    public static void denyOwnership(ServerPlayer player) {
        player.sendSystemMessage(
                Component.translatable("message.serialcraft.not_owner"));
    }

    /**
     * Sanea una cadena recibida del cliente antes de guardarla en NBT.
     *
     * El codigo original guardaba en el bloque, tal cual, lo que llegara en
     * ConfigPayload. Con ByteBufCodecs.STRING_UTF8 el limite por defecto son
     * 32767 caracteres: eso son ~64 KB de NBT por placa, replicados a cada
     * cliente en getUpdateTag y persistidos en la region. Colocar unos cientos
     * de placas asi corrompe el guardado por tamano.
     *
     * Ademas se filtran los codigos de formato (seccion) para que un ID de
     * placa no pueda inyectar color ni ofuscacion en el chat de otros.
     */
    public static String sanitize(@Nullable String raw, int maxLength, String fallback) {
        if (raw == null) return fallback;
        String cleaned = CONTROL_CHARS.matcher(raw.replace('\u00A7', ' ')).replaceAll("").trim();
        if (cleaned.isEmpty()) return fallback;
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength).trim();
            if (cleaned.isEmpty()) return fallback;
        }
        return cleaned;
    }

    public static void logRejected(String packet, ServerPlayer player) {
        // FIX: GameProfile es un 'record' en Authlib actual, el getter es name()
        SerialCraft.LOGGER.debug("Paquete {} rechazado para {}", packet, player.getGameProfile().name());
    }

    public static void logRejected(String packet, ServerPlayer player, String reason) {
        SerialCraft.LOGGER.debug("Paquete {} rechazado para {}: {}", packet, player.getGameProfile().name(), reason);
    }
}