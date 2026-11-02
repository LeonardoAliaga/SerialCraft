package com.serialcraft.datagen;

import com.serialcraft.block.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.world.item.Items;
import java.util.concurrent.CompletableFuture;

/** Identical recipes and advancement IDs on both loaders. */
public final class SerialCraftRecipes extends RecipeProvider {
    public SerialCraftRecipes(HolderLookup.Provider registries, RecipeOutput output) { super(registries, output); }
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

    public static final class Runner extends RecipeProvider.Runner {
        public Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) { super(output, registries); }
        @Override protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
            return new SerialCraftRecipes(registries, output);
        }
        @Override public String getName() { return "SerialCraft Recipes"; }
    }
}
