package com.serialcraft.integration.cc;

import com.serialcraft.SerialCraft;
import com.serialcraft.block.entity.ConnectorBlockEntity;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Periferico de CC:Tweaked para la laptop.
 *
 * ESTADO REAL: incompleto a proposito, y ahora dicho en voz alta. El original
 * exponia enviarComando() con un System.out.println y nada mas, dando la falsa
 * impresion de que la integracion funcionaba. No puede funcionar tal como esta
 * planteada: el puerto serial vive en el JVM del CLIENTE, y este periferico se
 * ejecuta en el servidor. Enlazarlos requiere enrutar el comando al cliente del
 * dueno de la laptop, que es exactamente lo que ya hace SerialOutputPayload.
 *
 * Lo que se puede hacer hoy sin cambios de arquitectura queda expuesto:
 * consultar el estado. Escribir requiere el paso por red, marcado como TODO
 * con la ruta concreta en vez de un stub silencioso.
 */
public class ArduinoPeripheral implements IPeripheral {

    private final ConnectorBlockEntity entity;

    public ArduinoPeripheral(ConnectorBlockEntity entity) {
        this.entity = entity;
    }

    @Override
    public @NotNull String getType() { return "arduino_connector"; }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return this == other
                || (other instanceof ArduinoPeripheral p && p.entity == this.entity);
    }

    // ── API Lua ───────────────────────────────────────────────────────────

    /** @return true si la laptop reporta una sesion de hardware activa. */
    @LuaFunction
    public final boolean isConnected() {
        return entity.isConnected();
    }

    @LuaFunction
    public final int getBaudRate() {
        return entity.getBaudRate();
    }

    /**
     * TODO: para implementarlo, publicar un payload S2C nuevo dirigido al dueno
     * de la laptop y reutilizar ConnectionManager.sendMessageToBoard en el
     * cliente. Requiere anadir un campo ownerUUID a ConnectorBlockEntity, que
     * hoy no tiene.
     */
    @LuaFunction
    public final boolean sendCommand(String command) {
        SerialCraft.LOGGER.debug("sendCommand() aun no implementado: {}", command);
        return false;
    }
}
