package com.serialcraft.connection;

import net.minecraft.network.chat.Component;

/**
 * Contrato comun de todo transporte hacia la placa fisica.
 *
 * SerialHandler y WifiHandler hacian lo mismo con firmas distintas, y
 * ConnectionManager tenia que conocer los detalles de ambos. Con una interfaz,
 * anadir un tercer transporte (Bluetooth, MQTT, un puente por puerto serie
 * virtual) no obliga a tocar ConnectionManager.
 */
public interface BoardLink {

    /** Nombre corto del transporte, para la UI y los logs. */
    String name();

    boolean isConnected();

    /** Envia una linea. Debe ser seguro llamarlo desde el hilo del cliente. */
    void send(String message);

    /** Cierra el transporte. Debe ser idempotente. */
    void disconnect();

    /** Descripcion legible del extremo actual ("COM3", "192.168.1.50"). */
    Component describe();
}
