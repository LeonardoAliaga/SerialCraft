package com.serialcraft.network.guard;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de tasa por jugador (token bucket).
 *
 * MOTIVO (vulnerabilidad real del codigo original):
 * SerialInputPayload lo emite el cliente una vez por CADA linea que escupe la
 * placa. Un sketch con Serial.println() dentro de loop() a 115200 baudios
 * genera miles de lineas por segundo, y cada una provocaba en el servidor un
 * recorrido de todas las placas del jugador mas un updateNeighborsAt().
 * Sin limite es un generador de lag trivial, y ni siquiera hace falta un
 * cliente modificado: basta un sketch mal escrito.
 *
 * Se aplica ANTES de tocar el mundo. Los paquetes excedentes se descartan en
 * silencio: responder al cliente convertiria esto en un amplificador de
 * trafico.
 */
public final class PacketRateLimiter {

    private final int  capacity;
    private final long refillIntervalNanos;

    /** Estado mutable protegido por su propio monitor. */
    private static final class Bucket {
        long tokens;
        long lastRefillNanos;
        Bucket(long tokens, long stamp) { this.tokens = tokens; this.lastRefillNanos = stamp; }
    }

    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * @param capacity        rafaga maxima tolerada
     * @param refillPerSecond tokens repuestos por segundo (tasa sostenida)
     */
    public PacketRateLimiter(int capacity, int refillPerSecond) {
        this.capacity            = Math.max(1, capacity);
        this.refillIntervalNanos = 1_000_000_000L / Math.max(1, refillPerSecond);
    }

    public boolean tryAcquire(ServerPlayer player) {
        return tryAcquire(player.getUUID());
    }

    /** @return true si el paquete puede procesarse; false si debe descartarse. */
    public boolean tryAcquire(UUID id) {
        long now = System.nanoTime();
        Bucket bucket = buckets.computeIfAbsent(id, k -> new Bucket(capacity, now));
        synchronized (bucket) {
            long elapsed = now - bucket.lastRefillNanos;
            if (elapsed >= refillIntervalNanos) {
                long refills = elapsed / refillIntervalNanos;
                bucket.tokens = Math.min(capacity, bucket.tokens + refills);
                bucket.lastRefillNanos += refills * refillIntervalNanos;
            }
            if (bucket.tokens <= 0) return false;
            bucket.tokens--;
            return true;
        }
    }

    /** Libera el estado de un jugador que se desconecta (evita fuga de memoria). */
    public void forget(UUID id) { buckets.remove(id); }

    public void clear() { buckets.clear(); }
}
