package com.serialcraft;

import com.serialcraft.block.ModBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

/** Restores the original recipes on the 26.2 datagen API. */
public final class ModRecipeGenerator extends FabricRecipeProvider {
    public ModRecipeGenerator(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput recipes) {
        return new com.serialcraft.datagen.SerialCraftRecipes(registries, recipes);
    }

    @Override public String getName() { return "SerialCraft Recipes"; }
}
