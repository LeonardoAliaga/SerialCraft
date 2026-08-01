package com.serialcraft.connection;

import com.fazecast.jSerialComm.SerialPort;
import com.serialcraft.SerialCraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Transporte USB/serial.
 *
 * Correcciones de concurrencia frente al original:
 *
 *  1. AUTO-JOIN. readLoop() capturaba cualquier excepcion y llamaba a
 *     disconnect(), que a su vez hacia serialThread.join(500). Como readLoop
 *     se ejecuta EN serialThread, el hilo se esperaba a si mismo: bloqueo de
 *     medio segundo en cada error de lectura, con el puerto ya medio cerrado.
 *     Ahora el cierre desde el propio hilo lector no hace join.
 *
 *  2. CARRERA connect/disconnect. El puerto vivia en un campo publico estatico
 *     de SerialCraftClient, escrito desde el hilo de UI y leido desde el hilo
 *     lector sin sincronizacion ni volatile. Ahora es un campo volatile
 *     encapsulado aqui, con connect/disconnect sincronizados.
 *
 *  3. EXCEPCIONES SILENCIADAS. printStackTrace() escribe en stderr sin
 *     contexto. Ahora va al logger del mod con el nombre del puerto.
 */
public class SerialHandler implements BoardLink {

    private static final int    READ_BUFFER_SIZE   = 1024;
    private static final int    READ_TIMEOUT_MS    = 50;
    private static final int    JOIN_TIMEOUT_MS    = 1000;
    private static final int    IDLE_SLEEP_MS      = 2;
    private static final String THREAD_NAME        = "SerialCraft-Reader";

    private volatile @Nullable SerialPort port;
    private volatile @Nullable Thread     readerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override public String name() { return "USB"; }

    @Override
    public boolean isConnected() {
        SerialPort p = port;
        return p != null && p.isOpen();
    }

    public String getPortName() {
        SerialPort p = port;
        return (p != null && p.isOpen()) ? p.getSystemPortName() : "";
    }

    public int getBaudRate() {
        SerialPort p = port;
        return (p != null && p.isOpen()) ? p.getBaudRate() : 0;
    }

    @Override
    public Component describe() {
        return isConnected()
                ? Component.literal(getPortName())
                : Component.translatable("gui.serialcraft.status.disconnected");
    }

    // ══════════════════════════════════════════════════════════════════════

    public synchronized Component connect(String portName, int baudRate) {
        if (isConnected()) return Component.translatable("message.serialcraft.already_connected");

        SerialPort[] available = SerialPort.getCommPorts();
        if (available.length == 0) return Component.translatable("message.serialcraft.no_ports");

        SerialPort target = null;
        for (SerialPort candidate : available) {
            if (candidate.getSystemPortName().equalsIgnoreCase(portName)) { target = candidate; break; }
        }
        if (target == null) {
            return Component.translatable("message.serialcraft.port_not_found", portName);
        }

        try {
            target.setBaudRate(baudRate);
            if (!target.openPort()) {
                return Component.translatable("message.serialcraft.port_busy", portName);
            }
            target.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, READ_TIMEOUT_MS, 0);

            this.port = target;
            running.set(true);

            Thread thread = new Thread(this::readLoop, THREAD_NAME);
            thread.setDaemon(true);   // no debe impedir que el juego cierre
            this.readerThread = thread;
            thread.start();

            return Component.translatable("message.serialcraft.connected", portName);

        } catch (Exception e) {
            SerialCraft.LOGGER.warn("Fallo al abrir el puerto {}", portName, e);
            closeQuietly(target);
            this.port = null;
            return Component.translatable("message.serialcraft.error", String.valueOf(e.getMessage()));
        }
    }

    @Override
    public void disconnect() {
        // Distinguir quien pide el cierre evita el auto-join descrito arriba.
        boolean fromReaderThread = Thread.currentThread() == readerThread;
        shutdown(!fromReaderThread);
    }

    private synchronized void shutdown(boolean waitForThread) {
        if (!running.compareAndSet(true, false) && port == null) return;

        SerialPort p = port;
        port = null;
        closeQuietly(p);

        Thread thread = readerThread;
        readerThread = null;
        if (waitForThread && thread != null && thread.isAlive()) {
            try {
                thread.join(JOIN_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void closeQuietly(@Nullable SerialPort p) {
        if (p == null) return;
        try { p.closePort(); } catch (Exception ignored) {}
    }

    @Override
    public void send(String message) {
        SerialPort p = port;
        if (p == null || !p.isOpen()) return;
        try {
            byte[] bytes = (message + '\n').getBytes(StandardCharsets.UTF_8);
            p.writeBytes(bytes, bytes.length);
        } catch (Exception e) {
            SerialCraft.LOGGER.warn("Error escribiendo en el puerto serial", e);
            disconnect();
        }
    }

    // ══════════════════════════════════════════════════════════════════════

    /**
     * Bucle lector. Acumula bytes hasta encontrar un salto de linea y entrega
     * lineas completas a ConnectionManager.
     *
     * El throttling ya NO vive aqui. El original decidia en este hilo si
     * descartar mensajes segun globalSerialSpeed, y en los modos lento y normal
     * simplemente TIRABA todos los mensajes menos el ultimo, en vez de
     * agruparlos. Ahora este hilo solo trocea lineas; la politica de tasa vive
     * en ConnectionManager, que es quien conoce el coste de un paquete.
     */
    private void readLoop() {
        StringBuilder buffer     = new StringBuilder();
        byte[]        readBuffer = new byte[READ_BUFFER_SIZE];

        while (running.get()) {
            SerialPort p = port;
            if (p == null || !p.isOpen()) break;

            try {
                int read = p.readBytes(readBuffer, readBuffer.length);
                if (read > 0) {
                    buffer.append(new String(readBuffer, 0, read, StandardCharsets.UTF_8));
                    drainLines(buffer);
                } else {
                    Thread.sleep(IDLE_SLEEP_MS);
                }

                // Proteccion contra una placa que nunca envia '\n': sin esto el
                // StringBuilder crece sin limite hasta agotar la memoria del
                // cliente. El original no lo contemplaba.
                if (buffer.length() > READ_BUFFER_SIZE * 8) buffer.setLength(0);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running.get()) SerialCraft.LOGGER.warn("Error leyendo del puerto serial", e);
                break;
            }
        }
        shutdown(false); // sin join: estamos dentro del propio hilo lector
    }

    private void drainLines(StringBuilder buffer) {
        int newlineIndex;
        while ((newlineIndex = buffer.indexOf("\n")) != -1) {
            String line = buffer.substring(0, newlineIndex).trim();
            buffer.delete(0, newlineIndex + 1);
            if (!line.isEmpty()) ConnectionManager.onMessageReceived(line);
        }
    }
}
