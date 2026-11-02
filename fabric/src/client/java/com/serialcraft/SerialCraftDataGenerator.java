package com.serialcraft;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/** Generates the existing recipes and their unlock advancements without changing IDs. */
public final class SerialCraftDataGenerator implements DataGeneratorEntrypoint {
    @Override public void onInitializeDataGenerator(FabricDataGenerator generator) {
        generator.createPack().addProvider(ModRecipeGenerator::new);
    }
}
