package com.serialcraft.integration.cc;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import com.serialcraft.block.entity.ConnectorBlockEntity;
import org.jetbrains.annotations.NotNull;

public class ArduinoPeripheral implements IPeripheral {
    private final ConnectorBlockEntity entity;

    public ArduinoPeripheral(ConnectorBlockEntity entity) {
        this.entity = entity;
    }

    // Este es el nombre del periférico en Lua (ej: peripheral.find("arduino_connector"))
    @NotNull
    @Override
    public String getType() {
        return "arduino_connector";
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other || (other instanceof ArduinoPeripheral && ((ArduinoPeripheral) other).entity == this.entity);
    }

    // ==========================================
    // FUNCIONES EXPUESTAS A LUA
    // ==========================================

    @LuaFunction
    public final String ping() {
        return "¡Hola Ronald! Conexión exitosa entre CC:Tweaked y SerialCraft.";
    }

    @LuaFunction
    public final void enviarComando(String comando) {
        // Por ahora solo lo imprimimos, pronto lo enlazaremos al Socket/USB real
        System.out.println("[SerialCraft -> Arduino] Comando recibido desde Lua: " + comando);
    }
}