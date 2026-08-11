package com.serialcraft;

import com.serialcraft.block.ModBlocks;
import com.serialcraft.block.entity.ModBlockEntities;
import com.serialcraft.board.BoardRegistry;
import com.serialcraft.item.ModItems;
import com.serialcraft.network.ModNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerialCraft implements ModInitializer {

    public static final String MOD_ID = "serialcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Mod-id de CC:Tweaked, para la dependencia opcional. */
    private static final String CC_MOD_ID = "computercraft";

    @Override
    public void onInitialize() {
        LOGGER.info("Inicializando SerialCraft");

        // 1. Registros de juego. El orden importa: ModItems referencia bloques
        //    en sus inicializadores estaticos, asi que los bloques van primero.
        ModBlocks.initialize();
        ModItems.initialize();
        ModBlockEntities.initialize();

        // 2. Indice de placas. Se engancha a los eventos de carga/descarga de
        //    BlockEntity, por lo que repuebla solo al cargar chunks; ya no
        //    depende de que el jugador vuelva a colocar cada bloque tras un
        //    reinicio del servidor.
        BoardRegistry.initialize();

        // 3. Red.
        ModNetworking.registerPayloads();
        ModNetworking.registerServerHandlers();


        LOGGER.info("SerialCraft listo");
    }
}
