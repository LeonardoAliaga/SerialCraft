package com.serialcraft;

import com.serialcraft.block.ModBlocks;
import com.serialcraft.block.entity.ModBlockEntities;
import com.serialcraft.item.ModItems;
import com.serialcraft.network.ModNetworking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerialCraft {

    public static final String MOD_ID = "serialcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public void initialize() {
        LOGGER.info("Inicializando SerialCraft");

        // 1. Registros de juego. El orden importa: ModItems referencia bloques
        //    en sus inicializadores estaticos, asi que los bloques van primero.
        ModBlocks.initialize();
        ModItems.initialize();
        ModBlockEntities.initialize();

        // Loader entrypoints attach lifecycle hooks to the shared board index.
        ModNetworking.registerPayloads();
        ModNetworking.registerServerHandlers();

        LOGGER.info("SerialCraft listo");
    }
}
