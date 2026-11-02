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
        return new RecipeProvider(registries, recipes) {
            @Override public void buildRecipes() {
                shaped(RecipeCategory.REDSTONE, ModBlocks.CONNECTOR_BLOCK)
                        .pattern("GGG").pattern("ICI").pattern("SRS")
                        .define('G', Items.GLASS_PANE).define('I', Items.IRON_INGOT)
                        .define('C', Items.COMPARATOR).define('S', Items.STONE_SLAB).define('R', Items.REDSTONE)
                        .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(output);
                shaped(RecipeCategory.REDSTONE, ModBlocks.IO_BLOCK)
                        .pattern("TRT").pattern("ICI").pattern("SDS")
                        .define('T', Items.REDSTONE_TORCH).define('R', Items.REPEATER)
                        .define('I', Items.IRON_INGOT).define('C', Items.COMPARATOR)
                        .define('S', Items.SMOOTH_STONE).define('D', Items.REDSTONE)
                        .unlockedBy("has_repeater", has(Items.REPEATER)).save(output);
            }
        };
    }

    @Override public String getName() { return "SerialCraft Recipes"; }
}
