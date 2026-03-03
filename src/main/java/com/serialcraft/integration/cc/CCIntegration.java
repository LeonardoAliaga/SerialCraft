package com.serialcraft.integration.cc;

import dan200.computercraft.api.peripheral.PeripheralLookup;
import com.serialcraft.block.entity.ModBlockEntities;

public class CCIntegration {
    public static void register() {
        // Registramos nuestro bloque conector como un periférico válido
        PeripheralLookup.get().registerForBlockEntity(
                (entity, context) -> new ArduinoPeripheral(entity),
                ModBlockEntities.CONNECTOR_BLOCK_ENTITY
        );
        System.out.println("[SerialCraft] ¡CC: Tweaked detectado e integrado correctamente!");
    }
}