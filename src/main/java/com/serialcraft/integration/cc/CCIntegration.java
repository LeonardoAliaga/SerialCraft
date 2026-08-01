package com.serialcraft.integration.cc;

import com.serialcraft.SerialCraft;
import com.serialcraft.block.entity.ModBlockEntities;
import dan200.computercraft.api.peripheral.PeripheralLookup;

public final class CCIntegration {

    private CCIntegration() {}

    public static void register() {
        PeripheralLookup.get().registerForBlockEntity(
                (entity, context) -> new ArduinoPeripheral(entity),
                ModBlockEntities.CONNECTOR_BLOCK_ENTITY
        );
        // Logger en vez de System.out.println: en un servidor dedicado stdout
        // no lleva marca de tiempo ni nivel, y se pierde entre la salida de
        // otros mods.
        SerialCraft.LOGGER.info("CC:Tweaked detectado, periferico registrado");
    }
}
